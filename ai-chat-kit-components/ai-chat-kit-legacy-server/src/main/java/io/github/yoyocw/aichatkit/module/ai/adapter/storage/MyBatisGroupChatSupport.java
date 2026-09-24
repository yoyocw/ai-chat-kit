package io.github.yoyocw.aichatkit.module.ai.adapter.storage;

import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.agent.AiAgentDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatConversationDO;
import io.github.yoyocw.aichatkit.module.ai.dal.dataobject.groupchat.AiGroupChatMemberDO;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.agent.AiAgentMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatConversationMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMemberMapper;
import io.github.yoyocw.aichatkit.module.ai.dal.mysql.groupchat.AiGroupChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import static io.github.yoyocw.aichatkit.compat.framework.common.exception.util.ServiceExceptionUtil.exception;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.MIN_MEMBER_COUNT;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.MAX_MEMBER_COUNT;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.GROUP_MEMBER_COUNT_INVALID;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.GROUP_MEMBER_INVALID;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.GROUP_CONVERSATION_NOT_EXISTS;
import static io.github.yoyocw.aichatkit.module.ai.enums.ErrorCodeConstants.GROUP_MESSAGE_GENERATING;

/** 原表群聊成员与会话操作，供旧管理入口及发送准备复用；写方法由调用方建立同源事务。 */
@Service
@RequiredArgsConstructor
public class MyBatisGroupChatSupport {
    /** 原群聊会话归属及行锁。 */
    private final AiGroupChatConversationMapper conversationMapper;
    /** 原会话成员及持久化排序。 */
    private final AiGroupChatMemberMapper memberMapper;
    /** 当前生成态查询。 */
    private final AiGroupChatMessageMapper messageMapper;
    /** 宿主真实智能体目录。 */
    private final AiAgentMapper agentMapper;

    /** @return 当前启用目录，沿用目录查询顺序 */
    public List<AiGroupMemberSnapshot> getAgents() {
        List<AiGroupMemberSnapshot> result = new ArrayList<>();
        for (AiAgentDO agent : agentMapper.selectEnabledList()) { result.add(toAgent(agent)); }
        return result;
    }
    /** 按给定顺序重建成员；调用方必须先取得会话锁及事务。 */
    public void replaceMembers(Long conversationId, Long userId, List<String> memberCodes) {
        memberMapper.deleteByConversationId(conversationId, userId);
        for (int i = 0; i < memberCodes.size(); i++) {
            memberMapper.insert(AiGroupChatMemberDO.builder().conversationId(conversationId).userId(userId)
                    .agentCode(memberCodes.get(i)).sortOrder(i).build());
        }
    }

    /** 规范化编码并按真实目录排序；无效数量、重复或停用成员沿用原业务错误。 */
    public List<String> validateMembers(List<String> requestedCodes) {
        if (requestedCodes == null || requestedCodes.size() < MIN_MEMBER_COUNT
                || requestedCodes.size() > MAX_MEMBER_COUNT) {
            throw exception(GROUP_MEMBER_COUNT_INVALID);
        }
        Set<String> canonicalCodes = new HashSet<String>();
        for (String requestedCode : requestedCodes) {
            if (!StringUtils.hasText(requestedCode)) {
                throw exception(GROUP_MEMBER_INVALID, requestedCodes);
            }
            String canonicalCode = requestedCode.trim().toUpperCase(Locale.ROOT);
            if (!canonicalCodes.add(canonicalCode)) {
                throw exception(GROUP_MEMBER_INVALID, requestedCodes);
            }
        }
        List<AiAgentDO> agents = agentMapper.selectEnabledListByCodes(canonicalCodes);
        if (agents.size() != canonicalCodes.size()) {
            throw exception(GROUP_MEMBER_INVALID, requestedCodes);
        }
        List<String> normalized = new ArrayList<String>();
        for (AiAgentDO agent : agents) {
            normalized.add(agent.getCode());
        }
        return normalized;
    }

    /** 读取已保存成员顺序；数量异常时拒绝发送。 */
    public List<String> loadMemberCodes(Long conversationId, Long userId) {
        List<String> result = new ArrayList<String>();
        for (AiGroupChatMemberDO member : memberMapper.selectListByConversationId(conversationId, userId)) {
            result.add(member.getAgentCode());
        }
        if (result.size() < MIN_MEMBER_COUNT || result.size() > MAX_MEMBER_COUNT) {
            throw exception(GROUP_MEMBER_COUNT_INVALID);
        }
        return result;
    }

    /** 按当前用户取得会话行锁，不扩大租户过滤。 */
    public AiGroupChatConversationDO validateConversationForUpdate(Long id, Long userId) {
        AiGroupChatConversationDO conversation = conversationMapper.selectByIdAndUserIdForUpdate(id, userId);
        if (conversation == null) {
            throw exception(GROUP_CONVERSATION_NOT_EXISTS);
        }
        return conversation;
    }

    /** 当前会话已有生成任务时拒绝重叠发送。 */
    public void ensureNotGenerating(Long conversationId, Long userId) {
        if (messageMapper.selectGeneratingCount(conversationId, userId) > 0) {
            throw exception(GROUP_MESSAGE_GENERATING);
        }
    }

    /** 沿用原会话更新时间写入，不覆盖发送时的新标题。 */
    public void touchConversation(Long conversationId) {
        AiGroupChatConversationDO current = conversationMapper.selectById(conversationId);
        conversationMapper.updateById(AiGroupChatConversationDO.builder().id(conversationId)
                .title(current.getTitle()).build());
    }

    /** 按原目录名称计算默认标题。 */
    public String buildDefaultTitle(List<String> memberCodes) {
        return buildDefaultTitleByAgents(toAgents(memberCodes));
    }

    /** 保持前两个成员名称组合及原中文格式。 */
    public String buildDefaultTitleByAgents(List<AiGroupMemberSnapshot> members) {
        return members.get(0).getName().replace("智能体", "") + "＋"
                + members.get(1).getName().replace("智能体", "") + "协同群";
    }

    /** 重查成员启用状态并恢复会话中的顺序。 */
    public List<AiGroupMemberSnapshot> loadEnabledAgents(List<String> memberCodes) {
        List<AiAgentDO> agents = agentMapper.selectEnabledListByCodes(new HashSet<String>(memberCodes));
        if (agents.size() != memberCodes.size()) {
            throw exception(GROUP_MEMBER_INVALID, memberCodes);
        }
        return toAgents(memberCodes, agents);
    }

    /** 仅从真实目录复制模型使用的成员字段。 */
    private AiGroupMemberSnapshot toAgent(AiAgentDO agent) {
        return new AiGroupMemberSnapshot(agent.getCode(), agent.getName(), agent.getRole());
    }

    /** 按保存的编码顺序还原目录快照，缺失成员沿用原错误。 */
    public List<AiGroupMemberSnapshot> toAgents(List<String> memberCodes) {
        return toAgents(memberCodes, agentMapper.selectListByCodes(memberCodes));
    }

    /** 按保存的编码顺序还原目录快照，缺失成员沿用原错误。 */
    private List<AiGroupMemberSnapshot> toAgents(List<String> memberCodes, List<AiAgentDO> agents) {
        Map<String, AiAgentDO> agentMap = new LinkedHashMap<String, AiAgentDO>();
        for (AiAgentDO agent : agents) {
            agentMap.put(agent.getCode(), agent);
        }
        List<AiGroupMemberSnapshot> result = new ArrayList<AiGroupMemberSnapshot>();
        for (String code : memberCodes) {
            AiAgentDO agent = agentMap.get(code);
            if (agent == null) {
                throw exception(GROUP_MEMBER_INVALID, memberCodes);
            }
            result.add(toAgent(agent));
        }
        return result;
    }
}

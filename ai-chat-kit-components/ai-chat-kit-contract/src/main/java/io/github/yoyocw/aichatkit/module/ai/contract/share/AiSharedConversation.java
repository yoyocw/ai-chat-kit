package io.github.yoyocw.aichatkit.module.ai.contract.share;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;

/** 分享专用不可变响应，不包含用户租户身份、模型会话、工具凭据或内部运行信息。 */
public final class AiSharedConversation {
    /** 当前公开会话标题。 */
    private final String title;
    /** 当前群聊有序成员编码，单聊为空。 */
    private final List<String> memberCodes;
    /** 当前已完成消息的动态安全视图，按消息id升序但不公开id。 */
    private final List<AiSharedMessage> messages;
    /** 有效分享授权路径读取的当前已存成员快照，不向匿名目录查询伪造登录身份。 */
    private final List<AiGroupMemberSnapshot> members;
    /** 复制已校验分享结果；禁止将完整结果写入日志。 */
    public AiSharedConversation(String title, List<String> memberCodes, List<AiSharedMessage> messages) {
        this(title, memberCodes, messages, Collections.emptyList());
    }

    /** 复制真实分享内容及成员快照，旧memberCodes接口保持。 */
    public AiSharedConversation(String title, List<String> memberCodes, List<AiSharedMessage> messages,
            List<AiGroupMemberSnapshot> members) {
        this.title = title;
        this.memberCodes = Collections.unmodifiableList(new ArrayList<String >(memberCodes));
        this.messages = Collections.unmodifiableList(new ArrayList<AiSharedMessage >(messages));
        this.members = Collections.unmodifiableList(new ArrayList<AiGroupMemberSnapshot>(members));
    }
    /** @return 当前公开会话标题 */
    public String getTitle() { return title; }
    /** @return 当前群聊有序成员编码，单聊为空 */
    public List<String> getMemberCodes() { return memberCodes; }
    /** @return 当前已完成消息的动态安全视图，按消息id升序但不公开id */
    public List<AiSharedMessage> getMessages() { return messages; }
    /** @return 已存名称与职责；缺历史快照时拒绝，不能用编码当名称 */
    public List<AiGroupMemberSnapshot> getMembers() {
        if (members.size() != memberCodes.size()) { throw new IllegalStateException("群聊成员快照缺失，需要真实数据补全"); }
        return members;
    }
}

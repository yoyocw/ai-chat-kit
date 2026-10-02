package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;
import io.github.yoyocw.aichatkit.ai.adapter.jdbc.entity.AiMemberEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.MIN_MEMBER_COUNT;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiGroupChatConstants.MAX_MEMBER_COUNT;

/** 成员持久化快照边界；历史空值只能补真实数据，不能从编码猜测名称或职责。 */
final class AiJdbcMemberSnapshots {
    /** 无状态的数据边界工具不创建实例。 */
    private AiJdbcMemberSnapshots() { }

    /** @return 已完整保存的成员，旧行缺字段时失败 */
    static AiGroupMemberSnapshot read(AiMemberEntity row) {
        AiGroupMemberSnapshot member = new AiGroupMemberSnapshot(row.getAgentCode(),
                row.getAgentName(), row.getAgentRole());
        validate(member);
        return member;
    }

    /** 创建与成员变更共用真实目录快照插入路径。 */
    static void insert(AiJdbcAccess access, AiJdbcScope scope, Long conversationId, List<AiGroupMemberSnapshot> members) {
        access.requireTransaction();
        for (int i = 0; i < members.size(); i++) {
            AiGroupMemberSnapshot member = members.get(i);
            AiMemberEntity row = scope.initialize(new AiMemberEntity(), "group");
            row.setConversationId(conversationId); row.setAgentCode(member.getCode());
            row.setAgentName(member.getName()); row.setAgentRole(member.getRole()); row.setSortOrder(i);
            if (access.members().insert(row) != 1) { throw new IllegalStateException("AI 群聊成员保存失败"); }
        }
    }

    /** 校验当前真实成员集合并提取有序编码，创建/更新/历史读取使用一致边界。 */
    static List<String> codes(List<AiGroupMemberSnapshot> members) {
        if (members == null || members.size() < MIN_MEMBER_COUNT || members.size() > MAX_MEMBER_COUNT) { throw missing(); }
        List<String> result = new ArrayList<>();
        Set<String> unique = new HashSet<>();
        for (AiGroupMemberSnapshot member : members) {
            validate(member);
            if (!unique.add(member.getCode())) { throw missing(); }
            result.add(member.getCode());
        }
        return result;
    }

    /** 列表按会话判断完整性；不抛历史数据异常，也不将数据库或权限异常吞为待补全。 */
    static boolean complete(List<AiGroupMemberSnapshot> members) {
        if (members == null || members.size() < MIN_MEMBER_COUNT || members.size() > MAX_MEMBER_COUNT) { return false; }
        Set<String> unique = new HashSet<>();
        for (AiGroupMemberSnapshot member : members) {
            if (!valid(member) || !unique.add(member.getCode())) { return false; }
        }
        return true;
    }

    /** @return 沿用旧群聊由前两名真实成员生成的标题，不使用虚构目录名称 */
    static String defaultTitle(List<AiGroupMemberSnapshot> members) {
        codes(members);
        return members.get(0).getName().replace("智能体", "") + "＋"
                + members.get(1).getName().replace("智能体", "") + "协同群";
    }

    /** 同可信目录字段限制，保证数据能够完整写入及展示。 */
    private static void validate(AiGroupMemberSnapshot member) {
        if (!valid(member)) { throw missing(); }
    }

    /** 同一字段规则同时用于严格执行读取与列表完整性判定。 */
    private static boolean valid(AiGroupMemberSnapshot member) {
        return member != null && member.getCode() != null && member.getCode().matches("[A-Z0-9_-]{1,128}")
                && !"ORCHESTRATOR".equals(member.getCode()) && text(member.getName(), 256)
                && text(member.getRole(), 4000);
    }

    /** 必须存在真实有界文本，不改变名称或职责原值。 */
    private static boolean text(String value, int max) { return value != null && !value.trim().isEmpty() && value.length() <= max; }
    /** 不回显成员职责或其他业务正文。 */
    private static IllegalStateException missing() {
        return new IllegalStateException("群聊成员真实快照缺失或无效，请按升级脚本说明补全真实数据");
    }
}

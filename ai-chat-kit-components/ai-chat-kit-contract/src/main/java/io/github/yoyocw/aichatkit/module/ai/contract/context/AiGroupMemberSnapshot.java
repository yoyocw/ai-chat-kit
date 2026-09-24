package io.github.yoyocw.aichatkit.module.ai.contract.context;

/** 异步执行使用的不可变成员快照，避免捕获可变宿主VO。 */
public final class AiGroupMemberSnapshot implements AiGroupMember {
    /** 稳定成员编码。 */
    private final String code;
    /** 展示名称。 */
    private final String name;
    /** 职责说明。 */
    private final String role;
    /** 从已验证宿主成员复制本轮字段，不提供授权能力。 */
    public AiGroupMemberSnapshot(String code, String name, String role) {
        this.code = code;
        this.name = name;
        this.role = role;
    }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getRole() { return role; }
}

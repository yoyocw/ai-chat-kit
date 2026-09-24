package io.github.yoyocw.aichatkit.module.ai.contract.error;

/** 宿主身份边界的安全失败分类；传输状态由入口适配器映射，不依赖 HTTP。 */
public enum AiIdentityError {
    /** 本地明确没有登录凭据或可信会话已经过期。 */
    UNAUTHENTICATED,
    /** 已明确违反归属约束或真实权限源拒绝操作。 */
    FORBIDDEN,
    /** 宿主端口明确报告身份或权限依赖不可用，不能用于猜测旧异常。 */
    DEPENDENCY_UNAVAILABLE,
    /** 已确认的部署配置或必要宿主能力缺失。 */
    CONFIGURATION,
    /** 核验原因未知或旧客户端已丢失分类信息，不能视为用户未登录。 */
    VERIFICATION_FAILED
}

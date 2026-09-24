package io.github.yoyocw.aichatkit.module.ai.contract.error;

/** 跨宿主执行错误契约；固定数字码和安全文案用于 SSE、持久化及宿主异常转换。 */
public enum AiExecutionError {
    /** 单聊分享无效，统一隐藏未找到、撤销和过期差异。 */
    CHAT_SHARE_NOT_EXISTS(1_509_000_014, "AI 对话分享不存在、已取消或已过期"),
    /** 群聊分享无效。 */
    GROUP_SHARE_NOT_EXISTS(1_509_000_015, "AI 群聊分享不存在、已取消或已过期"),
    /** 分享期限超出既有1至30天边界。 */
    SHARE_VALID_DAYS_INVALID(1_509_000_022, "分享有效天数必须为 1 至 30 天"),
    /** 独立事务重试后仍无法生成唯一分享码。 */
    SHARE_CODE_GENERATE_FAILED(1_509_000_023, "分享码生成失败，请稍后重试"),
    /** 群聊助手占位不存在或不属于当前用户。 */
    GROUP_MESSAGE_NOT_EXISTS(1_509_000_011, "AI 群聊消息不存在"),
    /** 群聊已终态或停止 CAS 未成功。 */
    GROUP_MESSAGE_NOT_GENERATING(1_509_000_012, "当前群聊消息不在生成中"),
    /** 与既有接口一致的安全业务错误。 */
    CHAT_MESSAGE_NOT_EXISTS(1_509_000_002, "AI 消息不存在"),
    /** 与既有接口一致的安全业务错误。 */
    CHAT_MESSAGE_NOT_GENERATING(1_509_000_004, "当前消息不在生成中"),
    /** 与既有接口一致的安全业务错误。 */
    BAILIAN_CONFIG_INVALID(1_509_000_005, "AI 模型或应用配置不可用，请联系管理员检查配置"),
    /** 与既有接口一致的安全业务错误。 */
    BAILIAN_CALL_FAILED(1_509_000_006, "AI 服务暂时不可用，请稍后重试"),
    /** 与既有接口一致的安全业务错误。 */
    GROUP_WORKFLOW_OUTPUT_INVALID(1_509_000_013, "百炼群聊工作流输出格式无效"),
    /** 与既有接口一致的安全业务错误。 */
    BAILIAN_QUOTA_UNAVAILABLE(1_509_000_024, "AI 服务余额或额度不足，请联系管理员处理"),
    /** 与既有接口一致的安全业务错误。 */
    BAILIAN_RATE_LIMITED(1_509_000_025, "AI 服务请求较多，请稍后重试"),
    /** 与既有接口一致的安全业务错误。 */
    BAILIAN_TIMEOUT(1_509_000_026, "AI 服务响应超时，请稍后重试"),
    /** 与既有接口一致的安全业务错误。 */
    BAILIAN_RESPONSE_INCOMPLETE(1_509_000_027, "AI 回复未完整生成，请重试");

    /** 会话过期只用于清理后重试，不包含第三方响应原文。 */
    public static final String BAILIAN_SESSION_EXPIRED_MESSAGE = "AI 会话已失效，请重新发起对话";
    /** 与已有 AI 接口兼容的业务错误编号。 */
    private final int code;
    /** 可展示给用户的固定文案。 */
    private final String msg;

    AiExecutionError(int code, String msg) { this.code = code; this.msg = msg; }
    /** @return 稳定业务错误编号 */
    public int getCode() { return code; }
    /** @return 不包含第三方原文的安全文案 */
    public String getMsg() { return msg; }
}

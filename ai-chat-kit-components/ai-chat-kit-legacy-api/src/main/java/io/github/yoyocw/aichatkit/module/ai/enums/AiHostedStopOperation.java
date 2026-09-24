package io.github.yoyocw.aichatkit.module.ai.enums;

/** AI 自有停止票据的固定操作和权限，不接受请求指定权限字符串。 */
public enum AiHostedStopOperation {
    /** 单聊助手消息停止。 */
    SINGLE_STOP("single-stop", "ai:chat:stopSeed"),
    /** 群聊助手消息停止。 */
    GROUP_STOP("group-stop", "aigroup:chat:message:stop");
    /** 票据操作码。 */
    private final String code;
    /** 固定功能权限。 */
    private final String permission;
    AiHostedStopOperation(String code, String permission) { this.code = code; this.permission = permission; }
    /** @return 固定操作码 */
    public String getCode() { return code; }
    /** @return 固定停止权限 */
    public String getPermission() { return permission; }
}

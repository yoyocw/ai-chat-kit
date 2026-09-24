package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.module.ai.config.AiHostedStopReceiver;
import lombok.Getter;
import lombok.Setter;

/** Redis 内部票据元数据；无原始凭据，不生成 toString，不可当外部请求身份使用。 */
@Getter
@Setter
public class AiHostedStopTicket {
    /** 协议版本，当前固定为1。 */
    private int version;
    /** 原用户访问令牌记录ID，消费时查库，不是刷新令牌。 */
    private Long userSessionId;
    /** 本次原业务机器访问令牌记录ID，绝非 AI 消费者会话ID。 */
    private Long originalMachineSessionId;
    /** 已验证原用户ID，必须为正。 */
    private Long userId;
    /** 固定 single-stop/group-stop 操作。 */
    private String operation;
    /** 目标助手消息ID，必须为正。 */
    private Long messageId;
    /** 签发时完整可信部署绑定，包含原caller与独立consumer。 */
    private AiHostedStopReceiver receiver;
    /** 签发时刻，Unix毫秒。 */
    private long issuedAt;
    /** 绝对到期时刻，Unix毫秒，不超过签发后30秒及原两会话到期。 */
    private long expiresAt;
}

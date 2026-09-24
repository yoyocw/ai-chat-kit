package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;

/** 独立 v2 停止票据元数据；无原机器、用户或消费者Token，不兼容v1。 */
public final class AiStopTicket {
    /** 协议版本，严格为2。 */
    private final int version;
    /** 签发时完整接收方快照。 */
    private final AiStopReceiver receiver;
    /** 真实原用户会话快照。 */
    private final AiHostSession user;
    /** 真实原机器会话和应用绑定快照。 */
    private final AiStopMachineIdentity originalMachine;
    /** 固定单聊或群聊停止用途。 */
    private final AiChatMode mode;
    /** 原助手消息正数编号。 */
    private final Long messageId;
    /** 签发时刻，UTC Unix毫秒。 */
    private final long issuedAtMillis;
    /** 票据、原机器和用户期限最小值，UTC Unix毫秒，不续期。 */
    private final long expiresAtMillis;

    /** 创建不可变快照；实际身份及有效性由宿主源和协调层复核。 */
    @JsonCreator
    public AiStopTicket(
            @JsonProperty("version") int version,
            @JsonProperty("receiver") AiStopReceiver receiver,
            @JsonProperty("user") AiHostSession user,
            @JsonProperty("originalMachine") AiStopMachineIdentity originalMachine,
            @JsonProperty("mode") AiChatMode mode,
            @JsonProperty("messageId") Long messageId,
            @JsonProperty("issuedAtMillis") long issuedAtMillis,
            @JsonProperty("expiresAtMillis") long expiresAtMillis) {
        this.version = version;
        this.receiver = receiver;
        this.user = user;
        this.originalMachine = originalMachine;
        this.mode = mode;
        this.messageId = messageId;
        this.issuedAtMillis = issuedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    /** @return 协议版本，严格为2。 */
    public int getVersion() { return version; }
    /** @return 签发时完整接收方快照。 */
    public AiStopReceiver getReceiver() { return receiver; }
    /** @return 真实原用户会话快照。 */
    public AiHostSession getUser() { return user; }
    /** @return 真实原机器会话和应用绑定快照。 */
    public AiStopMachineIdentity getOriginalMachine() { return originalMachine; }
    /** @return 固定单聊或群聊停止用途。 */
    public AiChatMode getMode() { return mode; }
    /** @return 原助手消息正数编号。 */
    public Long getMessageId() { return messageId; }
    /** @return 签发时刻，UTC Unix毫秒。 */
    public long getIssuedAtMillis() { return issuedAtMillis; }
    /** @return 票据、原机器和用户期限最小值，UTC Unix毫秒，不续期。 */
    public long getExpiresAtMillis() { return expiresAtMillis; }
}

package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.compat.framework.common.exception.enums.GlobalErrorCodeConstants;
import io.github.yoyocw.aichatkit.compat.framework.common.util.json.JsonUtils;
import io.github.yoyocw.aichatkit.module.ai.config.AiHostedStopProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiHostedStopReceiver;
import io.github.yoyocw.aichatkit.module.ai.dal.redis.stop.AiHostedStopTicketRedisDAO;
import io.github.yoyocw.aichatkit.module.ai.enums.AiHostedStopOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import static io.github.yoyocw.aichatkit.compat.framework.common.exception.util.ServiceExceptionUtil.exception0;

/** 内部一次性停止授权；不暴露HTTP、不签工具JWT、不替代AI消息来源与CAS校验。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.hosted-stop", name = "enabled", havingValue = "true")
public class AiHostedStopAuthorizationService {
    /** 独立协议前缀，不匹配任何MCP凭据格式。 */
    private static final String PREFIX = "ai_hosted_stop_v1_";
    /** 单次票据最大生存期，毫秒。 */
    private static final long MAX_TTL_MILLIS = 30000L;
    /** JDK安全随机源，每张票据256位。 */
    private final SecureRandom random = new SecureRandom();
    /** 默认关闭的部署接收者配置。 */
    private final AiHostedStopProperties properties;
    /** 当前原身份与独立消费者校验，不持有原始令牌。 */
    private final AiHostedStopIdentityValidator identityValidator;
    /** 摘要键和一次性消费。 */
    private final AiHostedStopTicketRedisDAO ticketDAO;

    /**
     * 签发目标受限身份票据，AI仍须消费前核准来源；不能复用已结束send的临时机器会话。
     * @param originalAuthorization 本次原业务机器Bearer凭据，至少保持到消费结束
     * @param userAuthorization 当前用户Bearer凭据，只在认证侧同步读取
     * @param operation 服务端固定停止模式
     * @param messageId 正数目标助手消息ID
     * @param audience 必须命中部署配置且匹配当前原caller
     * @return 独立opaque凭据，禁止放入URL、SSE、日志或模型上下文
     */
    public String issue(String originalAuthorization, String userAuthorization, AiHostedStopOperation operation,
                        Long messageId, String audience) {
        AiHostedStopReceiver receiver = properties.requireReceiver(audience);
        validateTarget(operation, messageId);
        AiHostedStopResult identity = identityValidator.issueIdentity(
                originalAuthorization, userAuthorization, operation, receiver);
        long issuedAt = System.currentTimeMillis();
        AiHostedStopTicket ticket = createTicket(identity, receiver, operation, messageId, issuedAt);
        String json = JsonUtils.toJsonString(ticket);
        // 仅真正NX碰撞重试；Redis异常或未知结果直接向外抛出。
        for (int attempt = 0; attempt < 3; attempt++) {
            String credential = newCredential();
            long ttl = ticket.getExpiresAt() - System.currentTimeMillis();
            if (ttl <= 0) { reject(); }
            if (ticketDAO.create(digest(credential), json, ttl)) { return credential; }
        }
        throw new IllegalStateException("停止票据创建失败");
    }

    /**
     * 非消费检查，供AI来源预检使用；检查成功不能执行停止。
     * @param consumerAuthorization 独立AI消费者Bearer凭据
     * @param credential 专用opaque凭据
     * @param operation 固定入口模式
     * @param messageId 固定目标消息
     * @param expectedAudience 调用方预期的明确接收者，必须匹配票据及当前部署配置
     * @return 最新原调用方身份，不代表票据已消费
     */
    public AiHostedStopResult inspect(String consumerAuthorization, String credential,
                                             AiHostedStopOperation operation, Long messageId, String expectedAudience) {
        return validateAndConsume(consumerAuthorization, credential, operation, messageId, expectedAudience, false);
    }

    /**
     * AI来源预检通过后消费；成功后AI须在本地事务内复查来源与助手消息并CAS。
     * @param consumerAuthorization 独立AI消费者Bearer凭据
     * @param credential 专用opaque凭据，不允许重放或失败后放回
     * @param operation 固定入口模式
     * @param messageId 固定目标消息
     * @param expectedAudience 调用方预期的明确接收者，必须匹配票据及当前部署配置
     * @return 消费成功时的当前原身份；失败/超时不得执行，需新签票据重试
     */
    public AiHostedStopResult consume(String consumerAuthorization, String credential,
                                             AiHostedStopOperation operation, Long messageId, String expectedAudience) {
        return validateAndConsume(consumerAuthorization, credential, operation, messageId, expectedAudience, true);
    }

    /** 无数据库长事务；身份复核后原子消费，不能承诺与跨库状态更新exactly-once。 */
    private AiHostedStopResult validateAndConsume(String consumerAuthorization, String credential,
                                                         AiHostedStopOperation operation, Long messageId,
                                                         String expectedAudience, boolean consume) {
        if (!properties.isEnabled()) { throw new IllegalStateException("停止授权未启用"); }
        validateTarget(operation, messageId);
        if (expectedAudience == null || !expectedAudience.matches("[a-zA-Z0-9_-]{1,64}")) { reject(); }
        AiHostedStopReceiver receiver = properties.requireReceiver(expectedAudience);
        // 先核验独立消费者，不能让匿名或原业务机器通过票据查询探测原身份。
        identityValidator.validateConsumer(consumerAuthorization, receiver);
        if (credential == null || !credential.matches("ai_hosted_stop_v1_[A-Za-z0-9_-]{43}")) { reject(); }
        String keyDigest = digest(credential);
        String originalJson = ticketDAO.read(keyDigest);
        AiHostedStopTicket ticket = parseTicket(originalJson);
        validateTicket(ticket, operation, messageId);
        if (!expectedAudience.equals(ticket.getReceiver().getAudience())
                || !receiver.equals(ticket.getReceiver())) { reject(); }
        AiHostedStopResult current = identityValidator.revalidate(ticket, operation, receiver);
        if (ticket.getExpiresAt() > current.getSessionExpiresAt()) { reject(); }
        // 身份校验耗时不延长票据，最终执行前必须仍有效。
        validateTicket(ticket, operation, messageId);
        if (consume && !ticketDAO.consume(keyDigest, originalJson)) { reject(); }
        if (ticket.getExpiresAt() <= System.currentTimeMillis()) { reject(); }
        // 将票据期限一并带到提交前复查，消费成功不延长30秒授权窗口。
        return new AiHostedStopResult(current.getUserSessionId(), current.getOriginalMachineSessionId(),
                current.getUserId(), current.getOriginalCaller(), Math.min(current.getSessionExpiresAt(), ticket.getExpiresAt()));
    }

    /** 从受信身份复制最小元数据，原Token只在调用栈出现。 */
    private AiHostedStopTicket createTicket(AiHostedStopResult identity, AiHostedStopReceiver receiver,
                                      AiHostedStopOperation operation, Long messageId, long issuedAt) {
        AiHostedStopTicket ticket = new AiHostedStopTicket();
        ticket.setVersion(1);
        ticket.setUserSessionId(identity.getUserSessionId());
        ticket.setOriginalMachineSessionId(identity.getOriginalMachineSessionId());
        ticket.setUserId(identity.getUserId());
        ticket.setOperation(operation.getCode());
        ticket.setMessageId(messageId);
        ticket.setReceiver(receiver);
        ticket.setIssuedAt(issuedAt);
        ticket.setExpiresAt(Math.min(issuedAt + MAX_TTL_MILLIS, identity.getSessionExpiresAt()));
        return ticket;
    }

    /** 解析失败仅抛脱敏异常，不把Redis原文或解析异常链送入日志。 */
    private AiHostedStopTicket parseTicket(String json) {
        if (json == null || json.length() > 8192) { reject(); }
        try {
            return JsonUtils.getObjectMapper().readValue(json, AiHostedStopTicket.class);
        } catch (IOException | RuntimeException ex) {
            throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "停止票据无效");
        }
    }

    /** 校验固定用途、两原会话、目标及有效期，未知协议不兼容降级。 */
    private void validateTicket(AiHostedStopTicket ticket, AiHostedStopOperation operation, Long messageId) {
        long now = System.currentTimeMillis();
        if (ticket == null || ticket.getVersion() != 1 || ticket.getReceiver() == null
                || ticket.getUserId() == null || ticket.getUserId() <= 0
                || ticket.getUserSessionId() == null || ticket.getUserSessionId() <= 0
                || ticket.getOriginalMachineSessionId() == null || ticket.getOriginalMachineSessionId() <= 0
                || !Objects.equals(ticket.getOperation(), operation.getCode())
                || !Objects.equals(ticket.getMessageId(), messageId)
                || ticket.getIssuedAt() <= 0 || ticket.getIssuedAt() > now
                || ticket.getExpiresAt() <= now || ticket.getExpiresAt() <= ticket.getIssuedAt()
                || ticket.getExpiresAt() - ticket.getIssuedAt() > MAX_TTL_MILLIS) { reject(); }
    }

    /** 入口调用方只能给出固定操作及正数消息，不接受任意权限或路径。 */
    private void validateTarget(AiHostedStopOperation operation, Long messageId) {
        if (operation == null || messageId == null || messageId <= 0) { reject(); }
    }

    /** 独立随机票据，不是JWT，也不进入OAuth2令牌存储。 */
    private String newCredential() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 仅摘要用于Redis定位，不输出原文或摘要。 */
    private String digest(String credential) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(credential.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : bytes) { result.append(String.format("%02x", value & 0xff)); }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("停止凭据摘要算法不可用");
        }
    }

    /** 拒绝无效或已消费票据，不回显内部凭据和身份。 */
    private void reject() {
        throw exception0(GlobalErrorCodeConstants.UNAUTHORIZED.getCode(), "停止票据无效或已消费");
    }
}

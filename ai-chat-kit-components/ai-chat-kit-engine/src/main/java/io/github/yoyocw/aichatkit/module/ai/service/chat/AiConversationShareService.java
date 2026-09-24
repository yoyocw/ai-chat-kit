package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.ai.engine.transaction.AiTransactionExecutor;
import io.github.yoyocw.aichatkit.module.ai.config.AiShareProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiConversationSharePort;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiShareGrant;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiShareLease;
import io.github.yoyocw.aichatkit.module.ai.contract.share.AiSharedConversation;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.dao.DuplicateKeyException;
import java.security.SecureRandom;
import java.util.Objects;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiShareConstants.*;
import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.*;

/** 分享用例：创建与撤销使用真实宿主身份，公开读取使用固定部署内的随机分享码能力。 */
public class AiConversationShareService {
    /** 创建及撤销前最后捕获的可信身份来源，公开读取不调用。 */
    private final AiInvocationContextPort identities;
    /** AI 自有分享存储，固定部署命名空间。 */
    private final AiConversationSharePort storage;
    /** 与分享数据源对应的真实事务管理器。 */
    private final AiTransactionExecutor transactions;
    /** 可信部署分享地址，沿用旧单群YAML键。 */
    private final AiShareProperties properties;
    /** 可信宿主提供的签发事务定义快照，不允许请求改变传播语义。 */
    private final TransactionDefinition issueDefinition;
    /** 公开读取包含计数写入，保留所选存储的原事务语义。 */
    private final TransactionDefinition publicReadDefinition;
    /** 密码学安全随机源，128位随机码与旧32位hex格式兼容。 */
    private final SecureRandom random = new SecureRandom();

    /** 保留中立存储默认值：独立签发事务，公开读取为独立READ_COMMITTED可写事务。 */
    public AiConversationShareService(AiInvocationContextPort identities, AiConversationSharePort storage,
            AiTransactionExecutor transactions, AiShareProperties properties) {
        this(identities, storage, transactions, properties,
                new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW), defaultPublicDefinition());
    }

    /**
     * @param identities 真实身份端口，缺失时仅允许公开读
     * @param storage 已绑定真实资源的分享存储
     * @param transactions 同源执行器
     * @param properties 已配置分享地址，沿用默认构造要求
     * @param issueDefinition 旧宿主签发事务定义，复制后不受外部修改影响
     * @param publicReadDefinition 公开读取与计数的可信事务定义，宿主应提供可写定义
     */
    public AiConversationShareService(AiInvocationContextPort identities, AiConversationSharePort storage,
            AiTransactionExecutor transactions, AiShareProperties properties,
            TransactionDefinition issueDefinition, TransactionDefinition publicReadDefinition) {
        this.identities = identities;
        this.storage = storage;
        this.transactions = transactions;
        this.properties = properties;
        this.issueDefinition = new DefaultTransactionDefinition(Objects.requireNonNull(issueDefinition));
        this.publicReadDefinition = new DefaultTransactionDefinition(Objects.requireNonNull(publicReadDefinition));
    }

    /** @return 与既有中立分享读取一致的可写独立事务定义 */
    private static TransactionDefinition defaultPublicDefinition() {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return definition;
    }

    /**
     * 创建或复用有效分享；冲突退出本次事务调用后重试，宿主REQUIRED参与外层事务时不保证独立重试。
     * @param mode 固定聊天模式
     * @param conversationId 已授权会话编号
     * @param validDays 默认7天，允许1至30天；复用有效链接不改变原期限
     * @param expectedContext 宿主创建分享权限核验后的归属快照
     * @return 实际分享码、可信配置生成的URL和数据库到期时刻
     */
    public AiShareGrant issueForContext(AiChatMode mode, Long conversationId, Integer validDays, AiInvocationContext expectedContext) {
        requiredMode(mode);
        int days = validDays == null ? DEFAULT_VALID_DAYS : validDays;
        if (days < MIN_VALID_DAYS || days > MAX_VALID_DAYS) { throw new AiExecutionException(SHARE_VALID_DAYS_INVALID); }
        for (int attempt = 0; attempt < SHARE_CODE_GENERATION_ATTEMPTS; attempt++) {
            try {
                AiShareGrant grant = transactions.execute(issueDefinition, () -> {
                    AiInvocationContext context = capture(expectedContext);
                    String base = properties.baseUrl(mode);
                    AiShareLease lease = storage.issue(context, mode, conversationId, days, randomCode());
                    return new AiShareGrant(lease.getShareCode(), base + "/" + lease.getShareCode(), lease.getExpireTimeMillis());
                });
                return Objects.requireNonNull(grant, "分享事务未返回结果");
            } catch (DuplicateKeyException ex) {
                // 异常先离开本次事务再重试；宿主REQUIRED参与外层事务时仍保留原回滚语义。
                if (attempt == SHARE_CODE_GENERATION_ATTEMPTS - 1) { throw new AiExecutionException(SHARE_CODE_GENERATE_FAILED); }
            }
        }
        throw new AiExecutionException(SHARE_CODE_GENERATE_FAILED);
    }

    /** @param mode 固定模式 @param conversationId 会话编号 @param expectedContext 撤销权限核验后的完整归属 */
    public void revokeForContext(AiChatMode mode, Long conversationId, AiInvocationContext expectedContext) {
        transactions.runRequired(() -> {
            storage.revoke(capture(expectedContext), requiredMode(mode), conversationId);
        });
    }

    /**
     * 独立公开能力边界，不捕获创建者身份；固定部署namespace由存储实例绑定。
     * @param mode 固定公开入口模式
     * @param shareCode 唯一外部能力参数，32位小写hex
     * @return 当前已完成消息的安全动态视图，撤销/过期/父删除在返回前复核
     */
    public AiSharedConversation readPublic(AiChatMode mode, String shareCode) {
        return transactions.execute(publicReadDefinition, () -> {
            requiredMode(mode);
            if (shareCode == null || !shareCode.matches("[0-9a-f]{32}")) {
                throw new AiExecutionException(mode == AiChatMode.SINGLE ? CHAT_SHARE_NOT_EXISTS : GROUP_SHARE_NOT_EXISTS);
            }
            return storage.readPublic(mode, shareCode);
        });
    }

    /** 最后一次捕获身份须与权限核验时的namespace/tenant/actor完全一致。 */
    private AiInvocationContext capture(AiInvocationContext expected) {
        if (identities == null) { throw new IllegalStateException("AI 分享创建或撤销缺少真实宿主身份端口"); }
        if (expected == null) { throw new IllegalArgumentException("AI 分享授权上下文缺失"); }
        AiInvocationContext actual = identities.capture(expected.getActorId());
        if (actual == null || !Objects.equals(actual.getNamespace(), expected.getNamespace())
                || !Objects.equals(actual.getTenantId(), expected.getTenantId())
                || !Objects.equals(actual.getActorId(), expected.getActorId())) { throw new IllegalStateException("AI 授权前后身份不一致"); }
        return actual;
    }

    /** 生成不可预测128位随机值，不使用会话编号、时间或用户身份派生。 */
    private String randomCode() {
        byte[] bytes = new byte[16]; random.nextBytes(bytes);
        char[] alphabet = "0123456789abcdef".toCharArray(); char[] encoded = new char[32];
        for (int i = 0; i < bytes.length; i++) { encoded[i * 2] = alphabet[(bytes[i] & 255) >>> 4]; encoded[i * 2 + 1] = alphabet[bytes[i] & 15]; }
        return new String(encoded);
    }

    /** 缺模式拒绝，不退回跨模式查询。 */
    private AiChatMode requiredMode(AiChatMode mode) {
        if (mode == null) { throw new IllegalArgumentException("AI 分享模式缺失"); } return mode;
    }
}

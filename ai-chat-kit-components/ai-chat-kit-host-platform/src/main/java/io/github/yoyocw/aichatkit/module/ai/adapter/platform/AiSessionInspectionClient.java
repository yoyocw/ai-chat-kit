package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.module.ai.config.AiSessionInspectionProperties;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.inspection.PlatformInspectionRequest;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.inspection.PlatformInspectionResult;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.inspection.PlatformInspectionTransport;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** AI 宿主会话适配：保留部署租户、后台身份和权限裁决，传输安全由本模块受限客户端实现。 */
public class AiSessionInspectionClient implements AutoCloseable {
    /** 显式启用及主体租户边界，不从外部请求获取。 */
    private final AiSessionInspectionProperties properties;
    private final int adminUserType;
    /** 固定源站与消费者租户；凭据供应器按请求读取宿主当前令牌，不自动申请或续期。 */
    private final PlatformInspectionTransport client;
    private final AtomicBoolean closed = new AtomicBoolean();

    /** 构造时固化受限传输边界；无有效部署配置时立即失败。 */
    public AiSessionInspectionClient(AiSessionInspectionProperties properties, int adminUserType) {
        if (properties == null) { throw new AiIdentityException(AiIdentityError.CONFIGURATION); }
        this.properties = properties;
        this.adminUserType = adminUserType;
        try {
            // 构造阶段仅检查固定部署配置，不进行网络核验。
            this.client = new PlatformInspectionTransport(properties.getBaseUrl(), properties.getConsumerTenantId(),
                    properties::getConsumerAccessToken);
        } catch (RuntimeException ex) {
            throw new AiIdentityException(AiIdentityError.CONFIGURATION);
        }
    }

    /**
     * 复核服务层从可信来源构造的查询，不接受控制器直接绑定任意主体。
     * @param subject 固定模式、真实凭据或已验签的用户会话身份
     * @return 已满足权限且属于当前部署的后台身份，期限保持 UTC epoch 毫秒
     * @throws IllegalStateException 配置、网络、身份或权限无效；绝不回退旧会话 API
     */
    public PlatformInspectionResult inspect(PlatformInspectionRequest subject) {
        PlatformInspectionResult identity = inspectWithPermissionResult(subject);
        if (!identity.isPermissionsSatisfied()) { throw new AiIdentityException(AiIdentityError.FORBIDDEN); }
        return identity;
    }

    /** 签发器及可选宿主适配判断固定权限；只有真实会话有效时才返回权限布尔值，异常不能降为 false。 */
    public PlatformInspectionResult inspectWithPermissionResult(PlatformInspectionRequest subject) {
        try {
            Long tenantId = properties.getSubjectTenantId();
            if (!properties.isEnabled() || tenantId == null || tenantId < 0) {
                throw new AiIdentityException(AiIdentityError.CONFIGURATION);
            }
            // 消费者凭据属于部署配置；缺失不能被误报为终端用户未登录。
            if (properties.getConsumerAccessToken() == null || properties.getConsumerAccessToken().trim().isEmpty()) {
                throw new AiIdentityException(AiIdentityError.CONFIGURATION);
            }
            if (subject == null) { throw failure(); }
            if (!Objects.equals(tenantId, subject.getExpectedTenantId())) {
                throw new AiIdentityException(AiIdentityError.FORBIDDEN);
            }
            PlatformInspectionResult identity = client.inspect(subject);
            // 通用身份仍须满足 AI 的租户及后台用户边界；权限由各固定场景裁决。
            if (identity == null || identity.getUserType() == null || identity.getTenantId() == null) { throw failure(); }
            if (!Integer.valueOf(adminUserType).equals(identity.getUserType())
                    || !Objects.equals(tenantId, identity.getTenantId())) {
                throw new AiIdentityException(AiIdentityError.FORBIDDEN);
            }
            return identity;
        } catch (AiIdentityException ex) {
            throw new AiIdentityException(ex.getError());
        } catch (RuntimeException ex) {
            // 传输已合并网络、协议与身份失败，不能猜测成未登录或源不可用。
            throw failure();
        }
    }

    /** @return 只含固定安全描述的认证错误 */
    private AiIdentityException failure() { return new AiIdentityException(AiIdentityError.VERIFICATION_FAILED); }

    /** 关闭此实例拥有的受限 HTTP 传输资源。 */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) { client.close(); }
    }
}


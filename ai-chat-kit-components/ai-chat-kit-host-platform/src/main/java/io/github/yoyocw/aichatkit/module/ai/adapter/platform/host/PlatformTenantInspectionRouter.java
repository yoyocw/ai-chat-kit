package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiSessionInspectionClient;
import io.github.yoyocw.aichatkit.module.ai.config.AiSessionInspectionProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/** 按可信主体租户选择受限消费者；租户与消费者的关系仅来自部署配置。 */
public final class PlatformTenantInspectionRouter implements AutoCloseable {
    private final Map<Long, AiSessionInspectionClient> clients;
    private final AtomicBoolean closed = new AtomicBoolean();

    public PlatformTenantInspectionRouter(PlatformHostInspectionProperties properties) {
        this(properties, AiSessionInspectionClient::new);
    }

    PlatformTenantInspectionRouter(PlatformHostInspectionProperties properties,
            Function<AiSessionInspectionProperties, AiSessionInspectionClient> factory) {
        Map<Long, AiSessionInspectionClient> created = new LinkedHashMap<>();
        try {
            if (properties == null || properties.getInspections() == null
                    || properties.getInspections().isEmpty() || factory == null) {
                throw configuration();
            }
            for (PlatformTenantInspectionBinding binding : properties.getInspections()) {
                validate(binding);
                Long tenantId = binding.getTenantId();
                // 先判重再创建，避免覆盖已创建且待关闭的受限客户端。
                if (created.containsKey(tenantId)) { throw configuration(); }
                AiSessionInspectionClient client = factory.apply(toLegacyProperties(binding));
                if (client == null) { throw configuration(); }
                created.put(tenantId, client);
            }
            this.clients = created;
        } catch (RuntimeException ex) {
            closeAll(created);
            throw configuration();
        }
    }

    public OAuth2SessionInspectionRespDTO inspect(OAuth2SessionInspectionReqDTO request) {
        return select(request).inspect(request);
    }

    /** 与固定租户客户端一致地保留权限布尔值，供普通宿主权限端口判断。 */
    public OAuth2SessionInspectionRespDTO inspectWithPermissionResult(OAuth2SessionInspectionReqDTO request) {
        return select(request).inspectWithPermissionResult(request);
    }

    private AiSessionInspectionClient select(OAuth2SessionInspectionReqDTO request) {
        if (request == null || request.getExpectedTenantId() == null || closed.get()) {
            throw new AiIdentityException(AiIdentityError.FORBIDDEN);
        }
        AiSessionInspectionClient client = clients.get(request.getExpectedTenantId());
        if (client == null) { throw new AiIdentityException(AiIdentityError.FORBIDDEN); }
        return client;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) { closeAll(clients); }
    }

    private static AiSessionInspectionProperties toLegacyProperties(PlatformTenantInspectionBinding binding) {
        AiSessionInspectionProperties properties = new AiSessionInspectionProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(binding.getBaseUrl());
        properties.setConsumerAccessToken(binding.getConsumerAccessToken());
        // 每个消费者只能检查自身租户，禁止跨租户消费者复用。
        properties.setConsumerTenantId(binding.getTenantId());
        properties.setSubjectTenantId(binding.getTenantId());
        return properties;
    }

    private static void validate(PlatformTenantInspectionBinding binding) {
        if (binding == null || binding.getTenantId() == null || binding.getTenantId() < 0
                || blank(binding.getBaseUrl()) || blank(binding.getConsumerAccessToken())) {
            throw configuration();
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private static void closeAll(Map<Long, AiSessionInspectionClient> clients) {
        for (AiSessionInspectionClient client : clients.values()) {
            try { client.close(); } catch (RuntimeException ignored) { /* best-effort shutdown */ }
        }
    }

    private static AiIdentityException configuration() {
        return new AiIdentityException(AiIdentityError.CONFIGURATION);
    }
}

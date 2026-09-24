package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiSessionInspectionClient;
import io.github.yoyocw.aichatkit.module.ai.config.AiSessionInspectionProperties;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 多租户路由只消费部署绑定，不读取外部地址、凭据或任意租户选择。 */
class PlatformTenantInspectionRouterTest {

    /**
     * 防止所有租户复用同一消费者：每个可信 expectedTenantId 必须命中自己的受限客户端。
     */
    @Test
    void routesEachTenantOnlyToItsOwnRestrictedClient() {
        AiSessionInspectionClient tenant11 = mock(AiSessionInspectionClient.class);
        AiSessionInspectionClient tenant22 = mock(AiSessionInspectionClient.class);
        when(tenant11.inspect(any())).thenReturn(identity(11L, 101L));
        when(tenant22.inspect(any())).thenReturn(identity(22L, 202L));
        Map<Long, AiSessionInspectionClient> clients = new HashMap<>();
        clients.put(11L, tenant11);
        clients.put(22L, tenant22);
        List<AiSessionInspectionProperties> created = new ArrayList<>();

        try (PlatformTenantInspectionRouter router = new PlatformTenantInspectionRouter(
                properties(binding(11L, "https://tenant11.example", "token-11"),
                        binding(22L, "https://tenant22.example", "token-22")),
                item -> {
                    created.add(item);
                    return clients.get(item.getSubjectTenantId());
                })) {
            assertThat(router.inspect(request(11L)).getUserId()).isEqualTo(101L);
            verify(tenant11).inspect(any(OAuth2SessionInspectionReqDTO.class));
            verify(tenant22, never()).inspect(any(OAuth2SessionInspectionReqDTO.class));

            assertThat(router.inspect(request(22L)).getUserId()).isEqualTo(202L);
            verify(tenant22).inspect(any(OAuth2SessionInspectionReqDTO.class));
        }

        assertThat(created).hasSize(2).allSatisfy(item -> {
            assertThat(item.isEnabled()).isTrue();
            assertThat(item.getConsumerTenantId()).isEqualTo(item.getSubjectTenantId());
        });
        assertThat(created).extracting(AiSessionInspectionProperties::getSubjectTenantId)
                .containsExactly(11L, 22L);
    }

    /** 防止未知租户回退首个或 legacy 客户端。 */
    @Test
    void rejectsUnknownTenantWithoutCallingConfiguredClients() {
        AiSessionInspectionClient tenant11 = mock(AiSessionInspectionClient.class);
        try (PlatformTenantInspectionRouter router = new PlatformTenantInspectionRouter(
                properties(binding(11L, "https://tenant11.example", "token-11")), item -> tenant11)) {
            assertThatThrownBy(() -> router.inspect(request(99L)))
                    .isInstanceOfSatisfying(AiIdentityException.class,
                            error -> assertThat(error.getError()).isEqualTo(AiIdentityError.FORBIDDEN));
            verifyNoInteractions(tenant11);
        }
    }

    /** 防止重复绑定覆盖先前消费者，并确保构造失败回收已经创建的自有客户端。 */
    @Test
    void duplicateTenantFailsAndClosesAlreadyCreatedClient() {
        AiSessionInspectionClient first = mock(AiSessionInspectionClient.class);
        assertThatThrownBy(() -> new PlatformTenantInspectionRouter(
                properties(binding(11L, "https://first.example", "token-1"),
                        binding(11L, "https://duplicate.example", "token-2")), item -> first))
                .isInstanceOf(AiIdentityException.class);
        verify(first).close();
    }

    /** 防止 Spring 重复关闭或调用方重复 close 导致宿主停机异常。 */
    @Test
    void closeIsIdempotentForOwnedClients() {
        AiSessionInspectionClient owned = mock(AiSessionInspectionClient.class);
        PlatformTenantInspectionRouter router = new PlatformTenantInspectionRouter(
                properties(binding(11L, "https://tenant11.example", "token-11")), item -> owned);
        router.close();
        router.close();
        verify(owned).close();
    }

    private PlatformHostInspectionProperties properties(PlatformTenantInspectionBinding... bindings) {
        PlatformHostInspectionProperties properties = new PlatformHostInspectionProperties();
        properties.setInspections(Arrays.asList(bindings));
        return properties;
    }

    private PlatformTenantInspectionBinding binding(Long tenantId, String baseUrl, String token) {
        PlatformTenantInspectionBinding binding = new PlatformTenantInspectionBinding();
        binding.setTenantId(tenantId);
        binding.setBaseUrl(baseUrl);
        binding.setConsumerAccessToken(token);
        return binding;
    }

    private OAuth2SessionInspectionReqDTO request(Long tenantId) {
        OAuth2SessionInspectionReqDTO request = new OAuth2SessionInspectionReqDTO();
        request.setSubjectType("USER");
        request.setAccessToken("user-token");
        request.setExpectedTenantId(tenantId);
        request.setExpectedUserId(7L);
        return request;
    }

    private OAuth2SessionInspectionRespDTO identity(Long tenantId, Long userId) {
        OAuth2SessionInspectionRespDTO identity = new OAuth2SessionInspectionRespDTO();
        identity.setTenantId(tenantId);
        identity.setUserId(userId);
        return identity;
    }
}

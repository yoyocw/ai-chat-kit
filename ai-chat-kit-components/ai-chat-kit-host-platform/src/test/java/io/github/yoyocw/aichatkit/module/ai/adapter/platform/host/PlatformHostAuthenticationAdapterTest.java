package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.enums.UserTypeEnum;
import io.github.yoyocw.aichatkit.compat.framework.security.core.LoginUser;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.SecurityFrameworkUtils;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiSessionInspectionClient;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** White-box checks for trusted tenant selection and fail-closed live-session validation. */
class PlatformHostAuthenticationAdapterTest {

    @AfterEach
    void clearHostThreadContexts() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
        assertThat(RequestContextHolder.getRequestAttributes()).isNull();
        assertThat(SecurityFrameworkUtils.getLoginUser()).isNull();
        assertThat(TenantContextHolder.getTenantId()).isNull();
    }

    @Test
    void twoTenantsUseOnlyTheirOwnRestrictedConsumerAndOriginalBearer() {
        AiSessionInspectionClient tenant11 = mock(AiSessionInspectionClient.class);
        AiSessionInspectionClient tenant22 = mock(AiSessionInspectionClient.class);
        when(tenant11.inspectWithPermissionResult(any())).thenReturn(identity(11L, 101L, 1001L, true));
        when(tenant22.inspectWithPermissionResult(any())).thenReturn(identity(22L, 202L, 2002L, true));
        PlatformHostAuthenticationAdapter adapter = adapter(tenant11, tenant22);

        login(11L, 101L, 1001L, "user-token-11", null);
        AiInvocationContext first = adapter.captureCurrent();
        assertThat(first.getTenantId()).isEqualTo("11");
        assertThat(first.getActorId()).isEqualTo("101");
        verify(tenant11).inspectWithPermissionResult(org.mockito.ArgumentMatchers.argThat(query ->
                Long.valueOf(11L).equals(query.getExpectedTenantId())
                        && Long.valueOf(101L).equals(query.getExpectedUserId())
                        && "user-token-11".equals(query.getAccessToken())));
        verify(tenant22, never()).inspectWithPermissionResult(any());

        clearHostThreadContexts();
        login(22L, 202L, 2002L, "user-token-22", null);
        AiInvocationContext second = adapter.captureCurrent();
        assertThat(second.getTenantId()).isEqualTo("22");
        assertThat(second.getActorId()).isEqualTo("202");
        verify(tenant22).inspectWithPermissionResult(org.mockito.ArgumentMatchers.argThat(query ->
                Long.valueOf(22L).equals(query.getExpectedTenantId())
                        && Long.valueOf(202L).equals(query.getExpectedUserId())
                        && "user-token-22".equals(query.getAccessToken())));
    }

    @Test
    void forgedTenantHeaderCannotSelectAnotherConsumer() {
        AiSessionInspectionClient tenant11 = mock(AiSessionInspectionClient.class);
        AiSessionInspectionClient tenant22 = mock(AiSessionInspectionClient.class);
        when(tenant11.inspectWithPermissionResult(any())).thenReturn(identity(11L, 101L, 1001L, true));
        PlatformHostAuthenticationAdapter adapter = adapter(tenant11, tenant22);

        login(11L, 101L, 1001L, "user-token-11", "22");
        assertThat(adapter.captureCurrent().getTenantId()).isEqualTo("11");
        verify(tenant11).inspectWithPermissionResult(any());
        verify(tenant22, never()).inspectWithPermissionResult(any());
    }

    @Test
    void mismatchedTenantContextIsRejectedBeforeCallingAuthenticationSource() {
        AiSessionInspectionClient tenant11 = mock(AiSessionInspectionClient.class);
        AiSessionInspectionClient tenant22 = mock(AiSessionInspectionClient.class);
        PlatformHostAuthenticationAdapter adapter = adapter(tenant11, tenant22);
        login(11L, 101L, 1001L, "user-token-11", null);
        TenantContextHolder.setTenantId(22L);

        assertIdentityError(AiIdentityError.FORBIDDEN, adapter::captureCurrent);
        verify(tenant11, never()).inspectWithPermissionResult(any());
        verify(tenant22, never()).inspectWithPermissionResult(any());
    }

    @Test
    void crossTenantAuthenticationResponseIsRejected() {
        AiSessionInspectionClient tenant11 = mock(AiSessionInspectionClient.class);
        AiSessionInspectionClient tenant22 = mock(AiSessionInspectionClient.class);
        when(tenant11.inspectWithPermissionResult(any())).thenReturn(identity(22L, 101L, 1001L, true));
        PlatformHostAuthenticationAdapter adapter = adapter(tenant11, tenant22);
        login(11L, 101L, 1001L, "user-token-11", null);

        assertIdentityError(AiIdentityError.FORBIDDEN, adapter::captureCurrent);
    }

    @Test
    void revokedSessionIsRejectedWithoutUsingCachedLoginSnapshot() {
        AiSessionInspectionClient tenant11 = mock(AiSessionInspectionClient.class);
        AiSessionInspectionClient tenant22 = mock(AiSessionInspectionClient.class);
        when(tenant11.inspectWithPermissionResult(any()))
                .thenThrow(new AiIdentityException(AiIdentityError.UNAUTHENTICATED));
        PlatformHostAuthenticationAdapter adapter = adapter(tenant11, tenant22);
        login(11L, 101L, 1001L, "revoked-token", null);

        assertIdentityError(AiIdentityError.UNAUTHENTICATED, adapter::captureCurrent);
    }

    @Test
    void permissionRevocationReturnsFalseFromFreshInspection() {
        AiSessionInspectionClient tenant11 = mock(AiSessionInspectionClient.class);
        AiSessionInspectionClient tenant22 = mock(AiSessionInspectionClient.class);
        when(tenant11.inspectWithPermissionResult(any())).thenReturn(identity(11L, 101L, 1001L, false));
        PlatformHostAuthenticationAdapter adapter = adapter(tenant11, tenant22);
        login(11L, 101L, 1001L, "user-token-11", null);
        AiInvocationContext invocation = new AiInvocationContext("platform", "11", "101", "request-1");

        assertThat(adapter.hasPermission(invocation, "ai:chat:send")).isFalse();
        verify(tenant11).inspectWithPermissionResult(org.mockito.ArgumentMatchers.argThat(query ->
                query.getRequiredPermissions() != null && !query.getRequiredPermissions().isEmpty()));
    }

    private PlatformHostAuthenticationAdapter adapter(AiSessionInspectionClient tenant11,
                                                       AiSessionInspectionClient tenant22) {
        Map<Long, AiSessionInspectionClient> clients = new HashMap<>();
        clients.put(11L, tenant11);
        clients.put(22L, tenant22);
        PlatformHostInspectionProperties properties = new PlatformHostInspectionProperties();
        properties.setInspections(Arrays.asList(binding(11L), binding(22L)));
        PlatformTenantInspectionRouter router = new PlatformTenantInspectionRouter(properties,
                item -> clients.get(item.getSubjectTenantId()));
        return new PlatformHostAuthenticationAdapter(router);
    }

    private PlatformTenantInspectionBinding binding(long tenantId) {
        PlatformTenantInspectionBinding binding = new PlatformTenantInspectionBinding();
        binding.setTenantId(tenantId);
        binding.setBaseUrl("https://tenant" + tenantId + ".example");
        binding.setConsumerAccessToken("consumer-token-" + tenantId);
        return binding;
    }

    private void login(long tenantId, long userId, long sessionId, String bearer, String forgedTenantHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + bearer);
        if (forgedTenantHeader != null) {
            request.addHeader("tenant-id", forgedTenantHeader);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        LoginUser user = new LoginUser();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUserType(UserTypeEnum.ADMIN.getValue());
        user.setAccessTokenId(sessionId);
        user.setExpiresTime(LocalDateTime.now().plusMinutes(5));
        SecurityFrameworkUtils.setLoginUser(user, request);
        TenantContextHolder.setTenantId(tenantId);
    }

    private OAuth2SessionInspectionRespDTO identity(long tenantId, long userId, long sessionId,
                                                     boolean permissionsSatisfied) {
        OAuth2SessionInspectionRespDTO identity = new OAuth2SessionInspectionRespDTO();
        identity.setTenantId(tenantId);
        identity.setUserId(userId);
        identity.setUserType(UserTypeEnum.ADMIN.getValue());
        identity.setSessionId(sessionId);
        identity.setExpiresAtMillis(System.currentTimeMillis() + 300_000L);
        identity.setPermissionsSatisfied(permissionsSatisfied);
        return identity;
    }

    private void assertIdentityError(AiIdentityError expected, Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(AiIdentityException.class,
                        failure -> assertThat(failure.getError()).isEqualTo(expected));
    }
}

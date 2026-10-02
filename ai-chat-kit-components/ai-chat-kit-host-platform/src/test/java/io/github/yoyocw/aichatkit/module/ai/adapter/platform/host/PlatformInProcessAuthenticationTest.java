package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import io.github.yoyocw.aichatkit.testnative.framework.common.enums.UserTypeEnum;
import io.github.yoyocw.aichatkit.testnative.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.testnative.framework.security.core.LoginUser;
import io.github.yoyocw.aichatkit.testnative.framework.security.core.util.SecurityFrameworkUtils;
import io.github.yoyocw.aichatkit.testnative.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.testnative.module.system.api.oauth2.OAuth2TokenApiImpl;
import io.github.yoyocw.aichatkit.testnative.module.system.api.permission.PermissionApiImpl;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Test doubles check the adapter's fail-closed behavior; they are not a live Platform database. */
class PlatformInProcessAuthenticationTest {
    private final OAuth2TokenApiImpl tokens = new OAuth2TokenApiImpl();
    private final PermissionApiImpl permissions = new PermissionApiImpl();
    private final PlatformLocalBeanResolver beans;
    private final PlatformInProcessAuthenticationAdapter adapter;

    PlatformInProcessAuthenticationTest() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("oauth2TokenApiImpl", tokens);
        factory.registerSingleton("permissionApiImpl", permissions);
        beans = new PlatformLocalBeanResolver(factory, getClass().getClassLoader(), PlatformLocalBeanResolverTest.ROOT);
        adapter = new PlatformInProcessAuthenticationAdapter(new PlatformLocalSessionSource(beans, 1L, "platform"));
    }

    @AfterEach
    void clearThreadState() {
        RequestContextHolder.resetRequestAttributes();
        SecurityFrameworkUtils.clear();
        TenantContextHolder.clear();
    }

    @Test
    void ordinaryBearerRequiresRawTokenThenCurrentSessionRecord() {
        login("ordinary-token", 11L, 101L, 1001L);
        validRecords();

        AiInvocationContext context = adapter.captureCurrent();
        AiHostSession session = adapter.currentSession(context);

        assertThat(context.getNamespace()).isEqualTo("platform");
        assertThat(context.getTenantId()).isEqualTo("11");
        assertThat(context.getActorId()).isEqualTo("101");
        assertThat(session.getSessionId()).isEqualTo("1001");
        assertThat(tokens.getLastRaw()).isEqualTo("ordinary-token");
        assertThat(tokens.getLastSessionId()).isEqualTo(1001L);
    }

    @Test
    void missingDuplicateAndDelegatedAuthorizationNeverReachTokenService() {
        login("ordinary-token", 11L, 101L, 1001L);
        MockHttpServletRequest request = request();
        request.removeHeader("Authorization");
        assertRejected(adapter::captureCurrent);
        request.addHeader("Authorization", "Bearer first");
        request.addHeader("Authorization", "Bearer second");
        assertRejected(adapter::captureCurrent);
        request.removeHeader("Authorization");
        request.addHeader("Authorization", "Bearer mcp_jwt_fake");
        assertRejected(adapter::captureCurrent);
        assertThat(tokens.getRawCalls()).isZero();
    }

    @Test
    void refreshCompatibleResponseWithoutAccessRecordIdIsRejectedBeforeSessionLookup() {
        login("refresh-compatible", 11L, 101L, 1001L);
        OAuth2AccessTokenCheckRespDTO refresh = record(null, 11L, 101L, LocalDateTime.now().plusMinutes(5));
        tokens.setRaw(CommonResult.success(refresh));

        assertRejected(adapter::captureCurrent);
        assertThat(tokens.getSessionCalls()).isZero();
    }

    @Test
    void identityOrTenantMismatchCannotReuseLiveRecord() {
        login("ordinary-token", 11L, 101L, 1001L);
        tokens.setRaw(CommonResult.success(record(1001L, 11L, 101L, LocalDateTime.now().plusMinutes(5))));
        tokens.setSession(CommonResult.success(record(1001L, 22L, 101L, LocalDateTime.now().plusMinutes(5))));

        assertRejected(adapter::captureCurrent);
    }

    @Test
    void trustedTenantContextMismatchIsRejectedBeforeTokenService() {
        login("ordinary-token", 11L, 101L, 1001L);
        TenantContextHolder.setTenantId(22L);

        assertRejected(adapter::captureCurrent);
        assertThat(tokens.getRawCalls()).isZero();
    }

    @Test
    void tenantIgnoreScopeCannotAuthenticateOrdinaryBearer() {
        login("ordinary-token", 11L, 101L, 1001L);
        TenantContextHolder.setIgnore(true);

        assertRejected(adapter::captureCurrent);
        assertThat(tokens.getRawCalls()).isZero();
    }

    @Test
    void differentUserSessionIdOrTypeInLiveRecordIsRejected() {
        login("ordinary-token", 11L, 101L, 1001L);
        tokens.setRaw(CommonResult.success(record(1001L, 11L, 101L, LocalDateTime.now().plusMinutes(5))));
        OAuth2AccessTokenCheckRespDTO live = record(1001L, 11L, 202L, LocalDateTime.now().plusMinutes(5));
        tokens.setSession(CommonResult.success(live));
        assertRejected(adapter::captureCurrent);

        live.setUserId(101L);
        live.setAccessTokenId(2002L);
        assertRejected(adapter::captureCurrent);

        live.setAccessTokenId(1001L);
        live.setUserType(-1);
        assertRejected(adapter::captureCurrent);
    }

    @Test
    void revokedOrUnavailableSessionNeverFallsBackToLoginSnapshot() {
        login("ordinary-token", 11L, 101L, 1001L);
        tokens.setRaw(CommonResult.success(record(1001L, 11L, 101L, LocalDateTime.now().plusMinutes(5))));
        tokens.setSession(CommonResult.error(401, "secret upstream response"));

        assertThatThrownBy(adapter::captureCurrent).isInstanceOf(AiIdentityException.class)
                .hasMessageNotContaining("secret upstream response");
    }

    @Test
    void expiryUsesEarliestOfLoginTokenAndRecordInHostTimezone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            login("ordinary-token", 11L, 101L, 1001L);
            LocalDateTime earliest = LocalDateTime.now().plusMinutes(2);
            tokens.setRaw(CommonResult.success(record(1001L, 11L, 101L, earliest.plusMinutes(2))));
            tokens.setSession(CommonResult.success(record(1001L, 11L, 101L, earliest)));

            AiHostSession session = adapter.currentSession(adapter.captureCurrent());
            assertThat(session.getExpiresAtMillis())
                    .isEqualTo(earliest.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void loginSnapshotCanBeEarliestExpiry() {
        login("ordinary-token", 11L, 101L, 1001L);
        LocalDateTime earliest = LocalDateTime.now().plusMinutes(2);
        SecurityFrameworkUtils.getLoginUser().setExpiresTime(earliest);
        validRecords();

        AiHostSession session = adapter.currentSession(adapter.captureCurrent());
        assertThat(session.getExpiresAtMillis())
                .isEqualTo(earliest.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    @Test
    void tokenSnapshotCanBeEarliestExpiry() {
        login("ordinary-token", 11L, 101L, 1001L);
        LocalDateTime earliest = LocalDateTime.now().plusMinutes(2);
        tokens.setRaw(CommonResult.success(record(1001L, 11L, 101L, earliest)));
        tokens.setSession(CommonResult.success(record(1001L, 11L, 101L, earliest.plusMinutes(2))));

        AiHostSession session = adapter.currentSession(adapter.captureCurrent());
        assertThat(session.getExpiresAtMillis())
                .isEqualTo(earliest.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
    }

    @Test
    void permissionServiceFailureIsNotGrantedAndIsReachedOnlyAfterLiveIdentity() {
        login("ordinary-token", 11L, 101L, 1001L);
        validRecords();
        permissions.setPermissions(CommonResult.error(503, "secret permission response"));
        AiInvocationContext context = adapter.captureCurrent();

        assertThatThrownBy(() -> adapter.hasPermission(context, "ai:chat:send"))
                .isInstanceOf(AiIdentityException.class)
                .hasMessageNotContaining("secret permission response");
        assertThat(tokens.getSessionCalls()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void explicitPermissionDenialReturnsFalseAfterFreshIdentityCheck() {
        login("ordinary-token", 11L, 101L, 1001L);
        validRecords();
        permissions.setPermissions(CommonResult.success(false));

        AiInvocationContext context = adapter.captureCurrent();
        assertThat(adapter.hasPermission(context, "ai:chat:send")).isFalse();
        assertThat(tokens.getSessionCalls()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void superAdminRoleInOrdinaryTenantDoesNotGrantPlatformAdministrator() {
        login("ordinary-token", 11L, 101L, 1001L);
        validRecords();
        permissions.setRoles(CommonResult.success(true));

        AiInvocationContext context = adapter.captureCurrent();
        assertThat(adapter.isPlatformAdministrator(context)).isFalse();
    }

    @Test
    void platformAdministratorRequiresConfiguredSystemTenantAndLiveSuperAdminRole() {
        login("ordinary-token", 1L, 101L, 1001L);
        tokens.setRaw(CommonResult.success(record(1001L, 1L, 101L, LocalDateTime.now().plusMinutes(5))));
        tokens.setSession(CommonResult.success(record(1001L, 1L, 101L, LocalDateTime.now().plusMinutes(5))));
        AiInvocationContext context = adapter.captureCurrent();

        permissions.setRoles(CommonResult.success(false));
        assertThat(adapter.isPlatformAdministrator(context)).isFalse();
        permissions.setRoles(CommonResult.success(true));
        assertThat(adapter.isPlatformAdministrator(context)).isTrue();
        PlatformInProcessAuthenticationAdapter noSystemTenant = new PlatformInProcessAuthenticationAdapter(
                new PlatformLocalSessionSource(beans, null, "platform"));
        assertThat(noSystemTenant.isPlatformAdministrator(context)).isFalse();
    }

    @Test
    void ordinaryOriginRequiresSuccessfulOrdinaryAuthentication() {
        login("ordinary-token", 11L, 101L, 1001L);
        validRecords();
        PlatformInProcessOriginAdapter origin = new PlatformInProcessOriginAdapter(adapter);
        assertThat(origin.capture("platform")).isNull();
        request().removeHeader("Authorization");
        assertRejected(() -> origin.capture("platform"));
    }

    private void validRecords() {
        tokens.setRaw(CommonResult.success(record(1001L, 11L, 101L, LocalDateTime.now().plusMinutes(5))));
        tokens.setSession(CommonResult.success(record(1001L, 11L, 101L, LocalDateTime.now().plusMinutes(5))));
    }

    private OAuth2AccessTokenCheckRespDTO record(Long id, Long tenantId, Long userId, LocalDateTime expiry) {
        OAuth2AccessTokenCheckRespDTO value = new OAuth2AccessTokenCheckRespDTO();
        value.setAccessTokenId(id);
        value.setTenantId(tenantId);
        value.setUserId(userId);
        value.setUserType(UserTypeEnum.ADMIN.getValue());
        value.setExpiresTime(expiry);
        return value;
    }

    private void login(String bearer, long tenantId, long userId, long sessionId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + bearer);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        LoginUser user = new LoginUser();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setUserType(UserTypeEnum.ADMIN.getValue());
        user.setAccessTokenId(sessionId);
        user.setExpiresTime(LocalDateTime.now().plusMinutes(5));
        SecurityFrameworkUtils.setLoginUser(user);
        TenantContextHolder.setTenantId(tenantId);
    }

    private MockHttpServletRequest request() {
        return (MockHttpServletRequest) ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }

    private void assertRejected(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOf(AiIdentityException.class);
    }
}

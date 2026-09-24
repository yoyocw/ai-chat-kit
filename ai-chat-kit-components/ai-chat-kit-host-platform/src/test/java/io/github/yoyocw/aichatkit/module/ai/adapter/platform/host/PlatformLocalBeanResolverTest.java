package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import io.github.yoyocw.aichatkit.testnative.framework.common.biz.system.permission.PermissionCommonApi;
import io.github.yoyocw.aichatkit.testnative.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.testnative.module.system.api.oauth2.OAuth2TokenApiImpl;
import io.github.yoyocw.aichatkit.testnative.module.system.api.permission.PermissionApiImpl;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Native-root binding tests use only a neutral test package, never a relocated compatibility target. */
class PlatformLocalBeanResolverTest {
    static final String ROOT = "io.github.yoyocw.aichatkit.testnative";

    @Test
    void missingNamedServicesFailsWithoutLeakingCause() {
        assertConfigurationFailure(new DefaultListableBeanFactory(), ROOT);
    }

    @Test
    void wrongTypeOrSameNameInterfaceMockCannotImpersonateLocalTarget() {
        DefaultListableBeanFactory wrong = named(new Object() {
            @Override public String toString() { return "Bearer secret-bearer-value"; }
        }, new PermissionApiImpl());
        assertConfigurationFailure(wrong, ROOT);

        DefaultListableBeanFactory mocks = named(mock(OAuth2TokenCommonApi.class),
                mock(PermissionCommonApi.class));
        assertConfigurationFailure(mocks, ROOT);
    }

    @Test
    void sameNameJdkInterfaceProxyCannotImpersonateLocalTarget() {
        Object remoteStyle = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{OAuth2TokenCommonApi.class}, (proxy, method, args) -> null);
        assertConfigurationFailure(named(remoteStyle, new PermissionApiImpl()), ROOT);
    }

    @Test
    void wrongNativeRootCannotFallBackToRelocatedCompatTypes() {
        assertConfigurationFailure(named(new OAuth2TokenApiImpl(), new PermissionApiImpl()),
                "io.github.yoyocw.aichatkit.compat");
    }

    @Test
    void nativeTargetReturnsImmutableIdentityAndUsesBothOriginalTokenQueries() {
        OAuth2TokenApiImpl token = new OAuth2TokenApiImpl();
        token.setRaw(CommonResult.success(identity(1001L)));
        token.setSession(CommonResult.success(identity(1001L)));
        PlatformLocalBeanResolver resolver = resolver(named(token, new PermissionApiImpl()));

        PlatformLocalBeanResolver.NativeIdentity raw = resolver.checkAccessToken("test-raw");
        PlatformLocalBeanResolver.NativeIdentity live = resolver.checkAccessTokenSession(1001L);

        assertThat(raw.getUserId()).isEqualTo(101L);
        assertThat(raw.getTenantId()).isEqualTo(11L);
        assertThat(raw.getAccessTokenId()).isEqualTo(1001L);
        assertThat(live.getAccessTokenId()).isEqualTo(1001L);
        assertThat(token.getLastRaw()).isEqualTo("test-raw");
        assertThat(token.getLastSessionId()).isEqualTo(1001L);
    }

    @Test
    void springAopAdviceRemainsInTheCallPath() {
        OAuth2TokenApiImpl target = new OAuth2TokenApiImpl();
        target.setRaw(CommonResult.success(identity(1001L)));
        target.setSession(CommonResult.success(identity(1001L)));
        AtomicInteger advised = new AtomicInteger();
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) invocation -> {
            advised.incrementAndGet();
            return invocation.proceed();
        });
        PlatformLocalBeanResolver resolver = resolver(named(factory.getProxy(), new PermissionApiImpl()));

        resolver.checkAccessToken("test-raw");
        resolver.checkAccessTokenSession(1001L);
        assertThat(advised.get()).isEqualTo(2);
    }

    private PlatformLocalBeanResolver resolver(DefaultListableBeanFactory beans) {
        return new PlatformLocalBeanResolver(beans, getClass().getClassLoader(), ROOT);
    }

    private DefaultListableBeanFactory named(Object token, Object permission) {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("oauth2TokenApiImpl", token);
        beans.registerSingleton("permissionApiImpl", permission);
        return beans;
    }

    private OAuth2AccessTokenCheckRespDTO identity(Long accessTokenId) {
        OAuth2AccessTokenCheckRespDTO value = new OAuth2AccessTokenCheckRespDTO();
        value.setAccessTokenId(accessTokenId);
        value.setUserId(101L);
        value.setTenantId(11L);
        value.setUserType(2);
        value.setExpiresTime(LocalDateTime.now().plusMinutes(5));
        return value;
    }

    private void assertConfigurationFailure(DefaultListableBeanFactory beans, String root) {
        assertThatThrownBy(() -> new PlatformLocalBeanResolver(beans, getClass().getClassLoader(), root))
                .isInstanceOf(IllegalStateException.class)
                .hasNoCause()
                .hasMessageNotContaining("secret-bearer-value");
    }
}

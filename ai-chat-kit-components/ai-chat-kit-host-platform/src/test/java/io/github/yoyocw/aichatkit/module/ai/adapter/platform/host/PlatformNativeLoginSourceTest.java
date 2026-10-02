package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.testnative.framework.security.core.LoginUser;
import io.github.yoyocw.aichatkit.testnative.framework.security.core.util.SecurityFrameworkUtils;
import io.github.yoyocw.aichatkit.testnative.framework.tenant.core.context.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformNativeLoginSourceTest {
    private static final String ROOT = PlatformLocalBeanResolverTest.ROOT;

    @AfterEach
    void clear() {
        SecurityFrameworkUtils.clear();
        TenantContextHolder.clear();
    }

    @Test
    void snapshotsOnlyNativeLoginAndTrustedThreadContext() {
        PlatformNativeLoginSource source = new PlatformNativeLoginSource(getClass().getClassLoader(), ROOT);
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(5);
        LoginUser user = new LoginUser();
        user.setId(101L);
        user.setTenantId(11L);
        user.setUserType(2);
        user.setAccessTokenId(1001L);
        user.setExpiresTime(expiry);
        SecurityFrameworkUtils.setLoginUser(user);
        SecurityFrameworkUtils.setSkipPermissionCheck(true);
        TenantContextHolder.setTenantId(11L);
        TenantContextHolder.setIgnore(true);

        PlatformNativeLoginSource.NativeLogin snapshot = source.currentLogin();
        user.setId(202L);
        assertThat(snapshot.getUserId()).isEqualTo(101L);
        assertThat(snapshot.getTenantId()).isEqualTo(11L);
        assertThat(snapshot.getAccessTokenId()).isEqualTo(1001L);
        assertThat(snapshot.getExpiresTime()).isEqualTo(expiry);
        assertThat(source.currentTenantId()).isEqualTo(11L);
        assertThat(source.tenantIgnored()).isTrue();
        assertThat(source.skipPermissionCheck()).isTrue();
        assertThat(source.adminUserType()).isEqualTo(2);
    }

    @Test
    void missingOrIncompleteNativeRootFailsAtConstructionWithoutLeakingCause() {
        assertThatThrownBy(() -> new PlatformNativeLoginSource(getClass().getClassLoader(),
                "io.github.yoyocw.aichatkit.missing"))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
        assertThatThrownBy(() -> new PlatformNativeLoginSource(
                new FilteredClassLoader(ROOT + ".framework.common.enums.UserTypeEnum"), ROOT))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }
}

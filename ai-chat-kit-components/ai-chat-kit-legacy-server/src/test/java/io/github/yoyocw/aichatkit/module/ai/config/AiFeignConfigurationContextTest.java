package io.github.yoyocw.aichatkit.module.ai.config;

import io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.DashboardApi;
import io.github.yoyocw.aichatkit.compat.module.fac.enums.ApiConstants;
import feign.Client;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.Targeter;
import org.springframework.cloud.openfeign.FeignContext;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 隔离上下文验证真实Feign注册与实例化，不启动Boot、发现服务或外部连接。 */
class AiFeignConfigurationContextTest {
    @Test
    void independentAiResolvesActualFeignClientWithoutNetwork() {
        AtomicInteger requests = new AtomicInteger();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // 仅替换传输层；若实例化意外发请求，立即失败，不允许连接任何目标。
            context.registerBean(Client.class, () -> (request, options) -> {
                requests.incrementAndGet();
                throw new AssertionError("Bean装配不应发送HTTP请求");
            });
            context.register(AiFeignConfiguration.class, FeignAutoConfiguration.class);
            context.refresh();
            DashboardApi api = context.getBean(DashboardApi.class);
            assertTrue(Proxy.isProxyClass(api.getClass()));
            assertEquals("org.springframework.cloud.openfeign.DefaultTargeter",
                    context.getBean(FeignContext.class).getInstance(ApiConstants.NAME, Targeter.class).getClass().getName());
            assertSame(api, context.getBean(DashboardApi.class));
            assertEquals(1, context.getBeanNamesForType(DashboardApi.class).length);
            // DashboardApi 沿用 @FeignClient 默认 primary=true，仍验证注册结果而非删除该断言。
            assertTrue(context.getBeanFactory().getBeanDefinition(DashboardApi.class.getName()).isPrimary());
            assertSame(context.getBean(Client.class), context.getBean(FeignContext.class)
                    .getInstance(ApiConstants.NAME, Client.class));
            assertEquals(0, requests.get());
        }
        assertEquals(0, requests.get());
    }
}

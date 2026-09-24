package io.github.yoyocw.aichatkit.compat.framework.tenant.config;

import io.github.yoyocw.aichatkit.compat.framework.tenant.core.rpc.TenantRequestInterceptor;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.tenant.TenantCommonApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "aichatkit.tenant", value = "enable", matchIfMissing = true) // 允许使用 aichatkit.tenant.enable=false 禁用多租户
@EnableFeignClients(clients = TenantCommonApi.class) // 主要是引入相关的 API 服务
public class AiChatKitTenantRpcAutoConfiguration {

    @Bean
    public TenantRequestInterceptor tenantRequestInterceptor() {
        return new TenantRequestInterceptor();
    }

}

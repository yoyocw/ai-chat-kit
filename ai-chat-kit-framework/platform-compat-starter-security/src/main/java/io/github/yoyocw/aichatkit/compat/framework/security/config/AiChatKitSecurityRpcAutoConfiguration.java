package io.github.yoyocw.aichatkit.compat.framework.security.config;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.ai.serviceapikey.ServiceApiKeyCommonApi;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.permission.PermissionCommonApi;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.user.ServiceUserCommonApi;
import io.github.yoyocw.aichatkit.compat.framework.security.core.rpc.LoginUserRequestInterceptor;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * Security 使用到 Feign 的配置项
 *
 * @author kelecc
 */
@AutoConfiguration
@EnableFeignClients(clients = {OAuth2TokenCommonApi.class, // 主要是引入相关的 API 服务
        PermissionCommonApi.class,
        ServiceUserCommonApi.class,
        ServiceApiKeyCommonApi.class})
public class AiChatKitSecurityRpcAutoConfiguration {

    @Bean
    public LoginUserRequestInterceptor loginUserRequestInterceptor() {
        return new LoginUserRequestInterceptor();
    }

}

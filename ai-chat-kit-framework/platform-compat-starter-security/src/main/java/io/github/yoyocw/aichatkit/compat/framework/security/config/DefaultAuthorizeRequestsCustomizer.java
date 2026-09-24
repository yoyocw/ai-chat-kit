package io.github.yoyocw.aichatkit.compat.framework.security.config;

import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * 默认的 URL 安全配置
 * 提供各模块通用的放行规则：Swagger、Actuator、Druid 监控
 *
 * @author kelecc
 */
public class DefaultAuthorizeRequestsCustomizer extends AuthorizeRequestsCustomizer {

    @Override
    public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
        // Swagger 接口文档
        registry.requestMatchers("/v3/api-docs/**").permitAll()
                .requestMatchers("/webjars/**").permitAll()
                .requestMatchers("/swagger-ui").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll();
        // Spring Boot Actuator 的安全配置
        registry.requestMatchers("/actuator").permitAll()
                .requestMatchers("/actuator/**").permitAll();
        // Druid 监控
        registry.requestMatchers("/druid/**").permitAll();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

}

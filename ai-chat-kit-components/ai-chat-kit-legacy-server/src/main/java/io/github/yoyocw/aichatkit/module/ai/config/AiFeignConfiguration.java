package io.github.yoyocw.aichatkit.module.ai.config;

import io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.DashboardApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * AI 模块 RPC 配置，只注册地图查询；身份复核使用通用受限认证客户端。
 */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(clients = {DashboardApi.class})
public class AiFeignConfiguration {
}

package io.github.yoyocw.aichatkit.compat.framework.apilog.config;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.infra.logger.ApiErrorLogCommonApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * API 日志使用到 Feign 的配置项
 *
 * @author kelecc
 */
@AutoConfiguration
@EnableFeignClients(clients = {ApiAccessLogCommonApi.class, ApiErrorLogCommonApi.class}) // 主要是引入相关的 API 服务
public class AiChatKitApiLogRpcAutoConfiguration {
}

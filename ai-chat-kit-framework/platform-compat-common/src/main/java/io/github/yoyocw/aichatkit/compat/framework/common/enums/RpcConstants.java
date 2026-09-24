package io.github.yoyocw.aichatkit.compat.framework.common.enums;

/**
 * RPC 相关的枚举
 *
 * 虽然放在 platform-compat-starter-rpc 会相对合适，但是每个 API 模块需要使用到，所以暂时只好放在此处
 *
 * @author kelecc
 */
public interface RpcConstants {

    /**
     * RPC API 的前缀
     */
    String RPC_API_PREFIX = "/rpc-api";

    /**
     * system 服务名
     *
     * 注意，需要保证和 spring.application.name 保持一致
     */
    String SYSTEM_NAME = "system-server";

    /**
     * system 服务的前缀
     */
    String SYSTEM_PREFIX = RPC_API_PREFIX + "/system";

    /**
     * infra 服务名
     *
     * 注意，需要保证和 spring.application.name 保持一致
     */
    String INFRA_NAME = "infra-server";
    /**
     * infra 服务的前缀
     */
    String INFRA_PREFIX = RPC_API_PREFIX + "/infra";

    /**
     * AI 服务名，必须与 AI 微服务的 {@code spring.application.name} 保持一致。
     */
    String AI_NAME = "ai-server";

    /**
     * AI 服务内部 RPC 接口前缀，不允许通过外部网关路由。
     */
    String AI_PREFIX = RPC_API_PREFIX + "/ai";

}

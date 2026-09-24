package io.github.yoyocw.aichatkit.compat.framework.security.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明允许纳入 MCP 服务密钥认证范围的只读 Controller 方法。
 *
 * <p>本注解只定义代码侧可开放上限，具体调用方仍必须在数据库中取得对应端点编码授权。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpServiceApi {

    /**
     * 全部 MCP 业务微服务中稳定且唯一的端点编码，建议使用“模块.领域.资源.动作”格式。
     *
     * @return 不随 HTTP 路径调整而改变的授权编码，长度不超过 128 个字符
     */
    String endpointCode();

    /**
     * 是否为标准分页接口；为 true 时过滤器强制校验 pageSize 不超过调用方上限。
     *
     * @return 分页接口返回 true，详情接口返回 false
     */
    boolean pageable() default false;
}

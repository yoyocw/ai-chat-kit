package io.github.yoyocw.aichatkit.ai.host.ruoyi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.LinkedHashMap;
import java.util.Map;

/** 仅适用锁定官方 RuoYi-Vue 原生普通登录链，不支持自定义委托兑换或伪装登录。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai-chat-kit.ai.ruoyi-host")
public class RuoyiHostProperties {
    /** 显式启用开关，默认关闭；不修改宿主认证过滤器。 */
    private boolean enabled;
    /** 固定原生登录模式，仅 native-login 有效，不推断委托来源。 */
    private String mode = "native-login";
    /** 必填的部署单租户隔离标识，不能取部门、请求头或用户参数作为多租户证明。 */
    private String singleTenantId;
    /** 服务端固定 AI 操作码到若依实际菜单权限码，未配置的操作拒绝，不提供全权默认值。 */
    private Map<String, String> permissionMappings = new LinkedHashMap<>();
}

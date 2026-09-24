package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterMarker;
import io.github.yoyocw.aichatkit.ai.starter.config.AiStarterProperties;
import io.github.yoyocw.aichatkit.ai.starter.host.*;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContextPort;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import java.util.EnumSet;
import java.util.Set;

/** 开启HTTP前验证装配，并核对最终MVC路径，防止旧全局前缀叠加或扫描产生不完整接口。 */
public final class AiWebActivation implements SmartInitializingSingleton {
    /** 最终容器，用于检查固定类型，不读取认证凭据。 */
    private final ListableBeanFactory beans;
    /** 校验后的完整固定前缀。 */
    private final String prefix;
    /** 固定模式快照，运行时不能由HTTP修改。 */
    private final Set<AiChatMode> modes;
    /** @param beans 宿主容器 @param environment 部署开关 @param web HTTP配置 @param starter 模式配置 */
    public AiWebActivation(ListableBeanFactory beans, Environment environment,
            AiWebProperties web, AiStarterProperties starter) {
        this.beans = beans;
        if (!web.isEnabled() || !Boolean.TRUE.equals(environment.getProperty("ai-chat-kit.ai.engine.enabled", Boolean.class))) {
            throw new IllegalStateException("AI Web需要明确开启引擎");
        }
        unique(beans, AiStarterMarker.class);
        if (starter.getNamespace() == null || starter.getNamespace().trim().isEmpty()
                || starter.getModes() == null || starter.getModes().isEmpty() || starter.getModes().contains(null)) {
            throw new IllegalStateException("AI Web宿主命名空间或模式配置无效");
        }
        prefix = validatePrefix(web.getPathPrefix());
        modes = EnumSet.copyOf(starter.getModes());
        unique(beans, AiInvocationContextPort.class);
        unique(beans, AiHostConversationManagementService.class);
        unique(beans, AiHostConversationShareService.class);
        unique(beans, AiPublicConversationShareService.class);
        if (modes.contains(AiChatMode.SINGLE)) { unique(beans, AiHostSingleChatService.class); }
        if (modes.contains(AiChatMode.GROUP)) {
            unique(beans, AiHostGroupChatService.class);
            unique(beans, AiHostGroupAgentService.class);
        }
    }
    /** @return 唯一明确适配器；不利用Primary掩盖多个身份或执行入口 */
    static <T> T unique(ListableBeanFactory beans, Class<T> type) {
        String[] names = beans.getBeanNamesForType(type);
        if (names.length != 1) { throw new IllegalStateException("AI Web需要唯一依赖: " + type.getSimpleName()); }
        return beans.getBean(names[0], type);
    }
    /** @param value 固定路径 @return 无尾斜线、模板或编码的规范路径 */
    private String validatePrefix(String value) {
        if (value == null || value.length() > 200 || !value.matches("(/[A-Za-z0-9_-]+)+")) {
            throw new IllegalStateException("AI Web路径前缀无效");
        }
        return value;
    }
    /** 核对MVC实际注册结果；相同方法同路径冲突由Spring自身拒绝启动。 */
    @Override
    public void afterSingletonsInstantiated() {
        java.util.List<java.util.Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod>> routes
                = new java.util.ArrayList<java.util.Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod>>();
        // 宿主可以有多个MVC映射器；累计检查本模块实际注册结果，不限制合法的其它映射器数量。
        for (RequestMappingHandlerMapping mapping : beans.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
            routes.addAll(mapping.getHandlerMethods().entrySet());
        }
        int single = 0;
        int group = 0;
        for (java.util.Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod> entry
                : routes) {
            Class<?> type = entry.getValue().getBeanType();
            boolean isSingle = AiWebSingleController.class.isAssignableFrom(type);
            boolean isGroup = AiWebGroupController.class.isAssignableFrom(type);
            if (!isSingle && !isGroup) { continue; }
            org.springframework.web.bind.annotation.RequestMapping annotation =
                    org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                            entry.getValue().getMethod(), org.springframework.web.bind.annotation.RequestMapping.class);
            if (annotation == null || annotation.value().length != 1) {
                throw new IllegalStateException("AI Web路由声明无效");
            }
            String expected = prefix + (isSingle ? "/chat" : "/group-chat") + annotation.value()[0];
            if (entry.getKey().getPatternValues().size() != 1
                    || !entry.getKey().getPatternValues().contains(expected)) {
                throw new IllegalStateException("AI Web路由被宿主前缀重复改写或产生冲突");
            }
            rejectConflicts(entry, routes, expected);
            if (isSingle) { single++; } else { group++; }
        }
        if (single != (modes.contains(AiChatMode.SINGLE) ? 11 : 0)
                || group != (modes.contains(AiChatMode.GROUP) ? 13 : 0)) {
            throw new IllegalStateException("AI Web所选模式路由不完整");
        }
    }
    /** 同一路径与HTTP方法不能因不同produces/params条件而悄悄指向旧接口。 */
    private void rejectConflicts(
            java.util.Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod> owned,
            java.util.List<java.util.Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod>> routes,
            String path) {
        for (java.util.Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod> other : routes) {
            if (owned == other || !other.getKey().getPatternValues().contains(path)) { continue; }
            Set<org.springframework.web.bind.annotation.RequestMethod> methods =
                    owned.getKey().getMethodsCondition().getMethods();
            Set<org.springframework.web.bind.annotation.RequestMethod> others =
                    other.getKey().getMethodsCondition().getMethods();
            if (methods.isEmpty() || others.isEmpty() || !java.util.Collections.disjoint(methods, others)) {
                throw new IllegalStateException("AI Web路径与宿主已有接口冲突");
            }
        }
    }
}

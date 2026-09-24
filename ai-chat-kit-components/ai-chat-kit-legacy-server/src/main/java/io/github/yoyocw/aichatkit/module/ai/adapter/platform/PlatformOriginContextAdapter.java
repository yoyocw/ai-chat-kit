package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.compat.framework.common.util.servlet.ServletUtils;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiCallerOrigin;
import io.github.yoyocw.aichatkit.module.ai.contract.origin.AiOriginContextPort;
import io.github.yoyocw.aichatkit.module.ai.service.chat.AiDelegatedCallContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.delegation.AiDelegatedChatController;

/** 仅从委托入口认证后设置的请求属性提取来源；普通入口不制造来源。 */
@Component
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.platform-host", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class PlatformOriginContextAdapter implements AiOriginContextPort {
    /** 与委托入口使用相同部署开关，不支持运行时热刷新。 */
    @Value("${ai-chat-kit.ai.delegated-entry.record-origin-enabled:false}")
    private boolean enabled;

    @Override
    public AiCallerOrigin capture(String appId) {
        if (!enabled) { return null; }
        HttpServletRequest request = ServletUtils.getRequest();
        Object value = request == null ? null : request.getAttribute(AiDelegatedCallContext.class.getName());
        if (value == null) {
            // 使用服务端已匹配的 Controller 识别委托入口，不接受用户自报调用类型。
            Object handler = request == null ? null
                    : request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
            if (handler instanceof HandlerMethod && AiDelegatedChatController.class
                    .isAssignableFrom(((HandlerMethod) handler).getBeanType())) {
                throw new IllegalStateException("委托入口来源上下文缺失");
            }
            return null;
        }
        if (!(value instanceof AiDelegatedCallContext)) {
            throw new IllegalStateException("委托来源上下文无效");
        }
        AiDelegatedCallContext delegated = (AiDelegatedCallContext) value;
        if (delegated.getOrigin() == null || delegated.getBinding() == null
                || delegated.getBinding().getAppIds() == null
                || !delegated.getBinding().getAppIds().contains(appId)) {
            throw new IllegalStateException("委托来源或应用授权无效");
        }
        return delegated.getOrigin();
    }
}

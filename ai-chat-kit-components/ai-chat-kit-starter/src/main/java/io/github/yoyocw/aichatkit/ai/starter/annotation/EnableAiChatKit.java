package io.github.yoyocw.aichatkit.ai.starter.annotation;

import io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration;
import io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterMarkerConfiguration;
import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivationConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 显式选择 AI Starter；还需开启 ai-chat-kit.ai.engine.enabled 并提供所选模式的真实宿主适配。
 * 仅普通导入启用标记，实际装配由 Boot 延迟自动配置导入与排序负责，不扫描业务包。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({AiRuntimeActivationConfiguration.class, AiStarterMarkerConfiguration.class})
@ImportAutoConfiguration(AiStarterAutoConfiguration.class)
public @interface EnableAiChatKit {
}

package io.github.yoyocw.aichatkit.ai.starter;

import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiModelRuntimeAutoConfiguration;
import io.github.yoyocw.aichatkit.ai.starter.annotation.EnableAiChatKit;
import io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterAutoConfiguration;
import io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterMarker;
import io.github.yoyocw.aichatkit.ai.starter.autoconfigure.AiStarterMarkerConfiguration;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import io.github.yoyocw.aichatkit.module.ai.service.memory.AiConversationMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** H1 的显式选择边界；子开关不能代替 @EnableAiChatKit。 */
class AiActivationBoundaryTest {

    private final ApplicationContextRunner modelRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AiModelRuntimeAutoConfiguration.class));

    /**
     * 防止删除运行时激活标记条件：没有启用注解时，即使总开关为 true 也不能创建模型客户端。
     */
    @Test
    void engineSwitchCannotActivateModelWithoutEnableAnnotation() {
        modelRunner.withPropertyValues("ai-chat-kit.ai.engine.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(BailianClient.class));
    }

    /**
     * 防止把显式导入配置重新声明为组件：宿主宽扫描 starter 包不能意外创建启用标记。
     */
    @Test
    void broadComponentScanCannotCreateActivationMarker() {
        new ApplicationContextRunner().withUserConfiguration(BroadStarterScan.class)
                .run(context -> assertThat(context).doesNotHaveBean(AiStarterMarker.class));
    }

    /**
     * 防止宿主宽扫描 io.github.yoyocw.aichatkit 时绕过激活条件：关闭状态不得从 @Service 创建模型相关业务对象。
     */
    @Test
    void broadHostScanCannotCreateMemoryServiceWithoutActivation() {
        new ApplicationContextRunner().withUserConfiguration(BroadEngineServiceScan.class)
                .withPropertyValues("ai-chat-kit.ai.engine.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AiConversationMemoryService.class));
    }

    /**
     * 防止总开关 false 时子开关或注解创建模型资源；轻量标记本身允许存在。
     */
    @Test
    void enableAnnotationWithEngineDisabledCreatesNoModelResource() {
        modelRunner.withUserConfiguration(ExplicitlyEnabledHost.class)
                .withPropertyValues(
                        "ai-chat-kit.ai.engine.enabled=false",
                        "ai-chat-kit.ai.web.enabled=true",
                        "ai-chat-kit.ai.mcp-jwt-v1.signing-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(AiStarterMarker.class);
                    assertThat(context).doesNotHaveBean(BailianClient.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = AiStarterMarkerConfiguration.class)
    static class BroadStarterScan {
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = AiConversationMemoryService.class)
    static class BroadEngineServiceScan {
        @org.springframework.context.annotation.Bean
        BailianProperties bailianProperties() { return new BailianProperties(); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAiChatKit
    static class ExplicitlyEnabledHost {
    }
}

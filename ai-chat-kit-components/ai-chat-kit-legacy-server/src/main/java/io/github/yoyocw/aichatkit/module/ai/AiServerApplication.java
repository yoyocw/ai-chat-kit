package io.github.yoyocw.aichatkit.module.ai;

import io.github.yoyocw.aichatkit.ai.engine.autoconfigure.AiRuntimeActivationConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * AI 智能对话服务启动入口，负责启动接口服务并注册到 Nacos。
 */
@SpringBootApplication
@Import(AiRuntimeActivationConfiguration.class)
public class AiServerApplication {

    /**
     * 启动 AI 独立服务。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiServerApplication.class, args);
    }

}

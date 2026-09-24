package example.aienginehost;

import io.github.yoyocw.aichatkit.ai.starter.annotation.EnableAiChatKit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 独立宿主仅扫描自身；注解选择 AI Starter，YAML 决定是否启用，不扫描林业业务模块。
 * 本样例无宿主业务库，因此排除 Boot 默认数据源装配，AI 自有资源由 JDBC 适配管理。
 * 已有业务数据源的真实宿主应保留自身装配，不需要复制该排除项。
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableAiChatKit
public class AiEngineHostApplication {

    /** 启动样例；默认关闭引擎，无数据库、注册中心和外部模型连接。 */
    public static void main(String[] args) {
        SpringApplication.run(AiEngineHostApplication.class, args);
    }
}

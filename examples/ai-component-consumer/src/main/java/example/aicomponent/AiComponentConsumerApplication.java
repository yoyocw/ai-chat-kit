package example.aicomponent;

import io.github.yoyocw.aichatkit.ai.starter.annotation.EnableAiChatKit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/** Minimal host used to verify that the published AI jars work outside the source reactor. */
@EnableAiChatKit
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class AiComponentConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiComponentConsumerApplication.class, args);
    }
}

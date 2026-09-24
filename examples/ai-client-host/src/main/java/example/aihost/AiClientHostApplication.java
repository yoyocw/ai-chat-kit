package example.aihost;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 无业务数据库的Boot2宿主；演示启动时引入客户端，不模拟AI成功结果。 */
@SpringBootApplication
public class AiClientHostApplication {
    /** @param args 标准Spring Boot启动参数，默认仅监听本地随机端口且AI关闭 */
    public static void main(String[] args) { SpringApplication.run(AiClientHostApplication.class, args); }
}

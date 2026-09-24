# AI Chat Kit

面向 Java 8 和 Spring Boot 2.7 的嵌入式 AI 聊天组件，提供单聊、群聊、流式输出、停止、会话管理、分享和工具授权。通过 Maven 依赖、`@EnableAiChatKit` 与配置启用。

## 组件

| 组件 | 职责 |
|---|---|
| `ai-chat-kit-contract` | 中立接口与身份、存储、授权契约 |
| `ai-chat-kit-engine` | 对话与执行流程 |
| `ai-chat-kit-starter` | 注解激活与自动装配 |
| `ai-chat-kit-adapter-web` | HTTP 与 SSE 入口 |
| `ai-chat-kit-adapter-jdbc` | AI 自有 PostgreSQL 存储 |
| `ai-chat-kit-adapter-mcp-jwt-v1` | 应用与工具范围授权、凭据签发 |
| `ai-chat-kit-adapter-hosted-proxy` | 可选宿主代理与停止协议 |
| `ai-chat-kit-mcp-jwt` | 中立 JWT 协议 |
| `ai-chat-kit-host-platform` | 可选平台宿主接入 |

Maven group 为 `io.github.yoyocw`，核心 Java 包为 `io.github.yoyocw.aichatkit`，兼容源码位于 `io.github.yoyocw.aichatkit.compat`。中立组件不依赖平台安全框架、业务服务或兼容模块。

## 接入

引入同版本 `ai-chat-kit-starter`、Web/JDBC 及所需适配组件，在启动类添加：

```java
import io.github.yoyocw.aichatkit.ai.starter.annotation.EnableAiChatKit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableAiChatKit
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

组件默认关闭。设置 `ai-chat-kit.ai.engine.enabled=true`，并配置模型、应用目录、存储和真实授权。平台宿主可额外引入 `ai-chat-kit-host-platform`，使用 `platform-host.mode=in-process`；原宿主包根通过部署环境提供，不写死在组件中。支持的宿主由组件提供所需身份端口，业务模块不需要手写 AI 适配代码。

完整依赖和配置见 [接入示例](examples/ai-engine-host/README.md)。任意其他登录框架仍需匹配的宿主适配器，不可用测试身份代替真实认证。

## 构建

使用 JDK 8、Maven 3.9。当前开发候选版本为 `1.2.0-SNAPSHOT`。

```sh
# 中立组件
mvn -B -ntp -Drevision=1.2.0-SNAPSHOT clean install

# 包含可选平台兼容组件
mvn -B -ntp -Pplatform-compat -Drevision=1.2.0-SNAPSHOT clean install
```

普通组件发布为薄 JAR、sources JAR 和独立 POM。每个 JAR 包含 `META-INF/LICENSE`。兼容模块仅按显式 profile 构建；宿主适配器的 provided 依赖不能重复打入已有宿主。

```powershell
./scripts/component-release/verify-package.ps1 -Repository <Maven仓库目录> -Version 1.2.0-SNAPSHOT
```

第三方依赖由配置的 Maven 仓库解析。可选兼容构建包含地理库时需要 OSGeo 仓库；默认中立组件不依赖这些库。

## 配置与运行边界

- H1 为启动时装配：开启、关闭和升级需要重启；移除依赖和接线后需要重新构建。当前不提供运行时 JAR 卸载。
- 身份命名空间应保持部署配置稳定，避免改名后将既有数据隔离到新命名空间。
- JDBC 使用 `ai_runtime_*` 表，不自动迁移宿主已有聊天表、历史记录或分享链接。首次共存建议使用独立路由前缀。
- 模型、数据库和密钥由部署环境配置，不分发本地环境文件、真实凭据或依赖缓存。
- 构建和隔离测试不等同真实业务联调或生产部署验收。

本轮命名、兼容方式与验证状态见 [中性命名交付说明](docs/componentization/platform-neutralization.md)。许可见 [LICENSE](LICENSE)。

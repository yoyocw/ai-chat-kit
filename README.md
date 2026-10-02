# AI Chat Kit

面向 Java 8 和 Spring Boot 2.7 的嵌入式 AI 聊天组件，提供单聊、群聊、流式输出、停止、会话管理、分享和工具授权。通过 Maven 依赖、`@EnableAiChatKit` 与配置启用。

## 组件

| 组件 | 职责 |
|---|---|
| `ai-chat-kit-contract` | 中立接口与身份、存储、授权契约 |
| `ai-chat-kit-engine` | 对话与执行流程 |
| `ai-chat-kit-starter` | 注解激活与自动装配 |
| `ai-chat-kit-adapter-web` | HTTP 与 SSE 入口 |
| `ai-chat-kit-adapter-mybatis-plus` | MyBatis-Plus 实现的 AI 自有 PostgreSQL 存储 |
| `ai-chat-kit-adapter-mcp-jwt-v1` | 应用与工具范围授权、凭据签发 |
| `ai-chat-kit-adapter-hosted-proxy` | 可选宿主代理与停止协议 |
| `ai-chat-kit-mcp-jwt` | 中立 JWT 协议 |
| `ai-chat-kit-host-platform` | 可选平台宿主接入 |

Maven group 为 `io.github.yoyocw`，核心 Java 包为 `io.github.yoyocw.aichatkit`。源码集中在 `ai-chat-kit-components`，不再包含平台通用框架、旧业务服务和 FAC。身份适配复用宿主已有认证，不把宿主框架复制进组件。

默认构建 8 个中立组件；平台宿主和若依宿主分别按需构建。独立的契约、存储、Web 和工具适配边界保留，使用方只选择需要的 JAR。

## 架构设计

框架采用 Spring 的依赖注入、条件装配和事务回调：模型 SPI 可替换默认适配，中立执行句柄在事务提交后消费，旧发送入口继续兼容。第一阶段重构及 153 项本地测试已完成，迁移范围见 [实施结果](docs/componentization/spring-refactor.md)；整体边界见 [设计](docs/superpowers/specs/2026-10-02-spring-framework-design.md) 与 [计划](docs/superpowers/plans/2026-10-02-spring-framework-design.md)。MVC 与默认模型 SDK 的物理制品拆分留待后续阶段。

## 接入

引入同版本 `ai-chat-kit-starter`、Web/MyBatis-Plus 及所需适配组件，在启动类添加：

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

持久化统一使用 MyBatis-Plus：五张 `ai_runtime_*` 表对应实体和 `BaseMapper`，会话锁、状态条件更新与分享有效期校验保留。组件内部持有独立会话工厂，不接管宿主 Mapper 扫描或插件；无需额外配置 `@MapperScan`。模块名为 `ai-chat-kit-adapter-mybatis-plus`，原有 `storage.jdbc` 配置继续兼容，数据库仍为 PostgreSQL。实现与验证见[存储迁移说明](docs/componentization/mybatis-plus-migration.md)。

## 构建

使用 JDK 8、Maven 3.9。当前开发候选版本为 `1.2.0-SNAPSHOT`。

```sh
# 中立组件
mvn -B -ntp -Drevision=1.2.0-SNAPSHOT clean install

# 增加平台宿主适配
mvn -B -ntp -Pai-platform-host -Drevision=1.2.0-SNAPSHOT clean install

# 增加若依宿主适配；先准备匹配的宿主 provided 制品
mvn -B -ntp -Pai-ruoyi-host -Drevision=1.2.0-SNAPSHOT clean install
```

普通组件发布为薄 JAR、sources JAR 和独立 POM。每个 JAR 包含 `META-INF/LICENSE`。平台宿主包不需要平台兼容 JAR；若依宿主的 provided 依赖由已有宿主提供。

```powershell
./scripts/component-release/verify-package.ps1 -Repository <Maven仓库目录> -Version 1.2.0-SNAPSHOT
# 同时验证平台宿主包
./scripts/component-release/verify-package.ps1 -Repository <Maven仓库目录> -Version 1.2.0-SNAPSHOT -IncludePlatformHost
```

第三方依赖由配置的 Maven 仓库解析。主工程不需要地理库、消息队列、服务注册中心或平台 ORM 框架。若依适配所需的宿主资源不代表所有组件的运行依赖。

## 配置与运行边界

- H1 为启动时装配：开启、关闭和升级需要重启；移除依赖和接线后需要重新构建。当前不提供运行时 JAR 卸载。
- 身份命名空间应保持部署配置稳定，避免改名后将既有数据隔离到新命名空间。
- JDBC 使用 `ai_runtime_*` 表，不自动迁移宿主已有聊天表、历史记录或分享链接。首次共存建议使用独立路由前缀。
- 模型、数据库和密钥由部署环境配置，不分发本地环境文件、真实凭据或依赖缓存。
- 构建和隔离测试不等同真实业务联调或生产部署验收。

最新存储实现及验证见 [MyBatis-Plus 迁移](docs/componentization/mybatis-plus-migration.md)，此前结构精简见 [项目精简结果](docs/componentization/slimming-results.md)。此前 Preview 的制品和验证记录保持不变；主分支更新不等同发布新版本。许可见 [LICENSE](LICENSE)。

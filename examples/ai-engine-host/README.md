# Spring Boot 2 AI接入样例

本样例面向Java 8 / Spring Boot 2.7.18，不继承平台父POM，不引入平台system/info/fac服务。默认关闭AI，可运行宿主健康与装配诊断；没有内置假用户、假数据库或默认全权授权。

## 接入步骤

1. 参考本目录pom.xml引入同版本的starter、adapter-web、adapter-jdbc及按需应用授权/MCP适配依赖。本地候选版本为`1.2.0-SNAPSHOT`，正式发布应使用可追溯制品。
2. 在目标项目配置类或启动类添加`@EnableAiChatKit`。只扫描宿主自己的包，不要求扫描平台业务包。
3. 参考application.yaml设置固定namespace、所选模式、应用/模型/成员目录与存储；秘密由部署环境注入。
4. 通用宿主需引入适配当前宿主的身份包，或实现真实身份捕获、会话复核、权限及必要业务接口。满足下述框架版本前提的平台项目可选择`ai-chat-kit-host-platform`，由组件提供身份、会话、权限和普通入口来源端口，无需另写 AI Port。
5. 准备AI自有PostgreSQL表，关闭自动SQL初始化；新库和既有库升级使用不同脚本，详见迁移清单。
6. 明确开启engine和所需web/MCP开关，再验证真实业务。缺失适配或资源配置会拒绝启动；健康接口正常不等于AI就绪。

```java
@SpringBootApplication
@EnableAiChatKit
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

上例需按目标包添加Spring及`io.github.yoyocw.aichatkit.ai.starter.annotation.EnableAiChatKit`导入。样例实际启动类额外排除Boot默认数据源，是因为样例没有宿主业务库；真实项目已有业务数据源时保留原配置，不照搬排除项。

## 本样例关键配置

```yaml
ai-chat-kit:
  ai:
    engine:
      enabled: false
    web:
      enabled: false
      path-prefix: /admin-api/ai-component
    starter:
      namespace: ai-engine-host-example
      modes: [SINGLE, GROUP]
    storage:
      type: postgresql
      jdbc:
        mode: isolated
        jdbc-url: ${AI_DB_URL:}
        username: ${AI_DB_USERNAME:}
        password: ${AI_DB_PASSWORD:}
        maximum-pool-size: 5
        connection-timeout-ms: 5000
        validation-timeout-ms: 1000
```

这是默认关闭的配置片段，不是完整业务配置；应用、模型、目录及MCP参数见 `src/main/resources/application.yaml`。独立资源由AI创建并关闭，不注册为宿主默认数据源/事务管理器。不支持把外部异库事务静默当作AI事务。

## 平台同进程接入

当前原平台宿主可按同一版本引入以下组件，并在启动类添加上文的 `@EnableAiChatKit`。身份、会话、权限、普通来源和数值主体映射由 `ai-chat-kit-host-platform` 提供，业务项目不需自编 AI Port。宿主仍须提供原安全、租户、OAuth2 和权限的本地实现 Bean；装配会核验 `oauth2TokenApiImpl` 与 `permissionApiImpl` 是原本地实现并保留 Spring AOP，缺失或替换时拒绝启动。

```xml
<dependency><groupId>io.github.yoyocw</groupId><artifactId>ai-chat-kit-starter</artifactId><version>${ai.version}</version></dependency>
<dependency><groupId>io.github.yoyocw</groupId><artifactId>ai-chat-kit-adapter-web</artifactId><version>${ai.version}</version></dependency>
<dependency><groupId>io.github.yoyocw</groupId><artifactId>ai-chat-kit-adapter-jdbc</artifactId><version>${ai.version}</version></dependency>
<dependency><groupId>io.github.yoyocw</groupId><artifactId>ai-chat-kit-host-platform</artifactId><version>${ai.version}</version></dependency>
<dependency><groupId>io.github.yoyocw</groupId><artifactId>ai-chat-kit-adapter-mcp-jwt-v1</artifactId><version>${ai.version}</version></dependency>
```

```yaml
ai-chat-kit:
  ai:
    engine:
      enabled: true
    starter:
      namespace: ${AI_NAMESPACE:platform}
    web:
      enabled: true
      path-prefix: /admin-api/ai-component
    platform-host:
      mode: in-process
      in-process:
        native-package-root: ${AI_NATIVE_PACKAGE_ROOT} # 宿主原生包根，仅部署环境提供，无默认值
        platform-tenant-id: ${AI_PLATFORM_TENANT_ID:} # 可空；空值禁止平台密钥管理
    mcp-jwt-v1:
      authorization-enabled: true
      signing-enabled: ${AI_MCP_SIGNING_ENABLED:false}
      verification-enabled: false
      # apps: 按实际 appId 固定 permission、mcpId、toolIds、endpointCodes
      # issuer: ${AI_MCP_ISSUER}
      # audience: ${AI_MCP_AUDIENCE}
      # keys:
      #   public-key: ${AI_MCP_PUBLIC_KEY}
      #   private-key: ${AI_MCP_PRIVATE_KEY}
```

上面只是接线片段：应用、模型、成员目录、AI 自有数据库与策略仍需按实际部署补齐。无工具应用仍需应用授权；启用用户工具时，签发开关、固定应用/工具/端点策略、数值主体映射和受限密钥源必须齐全。密钥通过部署秘密引用，不写入仓库。此模式只处理宿主普通登录与工具凭据签发，不能兼任仅持 `mcp_jwt_` 的资源侧验签；资源服务应使用独立受控适配和公钥信任配置，`verification-enabled=true` 与此模式同时开启会拒绝启动。当前源码与白盒装配验证不等于真实数据库、百炼或上线验收。

## 旧 HTTPS 普通模式

平台宿主在已有安全、租户框架中加入同版本`starter`、所需中立适配器及`ai-chat-kit-host-platform`，启动类添加`@EnableAiChatKit`，再启用以下配置。组件使用原始 Bearer、已认证`LoginUser`和`TenantContextHolder`共同确定租户；请求头或参数不能选择消费者凭据。

此模式复用宿主原生登录与租户上下文，并由组件自带的受限 HTTPS 客户端查询认证服务。无需额外安装平台兼容框架或在宿主新增 inspection 客户端类；认证服务仍须提供相应受限查询接口。原生包根只从部署配置读取，启动时严格核验所需类型与方法，缺失即拒绝。

```yaml
ai-chat-kit:
  ai:
    engine:
      enabled: true
    starter:
      namespace: platform
    web:
      enabled: true
      path-prefix: /admin-api/ai-component
    platform-host:
      mode: ordinary-bearer
      native-package-root: ${AI_NATIVE_PACKAGE_ROOT} # 原生登录/租户上下文；仅来自部署配置
      inspections:
        - tenant-id: ${AI_TENANT_ID}
          base-url: ${AI_AUTH_BASE_URL}
          consumer-access-token: ${AI_INSPECTION_TOKEN}
    mcp-jwt-v1:
      authorization-enabled: true
      signing-enabled: false
```

每个租户必须配置独立的受限消费者，`consumerTenantId`和`subjectTenantId`均固定为该项`tenant-id`。增加租户就增加列表项；重复租户、空凭据、非法 HTTPS 源站或未知租户会拒绝启动或调用。已有`session-inspection`固定租户配置仍可用于未配置多租户列表的接入。无工具聊天仍需`authorization-enabled`及真实应用权限策略；选择工具后必须启用签发并提供主体映射、密钥和资源侧信任配置。

从旧兼容包升级到精简版时，ordinary-bearer 需要上述原生包根；也接受已有 `platform-host.in-process.native-package-root` 配置作为回退。组件不会猜测宿主包名或退回复制的兼容身份类。同进程模式继续使用原有嵌套配置。

## 存量数据与旧入口

JDBC 组件创建和读取的是`ai_runtime_*`新存储。它与旧 server/MyBatis 使用的`ai_chat_*`、`ai_group_chat_*`数据模型不同，分享结构也不同；接入组件不会自动迁移历史会话、群成员快照或分享记录，也不会让新分享继承旧分享链接。

首次共存接入应保留旧 server/MyBatis 的历史与分享读取链路，并让组件 Web 使用独立前缀`/admin-api/ai-component`。切换旧入口、迁移旧表或兼容分享需要另行评审和真实数据验证。`reuse`或`reference`只描述数据库连接与事务资源复用，不表示新旧表结构兼容。

## 复用宿主资源

选择`reuse`时不填写AI独立连接参数，要求宿主数据源和管理器可明确解析且同源。选择`reference`时配置：

```yaml
ai-chat-kit:
  ai:
    storage:
      type: postgresql
      jdbc:
        mode: reference
        data-source-bean: yourActualDataSourceBean
        transaction-manager-bean: yourActualTransactionManagerBean
```

上述Bean名称需替换为宿主实际名称，且不得与isolated凭据混用。首次复用时存在未知附属线程资源会被保守拒绝；复杂MyBatis/JPA/JTA宿主须明确验证资源归属，历史兼容工程的测试不自动证明本次接入通过。

## 验证与部署边界

在仓库根目录可使用既有Maven profile构建样例：

```text
mvn -P ai-engine-example -pl examples/ai-engine-host -am package -DskipTests
```

`/host/health`仅代表宿主存活；`/host/engine`只列本地装配状态，不验证数据库、登录、模型或工具。默认监听127.0.0.1随机端口。关闭AI的启动已验证；完整启用及真实业务需实际宿主适配和环境，不以启动成功代替业务验收。

本轮结构调整与验证要求见 [项目精简计划](../../docs/componentization/slimming-plan.md)。旧服务端、旧表脚本和旧 SDK 样例已退出主工程；历史业务工程继续保留自己的读取路径，不自动迁移数据。其他宿主适配的源码存在不等于已完成真实业务验收。

# Spring 思想重构：第一阶段实施结果

日期：2026-10-02。设计依据：[整体设计](../superpowers/specs/2026-10-02-spring-framework-design.md)；任务范围：[实施计划](../superpowers/plans/2026-10-02-spring-framework-design.md)的任务 1–5。

本轮完成兼容阶段的代码重构：通过构造器注入、中立策略接口、条件自动配置和事务回调组合执行流程。保留 Java 8 / Spring Boot 2.7、现有 Maven 坐标、MyBatis-Plus 存储与表结构。代码基线为 `f3ac6e1fb85ae1131ef46e04b1197dc846115c1b`；本文件与实现共同提交。

## 已实现的职责边界

| 职责 | 实现与约束 |
|---|---|
| 模型策略 | `AiModelClient`、请求、事件、结果和异常位于无第三方依赖的 contract；`BailianModelClient` 封装现有供应方协议、错误分类和取消调用。 |
| 执行流程 | `AiSingleChatExecutor`、`AiGroupChatExecutor`、`AiGroupChatStreamExecutor` 依赖模型 SPI 和中立事件，不引用 Bailian、MVC 或 OutputStream。单群聊差异保持显式。 |
| 强制授权 | 引擎通过 `AiInvocationGuard` 调用 `AiInvocationAuthorizationPort`，业务快照不再提供授权决定。无工具的应用也检查权限；有工具时验证委派凭据。 |
| 事务与流消费 | `AiPreparedExecution` 仅在实际准备事务提交后就绪；提交前、回滚后和重复消费均拒绝。继续使用选定的 `AiTransactionExecutor` 与 REQUIRED 传播。 |
| HTTP 兼容 | 旧执行服务保留发送、停止和 MVC 返回签名，委托新内核；共用 `AiSseEventEncoder` 保持现有事件协议。 |
| 条件装配 | 宿主提供 `AiModelClient` Bean 后，默认模型客户端、属性 Bean 和资源不再自动创建。默认实现复用已有 Bean；保留 Starter 的 Primary 消歧与 MCP 严格唯一规则。 |
| 生命周期 | 客户端关闭与新调用登记使用同一锁协调；关闭后拒绝新调用、取消在途调用，重复关闭安全。仅回收客户端拥有的资源。 |

移除了四处多余的 `@EnableTransactionManagement`。真实事务继续由显式事务执行器管理；组件不会因此额外开启宿主的全局注解事务代理。持久化实现、同源约束、行锁和终态 CAS 保持原有语义。

执行句柄还处理了一个 Spring 回调边界：数据库已提交但较早的 `afterCommit` 回调抛错时，通过 `afterCompletion(COMMITTED)` 完成就绪转换。消费线程必须处于事务之外，避免将模型流调用放进长事务。

## 宿主接入与扩展

内置接入仍为依赖、`@EnableAiChatKit` 和配置，现有 `send` / `stop` 用法继续有效。需要接入其他传输时，可使用新增的中立入口：

```java
// singleChatService 是 Spring 注入的 AiHostSingleChatService。
// expectedActorId 只用于与真实登录身份核对，不是登录凭据。
AiPreparedExecution execution = singleChatService.prepare(request, expectedActorId);
// 如果 prepare 加入了宿主外层事务，必须等该事务真正提交。
// 在事务之外交给调用方选择的执行线程，只能消费一次。
execution.consume((event, data) -> eventConsumer.accept(event, data));
```

群聊对应 `AiHostGroupChatService.prepare(conversationId, content, expectedActorId)`。中立事件使用相互独立、不可修改的顶层 Map 快照；消费端不能修改这些 Map。旧 SSE 输出入口继续传递可修改的 Map 副本给自定义 writer。

替换模型时，注册一个 `AiModelClient` Bean，实现配置检查、事件流调用和取消。默认模型适配自动退让，不需要伪造 `BailianClient` 或 `BailianProperties`。SPI 中的 `stillGenerating` 用于在流调用期间检查执行状态，适配器需遵守取消及安全错误契约。工具凭据字段使用 transient、无 Bean getter 的专用读取方法，默认 Bean 序列化及请求的 `toString` 不输出凭据；供应方异常映射为安全消息。

`AiExecutionPolicy` 可以由宿主 Bean 替换；它控制生成占位的过期判断，并非 HTTP 超时器。默认使用已存在的 `BailianProperties.readTimeoutSeconds + 30`，没有该属性 Bean 时为 240 秒。默认记忆预算继续读取已有属性；自定义模型没有这些属性时使用最近 12 条、总预算 6000、摘要预算 2000、ASCII 每 token 3 字符、安全余量 20% 的默认值。需要调整时注册自定义 `AiConversationMemoryService`。

## 兼容与迁移清单

| 现有用法 | 本轮处理 |
|---|---|
| 内置宿主的依赖、注解、配置和发送/停止 | 保持；HTTP/SSE 字段和顺序由本地受控消费者验证。 |
| 自定义 `AiSingleChatBusinessPort` 只补充业务快照 | 可以继续替换，不影响引擎必经授权。 |
| 仅通过 `AiSingleChatBusinessPort.getMcpAuthorization` 自定义授权 | 迁移到独立 `AiInvocationAuthorizationPort`。旧方法签名保留并标记弃用，引擎不再调用；缺少独立授权端口时拒绝装配，不回退使用旧凭据。 |
| 手动构造 `AiChatExecutionService` | 构造器改为 `(AiSingleChatExecutor, AiChatStreamEventWriter)`，推荐由 Spring 注入。 |
| 手动构造群聊执行/流服务 | 分别改为 `AiGroupChatExecutionService(AiGroupChatExecutor)`、`AiGroupChatStreamService(AiGroupChatStreamExecutor)`。直接构造管理服务时，模型依赖也须改用 SPI。 |
| 直接调用低层 `AiGroupChatStreamService.createResponse*` | 必须在选定的 AI 事务执行器所管理的实际事务中准备，提交后消费。推荐使用宿主门面。 |
| 覆盖 `AiChatStreamEventWriter.write` | 旧单聊 SSE 入口仍调用覆盖方法，传入可修改副本；支持原地追加字段。 |
| 仅覆盖 writer 的 `writeProgress`、`writeBailianEvent` 或 `data` | 新内核不再调用这些供应方辅助方法；迁移到 `write` 覆盖或中立事件接收器。 |

这不是全部内部构造器或高级扩展的二进制无损升级。依赖内部实现的宿主需按上表重新编译和调整装配。群聊 MCP 绑定仍按既有边界拒绝，不扩大工具能力。

## 验证结果

JDK 8 / Maven 3.9.6；本轮使用隔离的候选 Maven 仓库，首次构建前其中没有本项目 JAR。

| 验证 | 测试数 | 失败 / 错误 / 跳过 |
|---|---:|---|
| 包含可选平台适配的完整 reactor clean install | 125 | 0 / 0 / 0 |
| 全量构建后新增并定向验证的装配、模型超时用例 | 8 | 0 / 0 / 0 |
| reactor 外复制的独立消费者，使用本轮候选 JAR | 20 | 0 / 0 / 0 |
| 合计（不同用例） | **153** | **0 / 0 / 0** |

其中独立消费者包含 15 项聊天流程测试，覆盖单群聊落库、停止提交后取消、必经授权、外层事务未提交/回滚、消费一次、会话失效的重试边界、完成审计失败、纯模型 SPI 和旧 writer 覆盖。补充故障测试还验证群聊会话失效最多重试一次、群聊完成审计失败、首个增量后永久断连的失败落库与部分回答保留，以及模型超时的安全终态。群聊每次成员事件输出时，通过独立数据库连接确认回复已经全部提交。引擎定向测试覆盖生命周期、模型映射、安全异常、提交回调异常、并发单次消费、SSE 编码和授权检查。原始超时、中断及嵌套超时均验证为安全、可重试的中立异常。

保留了有效的失败到通过证据：旧候选包的 8 项基线通过；扩充后的 9 项测试在旧包出现 3 项失败，分别对应业务授权绕过、未提交消费和重复消费。客户端生命周期旧实现有 2 项失败；提交回调异常和旧 writer 修改事件 Map 的回归也先复现失败，再修复通过。不是每项新增测试都有独立的旧版本失败记录。

数据库测试实际运行在本机专用 PostgreSQL，仅监听回环地址，各测试创建并清理随机 schema。模型、登录和业务接口使用受控替身；没有连接业务测试服、真实模型或执行部署。本地数据库验证不能替代真实宿主验收。线程上下文恢复测试使用自定义作用域适配器包装真实 JDBC 作用域，验证执行器调用作用域并恢复原值、事务资源释放；不能据此宣称真实宿主 ThreadLocal 已验收。群聊会话失效测试覆盖重试次数与终态，没有独立注入并核对非空旧 session 的持久化清理。

## 制品与复验

Maven POM 未修改：默认 8 个组件 JAR，加可选平台适配共 9 个，完整 reactor 仍是 12 个项目（含 3 个聚合/POM 项目）。8 个中立组件离线解析得到的 56 项第三方运行依赖，与重构前的坐标、版本、scope 和 JAR SHA-256 全部一致，没有增加模块或第三方框架依赖。现有包校验脚本核查了 9 组二进制 JAR、sources JAR、POM 和 LICENSE；sources 内容与当前模块源码/资源逐字节核对。独立消费者通过实际 JVM CodeSource 核对 7 个所用组件均来自本轮候选 JAR，没有加载组件 reactor 的 target/classes。

准备一次性本机 PostgreSQL 并设置 `AI_TEST_POSTGRES_URL`，使用独立 settings 和 Maven 本地仓库执行：

```sh
mvn -B -ntp -Pai-platform-host clean install
mvn -B -ntp -f <复制到-reactor-外的消费者>/pom.xml test
```

另行运行 `BailianModelClientTest`、`AiStarterCandidateSelectionTest` 与 `AiMcpV1CandidateSelectionTest`。最终定向执行共 13 项，其中 5 项已包含于全量构建、8 项是后补用例；合计 133 项不同组件测试，下次完整构建将包含这些用例。没有数据库变量时相关用例会跳过，不能作为本轮结果复现。

本地原始日志、CodeSource 和摘要位于被 Git 忽略的 `.verification/spring-framework-refactor`。以下列出本轮 `1.2.0-SNAPSHOT` 候选二进制的 SHA-256，记录本次测试对象，并非可复现构建承诺：

| 组件 | JAR SHA-256 |
|---|---|
| `ai-chat-kit-mcp-jwt` | `e9287c45e37ec5826b97326510d844f789f499ebc454682389d09df9a292477b` |
| `ai-chat-kit-contract` | `8d69bcc2add8463e6dfa59697b5e17d24e66b4896593d5ab561e12ab18061994` |
| `ai-chat-kit-engine` | `2f9261ed94311aab1a6e32f2c5fa6fc363027fc03a240174cae2cbe7c1d14b1d` |
| `ai-chat-kit-starter` | `b4a38b0debd6afb11894b2cbcc5af6caa3ade7d8469a12cccd7e2992cbfeff13` |
| `ai-chat-kit-adapter-web` | `a6c905fe327640a170616061af3d48b08006cb7db8806dcd5c585452d7ff742e` |
| `ai-chat-kit-adapter-mybatis-plus` | `58940df0a58743ded406761c97e5e037bba45f8c18f665d1bfcdcbfbf56c3f04` |
| `ai-chat-kit-adapter-mcp-jwt-v1` | `2d81c5a7fbf89d66b07802c2a8ecead5abcce536dd48ef23146d3b3241c6b22f` |
| `ai-chat-kit-adapter-hosted-proxy` | `38c43442a2c93c22475292ff2639528e57bf40c5bb0cd0d984588fde7c8b0f92` |
| `ai-chat-kit-host-platform` | `6872ae577ed3d7521e0756accc4e9eeaffa87a8687975cc9fc38934831ba0835` |

## 后续范围

本轮完成实施计划的第一阶段。MVC 兼容门面和默认模型适配仍位于 engine 制品中，MVC / OkHttp 物理依赖尚未移出；后续物理拆分按独立迁移阶段处理。组件启停、升级仍在重启后生效。本轮仅提交源码，不创建新 tag 或 Release。

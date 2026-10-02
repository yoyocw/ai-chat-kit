# Spring 思想重构实施计划

日期：2026-10-02
状态：**待实施；本文件中的任务均未完成。**
设计依据：[整体设计](../specs/2026-10-02-spring-framework-design.md)
起始代码：`717a6d7b9a65f153a68253d6524b7b9ddadfc827`

> 实施时遵循 executing-plans 或 subagent-driven-development 工作流。当前文件是可评审计划，不构成代码已改造或测试已通过的声明。

**目标：** 保持内置宿主接入方式、现有发送方法与安全语义，让模型与传输细节退出新执行内核，由 Spring 装配默认实现和替换扩展。旧自定义业务授权与直接构造执行器的用法单独提供迁移说明。

**架构：** 中立 Port + 构造器注入 + 条件自动配置 + 小型组合式执行协作类。第一阶段保持现有 Maven 模块数量与坐标。

**技术栈：** Java 8、Spring Boot 2.7.18、Spring 5.3、MyBatis-Plus、PostgreSQL、JUnit 5。

## 全局约束与职责

- 保留 `@EnableAiChatKit`、现有配置键和 HTTP/SSE 协议，不改 SQL 表结构。
- 可信身份、真实登录、应用授权、租户归属与工具范围检查不能变为可选回调。
- 保留 Starter 的普通单值注入 / Primary 消歧与 MCP 安全装配的严格唯一规则，分开测试，不统一收紧。
- 自定义旧业务授权须迁移独立授权 Port；保留方法签名不等于所有扩展装配和旧构造器零改动。
- 真实同源短事务、行锁、完成/停止 CAS、提交后取消继续有效。
- 不使用全局 Bean 查询驱动业务，不新增通用事件总线或插件注册中心。
- 不关闭宿主资源；第一阶段不声称移除 engine 的 MVC / OkHttp 物理依赖。
- 不连接真实业务环境；验证仅限本地受控资源和已授权测试。
- 每阶段提交包含具体差异、有效测试结果及遗留边界；最后核对远程分支一致性。

路径简称：`C = ai-chat-kit-components`；`J = src/main/java/io/github/yoyocw/aichatkit`；新文件名在各任务注明。

可并行分工：
- 模型工作项拥有 contract.model、Bailian 适配包与相关测试。
- 装配工作项拥有自动配置及生命周期测试，与模型工作项串行交接共享配置文件。
- 授权/编排工作项拥有执行器和业务 Port，必须等待模型契约定稿。
- Web 工作项拥有 SSE 编码与 Web 测试，等待中立事件接口定稿。
- 合并负责人管理 POM、README、发布脚本、独立消费者和集成验证。各工作项不得覆盖其他人的修改。

## 任务 1：明确特征行为与可替换点

涉及：
- `examples/ai-component-consumer/src/test/java/example/aicomponent/AiComponentConsumerChatFlowTest.java`
- `C/ai-chat-kit-starter/src/test/java/io/github/yoyocw/aichatkit/ai/starter/AiActivationBoundaryTest.java`
- `C/ai-chat-kit-engine/src/test/java/.../model/`（新增定向测试）
- 现有单聊/群聊执行器与流输出服务。

- [ ] 记录现有事件字段/次序、已输出增量后的恢复禁止、群聊完成后输出、停止提交后取消。
- [ ] 增加能观察外部语义的必要测试，不为机械转发方法堆叠测试。
- [ ] 梳理旧公开方法/构造器及直接消费者，形成兼容清单。

验收：重构前的受控行为基线可运行，测试能在关键语义被破坏时失败。

## 任务 2：模型策略与生命周期

涉及：
- `C/ai-chat-kit-contract/J/module/ai/contract/model/`：新增 AiModelClient、AiModelRequest、AiModelEvent、AiModelResult、AiModelFailure。
- `C/ai-chat-kit-engine/J/module/ai/framework/bailian/`：新增 BailianModelClient 适配器，保留底层客户端。
- `C/ai-chat-kit-engine/J/ai/engine/autoconfigure/AiModelRuntimeAutoConfiguration.java`
- `C/ai-chat-kit-engine/J/module/ai/config/BailianProperties.java`
- 单聊、群聊和会话管理中直接使用 BailianClient 的位置，以及对应自动配置的模型条件和构造注入。
- `C/ai-chat-kit-starter/J/ai/starter/autoconfigure/AiStarterDependencyVerifier.java`：模型完整性校验。

- [ ] 定义中立模型请求/事件/异常，单独处理秘密字段，避免默认日志与序列化泄漏。
- [ ] 实现默认百炼适配；迁移供应方错误映射、续接信息、提示协议和取消。
- [ ] 同阶段迁移全部执行配置、构造注入和 Starter 校验中的模型条件为 SPI；存在用户 SPI 时不多建默认客户端及线程资源。
- [ ] 使用完整 Starter 容器验收自定义 SPI，无 BailianClient / BailianProperties 仍可按选定模式装配。
- [ ] 对客户端关闭、新调用登记与取消增加同一生命周期协调，关闭幂等。
- [ ] 用受控模型验证取消、超时、会话失效以及默认 Bean 替换；证明没有第二套池。

验收：执行内核可使用假模型运行，不依赖 Bailian 具体类；原生产模型配置仍兼容。底层包与 OkHttp 第一阶段仍在 engine 制品内。

## 任务 3：授权职责与装配收敛

涉及：
- `C/ai-chat-kit-contract/J/module/ai/contract/context/AiSingleChatBusinessPort.java`
- `C/ai-chat-kit-engine/J/module/ai/service/chat/AiChatExecutionService.java`
- `C/ai-chat-kit-engine/J/module/ai/service/groupchat/AiGroupChatExecutionService.java`
- `C/ai-chat-kit-engine/J/ai/engine/autoconfigure/AiSingleExecutionAutoConfiguration.java`
- 同目录 GroupExecution / ConversationManagement / ConversationShare 配置。
- `C/ai-chat-kit-starter/J/ai/starter/autoconfigure/AiStarterDependencyVerifier.java`

- [ ] 引擎直接调用并验证 AiInvocationAuthorizationPort；业务快照与授权分离。
- [ ] 保留旧业务授权方法签名并给出扩展迁移说明；仅有旧业务授权实现时明确提示缺少独立授权 Port，不回退使用旧返回凭据。
- [ ] 为内置宿主和迁移后的自定义扩展分别验证授权只执行一次，直接构造执行器的兼容范围单独列明。
- [ ] 普通 Bean 与 Starter 身份候选保留 Spring 单值注入 / Primary 规则；MCP 中原有严格唯一的端口继续拒绝多候选，分别覆盖 Primary 与无 Primary 情况。
- [ ] 证实没有注解事务消费者后，移除四处多余 EnableTransactionManagement。
- [ ] 验证自定义业务 Bean、无工具、缺凭据、身份错配、候选冲突及宿主事务基础设施不被额外开启。

验收：用户替换业务上下文不能跳过应用授权；原短事务仍真实生效。

## 任务 4：传输隔离与执行步骤组合

涉及：
- `C/ai-chat-kit-contract/J/module/ai/contract/execution/`：新增中立事件接收与执行句柄（最终命名随任务 1 清单确定）。
- `C/ai-chat-kit-engine/J/module/ai/service/chat/AiChatExecutionService.java`
- 同目录 `AiChatStreamEventWriter.java`
- `C/ai-chat-kit-engine/J/module/ai/service/groupchat/AiGroupChatExecutionService.java` 与 `AiGroupChatStreamService.java`
- `C/ai-chat-kit-starter/J/ai/starter/host/AiHostSingleChatService.java`、`AiHostGroupChatService.java`
- `C/ai-chat-kit-adapter-web/src/test/java/.../AiWebActivationBoundaryTest.java`

- [ ] 新增中立入口，将同步准备与异步流消费分离；句柄在真实事务提交后就绪，回滚后失效，提交前消费拒绝。
- [ ] 覆盖 REQUIRED 加入宿主外层事务后的提交、回滚和未提交消费，不通过 REQUIRES_NEW 提前提交。
- [ ] 旧 StreamingResponseBody 方法委托新入口，保留已有签名、字段和错误语义。
- [ ] 共用 SSE 编码器；过渡期可留在 engine 的兼容包，不能引入 engine → adapter-web 反向依赖。
- [ ] 仅提取已验证重复的状态探针、重试判定与完成协调；单群聊差异保持显式。
- [ ] 保留开始审计与准备同事务；终态及回复提交后尽力记录完成审计，覆盖完成审计失败不改终态。
- [ ] 验证调用线程与流线程上下文清理、输出断连、取消交错、错误终态，以及两个模式的恢复差异。

验收：新内核没有 MVC/SSE 类型依赖；内置宿主及已有发送方法的消费者保持兼容；旧自定义授权和直接构造执行器的迁移符合任务 3 的明确范围。当前整个 engine JAR 仍含兼容 MVC 类型。

## 任务 5：集成回归与交付

涉及：
- `C/ai-chat-kit-adapter-mybatis-plus/src/test/java/.../AiJdbcPostgresIntegrationTest.java`
- 同模块 `AiJdbcActivationBoundaryTest.java`
- `examples/ai-component-consumer/`
- `scripts/component-release/verify-package.ps1`
- README 与实施结果文档（实施后新建）。

- [ ] 运行各变更的定向测试，再运行平台 profile 的 reactor 测试。
- [ ] 使用本地临时 PostgreSQL 验证真实事务/锁/CAS、同源绑定及分享最终状态，不能以 skipped 视作通过。
- [ ] 打包到独立候选 Maven 仓库，复制消费者到 reactor 外运行。
- [ ] 核查消费者实际加载候选 JAR，旧方法可运行，新 SPI 替换有效。
- [ ] 比较模块、第三方运行依赖与制品清单；第一阶段无新框架依赖。
- [ ] 记录实际测试数量/跳过/失败、源码版本、候选 SHA；按已有授权提交推送，复核工作区和远程一致。
- [ ] 保持现有 Release 不变，除非另有发布任务。

基础命令（环境参数另行传入，禁止写入真实凭据）：

```sh
mvn -B -ntp -Pai-platform-host clean install
mvn -B -ntp -f <独立消费者目录>/pom.xml test
```

验证时显式指定隔离的 settings、本地仓库和本地数据库 URL。定向测试按实际新增类选择，避免单独运行 engine 模块时漏建 contract。

## 后续物理依赖拆分：独立迁移阶段

这部分不混入第一阶段兼容重构：

- [ ] 盘点所有 MVC 返回类型消费者，明确新版本的迁移范围。
- [ ] 中立 engine 不保留 MVC 类型签名；Starter 改用中立结果；Web 适配承接 HTTP 门面。
- [ ] 确认不存在 starter / adapter-web 循环依赖。
- [ ] 仅在需要移除默认模型 SDK 或引入第二模型时拆可选模型制品，更新 BOM 与发布验证。
- [ ] 以真实缺少 MVC / 默认供应方类的独立消费者证明物理隔离，不以源码搜索代替类加载验证。

若必须维持旧制品与旧方法完全兼容，则延后此阶段；不得用 optional 依赖掩盖链接失败。

## 评审重点

逐阶段检查：授权是否仍必经、依赖是否单向、事务是否真实且短、生命周期是否只管理自有资源、兼容签名是否保留、验证是否来自本轮代码。评审发现差异时修正该阶段，再进行下一阶段。

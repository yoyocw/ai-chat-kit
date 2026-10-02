# Spring 思想重构实施计划

日期：2026-10-02
状态：**整体实施计划已完成，包括第一阶段任务 1–5 与后续物理依赖拆分。** 当前版本为 `2.0.0-SNAPSHOT`。第一阶段历史证据见[兼容重构实施结果](../../componentization/spring-refactor.md)；当前制品、迁移与验证见[物理拆分实施结果](../../componentization/spring-physical-split.md)。
设计依据：[整体设计](../specs/2026-10-02-spring-framework-design.md)
起始代码：`717a6d7b9a65f153a68253d6524b7b9ddadfc827`

> 第一阶段采用 subagent-driven-development 工作流，其实施起点为 `f3ac6e1fb85ae1131ef46e04b1197dc846115c1b`。下列任务 1–5 保留该阶段历史范围；物理拆分基线为 `59c3bff931efe69ba71a41c0ab379304ec1925d2`，具体工作见[物理拆分计划](2026-10-02-spring-physical-split.md)。两阶段受控验收均已完成，真实业务环境不在本次完成声明中。

**目标：** 保持配置键、HTTP/SSE 协议与安全语义，让模型与传输细节退出执行内核，由 Spring 装配默认实现和替换扩展。第一阶段保留旧 MVC 发送方法；已完成的第二阶段按 2.0 显式迁移 Java API 和 Maven 依赖。Starter 的 `send` / `prepare` 返回 `AiPreparedExecution`，旧底层 MVC 门面由既有 Web 制品提供。

**架构：** 中立 Port + 构造器注入 + 条件自动配置 + 小型组合式执行协作类。第一阶段保持当时的 Maven 模块数量与坐标；当前唯一新增模块为可选 `ai-chat-kit-model-bailian`，`adapter-web` 承接传输实现，`engine` / `starter` 不再包含或传递 MVC、Servlet、OkHttp 或百炼实现。

**技术栈：** Java 8、Spring Boot 2.7.18、Spring 5.3、MyBatis-Plus、PostgreSQL、JUnit 5。

## 全局约束与职责

- 保留 `@EnableAiChatKit`、现有配置键和 HTTP/SSE 协议，不改 SQL 表结构。
- 可信身份、真实登录、应用授权、租户归属与工具范围检查不能变为可选回调。
- 保留 Starter 的普通单值注入 / Primary 消歧与 MCP 安全装配的严格唯一规则，分开测试，不统一收紧。
- 自定义旧业务授权须迁移独立授权 Port；保留方法签名不等于所有扩展装配和旧构造器零改动。
- 真实同源短事务、行锁、完成/停止 CAS、提交后取消继续有效。
- 不使用全局 Bean 查询驱动业务，不新增通用事件总线或插件注册中心。
- 不关闭宿主资源；第一阶段未移除 engine 的 MVC / OkHttp 物理依赖，第二阶段已由实际缺类的独立无 Web 消费者证明物理移除。
- 不连接真实业务环境；验证仅限本地受控资源和已授权测试。
- 每阶段保留差异、有效测试结果及遗留边界。任务 1–5 共用契约且须组合编译，本轮以一个可编译提交交付，最后核对远程分支一致性。

路径简称：`C = ai-chat-kit-components`；`J = src/main/java/io/github/yoyocw/aichatkit`；新文件名在各任务注明。

可并行分工：
- 模型工作项拥有 contract.model、Bailian 适配包与相关测试。
- 装配工作项拥有自动配置及生命周期测试，与模型工作项串行交接共享配置文件。
- 授权/编排工作项拥有执行器和业务 Port，必须等待模型契约定稿。
- Web 工作项拥有 SSE 编码与 Web 测试，等待中立事件接口定稿。
- 合并负责人管理 POM、README、发布脚本、独立消费者和集成验证。各工作项不得覆盖其他人的修改。

## 第一阶段历史记录（任务 1–5 已完成）

下列步骤及验收保持第一阶段当时的兼容范围；源码路径已更新到拆分后的现存位置，当前状态以第二阶段验收和[物理拆分实施结果](../../componentization/spring-physical-split.md)为准。

## 任务 1：明确特征行为与可替换点

涉及：
- `examples/ai-component-consumer/src/test/java/example/aicomponent/AiComponentConsumerChatFlowTest.java`
- `C/ai-chat-kit-starter/src/test/java/io/github/yoyocw/aichatkit/ai/starter/AiActivationBoundaryTest.java`
- `C/ai-chat-kit-engine/src/test/java/.../model/`（新增定向测试）
- 现有单聊/群聊执行器与流输出服务。

- [x] 记录现有事件字段/次序、已输出增量后的恢复禁止、群聊完成后输出、停止提交后取消。
- [x] 增加能观察外部语义的必要测试，不为机械转发方法堆叠测试。
- [x] 梳理旧公开方法/构造器及直接消费者，形成兼容清单。

验收：重构前的受控行为基线可运行，测试能在关键语义被破坏时失败。

## 任务 2：模型策略与生命周期

涉及：
- `C/ai-chat-kit-contract/J/module/ai/contract/model/`：新增 AiModelClient、AiModelRequest、AiModelEvent、AiModelResult、AiModelException（原计划候选名 AiModelFailure）。
- `C/ai-chat-kit-model-bailian/J/module/ai/framework/bailian/`：BailianModelClient 与底层客户端的当前位置；第一阶段曾在 engine 内新增适配器。
- `C/ai-chat-kit-engine/J/ai/engine/autoconfigure/AiModelRuntimeAutoConfiguration.java`
- `C/ai-chat-kit-model-bailian/J/module/ai/config/BailianProperties.java`（第一阶段曾在 engine 内）
- 单聊、群聊和会话管理中直接使用 BailianClient 的位置，以及对应自动配置的模型条件和构造注入。
- `C/ai-chat-kit-starter/J/ai/starter/autoconfigure/AiStarterDependencyVerifier.java`：模型完整性校验。

- [x] 定义中立模型请求/事件/异常，单独处理秘密字段，避免默认日志与序列化泄漏。
- [x] 实现默认百炼适配；迁移供应方错误映射、续接信息、提示协议和取消。
- [x] 同阶段迁移全部执行配置、构造注入和 Starter 校验中的模型条件为 SPI；存在用户 SPI 时不多建默认客户端及线程资源。
- [x] 使用完整 Starter 容器验收自定义 SPI，无 BailianClient / BailianProperties 仍可按选定模式装配。
- [x] 对客户端关闭、新调用登记与取消增加同一生命周期协调，关闭幂等。
- [x] 用受控模型验证取消、超时、会话失效以及默认 Bean 替换；证明没有第二套池。

第一阶段验收：执行内核可使用假模型运行，不依赖 Bailian 具体类；原模型配置保持兼容。当时底层包与 OkHttp 仍在 engine 制品内，现已迁至可选模型制品。

## 任务 3：授权职责与装配收敛

涉及：
- `C/ai-chat-kit-contract/J/module/ai/contract/context/AiSingleChatBusinessPort.java`
- `C/ai-chat-kit-engine/J/module/ai/service/chat/AiSingleChatExecutor.java`
- `C/ai-chat-kit-engine/J/module/ai/service/groupchat/AiGroupChatExecutor.java`
- `C/ai-chat-kit-engine/J/ai/engine/autoconfigure/AiSingleExecutionAutoConfiguration.java`
- 同目录 GroupExecution / ConversationManagement / ConversationShare 配置。
- `C/ai-chat-kit-starter/J/ai/starter/autoconfigure/AiStarterDependencyVerifier.java`

- [x] 引擎直接调用并验证 AiInvocationAuthorizationPort；业务快照与授权分离。
- [x] 保留旧业务授权方法签名并给出扩展迁移说明；仅有旧业务授权实现时明确提示缺少独立授权 Port，不回退使用旧返回凭据。
- [x] 通过内置宿主接入门面配合受控身份端口，以及迁移后的自定义模型扩展，验证单群聊每轮授权只执行一次；原生登录适配沿用独立受控测试，真实宿主发送链待集成验收。直接构造执行器的兼容范围单独列明。
- [x] 普通 Bean 与 Starter 身份候选保留 Spring 单值注入 / Primary 规则；MCP 中原有严格唯一的端口继续拒绝多候选，分别覆盖 Primary 与无 Primary 情况。
- [x] 证实没有注解事务消费者后，移除四处多余 EnableTransactionManagement。
- [x] 验证自定义业务 Bean、无工具、缺凭据、身份错配、候选冲突及宿主事务基础设施不被额外开启。

验收：用户替换业务上下文不能跳过应用授权；原短事务仍真实生效。

## 任务 4：传输隔离与执行步骤组合

涉及：
- `C/ai-chat-kit-contract/J/module/ai/contract/execution/`：新增中立事件接收与执行句柄（最终命名随任务 1 清单确定）。
- `C/ai-chat-kit-engine/J/module/ai/service/chat/AiSingleChatExecutor.java`
- `C/ai-chat-kit-adapter-web/J/module/ai/service/chat/AiChatExecutionService.java` 与 `AiChatStreamEventWriter.java`（第一阶段曾在 engine 内）
- `C/ai-chat-kit-engine/J/module/ai/service/groupchat/AiGroupChatExecutor.java` 与 `AiGroupChatStreamExecutor.java`
- `C/ai-chat-kit-adapter-web/J/module/ai/service/groupchat/AiGroupChatExecutionService.java` 与 `AiGroupChatStreamService.java`（第一阶段曾在 engine 内）
- `C/ai-chat-kit-starter/J/ai/starter/host/AiHostSingleChatService.java`、`AiHostGroupChatService.java`
- `C/ai-chat-kit-adapter-web/src/test/java/.../AiWebActivationBoundaryTest.java`

- [x] 新增中立入口，将同步准备与异步流消费分离；句柄在真实事务提交后就绪，回滚后失效，提交前消费拒绝。
- [x] 覆盖 REQUIRED 加入宿主外层事务后的提交、回滚和未提交消费，不通过 REQUIRES_NEW 提前提交。
- [x] 第一阶段旧 StreamingResponseBody 方法委托新入口并保留当时的签名、字段与错误语义；2.0 门面返回类型按下节迁移。
- [x] 第一阶段共用 SSE 编码器并暂留 engine 兼容包；第二阶段已移入 adapter-web，全程没有 engine → adapter-web 反向依赖。
- [x] 仅提取已验证重复的状态探针、重试判定与完成协调；单群聊差异保持显式。
- [x] 保留开始审计与准备同事务；终态及回复提交后尽力记录完成审计，覆盖完成审计失败不改终态。
- [x] 在受控身份和自定义作用域适配器下，验证流线程原上下文恢复、事务资源释放、输出断连、取消交错、错误终态和两模式的恢复次数/输出差异；实际原生宿主上下文及非空群聊旧 session 的独立清理验收不由本轮替身测试证明。

第一阶段验收：新内核没有 MVC/SSE 类型依赖，内置宿主及已有发送方法的消费者保持当时的兼容范围；旧自定义授权与直接构造执行器迁移见任务 3。当时整个 engine JAR 仍含兼容 MVC 类型；当前这些类型由 adapter-web JAR 提供。

## 任务 5：集成回归与交付

涉及：
- `C/ai-chat-kit-adapter-mybatis-plus/src/test/java/.../AiJdbcPostgresIntegrationTest.java`
- 同模块 `AiJdbcActivationBoundaryTest.java`
- `examples/ai-component-consumer/`
- `scripts/component-release/verify-package.ps1`
- README 与实施结果文档（实施后新建）。

- [x] 运行各变更的定向测试，再运行平台 profile 的 reactor 测试。
- [x] 使用本地临时 PostgreSQL 验证真实事务/锁/CAS、同源绑定及分享最终状态，不能以 skipped 视作通过。
- [x] 打包到独立候选 Maven 仓库，复制消费者到 reactor 外运行。
- [x] 核查消费者实际加载候选 JAR，旧方法可运行，新 SPI 替换有效。
- [x] 比较模块、第三方运行依赖与制品清单；第一阶段无新框架依赖。
- [x] 记录实际测试数量/跳过/失败、源码版本、候选 SHA；按已有授权提交推送，复核工作区和远程一致。
- [x] 保持现有 Release 不变，除非另有发布任务。

基础命令（环境参数另行传入，禁止写入真实凭据）：

```sh
mvn -B -ntp -Pai-platform-host clean install
mvn -B -ntp -f <独立消费者目录>/pom.xml test
```

验证时显式指定隔离的 settings、本地仓库和本地数据库 URL。定向测试按实际新增类选择，避免单独运行 engine 模块时漏建 contract。

## 第二阶段：物理依赖拆分（已完成）

本阶段采用 `2.0.0-SNAPSHOT` 显式迁移，详见[物理拆分计划](2026-10-02-spring-physical-split.md)及[物理拆分实施结果](../../componentization/spring-physical-split.md)。第一阶段旧 Java API 与 Maven 组合的历史兼容范围保留，不承诺未经迁移的旧二进制与 2.0 混用。

- [x] 盘点 MVC 返回类型消费者并完成迁移：Starter 的 `send` / `prepare` 返回 `AiPreparedExecution`，Controller 使用 `AiWebStreamResponseFactory.stream` 包装。
- [x] `engine` / `starter` 真正移除 MVC、Servlet、OkHttp 与百炼实现；已有 `adapter-web` 承接三个旧低层门面、writer 和 SSE 编码器并保留 FQCN。
- [x] 低层 Web 执行装配仅要求运行时标记与引擎开启，宿主自有 Controller 可在 `web=false` 时继续使用；内置路由仍受 Web 开关与完整 Starter 门面控制，不增加新配置键。
- [x] 确认不存在 starter / adapter-web 循环依赖，没有新增独立 Web Starter 或兼容聚合模块。
- [x] 仅新增可选 `ai-chat-kit-model-bailian`，迁移默认模型协议、配置、提示与生命周期，更新 BOM 及包校验；自定义模型与策略优先，默认预算保留。
- [x] 使用真实缺少 MVC、Servlet、OkHttp 与百炼类的独立无 Web 消费者证明物理隔离；两组消费者均从冻结候选仓库加载 JAR，没有使用 reactor 类目录或过滤类加载器代替制品验证。
- [x] 完成真实本地 PostgreSQL 回归、独立 Web 与无 Web 消费者验收、组件/POM 构建、示例独立打包及制品/依赖清单核对。

## 最终受控验收结果

| 验证范围 | 结果 |
|---|---|
| 组件测试 | 153 项 |
| reactor 外独立 Web 消费者 | 20 项测试；核对 8 个组件的 JAR CodeSource |
| reactor 外独立无 Web 消费者 | 5 项测试；核对 4 个组件的 JAR CodeSource；运行 classpath 实际缺少 MVC/Servlet/OkHttp/百炼 |
| 合计 | **178 项测试，0 失败、0 错误、0 跳过** |
| 构建与候选 | 13 个组件/POM 项目通过，示例独立打包通过；实际候选 10 个组件 JAR |
| 第三方依赖 | 最小 Starter 24 → 14；全组件 56 项第三方坐标、scope、hash 保持不变 |

完整证据与迁移范围见[物理拆分实施结果](../../componentization/spring-physical-split.md)。整体计划的源码实施与受控验收已完成；真实宿主联调、真实模型、远程部署及公开 Release/tag 不计入本次完成声明。

## 评审重点

已按两阶段边界检查授权必经、单向依赖、真实短事务、资源所有权、Java 迁移范围与当前候选验证。第一阶段兼容签名和第二阶段 API 迁移分别记录，不将历史测试或 reactor 类目录当作当前独立制品证据。

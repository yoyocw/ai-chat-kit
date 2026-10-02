# AI Chat Kit 项目精简结果

> 本文记录精简阶段。后续 2026-10-02 已将持久化迁至 MyBatis-Plus，BOM 增加 2 项版本管理，运行依赖由 50 项增至 56 项；最新实现和验证见[存储迁移说明](mybatis-plus-migration.md)。

日期：2026-10-02。适用于当前主分支；既有 `v1.2.0-preview.1` 标签与 Release 附件仍对应此前源码。

## 结构变化

| 同口径指标 | 精简前 | 精简后 |
|---|---:|---:|
| 源码及构建文件 | 834 | 329 |
| Java 文件（含测试） | 753 | 290 |
| POM 文件（含可选模块和示例） | 32 | 15 |
| 默认 reactor 项目 | 12 | 11 |
| 加平台宿主的 reactor 项目 | 28 | 12 |
| BOM 依赖声明 | 108 | 18 |
| BOM 属性 | 62 | 8 |

文件统计包含组件、旧框架、依赖 BOM、旧 FAC、示例、脚本、旧 SQL、根 POM/README，排除文档目录和生成物。Java 文件减少约 61.5%。其中 11 个平台兼容模块、旧 API/server、FAC、失效 client 示例及旧 SQL 已移出主工程；513 个原文件保留在本地归档并逐字节核验。旧 Git 历史和既有 Preview 可追溯。

默认交付 8 个中立组件，按需增加平台或若依宿主。MCP JWT 协议模块从 framework 移至 components，Maven 坐标与 Java 包保持。`AiServiceBindingDTO` 移入 contract，保留原类名和访问器；平台宿主不再依赖旧 API 包和复制的平台框架。移除未使用的版本管理、仓库镜像及 MapStruct 处理器，没有引入新框架或升级运行时版本。

ordinary-bearer 使用组件自有的窄会话协议与 HTTPS 客户端，通过配置的原生包根读取宿主可信登录。保留原始 Bearer、登录快照、实时会话的一致性检查、权限拒绝和租户隔离；不重定向、不重试，响应限制 16 KiB 并严格解析。in-process 原生桥保持原接入路径。关闭或无启用注解时，不解析原生登录类型。

## 本轮验证

- 默认 11 项 reactor 构建成功；平台宿主 12 项完整 `clean install` 成功。
- 平台组合共 94 项测试，失败 0、错误 0、跳过 0。其中宿主 66 项、本地 PostgreSQL 集成 5 项。
- 源码树外消费者使用本轮冻结的候选 JAR，8 项测试全部通过，覆盖启停装配、单聊、群聊、停止、历史及分享。模型 I/O 使用替身，数据库为本轮新建、只监听本机的 PostgreSQL 14.22。
- 9 个组件的普通 JAR、sources JAR、独立 POM 均通过制品检查；薄 JAR 不包含平台兼容类，许可原文保留。248 个 sources 条目与当前源码和资源逐字节一致。
- 独立 JVM 只使用新 9 个候选 JAR、原宿主编译快照及第三方依赖；类来源、身份、工具签发和启停装配探针通过。原宿主服务使用替身。该宿主快照有 111 个源码文件，其中 109 个与当前原业务源码一致，另 2 个辅助文件已有后续变化；本项属于指定快照的二进制验证，不是当前真实宿主的完整验收。
- 中立 8 包按相同消费者声明顺序解析，50 项第三方运行依赖的坐标、范围和 JAR SHA-256 与原清单一致。因此本轮量化结果是源码和构建结构精简，没有将其宣传为运行内存、启动时间或吞吐量提升。
- 若依可选模块在本地提供的 RuoYi 3.9.2 制品下编译成功。该模块没有独立测试，本轮未验证宿主制品来源或真实若依运行。

本轮本地证据包含独立缓存、归档与输入摘要、构建日志、Surefire 报告、候选摘要和消费者结果，不随公开源码分发。旧服务端/MyBatis 测试随归档保留，不计入当前测试通过数，也不据此声称旧服务端已验证。

## 升级事项

1. 平台构建 profile 从 `platform-compat` 改为 `ai-platform-host`。若依仍为 `ai-ruoyi-host`，默认构建不要求任何宿主制品。
2. 删除旧 `platform-compat-*`、`ai-chat-kit-legacy-api/server` 和 FAC 依赖；使用同版本中立组件与所需宿主包。不要将旧 API JAR 与新 contract 同时加入类路径。
3. ordinary-bearer 配置 `ai-chat-kit.ai.platform-host.native-package-root`；同时兼容既有 `platform-host.in-process.native-package-root`。显式开启而未配置正确宿主类型时，启动失败；不能从请求猜测包名。
4. 自定义实现若直接调用旧会话 inspection DTO 或客户端方法，需迁移到宿主包自有的 `PlatformInspectionRequest/Result`。不承诺这些旧内部 API 的无修改二进制兼容。
5. 旧 server、MyBatis 表和 SQL 不属于当前中立 JDBC 运行链；新 JDBC 使用 `ai_runtime_*`，不会自动搬迁历史聊天数据或分享链接。
6. `@EnableAiChatKit`、现有中立组件坐标和启停配置保持；启用、停用及升级仍需重启。真实宿主登录、远端认证服务、真实模型与部署联调继续暂缓。

接入步骤见[示例说明](../../examples/ai-engine-host/README.md)，依赖许可见[第三方记录](third-party-notices.md)。本次推送源码，不创建新版本标签或替换已发布附件。

# Spring 物理依赖拆分实施计划

日期：2026-10-02；基线：`59c3bff931efe69ba71a41c0ab379304ec1925d2`。
范围：继续完成已批准[整体设计](../specs/2026-10-02-spring-framework-design.md)中的后续物理拆分。用户已要求“继续完成所有重构”，本轮连续实施、验证、提交及同步远程。

## 设计决定

- `contract` 保持纯 Java；`engine` 与 `starter` 不依赖 MVC、Servlet、OkHttp 或具体模型类。
- Starter 单群聊门面直接注入中立执行器，`send` / `prepare` 返回 `AiPreparedExecution`。原 StreamingResponseBody 返回接口属于显式迁移；HTTP/SSE 路径、字段和状态语义保持。
- 现有 `adapter-web` 承接 SSE 编码、旧底层 MVC 门面和 Web 入口。不增加独立 Web Starter 或兼容聚合模块，不建立 starter → adapter-web 反向依赖。
- 新增且仅新增 `ai-chat-kit-model-bailian` 可选制品，移动百炼协议、属性、提示、客户端、生命周期及默认装配。模型适配依赖 engine，engine 不引用该适配。共享安全引用归一化属于中立实现，移除其供应方类型依赖。
- 供应方配置先于中立执行策略默认值装配，保持原 readTimeout+30 秒占位过期及记忆预算；宿主自定义 AiModelClient 优先，不创建默认模型资源。
- 发布版本线改为 `2.0.0-SNAPSHOT`，与 1.2 的 Java API/依赖迁移明确区分。只推送源码，不生成公开 Release/tag。
- 旧来源、安全授权、真实事务/锁/CAS、审计及提交后取消语义保持；不连接真实业务环境，不增加运行时插件中心。

## 任务与拥有者

- [x] 模型拆分：模型工作项拥有 engine 中 bailian 包/属性/模型装配/记忆配置、plain 响应适配中模型引用、新模型模块及对应测试。父任务拥有所有现存 POM、聚合、BOM。
- [x] 传输拆分：Web 工作项拥有 engine 中三个 MVC 门面、writer/encoder、单群聊自动配置，starter 门面/装配/校验、adapter-web 代码/测试；供应方 writer 辅助方法迁移为中立事件方法，避免 Web → 百炼依赖。
- [x] 物理隔离消费者：独立无 Web 示例只依赖 Starter、MyBatis-Plus、自定义模型，实际 classpath 缺少 MVC/Servlet/OkHttp/百炼；验证启用、关闭、单群聊、权限、事务及 JAR CodeSource。不能仅使用 FilteredClassLoader 冒充制品隔离。
- [x] 集成交付：父任务调整现有消费者/API、示例及 POM，更新包校验和迁移说明；运行受控 PostgreSQL 全量、独立 Web 与无 Web 消费者、默认模型装配/替换测试。
- [x] 全分支只读审查，修复阻塞；核对新候选源码/二进制、依赖闭包及 2.0 坐标。交付时按已有授权快进 main、推送并复核工作区与远程一致。

独立测试与包校验位于 `.verification/spring-physical-split`，候选仓库构建前不含本项目 JAR；保留上一阶段证据。并行工作有排他文件归属，Maven reactor 变更构建串行；不覆盖他人编辑。

## 验收与文档

成功标准为完整移除物理依赖、普通替换点退让、启用/关闭边界无副作用、迁移后的 HTTP/中立执行语义通过真实本地事务验证、没有循环依赖、制品按新依赖组合可用。真实宿主、真实模型、远程部署与 Release 不属于这次重构完成声明。

自动配置遵循 [Spring Boot 2.7 官方文档](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/features.html#features.developing-auto-configuration)：明确条件与排序，使用独立上下文验证可选类缺失。

## 完成记录

模型、传输、独立消费者和集成改造均已完成。组件 153 项、独立 HTTP 消费者 20 项、独立无 Web 消费者 5 项测试通过，无失败、错误或跳过。10 组候选制品完成源码/资源字节及包边界核对；最终证据和显式 2.0 迁移见[完整重构说明](../../componentization/spring-physical-split.md)。

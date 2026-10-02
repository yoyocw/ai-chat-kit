# MyBatis-Plus 存储迁移

目标：全部 AI 表访问改为 MyBatis-Plus，保持组件轻量、业务端口及数据库语义不变。

## 设计与实施

1. 使用 MyBatis-Plus 3.5.15 的 spring 模块和 mybatis-spring 2.1.2；不引入 Boot Starter、通用 Service 层、代码生成器、分页或租户插件。
2. 五张表映射为实体和 BaseMapper；普通 CRUD 使用 Wrapper 绑定参数。分享 UPDATE RETURNING 和来源 INSERT SELECT 保留为命名参数 Mapper SQL。
3. 独立 SqlSessionFactory/SqlSessionTemplate 只由 AI 持有器管理，不作为宿主全局 Bean；同源 Spring 事务连接复用。一级缓存按语句清理，禁用二级缓存及 SQL 日志。
4. 所有身份查询显式限定 namespace、tenant_id、actor_id、mode、deleted；保留 FOR UPDATE、生成态条件更新、分享最终有效性检查和数据库时钟。
5. 模块目录和 Maven 坐标统一为 `ai-chat-kit-adapter-mybatis-plus`。保留 postgresql 存储类型、reuse/reference/isolated 配置与现有 SQL；数据库仍为 PostgreSQL。

## 验收

- 生产存储代码不再使用 JdbcTemplate；测试可使用独立 JDBC 连接核对真实落库结果。
- 验证无连接启动、未启用零装配、不污染宿主数据源/事务管理器/会话工厂。
- 在独立本机 PostgreSQL 验证 CRUD、身份隔离、锁与并发、CAS、来源、审计、事务回滚、分享到期/撤销。
- 构建组件及可选宿主模块，安装本轮 JAR 后运行独立消费者测试；更新运行时依赖清单。
- 本轮不连接业务测试服，不执行部署或真实模型联调。

## 验证结果

2026-10-02，JDK 8 / Maven 3.9.6：

- 包含平台适配的 12 模块构建成功，101 项测试全部通过，失败、错误、跳过均为 0。
- JDBC 适配模块的 7 项装配测试及 12 项 PostgreSQL 测试通过。新增 7 项覆盖同事务读取已提交变化、同源宿主事务参与/回滚及异源拒绝、来源与审计原子性、超时与删除审计、并发发送、分享租约/计数回滚、内容读取期间撤销分享。
- 将独立消费者复制到验证目录，以本轮安装的 JAR 运行，8 项测试全部通过，包含单群聊、停止、会话历史和分享。测试类路径核对为 7 个当前组件 JAR，无组件工作区的 target/classes。
- 9 个组件（含可选平台适配）的 JAR、sources JAR、POM、LICENSE 通过现有包校验脚本；sources 内容与当前源码及资源逐字节一致。
- 中立 8 组件依赖审计为 56 个第三方运行 JAR：原 50 项坐标、范围和二进制摘要不变，增加 6 个 MyBatis 相关 JAR。新增许可与摘要见[增量清单](mybatis-plus-runtime-additions.json)。没有引入 MyBatis-Plus Boot Starter、SQL 解析器、代码生成器或新业务模块。

数据库为本轮新建的本机专用 PostgreSQL 14.10（Zonky 二进制包版本 14.10.1），仅监听 127.0.0.1。各测试创建并清理随机 schema；模型、登录及业务接口使用受控替身。测试结束后关闭本轮数据库实例。本轮未进行业务测试服、真实模型或部署验收。

构建使用独立 Maven 缓存，第三方从已有缓存和 Maven Central 解析；并非完全离线构建。本轮证据位于被 Git 忽略的 `.verification/mybatis-plus-migration`，不随源码推送。

## 复验与兼容范围

准备一次性本机 PostgreSQL 测试库 `ai_component_test`，设置 `AI_TEST_POSTGRES_URL` 后执行：

```sh
mvn -B -ntp -Pai-platform-host clean install
mvn -B -ntp -f examples/ai-component-consumer/pom.xml test
```

未设置该变量会跳过真实数据库用例，不能据此声称数据库回归通过。持久化迁移无需新增 SQL；已建 `ai_runtime_*` 表可继续使用。模块名统一为 `ai-chat-kit-adapter-mybatis-plus`。Java 存储适配包及 `storage.jdbc` 配置键继续兼容，实际查询全部使用 MyBatis-Plus。业务接入继续使用契约端口和 Starter；内部 DAL 的旧 `jdbc()`/参数数组接口已移除。

若依宿主仍按需构建，本轮未重跑其独立宿主验收。MyBatis-Plus 引入后宿主需避免将其版本强制降级；本轮验证版本为 MyBatis-Plus 3.5.15、MyBatis 3.5.19、mybatis-spring 2.1.2。

设计依据：[MyBatis-Plus 安装说明](https://baomidou.com/getting-started/install/)、[MyBatis 数据变更返回结果](https://mybatis.org/mybatis-3/sqlmap-xml.html)、[MyBatis 本地缓存](https://mybatis.org/mybatis-3/java-api.html)。

## 模块命名统一

模块目录、Maven artifactId、BOM、聚合构建、两个接入示例及发布校验脚本统一使用 `ai-chat-kit-adapter-mybatis-plus`。已有接入方需要将存储依赖的 artifactId 同步切换，版本保持 `1.2.0-SNAPSHOT`；不同时引入旧坐标与新坐标。此处仅调整模块命名，不改变业务接口或数据库结构。

更名后使用新的候选缓存重新构建：101 项组件测试及 8 项独立消费者测试全部通过，无失败、错误或跳过。9 个组件的制品及源码包校验通过；新 JAR 的 Maven 元数据、消费者类路径均使用新坐标，候选缓存中无旧存储坐标 JAR。验证记录在 `.verification/mybatis-plus-module-rename`，此前迁移记录保留。

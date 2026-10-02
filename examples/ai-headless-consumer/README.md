# 独立无 Web 候选制品消费者

本项目不属于根 Maven reactor。仅直接依赖 `ai-chat-kit-starter`、`ai-chat-kit-adapter-mybatis-plus` 的 `2.0.0-SNAPSHOT` 候选 JAR，以及 Boot 2.7.18 的测试设施，Java 8 编译。通过普通 `ApplicationContextRunner` 运行，不使用过滤 ClassLoader。

五个测试覆盖实际 classpath 无 Spring Web/MVC、Servlet、OkHttp、百炼，contract/engine/starter/storage 四类 CodeSource 均为对应版本 JAR（候选仓库位置另由执行命令和验证记录核对），无注解或 engine=false 无 AI 资源，单聊准备/消费、群聊全部成员回复提交后输出、每轮一次应用授权及拒绝后零模型调用。模型、身份、应用授权和目录是显式受控 fixture；数据库、存储适配及事务是真实本地实现。测试不证明生产认证、真实模型或远程部署。

先将本轮候选制品安装到一个独立 Maven 仓库，再以该仓库运行本项目。禁止把 reactor `target/classes` 加入本项目 classpath。

```powershell
mvn -f examples/ai-headless-consumer/pom.xml -Dmaven.repo.local=<candidate-repository> test
```

三个数据库测试要求 `AI_TEST_POSTGRES_URL=jdbc:postgresql://127.0.0.1:<port>/ai_component_test`（受控本地测试库、postgres 用户、空密码）。每个测试创建随机 schema，关闭组件自有连接池并删除该 schema。未设置此变量时，仅执行物理 classpath 与禁用边界测试，数据库测试跳过，不能据此声称数据库验收通过。

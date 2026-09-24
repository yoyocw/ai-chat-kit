# AI Chat Kit 中性命名产品验收矩阵

版本：0.3；日期：2026-09-24；负责人：产品经理；状态：新命名候选的本地构建、制品内容与隔离验证已有证据；最终 ZIP、真实业务及公开交付待验。

本轮目标是让公开源码、Java 包路径、README、Maven 坐标和可分发制品统一使用中性名称 `ai-chat-kit`。现有宿主源码保持只读，组件通过可选宿主适配复用其登录、租户和权限；用户仍以依赖、启用注解与可信部署配置接入，无需自行编写必需的 AI Port。验收范围与技术边界见[中性命名交付说明](platform-neutralization.md)。

本文件只记录本轮迁移后的验收。上一轮构建数量、测试结果及 ZIP 均对应不同源码和制品，不能作为本轮通过证据。真实业务、模型与部署联调按用户安排暂缓；仓库公开和发布操作尚未获得具体授权。

## 1. 验收条件与当前状态

| ID | 本轮验收条件 | 当前证据与判定 |
|---|---|---|
| PN-01 对外命名 | 对冻结源码、Java `package`/`import`、类名、自动装配清单、模块路径、脚本、README、发布 POM 和最终附件做全路径扫描；原两项品牌/行业英文标识在对外代码与说明中为零。许可与必要的历史归属材料另按原文保留，扫描范围和排除项必须记录。 | **本地扫描通过**：当前发布源码 836 文件、新候选 10 组件共 30 制品及 622 个归档条目均已扫描，命中 0；20 个 LICENSE 条目保留许可原文并单列。最终冻结输入 804 文件与当前源码一致；这只说明最终输入状态，不声称整个构建过程中从未调整文件。 |
| PN-02 双构建 | 使用本轮冻结源码和新的自有制品缓存，分别完成默认构建与完整 `platform-compat` 构建；记录模块数、实际测试数、失败/错误/跳过及输入摘要。旧缓存仅可提供第三方依赖。 | **本地构建通过**：默认 12 模块 `clean install` 成功，28 项中失败 0、错误 0、跳过 5，实际运行 23 项；完整兼容构建 28 模块成功，139 项中失败 0、错误 0、跳过 5，实际运行 134 项。5 项均因本地 PostgreSQL 未启动而跳过，不计为数据库集成通过；两次构建数量不相加。 |
| PN-03 可分发制品 | 生成新坐标的薄 JAR、sources JAR、发布 POM 和摘要；核对 sources 与冻结输入逐字节一致、依赖闭合、许可随包以及公开名称零残留。独立消费者从新候选解析，不依赖工作区 `target` 输出，也不传播宿主提供的兼容库。 | **候选内容与独立解析通过，最终 ZIP 完整性待随包核对**：10 个自有组件的 30 件 JAR/sources/POM 制品摘要已核对；10 个 sources JAR 的 250 个源码及资源条目与当前 `src/main` 逐项 SHA 一致。独立消费者用新缓存运行 `dependency:tree` 成功，解析到精确 10 个候选自有模块，运行树无兼容库传播。中立 8 组件的直接外部依赖 19/19 一致，审计顺序下 50 项运行依赖坐标、scope 和 JAR 摘要一致。最终 ZIP 以随包 MANIFEST 和外部 SHA 核对记录确认完整性。 |
| PN-04 原生宿主桥接 | 可选 `ai-chat-kit-host-platform` 在 `in-process` 模式复用原宿主的可信本地身份、会话与权限；业务模块无需手写必需 AI Port。`native-package-root` 仅来自可信部署配置，启用时必填，不内置任何品牌包名；启动时严格核验 Bean 名称、AOP 目标、接口及方法签名，失败即拒绝，不绕过安全切面。身份 namespace 由固定部署配置提供，不随代码改名自动变更既有会话归属。 | **受控实现验证通过**：新宿主定向测试 41/0/0/0；原宿主 111 份源码与当前输入的 SHA 一致，配置式桥接的本地身份、授权和装配边界已在受控用例及独立探针中验证。真实登录和权限服务仍待业务环境验收。 |
| PN-05 宿主隔离与拒绝路径 | 未启用注解或关闭引擎时不要求原生宿主类存在；启用时验证 Bearer、可信登录快照与实时会话一致，权限故障、撤销、跨租户、错误包根和错误 Bean 均拒绝。普通宿主登录及工具签发与资源端信任接线分离。使用当前原宿主源码编译类在独立 JVM 验证二进制链接，并标明服务/数据库替身范围。 | **精确候选 JAR 局部链路通过**：最终独立 JVM 类路径 147 项，组件来源均为精确 10 个候选 JAR，无工作区编译输出或兼容组件；30 件制品 SHA 一致，原宿主 111 份源码无变化。普通身份、工具签发、启用、关闭和二进制链接五项标记均 PASS。原宿主服务与数据库由内存替身提供，不计作真实业务验收。 |
| PN-06 发布边界 | 固定源码提交、版本、JAR/POM、附件和摘要的一一映射；公开渠道中匿名用户可获取并校验新候选。 | 本轮尚未形成公开发布结论，也未执行公开操作。 |

PN-01 至 PN-05 已取得对应范围的本地候选证据；最终 ZIP 的 MANIFEST 与外部 SHA 须在打包后核对，不能预先判定附件完整。PN-06 在具体公开动作获授权并完成匿名复核后判定。真实登录、数据库、模型、业务 HTTP 与部署结果必须另行记录，不从本地替身或源码扫描推断。

<!-- TRACEABILITY-METADATA:BEGIN -->
```yaml
schema:
  name: testany-traceability
  version: "1.0.0"
  profile: prd-profile-v1
artifact:
  id: PRD-AI-CHAT-KIT-NEUTRAL-001
  type: PRD
  title: AI Chat Kit 中性命名产品验收矩阵
  status: draft
  owners: ["产品经理"]
  created_at: 2026-09-24
  updated_at: 2026-09-24
  source_documents:
    - "platform-neutralization.md"
entities:
  requirements:
    - id: PN-01
      class: non_functional
      title: 对外命名
      statement: "冻结输入与候选制品的对外命名扫描通过"
      priority: P0
      status: draft
      scope: 中性命名交付
      acceptance_criteria: ["第1节PN-01的全部条件通过"]
    - id: PN-02
      class: non_functional
      title: 双构建
      statement: "默认和完整兼容构建均使用本轮输入通过"
      priority: P0
      status: draft
      scope: 中性命名交付
      acceptance_criteria: ["第1节PN-02的全部条件通过"]
    - id: PN-03
      class: functional
      title: 可分发制品
      statement: "新JAR、sources和POM可由独立消费者解析"
      priority: P0
      status: draft
      scope: 中性命名交付
      acceptance_criteria: ["第1节PN-03的全部条件通过"]
    - id: PN-04
      class: functional
      title: 原生宿主桥接
      statement: "可信配置接线保持身份和命名空间安全"
      priority: P0
      status: draft
      scope: 中性命名交付
      acceptance_criteria: ["第1节PN-04的全部条件通过"]
    - id: PN-05
      class: non_functional
      title: 宿主隔离
      statement: "关闭态和拒绝路径可复测，原宿主二进制可链接"
      priority: P0
      status: draft
      scope: 中性命名交付
      acceptance_criteria: ["第1节PN-05的全部条件通过"]
    - id: PN-06
      class: functional
      title: 公开交付
      statement: "获授权后的新制品公开可获取且可追溯"
      priority: P0
      status: draft
      scope: 中性命名交付
      acceptance_criteria: ["第1节PN-06的全部条件通过"]
  risks: []
  must_not_regress: []
  external_behaviors: []
  decisions: []
  flows: []
  test_cases: []
relations: []
waivers: []
```
<!-- TRACEABILITY-METADATA:END -->

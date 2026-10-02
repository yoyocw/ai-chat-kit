# 第三方依赖来源与许可记录

> 2026-10-02 存储迁移后的中立运行依赖为 **56 项**：下文原 50 项坐标、范围、JAR 摘要保持一致，新增 6 项及上游许可证见 [MyBatis-Plus 增量清单](mybatis-plus-runtime-additions.json)。本轮构建为 8 个中立组件加 1 个可选平台宿主，验证见[迁移说明](mybatis-plus-migration.md)。下文保留 2026-09-24 候选的历史记录。

本记录对应 AI Chat Kit `1.2.0-SNAPSHOT` 的中立组件运行依赖。2026-09-24 对本轮重新构建的 8 个中立组件复核：发布 POM 的 19 个直接第三方依赖与原清单一致；按原审计消费者相同声明顺序解析的 50 个运行依赖，其坐标、范围与实际 JAR SHA-256 均与随附清单一致。使用新候选仓库和已有第三方缓存，不将该审计描述为完全空缓存验证。

完整坐标、许可名称、来源 POM、JAR SHA-256 和随包 LICENSE/NOTICE 条目见 [CSV 清单](third-party-runtime-inventory.csv) 和[结构化清单](third-party-runtime-inventory.json)。50 项均有实际 JAR 和许可声明（含父 POM 继承）；来源清单保留原审计记录，本轮重新核对解析结果和二进制摘要。

## 许可与归属

根 MIT 文本及原版权声明原样保留。本轮交付的 10 个普通 JAR 与 10 个 sources JAR 均含与根文件字节一致的 `META-INF/LICENSE`，不合并第三方编译类。第三方由 Maven 按原坐标获取，其许可和 NOTICE 不因本项目使用 MIT 而改变。

清单原样记录上游声明，不擅自判断多许可的组合关系。其中 logback-classic/core 1.2.12 声明 EPL 1.0 和 GNU Lesser General Public License；jakarta.annotation-api 1.3.5 声明 EPL 2.0、GPL2 w/ CPE。具体声明来源、链接及摘要见 JSON。本记录是来源清单，不是所有使用方式的许可兼容结论。

## 使用范围

8 库审计没有宿主 BOM 覆盖。宿主 BOM、消费者声明顺序和 Maven 依赖仲裁会改变最终版本，不能将上述 50 项视为每个宿主的固定闭包；接入方应核对自身实际解析结果。

本清单描述中立组件运行依赖，不含可选平台兼容库、宿主额外依赖、构建与测试工具。制品附件分发组件和样例，不附第三方缓存或完整可执行应用。宿主可选包及当前验证边界见 [中性命名交付说明](platform-neutralization.md)。

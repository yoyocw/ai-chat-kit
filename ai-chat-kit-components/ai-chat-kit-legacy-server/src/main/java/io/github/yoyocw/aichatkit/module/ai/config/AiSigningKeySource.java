package io.github.yoyocw.aichatkit.module.ai.config;

/** 部署显式选择的私钥归属；来源不可根据失败情况自动切换。 */
public enum AiSigningKeySource {
    /** 从宿主秘密配置注入原私钥，不访问数据库。 */
    CONFIGURATION,
    /** 读取 AI 自有数据源的启用密钥，不访问 system 数据库或秘密配置私钥。 */
    DATABASE
}

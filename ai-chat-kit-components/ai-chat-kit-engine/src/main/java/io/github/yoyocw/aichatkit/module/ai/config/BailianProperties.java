package io.github.yoyocw.aichatkit.module.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云百炼应用调用配置，只保存服务地址、凭证标识与网络超时边界。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ai-chat-kit.ai.bailian")
public class BailianProperties {

    /** 百炼 HTTPS API 根地址，不允许用户信息、查询参数或片段；默认使用北京地域公共端点。 */
    private String baseUrl = "https://dashscope.aliyuncs.com/api/v1";
    /** 百炼 API Key，生产环境必须通过 DASHSCOPE_API_KEY 环境变量注入。 */
    private String apiKey;
    /** 已发布的多智能体编排工作流应用 ID，生产环境通过 DASHSCOPE_GROUP_APP_ID 环境变量注入。 */
    private String groupAppId;
    /** 是否要求百炼返回智能体执行过程，用于生成脱敏后的前端进度事件。 */
    private boolean hasThoughts = true;
    /** 是否启用模型深度思考；开启后会增加响应耗时和 Token 消耗。 */
    private boolean enableThinking = false;
    /** 建立 HTTPS 连接的最大等待秒数，范围 1 至 60。 */
    private int connectTimeoutSeconds = 10;
    /** 流式读取最大空闲秒数，同时作为单次 HTTP 调用总时限，范围 1 至 600。 */
    private int readTimeoutSeconds = 180;
    /** 每次提示词完整保留的最近已完成消息数量，默认十二条。 */
    private int historyRecentMessageCount = 12;
    /** 本地历史上下文最大估算 Token 数；最近消息超限时仍保留完整原文。 */
    private int historyMaxTokens = 6000;
    /** 滚动摘要最大估算 Token 数，实际还会为最近消息让出预算。 */
    private int historySummaryMaxTokens = 2000;
    /** ASCII 字符折算一个 Token 的校准数量，默认三字符以保持保守估算。 */
    private int historyAsciiCharsPerToken = 3;
    /** Token 估算附加安全余量百分比，用于覆盖不同百炼模型分词差异。 */
    private int historyTokenSafetyPercent = 20;
    /** 是否轮询宿主共享消息状态以取消跨实例停止的调用；默认关闭，开启后必须提供可信探针。 */
    private boolean generationWatchEnabled = false;
    /** 状态检查间隔，单位毫秒，范围 200 至 10000；不是跨节点停止的绝对时延保证。 */
    private int generationWatchIntervalMillis = 1000;
    /** 本实例同时监测的上限，范围 1 至 1024；超过上限拒绝新模型调用，默认 128。 */
    private int generationWatchMaxCalls = 128;

    /**
     * 检查网络超时边界，避免零值关闭超时或过大配置长期占用调用资源。
     *
     * @return 连接和读取超时均在允许范围内时为 true
     */
    public boolean hasValidNetworkTimeouts() {
        return connectTimeoutSeconds >= 1 && connectTimeoutSeconds <= 60
                && readTimeoutSeconds >= 1 && readTimeoutSeconds <= 600;
    }
}

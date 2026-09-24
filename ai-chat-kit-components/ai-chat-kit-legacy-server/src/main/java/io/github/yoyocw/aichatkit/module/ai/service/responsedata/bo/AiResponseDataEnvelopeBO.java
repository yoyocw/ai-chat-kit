package io.github.yoyocw.aichatkit.module.ai.service.responsedata.bo;

import lombok.Data;

import java.util.List;

/**
 * AI 消息结构化扩展结果信封，为不同业务数据提供统一版本、类型、来源和生成时间边界。
 *
 * @param <T> 已按业务白名单组装的扩展数据类型
 */
@Data
public class AiResponseDataEnvelopeBO<T> {

    /** 协议版本，当前固定为 AI_RESPONSE_DATA_V1。 */
    private String schemaVersion;
    /** 扩展结果业务类型，例如地图任务列表或群聊汇总结果。 */
    private String type;
    /** 与业务类型对应的强类型安全数据，不允许保存百炼原始输出。 */
    private T data;
    /** 可公开展示的来源元数据列表；当前无安全来源时返回空列表。 */
    private List<AiResponseDataSourceBO> sources;
    /** 信封生成时间，使用包含时区偏移量的 ISO-8601 字符串。 */
    private String generatedAt;
}

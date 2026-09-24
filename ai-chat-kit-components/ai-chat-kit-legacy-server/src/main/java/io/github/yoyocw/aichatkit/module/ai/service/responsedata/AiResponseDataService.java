package io.github.yoyocw.aichatkit.module.ai.service.responsedata;

import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianGroupOutputException;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessPresentation;
import com.fasterxml.jackson.databind.JsonNode;


/**
 * AI 消息结构化扩展结果服务，负责将可信业务数据组装为稳定、可版本化的安全 JSON 信封。
 */
public interface AiResponseDataService extends io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleResponseDataPort {

    /**
     * 构造单聊地图任务列表扩展结果。
     *
     * @param messageId 助手消息编号，仅用于不含业务载荷的安全日志定位
     * @param presentation 受控宿主适配层生成的展示数据；为空则不生成信封
     * @return V1 扩展结果 JSON；可选结果序列化失败时返回 null，不能影响回答正文完成
     */
    String renderBusinessPresentation(Long messageId, AiBusinessPresentation presentation);

    /**
     * 从群聊工作流扩展节点构造字段白名单结果。
     *
     * @param responseData 百炼群聊工作流已解析的扩展节点；为空表示没有扩展结果
     * @return V1 扩展结果 JSON；未提供扩展节点时返回 null
     * @throws BailianGroupOutputException 节点类型或白名单字段类型不符合群聊输出契约时抛出
     */
    String buildGroupChatResult(JsonNode responseData);
    /**
     * 将上游引用白名单合并至单聊扩展信封，保留地图数据。
     * @param envelope 本服务生成的地图信封，可为空
     * @param output 上游输出，仅提取 doc_references
     * @return 含 sources 的信封；无地图且无来源时返回 null
     */
    String mergeSources(String envelope, String output);}

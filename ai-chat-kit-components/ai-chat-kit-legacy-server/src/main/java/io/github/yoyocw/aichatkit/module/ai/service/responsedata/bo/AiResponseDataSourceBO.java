package io.github.yoyocw.aichatkit.module.ai.service.responsedata.bo;

import lombok.Data;

/**
 * AI 扩展结果的安全来源元数据，仅描述允许页面展示的公开来源，不保存内部检索或工具执行信息。
 */
@Data
public class AiResponseDataSourceBO {

    /** 上游引用编号，与正文标记对应；不自行重新排序编号。 */
    private String indexId;

    /** 来源类型稳定编码，例如 PUBLIC_DOCUMENT；不得使用内部知识库或工具实现名称。 */
    private String sourceType;
    /** 页面允许展示的来源标题，不包含内部路径、切片正文或召回分数。 */
    private String title;
    /** 经服务端审核允许公开访问的来源地址；内部地址或无公开地址时为空。 */
    private String url;
}

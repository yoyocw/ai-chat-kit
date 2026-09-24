package io.github.yoyocw.aichatkit.module.ai.contract.context;

/** 单聊结果协议适配；宿主负责业务展示白名单，模型引擎不直接访问页面实体。 */
public interface AiSingleResponseDataPort {
    /** @param messageId 助手消息编号 @param presentation 已授权展示数据
     * @return 宿主展示信封JSON，可为空 */
    String renderBusinessPresentation(Long messageId, AiBusinessPresentation presentation);
    /** @param envelope 现有宿主信封 @param output 模型输出JSON，只提取允许的引用信息
     * @return 合并后的安全信封，可为空 */
    String mergeSources(String envelope, String output);
}

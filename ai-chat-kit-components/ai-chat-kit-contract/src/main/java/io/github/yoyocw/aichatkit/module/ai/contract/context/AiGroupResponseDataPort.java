package io.github.yoyocw.aichatkit.module.ai.contract.context;

/** 群聊结果展示协议适配，宿主负责白名单/版本处理，引擎不绑定宿主展示服务。 */
public interface AiGroupResponseDataPort {
    /** @param responseJson 模型解析出的可选JSON，待处理数据而非指令
     * @return 经宿主裁剪后的展示JSON，可为空；错误应向外传播终止本轮完成 */
    String build(String responseJson);
}

package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

/**
 * 百炼群聊工作流输出不符合约定 JSON 契约时抛出的异常。
 */
public class BailianGroupOutputException extends RuntimeException {

    /**
     * 创建工作流输出格式异常。
     *
     * @param message 不包含敏感原文的失败说明
     */
    public BailianGroupOutputException(String message) {
        super(message);
    }
}

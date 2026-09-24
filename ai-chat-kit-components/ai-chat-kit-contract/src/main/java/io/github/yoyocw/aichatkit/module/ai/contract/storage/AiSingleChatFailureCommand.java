package io.github.yoyocw.aichatkit.module.ai.contract.storage;

import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 失败落库命令，只允许安全错误文案，不接收异常对象或凭据。 */
public final class AiSingleChatFailureCommand {
    /** 同轮已捕获上下文。 */
    private final AiInvocationContext context;
    /** 本轮助手消息编号。 */
    private final Long messageId;
    /** 已收到的部分正文，禁止写入日志。 */
    private final String partialContent;
    /** 业务层已转换的安全错误说明。 */
    private final String errorMessage;
    /** 创建失败快照；仅内部调用。 */
    public AiSingleChatFailureCommand(AiInvocationContext context, Long messageId,
                                      String partialContent, String errorMessage) {
        this.context = context;
        this.messageId = messageId;
        this.partialContent = partialContent;
        this.errorMessage = errorMessage;
    }
    public AiInvocationContext getContext() { return context; }
    public Long getMessageId() { return messageId; }
    public String getPartialContent() { return partialContent; }
    public String getErrorMessage() { return errorMessage; }
}

package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import java.io.IOException;

/**
 * 百炼短期会话已失效异常，用于清理本地 session_id 并在无会话上下文下安全重试一次。
 */
public class BailianSessionExpiredException extends IOException {

    public BailianSessionExpiredException(String message) {
        super(message);
    }
}

package io.github.yoyocw.aichatkit.module.ai.framework.stream;

import java.util.function.Consumer;

/**
 * AI SSE 打印输出器，将模型文本按 Unicode 字符匀速交给事件发送回调。
 */
public final class AiSsePrinter {

    /** 相邻字符发送间隔，单位毫秒；20 毫秒兼顾打印观感和长回答耗时。 */
    private static final long CHARACTER_INTERVAL_MILLIS = 20L;

    /**
     * 按 Unicode 字符顺序输出文本，避免拆断代理字符对。
     *
     * @param content 模型返回的文本块；为空时不输出
     * @param characterConsumer 单个完整字符的发送回调
     * @throws IllegalStateException 输出线程被中断时抛出，并保留线程中断状态
     */
    public static void print(String content, Consumer<String> characterConsumer) {
        if (content == null || content.isEmpty()) {
            return;
        }
        content.codePoints().forEachOrdered(codePoint -> {
            characterConsumer.accept(new String(Character.toChars(codePoint)));
            pause();
        });
    }

    private static void pause() {
        try {
            Thread.sleep(CHARACTER_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI SSE 逐字输出线程已中断", ex);
        }
    }

    private AiSsePrinter() {
    }
}

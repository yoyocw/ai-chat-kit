package io.github.yoyocw.aichatkit.module.ai.service.memory;

import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * AI 会话滚动记忆服务，以本地抽取式摘要保留旧事实，并完整保留最近消息原文。
 */
@RequiredArgsConstructor
public class AiConversationMemoryService {

    /** 百炼历史窗口和 Token 估算校准配置。 */
    private final BailianProperties properties;

    /**
     * 构建本轮历史上下文，并在消息超过近期窗口时推进持久化摘要。
     *
     * @param storedSummary 会话已保存的滚动摘要
     * @param storedCursor 已进入摘要的最大消息编号
     * @param messages 游标之后的已完成消息与最近消息并集
     * @return 模型上下文和需要持久化的新记忆状态
     */
    public AiConversationMemoryResult build(String storedSummary, Long storedCursor,
                                             List<AiConversationMemoryMessage> messages) {
        List<AiConversationMemoryMessage> ordered = new ArrayList<AiConversationMemoryMessage>(messages);
        ordered.sort(Comparator.comparing(AiConversationMemoryMessage::getId));
        int recentCount = Math.max(1, properties.getHistoryRecentMessageCount());
        int foldEnd = Math.max(0, ordered.size() - recentCount);
        String summary = StringUtils.hasText(storedSummary) ? storedSummary.trim() : "";
        Long cursor = storedCursor;
        for (int i = 0; i < foldEnd; i++) {
            AiConversationMemoryMessage message = ordered.get(i);
            if (cursor == null || message.getId() > cursor) {
                summary = appendSummary(summary, formatSummary(message));
                cursor = message.getId();
            }
        }
        List<String> recentLines = new ArrayList<String>();
        for (int i = foldEnd; i < ordered.size(); i++) {
            recentLines.add(formatRecent(ordered.get(i)));
        }
        int recentTokens = estimateTokens(String.join("\n", recentLines));
        int summaryBudget = Math.max(0, Math.min(properties.getHistorySummaryMaxTokens(),
                properties.getHistoryMaxTokens() - recentTokens));
        summary = trimOldestLines(summary, summaryBudget);
        String context = buildContext(summary, recentLines);
        boolean changed = !summary.equals(StringUtils.hasText(storedSummary) ? storedSummary.trim() : "")
                || (cursor == null ? storedCursor != null : !cursor.equals(storedCursor));
        return new AiConversationMemoryResult(context, summary, cursor, changed);
    }

    /** 将旧消息原文按行追加到抽取式滚动摘要，避免无模型改写产生虚构事实。 */
    private String appendSummary(String summary, String line) {
        return StringUtils.hasText(summary) ? summary + "\n" + line : line;
    }

    /** 按完整消息行从最旧端释放摘要预算，不截断任一条消息正文。 */
    private String trimOldestLines(String summary, int tokenBudget) {
        if (!StringUtils.hasText(summary) || tokenBudget <= 0) {
            return "";
        }
        String value = summary;
        while (estimateTokens(value) > tokenBudget) {
            int newline = value.indexOf('\n');
            if (newline < 0) {
                return "";
            }
            value = value.substring(newline + 1);
        }
        return value;
    }

    /** 组合稳定旧记忆与最近原文，使百炼提示词能区分两类上下文。 */
    private String buildContext(String summary, List<String> recentLines) {
        StringBuilder context = new StringBuilder();
        if (StringUtils.hasText(summary)) {
            context.append("较早对话记忆：\n").append(summary).append('\n');
        }
        if (!recentLines.isEmpty()) {
            context.append("最近对话原文：\n").append(String.join("\n", recentLines));
        }
        return context.toString();
    }

    /** 格式化一条最近消息并完整保留原文换行。 */
    private String formatRecent(AiConversationMemoryMessage message) {
        String content = message.getContent() == null ? "" : message.getContent().trim();
        return resolveSpeaker(message) + "：" + content;
    }

    /** 将旧消息换行折叠为空格，保证摘要按完整消息行释放预算。 */
    private String formatSummary(AiConversationMemoryMessage message) {
        String content = message.getContent() == null ? "" : message.getContent().trim()
                .replaceAll("[\\r\\n]+", " ");
        return resolveSpeaker(message) + "：" + content;
    }

    /** 兜底缺失的群聊发言者名称，避免把字符串 null 写入模型上下文。 */
    private String resolveSpeaker(AiConversationMemoryMessage message) {
        return StringUtils.hasText(message.getSpeaker()) ? message.getSpeaker().trim() : "助手";
    }

    /**
     * 以中文字符约一 Token、ASCII 连续字符按校准比例估算，并加入安全余量。
     * 该估算器只用于预算保护，不能替代目标模型官方计费 Token 统计。
     */
    private int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        int ascii = 0;
        int nonAscii = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) <= 0x7F) {
                ascii++;
            } else {
                nonAscii++;
            }
        }
        int asciiRatio = Math.max(1, properties.getHistoryAsciiCharsPerToken());
        int base = nonAscii + (ascii + asciiRatio - 1) / asciiRatio;
        return (base * (100 + Math.max(0, properties.getHistoryTokenSafetyPercent())) + 99) / 100;
    }
}

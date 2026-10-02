package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianStreamEvent;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianStreamEventType;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单聊 SSE 事件写出组件，统一负责事件数据组装、UTF-8 编码、刷新和客户端断开容错。
 */
public class AiChatStreamEventWriter {

    /**
     * 将键值对按传入顺序组装为 SSE 事件数据。
     *
     * @param values 交替排列的字段名和字段值，调用方必须保证数量为偶数
     * @return 保持字段顺序的事件数据
     */
    public Map<String, Object> data(Object... values) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            data.put(String.valueOf(values[index]), values[index + 1]);
        }
        return data;
    }

    /**
     * 将百炼安全流事件转换为单聊 SSE 协议，进度与最终回答正文保持分离。
     *
     * @param outputStream Servlet 异步响应输出流
     * @param messageId 助手消息编号
     * @param event 百炼安全流事件
     */
    public void writeBailianEvent(OutputStream outputStream, Long messageId, BailianStreamEvent event) {
        if (event.getType() == BailianStreamEventType.DELTA) {
            // 直接转发上游文本块，避免逐字休眠拖延错误读取、停止响应和长文本生成。
            write(outputStream, "delta", data("messageId", messageId, "content", event.getContent()));
            return;
        }
        if (event.getType() == BailianStreamEventType.PROGRESS) {
            writeProgress(outputStream, messageId, event.getStage(), event.getMessage(), event.getActionName());
        }
    }

    /**
     * 输出前端可直接展示的处理进度；动作名称为空时不产生冗余字段。
     *
     * @param outputStream Servlet 异步响应输出流
     * @param messageId 助手消息编号
     * @param stage 稳定阶段编码
     * @param message 安全进度说明
     * @param actionName 可选安全动作名称
     */
    public void writeProgress(OutputStream outputStream, Long messageId, String stage,
                              String message, String actionName) {
        Map<String, Object> eventData = data("messageId", messageId, "stage", stage, "message", message);
        if (StringUtils.hasText(actionName)) {
            eventData.put("actionName", actionName);
        }
        write(outputStream, "progress", eventData);
    }

    /**
     * 写出一个 UTF-8 SSE 事件并立即刷新，写出失败时抛出非受检 IO 异常交由业务层收口。
     *
     * @param outputStream Servlet 异步响应输出流
     * @param event 事件名称
     * @param eventData 事件 JSON 数据
     * @throws UncheckedIOException 客户端断开或响应写出失败时抛出
     */
    public void write(OutputStream outputStream, String event, Map<String, Object> eventData) {
        new io.github.yoyocw.aichatkit.ai.engine.execution.AiSseEventEncoder(outputStream).accept(event, eventData);
    }

    /**
     * 尝试写出终态事件；客户端已断开时忽略写出异常，因为消息终态已由业务层保存。
     *
     * @param outputStream Servlet 异步响应输出流
     * @param event 事件名称
     * @param eventData 事件 JSON 数据
     */
    public void writeQuietly(OutputStream outputStream, String event, Map<String, Object> eventData) {
        try {
            write(outputStream, event, eventData);
        } catch (RuntimeException ignored) {
            // 客户端主动断开后无法再写终态事件，业务层已经负责保存数据库状态。
        }
    }
}

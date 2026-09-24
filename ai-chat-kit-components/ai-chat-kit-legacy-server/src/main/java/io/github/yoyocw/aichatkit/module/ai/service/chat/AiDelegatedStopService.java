package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.adapter.platform.AiHostedStopProxyService;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 保留委托停止 URL 的本地适配，仅消费 AI 自有票据，未配置按需拒绝，无旧 system RPC。 */
@Service
@RequiredArgsConstructor
public class AiDelegatedStopService {
    /** 本地来源预检、一次消费和事务/CAS协调能力。 */
    private final ObjectProvider<AiHostedStopProxyService> stopService;

    /**
     * 通过固定入口处理 AI 自有一次性停止票据。
     * @param mode 控制器固定单聊或群聊
     * @param messageId 正数助手消息编号
     * @param ticket AI 自有协议票据，不兼容 system 旧票据
     * @throws IllegalStateException 未配置、身份、票据或提交无效，不回退远程专用接口
     */
    public void stop(AiChatMode mode, Long messageId, String ticket) {
        try {
            stopService.getObject().consumeTicket(mode, messageId, ticket);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("当前无法完成授权停止");
        }
    }
}
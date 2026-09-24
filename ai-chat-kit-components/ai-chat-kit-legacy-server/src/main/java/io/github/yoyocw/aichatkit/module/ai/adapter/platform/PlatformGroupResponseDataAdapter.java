package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.compat.framework.common.util.json.JsonUtils;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupResponseDataPort;
import io.github.yoyocw.aichatkit.module.ai.service.responsedata.AiResponseDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 复用林业原有群聊展示白名单，所有数据仍通过原结果服务处理。 */
@Component
@RequiredArgsConstructor
public class PlatformGroupResponseDataAdapter implements AiGroupResponseDataPort {
    /** 宿主展示协议的已有实现。 */
    private final AiResponseDataService responseDataService;

    @Override
    public String build(String responseJson) {
        return responseDataService.buildGroupChatResult(responseJson == null ? null : JsonUtils.parseTree(responseJson));
    }
}

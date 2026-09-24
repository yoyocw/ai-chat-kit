package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 仅v2票据的局部ObjectMapper使用，复用不可变宿主会话契约，不改全局JSON配置。 */
abstract class AiHostSessionJsonMixin {
    /** 指定既有会话构造器字段；输入仍由严格票据校验及真实会话源复核。 */
    @JsonCreator
    AiHostSessionJsonMixin(@JsonProperty("namespace") String namespace, @JsonProperty("tenantId") String tenantId,
            @JsonProperty("actorId") String actorId, @JsonProperty("sessionId") String sessionId,
            @JsonProperty("expiresAtMillis") long expiresAtMillis) { }
}

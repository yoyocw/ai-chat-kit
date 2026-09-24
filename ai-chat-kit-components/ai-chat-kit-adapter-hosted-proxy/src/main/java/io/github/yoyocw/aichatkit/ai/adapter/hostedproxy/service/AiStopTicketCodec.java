package io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.service;

import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.api.AiStopTicket;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 专用v2 opaque票据及有界元数据编解码，不识别MCP或v1票据，不含任何登录Token。 */
final class AiStopTicketCodec {
    /** 版本隔离前缀，旧协议无法进入本模块。 */
    private static final String PREFIX = "ai_hosted_stop_v2_";
    /** 仅元数据使用的局部严格映射器。 */
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .addMixIn(AiHostSession.class, AiHostSessionJsonMixin.class);
    /** 256位随机票据来源，不产生用户或机器身份。 */
    private final SecureRandom random = new SecureRandom();

    /** @return 新256位随机票据，不返回或保存原始用户凭据 */
    String credential() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** @return v2凭据摘要，仅摘要可进入存储键 */
    String digest(String credential) {
        if (credential == null || !credential.matches("ai_hosted_stop_v2_[A-Za-z0-9_-]{43}")) {
            throw AiStopChecks.denied();
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(credential.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte part : bytes) { value.append(String.format("%02x", part & 255)); }
            return value.toString();
        } catch (Exception exception) { throw AiStopChecks.denied(); }
    }

    /** @return 有界元数据JSON，不输出编解码异常中的源文本 */
    String encode(AiStopTicket ticket) {
        try {
            String json = mapper.writeValueAsString(ticket);
            if (json.length() > 8192) { throw AiStopChecks.denied(); }
            return json;
        } catch (Exception exception) { throw AiStopChecks.denied(); }
    }

    /** @return 严格解析的不可变快照，随后必须核验全部绑定及真实会话 */
    AiStopTicket decode(String json) {
        if (json == null || json.length() > 8192) { throw AiStopChecks.denied(); }
        try { return mapper.readValue(json, AiStopTicket.class); }
        catch (Exception exception) { throw AiStopChecks.denied(); }
    }
}

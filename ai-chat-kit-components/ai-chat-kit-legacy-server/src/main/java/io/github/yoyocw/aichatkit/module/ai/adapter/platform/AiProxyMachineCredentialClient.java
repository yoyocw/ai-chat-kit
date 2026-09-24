package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import io.github.yoyocw.aichatkit.module.ai.config.AiHostedProxyProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiHostedStopProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiSessionInspectionProperties;
import io.github.yoyocw.aichatkit.ai.adapter.hostedproxy.config.AiHostedProxyStopProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** 标准 OAuth2 专用机器凭据适配；不使用 system 内部直签服务，不缓存或自动重试临时令牌。 */
@Slf4j
@Component
@ConditionalOnExpression("${ai-chat-kit.ai.business-proxy.enabled:false} or ${ai-chat-kit.ai.hosted-stop.enabled:false} or ${ai-chat-kit.ai.hosted-proxy.stop-enabled:false}")
public class AiProxyMachineCredentialClient {
    /** 宿主秘密及短期寿命上限。 */
    private final AiHostedProxyProperties properties;
    /** 停止候选独立启用，不要求同时开启发送或本地工具签名。 */
    private final AiHostedStopProperties stopProperties;
    /** 可选v2停止配置，缺失默认为关闭，不影响旧4参构造或DELETE清理。 */
    private final ObjectProvider<AiHostedProxyStopProperties> v2StopProperties;
    /** 固定主体租户，不采信外部租户头。 */
    private final AiSessionInspectionProperties inspection;
    /** 复核签发结果的真实机器类型、当前客户端实体、scope/resource。 */
    private final AiSessionInspectionClient inspector;
    /** 启动时固定的标准 OAuth2 地址及客户端编号。 */
    private final String endpoint;
    /** 本次部署固定客户端，不允许运行中改名造成跨客户端撤销。 */
    private final String clientId;
    /** 本次部署固定客户端实体编号。 */
    private final Long clientRecordId;
    /** 独立客户端，不使用携带身份或报文日志的全局拦截器。 */
    private final OkHttpClient http = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build();
    /** 只在本地解析有限报文，拒绝重复字段与尾随内容。 */
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /** 固定目标和实体；缺配置即拒绝装配，不获取真实令牌。 */
    public AiProxyMachineCredentialClient(AiHostedProxyProperties properties,
            AiSessionInspectionProperties inspection, AiSessionInspectionClient inspector, AiHostedStopProperties stopProperties) {
        this(properties, inspection, inspector, stopProperties, null);
    }

    /** 原OAuth传输及验证保持不变，仅允许显式v2开关独立启用机器申请。 */
    @Autowired
    public AiProxyMachineCredentialClient(AiHostedProxyProperties properties,
            AiSessionInspectionProperties inspection, AiSessionInspectionClient inspector, AiHostedStopProperties stopProperties,
            ObjectProvider<AiHostedProxyStopProperties> v2StopProperties) {
        this.properties = properties;
        this.inspection = inspection;
        this.inspector = inspector;
        this.stopProperties = stopProperties;
        this.v2StopProperties = v2StopProperties;
        this.clientId = properties.getClientId();
        this.clientRecordId = properties.getClientRecordId();
        this.endpoint = origin(properties.getAuthorizationBaseUrl()).resolve("/admin-api/system/oauth2/token").toString();
        if (clientId == null || !clientId.matches("[a-zA-Z0-9_-]{1,64}") || clientRecordId == null
                || clientRecordId <= 0 || !inspection.isEnabled()) { throw failure(); }
        validateLifetime();
    }

    /**
     * 签发后立即复核真实实体及期限；不合规响应中的可识别令牌也尽力撤销。
     * @return 本轮临时机器凭据，调用方必须在 finally 中撤销
     * @throws IllegalStateException 签发、身份、期限或配置无效，不回退内部直签
     */
    public AiProxyMachineToken acquire() {
        String token = null;
        try {
            validateLifetime();
            JsonNode data = exchange("POST", new FormBody.Builder().add("grant_type", "client_credentials")
                    .add("scope", "ai.invoke").build());
            // 以收到签发响应时刻计算剩余寿命；不把正常请求耗时误判为超长令牌，不加隐式时钟容差。
            long receivedAt = Instant.now().toEpochMilli();
            if (data.path("access_token").isTextual()) { token = data.path("access_token").textValue(); }
            validateToken(token);
            if (!data.path("token_type").isTextual() || !"bearer".equalsIgnoreCase(data.path("token_type").textValue())
                    || !data.path("expires_in").isIntegralNumber() || !data.path("expires_in").canConvertToLong()
                    || data.path("expires_in").longValue() <= 0
                    || data.path("expires_in").longValue() > properties.getMaxTokenLifetimeSeconds()
                    || !"ai.invoke".equals(data.path("scope").textValue())) { throw failure(); }
            OAuth2SessionInspectionReqDTO query = new OAuth2SessionInspectionReqDTO();
            query.setSubjectType("MACHINE");
            query.setAccessToken(token);
            query.setExpectedTenantId(inspection.getSubjectTenantId());
            query.setExpectedUserId(0L);
            query.setExpectedClientRecordId(clientRecordId);
            query.setRequiredScope("ai.invoke");
            query.setRequiredResource("platform-ai");
            OAuth2SessionInspectionRespDTO identity = inspector.inspect(query);
            if (!Objects.equals(clientId, identity.getClientId())
                    || identity.getExpiresAtMillis() > receivedAt + properties.getMaxTokenLifetimeSeconds() * 1000L) {
                throw failure();
            }
            return new AiProxyMachineToken(token, identity.getTenantId());
        } catch (RuntimeException ex) {
            if (token != null) { revoke(token); }
            throw failure();
        }
    }

    /**
     * 撤销本轮凭据；令牌在 DELETE 表单中，不进入 URL，失败不遮蔽原始流结果。
     * @param token 本轮签发的原始机器令牌
     * @return 是否确认撤销；失败写固定运维告警，不能默默保留临时令牌
     */
    public boolean revoke(String token) {
        try {
            validateToken(token);
            JsonNode data = exchange("DELETE", new FormBody.Builder().add("token", token).build());
            if (!data.isBoolean() || !data.booleanValue()) { throw failure(); }
            return true;
        } catch (RuntimeException ex) {
            log.error("AI 代理临时机器令牌撤销未确认，请检查认证服务；不重试，按专用客户端短期有效期收敛");
            return false;
        }
    }

    /** Basic 仅放认证头，表单仅放协议参数；所有错误丢弃报文与底层异常。 */
    private JsonNode exchange(String method, RequestBody body) {
        try {
            String secret = properties.getClientSecret();
            if (("POST".equals(method) && !properties.isEnabled() && !stopProperties.isEnabled() && !v2StopEnabled())
                    || secret == null || secret.isEmpty() || secret.length() > 4096
                    || secret.chars().anyMatch(Character::isISOControl)) { throw failure(); }
            Request request = new Request.Builder().url(endpoint)
                    .header("Authorization", Credentials.basic(clientId, secret, StandardCharsets.UTF_8))
                    .header("tenant-id", String.valueOf(inspection.getSubjectTenantId()))
                    .header("Accept", "application/json").method(method, body).build();
            try (Response response = http.newCall(request).execute()) {
                if (response.code() != 200 || response.body() == null) { throw failure(); }
                JsonNode root = mapper.readTree(readBounded(response.body()));
                if (root == null || !root.path("code").isIntegralNumber()
                        || root.path("code").bigIntegerValue().signum() != 0 || root.get("data") == null) { throw failure(); }
                return root.get("data");
            }
        } catch (Exception ex) { throw failure(); }
    }

    /** 实际报文最多 16 KiB，包括分块及解压后的响应。 */
    private byte[] readBounded(ResponseBody body) throws Exception {
        try (InputStream input = body.byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer, 0, Math.min(buffer.length, 16385 - output.size()))) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > 16384) { throw failure(); }
            }
            return output.toByteArray();
        }
    }

    /** v2使用显式类型配置；旧4参构造及未装配配置均保持关闭，仅影响POST申请。 */
    private boolean v2StopEnabled() {
        AiHostedProxyStopProperties value = v2StopProperties == null ? null : v2StopProperties.getIfAvailable();
        return value != null && value.isStopEnabled();
    }

    /** 固定短期上限；响应丢失无法取得令牌时，认证侧配置的该寿命是最终收敛边界。 */
    private void validateLifetime() {
        if (properties.getMaxTokenLifetimeSeconds() < 30 || properties.getMaxTokenLifetimeSeconds() > 900) { throw failure(); }
    }

    /** 只接受原始不透明令牌，不允许空白、控制符或无限长度。 */
    private void validateToken(String token) {
        if (token == null || token.isEmpty() || token.length() > 4096
                || token.chars().anyMatch(value -> Character.isWhitespace(value) || Character.isISOControl(value))) { throw failure(); }
    }

    /** 固定 HTTPS 源站；禁止请求选择地址、路径、用户信息或查询。 */
    static URI origin(String value) {
        try {
            if (value == null || value.length() > 500) { throw failure(); }
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                    || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535))) { throw failure(); }
            return uri;
        } catch (RuntimeException ex) { throw failure(); }
    }

    /** @return 不含地址、秘密、令牌或响应的固定错误 */
    private static IllegalStateException failure() { return new IllegalStateException("AI 代理机器凭据不可用"); }
}

package io.github.yoyocw.aichatkit.compat.framework.security.core.oauth2;

import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.OAuth2SessionInspectionApi;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionRespDTO;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 无宿主业务依赖的受限会话 HTTP 客户端；每次查询，不缓存身份或使用全局身份拦截器。 */
public class OAuth2SessionInspectionClient implements AutoCloseable {
    /** 启动时固定的可信 HTTPS 目标，只访问标准核验路径。 */
    private final String endpoint;
    /** 服务消费者所属租户，由部署配置提供，不取自主体请求。 */
    private final Long consumerTenantId;
    /** 当前消费者令牌供应接口，生命周期由宿主负责。 */
    private final OAuth2InspectionCredentialProvider credentialProvider;
    /** 全部传输阶段有限时，不重定向、不自动重试、不记录敏感报文。 */
    private final OkHttpClient http = new OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build();
    /** 拒绝重复字段、类型强转和尾随内容，解析异常不会携带到上层日志。 */
    private final ObjectMapper mapper = new ObjectMapper().disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 固定受信任传输边界，缺配置时立即失败，不在构造阶段获取令牌。
     * @param baseUrl HTTPS 源站，不包含业务路径、用户信息、查询或片段
     * @param consumerTenantId 服务消费者租户，非负
     * @param credentialProvider 宿主服务凭据供应接口
     * @throws IllegalStateException 部署配置不完整或不合法
     */
    public OAuth2SessionInspectionClient(String baseUrl, Long consumerTenantId,
                                        OAuth2InspectionCredentialProvider credentialProvider) {
        try {
            if (baseUrl == null || baseUrl.length() > 500 || consumerTenantId == null
                    || consumerTenantId < 0 || credentialProvider == null) { throw failure(); }
            URI origin = URI.create(baseUrl);
            if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getUserInfo() != null
                    || origin.getQuery() != null || origin.getFragment() != null
                    || !(origin.getRawPath().isEmpty() || "/".equals(origin.getRawPath()))
                    || (origin.getPort() != -1 && (origin.getPort() < 1 || origin.getPort() > 65535))) {
                throw failure();
            }
            this.endpoint = origin.resolve(OAuth2SessionInspectionApi.PATH).toString();
            this.consumerTenantId = consumerTenantId;
            this.credentialProvider = credentialProvider;
        } catch (RuntimeException ex) { throw failure(); }
    }

    /**
     * 查询可信调用层构造的主体，并校验通用身份一致性。
     * @param subject 不得直接绑定外部请求；TOKEN 和 SESSION_ID 二选一
     * @return 当前身份；业务调用层必须按自身要求判断 permissionsSatisfied
     * @throws IllegalStateException 配置、网络、协议或身份无效；脱敏且不回退旧接口
     */
    public OAuth2SessionInspectionRespDTO inspect(OAuth2SessionInspectionReqDTO subject) {
        try {
            if (closed.get()) { throw failure(); }
            // 固化本轮条件，避免调用期间 DTO 改变导致校验目标漂移。
            OAuth2SessionInspectionReqDTO snapshot = mapper.readValue(mapper.writeValueAsBytes(subject),
                    OAuth2SessionInspectionReqDTO.class);
            validateSubject(snapshot);
            String token = credentialProvider.getAccessToken();
            validateToken(token);
            Request request = new Request.Builder().url(endpoint)
                    .header("Authorization", "Bearer " + token)
                    .header("tenant-id", String.valueOf(consumerTenantId)).header("Accept", "application/json")
                    .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"),
                            mapper.writeValueAsBytes(snapshot))).build();
            try (Response response = http.newCall(request).execute()) {
                if (response.code() != 200 || response.body() == null) { throw failure(); }
                JsonNode root = mapper.readTree(readBounded(response.body()));
                if (root == null || !root.path("code").isIntegralNumber() || root.path("code").bigIntegerValue().signum() != 0
                        || !root.path("data").isObject()
                        || !root.path("data").path("permissionsSatisfied").isBoolean()) { throw failure(); }
                // 老认证端不认识管理员断言时不能被当作授权成功；未请求时保持旧响应兼容。
                if (snapshot.isRequirePlatformAdministrator()
                        && !root.path("data").path("platformAdministrator").isBoolean()) { throw failure(); }
                OAuth2SessionInspectionRespDTO identity = mapper.treeToValue(root.get("data"),
                        OAuth2SessionInspectionRespDTO.class);
                validateIdentity(snapshot, identity);
                return identity;
            }
        } catch (IOException | RuntimeException ex) {
            // 不传播可能包含令牌、HTTP 正文或部署地址的底层异常和 cause。
            throw failure();
        }
    }

    /** 约束查询形态；用户和机器的具体授权仍由认证服务及宿主业务层裁决。 */
    private void validateSubject(OAuth2SessionInspectionReqDTO subject) {
        if (subject == null || subject.getExpectedTenantId() == null || subject.getExpectedTenantId() < 0
                || !("USER".equals(subject.getSubjectType()) || "MACHINE".equals(subject.getSubjectType()))
                || (subject.getAccessToken() == null) == (subject.getSessionId() == null)
                || subject.getRequiredPermissions() == null || subject.getRequiredPermissions().size() > 16) {
            throw failure();
        }
        if (subject.isRequirePlatformAdministrator() && !"USER".equals(subject.getSubjectType())) { throw failure(); }
        if (subject.getSessionId() != null && (subject.getSessionId() <= 0 || subject.getExpectedUserId() == null
                || ("MACHINE".equals(subject.getSubjectType()) && subject.getExpectedClientRecordId() == null))) {
            throw failure();
        }
        if (subject.getExpectedClientRecordId() != null && subject.getExpectedClientRecordId() <= 0) { throw failure(); }
        if (subject.getExpectedUserId() != null && ("USER".equals(subject.getSubjectType())
                ? subject.getExpectedUserId() <= 0 : subject.getExpectedUserId() != 0)) { throw failure(); }
        if (subject.getAccessToken() != null) { validateToken(subject.getAccessToken()); }
    }

    /** 凭据必须为单个原始不透明令牌，不容忍前后空白或控制字符。 */
    private void validateToken(String token) {
        if (token == null || token.isEmpty() || token.length() > 4096) { throw failure(); }
        for (int i = 0; i < token.length(); i++) {
            if (Character.isWhitespace(token.charAt(i)) || Character.isISOControl(token.charAt(i))) { throw failure(); }
        }
    }

    /** 仅核对通用身份、主体约束及 UTC 有效期，不解释业务权限。 */
    private void validateIdentity(OAuth2SessionInspectionReqDTO subject, OAuth2SessionInspectionRespDTO identity) {
        if (identity == null || identity.getSessionId() == null || identity.getSessionId() <= 0
                || identity.getUserId() == null || identity.getUserType() == null
                || !Objects.equals(subject.getExpectedTenantId(), identity.getTenantId())
                || identity.getClientId() == null || identity.getClientId().trim().isEmpty()
                || identity.getClientRecordId() == null || identity.getClientRecordId() <= 0
                || identity.getExpiresAtMillis() == null
                || identity.getExpiresAtMillis() <= Instant.now().toEpochMilli()) { throw failure(); }
        if (("USER".equals(subject.getSubjectType()) ? identity.getUserId() <= 0 : identity.getUserId() != 0)
                || (subject.getSessionId() != null && !subject.getSessionId().equals(identity.getSessionId()))
                || (subject.getExpectedUserId() != null && !subject.getExpectedUserId().equals(identity.getUserId()))
                || (subject.getExpectedClientRecordId() != null
                    && !subject.getExpectedClientRecordId().equals(identity.getClientRecordId()))) { throw failure(); }
    }

    /** 实际读取最多 16 KiB，分块和透明解压后的响应也受限。 */
    private byte[] readBounded(ResponseBody body) throws IOException {
        if (body.contentLength() > 16384) { throw failure(); }
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

    /** @return 无敏感配置、原文及 cause 的统一错误 */
    private static IllegalStateException failure() { return new IllegalStateException("受限会话认证不可用或身份不匹配"); }

    /** 释放此客户端独占的调用、线程池及连接池；允许宿主重复关闭。 */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) { return; }
        http.dispatcher().cancelAll();
        http.dispatcher().executorService().shutdown();
        http.connectionPool().evictAll();
        if (http.cache() != null) {
            try { http.cache().close(); } catch (IOException ignored) { /* no cache is configured by default */ }
        }
    }
}

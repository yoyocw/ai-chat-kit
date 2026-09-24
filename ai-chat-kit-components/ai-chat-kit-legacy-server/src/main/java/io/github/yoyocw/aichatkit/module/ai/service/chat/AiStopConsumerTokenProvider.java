package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.module.ai.config.AiStopConsumerProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.Credentials;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** 独立 AI 消费身份 Provider：按需申请、进程内短缓存，不接受外部 Token，不自动刷新或重试。 */
@Component
@RequiredArgsConstructor
public class AiStopConsumerTokenProvider {
    /** 固定停止核验权限，不接受调用方提供 scope。 */
    private static final String SCOPE = "ai.stop.consume";
    /** 实际读取响应上限，字节，不仅检查 Content-Length。 */
    private static final int MAX_BODY_BYTES = 16384;
    /** 本地缓存最多五分钟，不改变服务端会话有效期。 */
    private static final long MAX_CACHE_MILLIS = 300000L;
    /** 扣除网络往返、时间粒度误差，并为后续核验留出余量。 */
    private static final long SAFETY_MILLIS = 10000L;
    /** 部署身份来源，不使用 SecurityContext 或租户 ThreadLocal。 */
    private final AiStopConsumerProperties properties;
    /** 专用正常 TLS 客户端，无业务拦截器、Cookie、重定向或连接失败重试。 */
    private final OkHttpClient http = new OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build();
    /** 直接解析且不记录原文，避免通用 JSON 异常日志输出凭据。 */
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    /** 缓存绑定的配置副本，所有缓存字段仅在同步方法内访问。 */
    private AiStopConsumerProperties cachedConfig;
    /** 缓存的内部 Bearer 头，禁止日志和模型输入。 */
    private String cachedAuthorization;
    /** 单调时钟失效点，纳秒。 */
    private long expiresAtNanos;
    /** 失败后一秒拒绝排队申请，防止并发失败形成请求风暴，无后台重试。 */
    private long retryAfterNanos;

    /**
     * 获取固定 AI 消费身份；并发成功申请仅执行一次，配置变化立即丢弃旧缓存。
     * @return 内部 RPC Authorization 值，不得向用户返回或由请求参数覆盖
     * @throws IllegalStateException 配置无效、关闭、申请失败或有效时间不足；异常不含秘密及 cause
     */
    public synchronized String getAuthorization() {
        AiStopConsumerProperties config = properties.snapshot();
        if (!config.equals(cachedConfig)) {
            clear();
            retryAfterNanos = 0;
            cachedConfig = config;
        }
        try {
            validate(config);
        } catch (RuntimeException ex) {
            clear();
            throw failure();
        }
        long now = System.nanoTime();
        if (cachedAuthorization != null && expiresAtNanos - now > 0) { return cachedAuthorization; }
        clear();
        // 排队调用不得延长失败冷却期；到达固定截止后才允许下一次主动申请。
        if (retryAfterNanos != 0 && retryAfterNanos - now > 0) { throw failure(); }
        try {
            return acquire(config, now);
        } catch (IOException | RuntimeException ex) {
            clear();
            retryAfterNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            throw failure();
        }
    }

    /** 固定服务器与部署身份校验；关闭时不执行任何 HTTP 请求。 */
    private void validate(AiStopConsumerProperties config) {
        if (!config.isEnabled() || config.getBaseUrl() == null || config.getBaseUrl().length() > 500
                || config.getClientId() == null || !config.getClientId().matches("[a-zA-Z0-9_-]{1,64}")
                || config.getClientSecret() == null || config.getClientSecret().isEmpty()
                || config.getClientSecret().length() > 1024 || config.getClientSecret().contains("\r")
                || config.getClientSecret().contains("\n") || config.getTenantId() == null
                || config.getTenantId() < 0) { throw failure(); }
        URI uri = URI.create(config.getBaseUrl());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                || (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535))) { throw failure(); }
    }

    /** 使用配置租户、Basic 认证及固定 form 申请，不携带外部用户头。 */
    private String acquire(AiStopConsumerProperties config, long startedAt) throws IOException {
        Request request = new Request.Builder()
                .url(URI.create(config.getBaseUrl()).resolve("/admin-api/system/oauth2/token").toString())
                .header("Authorization", Credentials.basic(config.getClientId(), config.getClientSecret(), StandardCharsets.UTF_8))
                .header("tenant-id", String.valueOf(config.getTenantId()))
                .header("Accept", "application/json")
                .post(new FormBody.Builder().add("grant_type", "client_credentials").add("scope", SCOPE).build())
                .build();
        AiStopConsumerTokenDTO token;
        try (Response response = http.newCall(request).execute()) {
            if (response.code() != 200 || response.body() == null) { throw failure(); }
            CommonResult<AiStopConsumerTokenDTO> result = mapper.readValue(readBounded(response.body()),
                    mapper.getTypeFactory().constructParametricType(CommonResult.class, AiStopConsumerTokenDTO.class));
            if (result == null || !Integer.valueOf(0).equals(result.getCode())) { throw failure(); }
            token = result.getData();
        }
        long lifetime = validateToken(token);
        if (!config.equals(properties.snapshot())) { throw failure(); }
        long deadline = startedAt + TimeUnit.MILLISECONDS.toNanos(lifetime);
        if (deadline - System.nanoTime() <= 0) { throw failure(); }
        cachedAuthorization = "Bearer " + token.getAccessToken();
        expiresAtNanos = deadline;
        return cachedAuthorization;
    }

    /** 按真实响应流限量读取，未知长度及分块响应同样受限。 */
    private byte[] readBounded(ResponseBody body) throws IOException {
        if (body.contentLength() > MAX_BODY_BYTES) { throw failure(); }
        try (InputStream input = body.byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer, 0, Math.min(buffer.length, MAX_BODY_BYTES + 1 - output.size()))) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > MAX_BODY_BYTES) { throw failure(); }
            }
            return output.toByteArray();
        }
    }

    /** 严格拒绝异常凭据、权限及时间；在乘法前校验溢出，不使用 refresh_token。 */
    private long validateToken(AiStopConsumerTokenDTO token) {
        if (token == null || !"bearer".equalsIgnoreCase(token.getTokenType()) || !SCOPE.equals(token.getScope())
                || token.getAccessToken() == null || !token.getAccessToken().matches("[A-Za-z0-9._~+/-]{1,4096}=*")
                || token.getAccessToken().length() > 4096 || token.getExpiresIn() == null
                || token.getExpiresIn() <= 0 || token.getExpiresIn() > Long.MAX_VALUE / 1000L) { throw failure(); }
        long lifetime = Math.min(MAX_CACHE_MILLIS, token.getExpiresIn() * 1000L - SAFETY_MILLIS);
        if (lifetime <= 0) { throw failure(); }
        return lifetime;
    }

    /** 过期及失败后丢弃内存凭据，不触发远端撤销或自动重试消费。 */
    private void clear() {
        cachedAuthorization = null;
        expiresAtNanos = 0;
    }

    /** 不保留 HTTP/JSON 原异常，避免调用方打印敏感请求和响应。 */
    private IllegalStateException failure() {
        return new IllegalStateException("AI 停止消费身份暂不可用");
    }
}

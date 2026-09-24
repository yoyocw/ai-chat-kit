package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.module.ai.api.delegation.dto.McpDelegationRespDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.biz.system.oauth2.dto.OAuth2SessionInspectionReqDTO;
import io.github.yoyocw.aichatkit.compat.framework.common.util.json.JsonUtils;
import io.github.yoyocw.aichatkit.module.ai.config.AiHostedProxyProperties;
import io.github.yoyocw.aichatkit.module.ai.config.AiSessionInspectionProperties;
import io.github.yoyocw.aichatkit.module.ai.controller.admin.aiproxy.vo.AiProxySendReqVO;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** AI 承接业务代理生命周期；用户登录令牌仅用于认证复核，下游只接收机器令牌及受限委托。 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ai-chat-kit.ai.business-proxy", name = "enabled", havingValue = "true")
public class AiHostedBusinessProxyService {
    /** 管理员部署的固定目标及开关。 */
    private final AiHostedProxyProperties properties;
    /** 当前部署允许的主体租户。 */
    private final AiSessionInspectionProperties inspection;
    /** 在准备流之前检查真实用户及具体入口权限。 */
    private final AiSessionInspectionClient inspector;
    /** 标准 OAuth2 获取和撤销临时机器令牌。 */
    private final AiProxyMachineCredentialClient machineClient;
    /** 本地签发同租户、同机器客户端的委托；缺少配置不能启动代理。 */
    private final AiLocalDelegationSigningService signing;
    /** 独立流式连接，有界总时长且关闭库级重试和重定向，避免重复发送对话。 */
    private final OkHttpClient http = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(3, TimeUnit.SECONDS).writeTimeout(5, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS).callTimeout(190, TimeUnit.SECONDS).build();

    /**
     * 准备用户已授权的发送，模式只由固定入口决定。
     * @param request 无地址或凭据的用户对话内容
     * @param authorization 当前真实用户 Bearer 令牌
     * @param group 是否为群聊，群聊必须给出现有会话编号
     * @return 逐事件转发的 SSE；回调未运行时不产生临时机器令牌
     */
    public StreamingResponseBody send(AiProxySendReqVO request, String authorization, boolean group) {
        if (!properties.isEnabled() || authorization == null || !authorization.startsWith("Bearer ")
                || request == null || (group && request.getConversationId() == null)) {
            throw new IllegalStateException("AI 业务代理请求或配置无效");
        }
        String mode = group ? "group" : "single";
        URI target = AiProxyMachineCredentialClient.origin(properties.getAiBaseUrl())
                .resolve("/admin-api/ai/delegated/" + mode + "/send");
        OAuth2SessionInspectionReqDTO query = new OAuth2SessionInspectionReqDTO();
        query.setSubjectType("USER");
        query.setAccessToken(authorization.substring(7));
        query.setExpectedTenantId(inspection.getSubjectTenantId());
        query.setRequiredPermissions(Collections.singletonList(group ? "aigroup:chat:message:send" : "ai:chat:seed"));
        inspector.inspect(query);
        byte[] body = JsonUtils.toJsonByte(request);
        return output -> stream(target, authorization, mode, body, output);
    }

    /** 成功、异常、提前 EOF 和客户端断开都关闭下游并撤销临时机器令牌。 */
    private void stream(URI target, String userAuthorization, String mode, byte[] body, OutputStream output) {
        AiProxyMachineToken machine = null;
        try {
            machine = machineClient.acquire();
            String machineAuthorization = "Bearer " + machine.getAccessToken();
            McpDelegationRespDTO delegation = signing.issueForCaller(machineAuthorization, userAuthorization, mode);
            try (Response response = open(target, machineAuthorization, delegation.getCredential(), machine.getTenantId(), body);
                    InputStream input = response.body().byteStream()) { AiProxySseRelay.transfer(input, output); }
        } catch (Exception ex) {
            try { AiProxySseRelay.failure(output); } catch (IOException disconnected) {
                // 浏览器断开不跳过 finally，错误正文与凭据均不记日志。
            }
        } finally {
            if (machine != null) { machineClient.revoke(machine.getAccessToken()); }
        }
    }

    /** 固定目标和标准 TLS，不重定向或重试，不缓存 SSE 正文。 */
    private Response open(URI target, String authorization, String delegation,
            Long tenantId, byte[] body) throws IOException {
        Request request = new Request.Builder().url(target.toString()).header("Accept", "text/event-stream")
                .header("Authorization", authorization).header("X-AI-User-Delegation", delegation)
                .header("tenant-id", String.valueOf(tenantId))
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body)).build();
        Response response = http.newCall(request).execute();
        if (response.code() != 200 || response.body() == null || response.header("Content-Type") == null
                || !response.header("Content-Type").toLowerCase(Locale.ROOT).startsWith("text/event-stream")) {
            response.close();
            throw new IOException("AI 下游响应无效");
        }
        return response;
    }
}

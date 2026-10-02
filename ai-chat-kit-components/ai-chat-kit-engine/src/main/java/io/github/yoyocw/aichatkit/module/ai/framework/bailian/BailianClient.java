package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import io.github.yoyocw.aichatkit.module.ai.framework.json.AiEngineJson;
import io.github.yoyocw.aichatkit.module.ai.config.BailianProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

import static io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError.*;

/**
 * 阿里云百炼应用 HTTP 客户端，负责鉴权、SSE 解析、增量文本提取与上游请求取消。
 */
public class BailianClient implements AutoCloseable {

    /** 百炼 HTTP 请求正文的 UTF-8 JSON 媒体类型。 */
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    /** 百炼服务端配置，不向客户端或错误消息传播凭证值。 */
    private final BailianProperties properties;
    /** 复用连接池，并限制读空闲时间与单次调用总时长。 */
    private final OkHttpClient httpClient;
    /** 可选的跨节点已提交状态检查；不持有宿主凭据。 */
    private final BailianGenerationMonitor generationMonitor;
    /** 当前 JVM 内的活动请求；按单聊或群聊消息编号定位取消目标。 */
    private final Map<String, Call> activeCalls = new ConcurrentHashMap<>();
    /** 登记与停机共用协调状态，确保关闭开始后不会漏入新调用。 */
    private final Object lifecycleLock = new Object();
    /** 仅在 lifecycleLock 内访问。 */
    private boolean closed;

    public BailianClient(BailianProperties properties) {
        // 启动时仅要求网络边界有效，允许宿主在未配置调用凭据时完成组件装配。
        if (properties == null || !properties.hasValidNetworkTimeouts()) {
            throw new IllegalArgumentException("百炼网络超时配置无效");
        }
        this.properties = properties;
        this.generationMonitor = properties.isGenerationWatchEnabled()
                ? new BailianGenerationMonitor(properties.getGenerationWatchIntervalMillis(),
                        properties.getGenerationWatchMaxCalls()) : null;
        this.httpClient = new OkHttpClient.Builder()
                // 请求携带临时工具凭据，禁止自动转发或网络层重放；业务会话重试由调用方控制。
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(properties.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(properties.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .callTimeout(properties.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }

    /**
     * 校验调用百炼所需配置。
     *
     * @return 配置是否完整且超时值有效
     */
    public boolean isConfigured(String appId) {
        return StringUtils.hasText(properties.getApiKey())
                && appId != null && appId.matches("[A-Fa-f0-9]{32}")
                && validBaseUrl() != null
                && properties.hasValidNetworkTimeouts();
    }

    /**
     * 校验多智能体编排工作流调用配置。
     *
     * @param appId 本轮数据库配置中的应用 ID，不读取文件中的 group-app-id
     * @return API Key、群聊工作流 APP_ID 和超时配置是否完整
     */
    public boolean isGroupWorkflowConfigured(String appId) {
        return isConfigured(appId);
    }

    /**
     * 调用已发布的百炼应用并逐段消费 SSE 增量文本。
     *
     * @param messageId 本地助手消息编号，用于停止指定上游请求
     * @param prompt 用户本轮问题
     * @param sessionId 百炼短期会话标识，首轮为空
     * @param eventConsumer 已脱敏处理进度与最终回答增量的消费者
     * @return 完整回复、请求编号和百炼原始 output 数据
     * @throws IOException 网络失败、非成功 HTTP 状态或百炼业务错误
     */
    public BailianStreamResult stream(Long messageId, String appId, String prompt, String sessionId,
                                      Consumer<BailianStreamEvent> eventConsumer) throws IOException {
        return stream(messageId, appId, prompt, sessionId, null, eventConsumer);
    }

    /**
     * 调用普通对话工作流，并传递由服务端组装的自定义变量。
     *
     * @param messageId 本地助手消息编号，用于停止指定上游请求
     * @param prompt 用户本轮问题
     * @param sessionId 百炼短期会话标识，首轮为空
     * @param bizParams 已按工作流变量白名单组装的参数
     * @param eventConsumer 已脱敏处理进度与最终回答增量的消费者
     * @return 完整回复、请求编号和百炼原始 output 数据
     * @throws IOException 网络失败、非成功 HTTP 状态或百炼业务错误
     */
    public BailianStreamResult stream(Long messageId, String appId, String prompt, String sessionId,
                                      Map<String, Object> bizParams,
                                      Consumer<BailianStreamEvent> eventConsumer) throws IOException {
        return stream(messageId, appId, prompt, sessionId, bizParams, eventConsumer, null);
    }

    /**
     * 带共享状态探针的单聊调用；参数沿用原 stream，监测关闭时不执行探针。
     * @param stillGenerating 在可信宿主作用域内读取已提交生成状态，监测开启时不可为空
     * @return 百炼完整响应；停止/检查失败通过既有异常路径收口
     * @throws IOException 模型调用或状态监测不可用
     */
    public BailianStreamResult stream(Long messageId, String appId, String prompt, String sessionId,
                                      Map<String, Object> bizParams, Consumer<BailianStreamEvent> eventConsumer,
                                      BooleanSupplier stillGenerating) throws IOException {
        return streamApplication("single:" + messageId, appId, prompt, sessionId,
                bizParams, eventConsumer, stillGenerating);
    }

    /**
     * 调用多智能体编排工作流，成员候选和本地历史通过工作流自定义变量传递。
     *
     * @param messageId 生成中的群聊占位消息编号
     * @param appId 本轮数据库配置快照中的应用 ID
     * @param prompt 用户本轮问题
     * @param sessionId 百炼短期会话标识，首次或过期重试时为空
     * @param bizParams 已按白名单组装的工作流自定义变量
     * @return 工作流完整输出
     * @throws IOException 网络失败、HTTP 错误或百炼业务错误
     */
    public BailianStreamResult streamGroupWorkflow(Long messageId, String appId, String prompt, String sessionId,
                                                    Map<String, Object> bizParams) throws IOException {
        return streamGroupWorkflow(messageId, appId, prompt, sessionId, bizParams, null);
    }

    /**
     * 带共享状态探针的群聊调用，停止条件和资源释放行为与单聊相同。
     * @param stillGenerating 可信已提交状态探针；监测开启时不可为空
     * @return 群聊模型响应
     * @throws IOException 模型调用或状态监测不可用
     */
    public BailianStreamResult streamGroupWorkflow(Long messageId, String appId, String prompt, String sessionId,
                                                    Map<String, Object> bizParams,
                                                    BooleanSupplier stillGenerating) throws IOException {
        return streamApplication("group:" + messageId, appId, prompt, sessionId,
                bizParams, delta -> { }, stillGenerating);
    }

    private BailianStreamResult streamApplication(String callKey, String appId, String prompt, String sessionId,
                                                   Map<String, Object> bizParams,
                                                   Consumer<BailianStreamEvent> eventConsumer,
                                                   BooleanSupplier stillGenerating) throws IOException {
        Request request = buildRequest(appId, prompt, sessionId, bizParams, callKey.startsWith("single:"));
        Call call = httpClient.newCall(request);
        synchronized (lifecycleLock) {
            if (closed || activeCalls.putIfAbsent(callKey, call) != null) {
                throw new BailianCallException(BAILIAN_CALL_FAILED, false);
            }
        }
        long startNanos = System.nanoTime();
        try (BailianGenerationWatch watch = generationMonitor == null ? null
                : generationMonitor.watch(call, stillGenerating);
                Response response = call.execute()) {
            assertSuccessful(response);
            return readStream(response.body(), eventConsumer, startNanos);
        } catch (BailianSessionExpiredException ex) {
            // 会话失效由既有服务层清理并仅重试一次，不能吞成一般失败。
            throw ex;
        } catch (IOException ex) {
            throw BailianCallException.from(ex);
        } finally {
            activeCalls.remove(callKey, call);
        }
    }

    /**
     * 取消仍在执行的百炼 HTTP 请求。
     *
     * @param messageId 本地助手消息编号
     * @return 是否找到并取消了活动请求；尚未发起或已经结束时返回 false
     */
    public boolean cancel(Long messageId) {
        return cancel("single:" + messageId);
    }

    /**
     * 取消仍在执行的群聊工作流请求。
     *
     * @param messageId 群聊生成占位消息编号
     * @return 是否找到并取消了活动请求
     */
    public boolean cancelGroupWorkflow(Long messageId) {
        return cancel("group:" + messageId);
    }

    private boolean cancel(String callKey) {
        Call call = activeCalls.get(callKey);
        if (call == null) {
            return false;
        }
        call.cancel();
        return true;
    }

    /** 停机时取消本实例活动请求并释放自有连接池与监测线程，不操作其他宿主资源。 */
    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            activeCalls.values().forEach(Call::cancel);
            if (generationMonitor != null) {
                generationMonitor.close();
            }
            httpClient.dispatcher().cancelAll();
            httpClient.dispatcher().executorService().shutdown();
            httpClient.connectionPool().evictAll();
        }
    }

    private Request buildRequest(String appId, String prompt, String sessionId, Map<String, Object> bizParams,
                                 boolean includeThoughts) throws BailianCallException {
        if (!isConfigured(appId)) {
            throw new BailianCallException(BAILIAN_CONFIG_INVALID, false);
        }
        // 单聊与群聊都使用 prompt，并仅发送各自服务端按已发布变量白名单组装的 biz_params。
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", prompt);
        if (StringUtils.hasText(sessionId)) {
            input.put("session_id", sessionId);
        }
        if (bizParams != null && !bizParams.isEmpty()) {
            input.put("biz_params", bizParams);
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("incremental_output", true);
        if (includeThoughts) {
            parameters.put("has_thoughts", properties.isHasThoughts());
            parameters.put("enable_thinking", properties.isEnableThinking());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("input", input);
        payload.put("parameters", parameters);
        payload.put("debug", new LinkedHashMap<String, Object>());
        try {
            HttpUrl baseUrl = validBaseUrl();
            if (baseUrl == null) {
                throw new BailianCallException(BAILIAN_CONFIG_INVALID, false);
            }
            HttpUrl.Builder urlBuilder = baseUrl.newBuilder();
            // 去掉根路径末尾的空段，并逐段追加已验证的应用 ID，避免注入查询或其它路径。
            while (urlBuilder.build().encodedPath().endsWith("/")
                    && urlBuilder.build().pathSize() > 1) {
                urlBuilder.removePathSegment(urlBuilder.build().pathSize() - 1);
            }
            HttpUrl url = urlBuilder.addPathSegment("apps").addPathSegment(appId)
                    .addPathSegment("completion").build();
            return new Request.Builder().url(url)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("X-DashScope-SSE", "enable")
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(AiEngineJson.toJsonString(payload), JSON_MEDIA_TYPE))
                    .build();
        } catch (IllegalArgumentException ex) {
            // URL 或请求头配置无效时，OkHttp 异常可能带入原值，只保留安全配置错误。
            throw new BailianCallException(BAILIAN_CONFIG_INVALID, false);
        }
    }

    /** 按 Content-Type 区分 SSE 与 JSON 错误，防止 200 错误体被当成无数据长连接。 */
    private void assertSuccessful(Response response) throws IOException {
        ResponseBody body = response.body();
        MediaType type = body == null ? null : body.contentType();
        if (response.isSuccessful() && type != null && "text".equalsIgnoreCase(type.type())
                && "event-stream".equalsIgnoreCase(type.subtype())) {
            return;
        }
        JsonNode error = null;
        if (body != null) {
            try {
                // 错误正文仅取 16 KiB、最多等两秒；不为读完异常大体积正文阻塞整次调用。
                body.source().timeout().deadline(2, TimeUnit.SECONDS);
                error = AiEngineJson.readTree(response.peekBody(16 * 1024).string());
            } catch (IOException ignored) {
                // 坏 JSON 或错误体读取失败仍按已收到的 HTTP 状态分类，禁止日志输出原文。
            }
        }
        if (error != null) {
            assertNoBailianError(error, response.code());
        }
        throw response.isSuccessful() ? new BailianCallException(BAILIAN_RESPONSE_INCOMPLETE, true)
                : BailianCallException.fromUpstream(response.code(), "", "");
    }

    private BailianStreamResult readStream(ResponseBody body, Consumer<BailianStreamEvent> eventConsumer,
                                           long startNanos) throws IOException {
        BufferedSource source = body.source();
        StringBuilder content = new StringBuilder();
        Set<String> emittedProgressKeys = new LinkedHashSet<String>();
        String requestId = null;
        String sessionId = null;
        String finishReason = null;
        String responseData = null;
        BailianUsage usage = null;
        Long firstTokenMs = null;
        int toolCallCount = 0;
        boolean completed = false;
        String line;
        while ((line = readStreamLine(source)) != null) {
            if (!line.startsWith("data:")) {
                continue;
            }
            String data = line.substring(5).trim();
            if (data.isEmpty()) {
                continue;
            }
            if ("[DONE]".equals(data)) {
                completed = true;
                finishReason = "stop";
                break;
            }
            JsonNode root = parseStreamFrame(data);
            assertNoBailianError(root, 200);
            JsonNode output = root.path("output");
            String currentFinishReason = output.path("finish_reason").asText("");
            JsonNode thoughts = output.path("thoughts");
            emitProgressEvents(thoughts, emittedProgressKeys, eventConsumer);
            toolCallCount = Math.max(toolCallCount, countToolCalls(thoughts));
            String delta = extractText(output);
            if (!delta.isEmpty()) {
                if (firstTokenMs == null) {
                    firstTokenMs = elapsedMillis(startNanos);
                }
                content.append(delta);
                eventConsumer.accept(BailianStreamEvent.delta(delta));
            }
            BailianUsage currentUsage = BailianUsage.from(root.path("usage"));
            if (currentUsage != null) {
                usage = currentUsage;
            }
            requestId = root.path("request_id").asText(requestId);
            sessionId = output.path("session_id").asText(sessionId);
            finishReason = currentFinishReason;
            responseData = sanitizeResponseData(output, responseData);
            // 非正常结束的尾帧也可能含正文，先保留片段，再明确标记未完整生成。
            if (StringUtils.hasText(finishReason) && !"null".equals(finishReason)
                    && !"stop".equals(finishReason)) {
                throw new BailianCallException(BAILIAN_RESPONSE_INCOMPLETE, true);
            }
            if ("stop".equals(finishReason)) {
                // 官方应用协议的 stop 尾帧包含最终 usage/session，先完整消费再结束，不等 EOF。
                completed = true;
                break;
            }
        }
        if (!completed || !StringUtils.hasText(content)) {
            throw new BailianCallException(BAILIAN_RESPONSE_INCOMPLETE, true);
        }
        BailianStreamResult result = new BailianStreamResult();
        result.setContent(content.toString());
        result.setRequestId(requestId);
        result.setSessionId(sessionId);
        result.setFinishReason(finishReason);
        result.setResponseData(responseData);
        result.setFirstTokenMs(firstTokenMs);
        result.setToolCallCount(toolCallCount);
        if (usage != null) {
            result.setModelNames(usage.getModelNames());
            result.setInputTokens(usage.getInputTokens());
            result.setOutputTokens(usage.getOutputTokens());
        }
        return result;
    }

    /** 区分流读取超时与连接中断；正文已输出也必须进入失败终态。 */
    private String readStreamLine(BufferedSource source) throws IOException {
        try {
            return source.readUtf8Line();
        } catch (IOException ex) {
            BailianCallException failure = BailianCallException.from(ex);
            throw failure.getErrorCode() == BAILIAN_TIMEOUT ? failure
                    : new BailianCallException(BAILIAN_RESPONSE_INCOMPLETE, true);
        }
    }

    /** 直接使用 Jackson，避免通用 JSON 工具在解析失败时记录完整上游数据。 */
    private JsonNode parseStreamFrame(String data) throws IOException {
        try {
            JsonNode root = AiEngineJson.readTree(data);
            if (root != null && root.isObject()) { return root; }
        } catch (IOException ignored) {
            // 不保留解析异常的原因链，其中可能包含上游数据与临时凭据。
        }
        throw new BailianCallException(BAILIAN_RESPONSE_INCOMPLETE, true);
    }

    /** 统计当前 thoughts 快照中的非推理、非最终回答动作数量。 */
    private int countToolCalls(JsonNode thoughts) {
        if (!thoughts.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode thought : thoughts) {
            String actionType = thought.path("action_type").asText("");
            if (StringUtils.hasText(actionType) && !"reasoning".equalsIgnoreCase(actionType)
                    && !"response".equalsIgnoreCase(actionType)) {
                count++;
            }
        }
        return count;
    }

    /** 将单调时钟耗时转换为非负毫秒数。 */
    private long elapsedMillis(long startNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    /**
     * 将百炼 thoughts 转换为去重后的安全进度事件，不传输模型原始思考和工具入参。
     */
    private void emitProgressEvents(JsonNode thoughts, Set<String> emittedProgressKeys,
                                    Consumer<BailianStreamEvent> eventConsumer) {
        if (!thoughts.isArray()) {
            return;
        }
        for (JsonNode thought : thoughts) {
            String actionType = sanitizeDisplayText(thought.path("action_type").asText(""), 40);
            String actionName = sanitizeDisplayText(thought.path("action_name").asText(""), 80);
            if (!StringUtils.hasText(actionType) && !StringUtils.hasText(actionName)) {
                continue;
            }
            String progressKey = actionType.toLowerCase(Locale.ROOT) + '|' + actionName;
            if (!emittedProgressKeys.add(progressKey)) {
                continue;
            }
            eventConsumer.accept(buildProgressEvent(actionType, actionName));
        }
    }

    /**
     * 根据百炼动作类型生成稳定、非敏感的前端进度文案。
     */
    private BailianStreamEvent buildProgressEvent(String actionType, String actionName) {
        if ("reasoning".equalsIgnoreCase(actionType)) {
            return BailianStreamEvent.progress("reasoning", "正在分析问题", null);
        }
        if ("response".equalsIgnoreCase(actionType)) {
            return BailianStreamEvent.progress("generating", "正在整理回答", null);
        }
        String message = StringUtils.hasText(actionName) ? "正在执行：" + actionName : "正在调用业务工具";
        return BailianStreamEvent.progress("tool_call", message, actionName);
    }

    /**
     * 删除控制字符并限制展示长度，避免第三方动作名称污染 SSE 和页面展示。
     */
    private String sanitizeDisplayText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String sanitized = value.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s+", " ");
        return sanitized.length() <= maxLength ? sanitized : sanitized.substring(0, maxLength);
    }

    /**
     * 移除原始思考数组后再保存扩展结果，防止思维链和工具入参进入数据库或前端 result 事件。
     */
    private String sanitizeResponseData(JsonNode output, String previousResponseData) {
        if (output.isMissingNode()) {
            return previousResponseData;
        }
        JsonNode safeOutput = output.deepCopy();
        if (safeOutput.isObject()) {
            ObjectNode safeObject = (ObjectNode) safeOutput;
            // 文档引用可能只出现在中间分片，累积安全字段，不由末帧空值覆盖。
            JsonNode previous = previousResponseData == null ? null : AiEngineJson.parseTree(previousResponseData);
            safeObject.set("doc_references", BailianCitationSupport.merge(
                    previous == null ? null : previous.path("doc_references"), output.path("doc_references")));
            safeObject.remove("thoughts");
            safeObject.remove("thought");
            safeObject.remove("reasoning_content");
            safeObject.remove("reasoningContent");
        }
        return safeOutput.toString();
    }

    private void assertNoBailianError(JsonNode root, int status) throws IOException {
        JsonNode error = root.path("error").isObject() ? root.path("error") : root;
        String code = error.path("code").asText("");
        String message = error.path("message").asText("");
        if (!StringUtils.hasText(code) && (!StringUtils.hasText(message) || root.has("output"))) {
            return;
        }
        if (isSessionExpired(code, message)) {
            throw new BailianSessionExpiredException(BAILIAN_SESSION_EXPIRED_MESSAGE);
        }
        throw BailianCallException.fromUpstream(status, code, message);
    }

    private boolean isSessionExpired(String code, String message) {
        String detail = (code + " " + message).toLowerCase(Locale.ROOT);
        boolean mentionsSession = detail.contains("session") || detail.contains("会话");
        return mentionsSession && (detail.contains("invalid") || detail.contains("expired")
                || detail.contains("not found") || detail.contains("失效")
                || detail.contains("过期") || detail.contains("不存在"));
    }

    private String extractText(JsonNode output) {
        return output.path("text").asText("");
    }

    /** 校验配置地址；失败仅返回空值，不传播可能包含敏感地址的解析异常。 */
    private HttpUrl validBaseUrl() {
        String value = properties.getBaseUrl();
        if (!StringUtils.hasText(value) || !value.regionMatches(true, 0, "https://", 0, 8)
                || !value.equals(value.trim())
                || value.indexOf('\\') >= 0 || value.indexOf('@') >= 0
                || value.chars().anyMatch(Character::isWhitespace)) {
            return null;
        }
        HttpUrl url = HttpUrl.parse(value);
        return url != null && "https".equals(url.scheme())
                && url.username().isEmpty() && url.password().isEmpty()
                && url.query() == null && url.fragment() == null ? url : null;
    }
}

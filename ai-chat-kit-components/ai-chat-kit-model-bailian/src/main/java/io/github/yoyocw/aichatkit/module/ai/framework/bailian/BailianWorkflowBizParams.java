package io.github.yoyocw.aichatkit.module.ai.framework.bailian;

import io.github.yoyocw.aichatkit.module.ai.framework.json.AiEngineJson;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMember;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.BAILIAN_AGENT_USER_PROMPT_PARAMS;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.BAILIAN_AGENT_USER_DEFINED_PARAMS;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.BAILIAN_AGENT_USER_DEFINED_TOKENS;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.BAILIAN_AGENT_USER_TOKEN;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.BAILIAN_VAR_AGENT_CODE;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.BAILIAN_VAR_AVAILABLE_AGENTS_JSON;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.BAILIAN_VAR_BUSINESS_ARTIFACT_JSON;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.BAILIAN_VAR_HISTORY_SUMMARY;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.BAILIAN_VAR_PAGE_CONTEXT_JSON;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.BAILIAN_VAR_TRACE_CODE;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.PAGE_CODE_CHAT;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.PAGE_CODE_GROUP_CHAT;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.MCP_AUTHORIZATION_HEADER;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiChatConstants.MCP_TRACE_CODE_HEADER;

/**
 * 百炼应用变量组装器，分别组装普通对话提示词、旧版工具鉴权和群聊工作流变量。
 */
public final class BailianWorkflowBizParams {

    /**
     * 组装普通对话变量与旧版工具鉴权；新版应用的透传能力需要独立验证。
     *
     * @param pageContextJson 页面上下文 JSON 字符串，只能用于理解交互场景
     * @param businessArtifactJson 后端按当前用户权限查询并裁剪后的业务事实 JSON
     * @param historySummary 服务端生成的有限历史摘要
     * @param mcpId 百炼 platform-readonly MCP 服务 ID，为空时不启用 MCP 鉴权透传
     * @param authorization 后端签发的完整 MCP JWT Authorization，禁止使用模型入参
     * @param userAuthToolIds 旧版 HTTP 工具 ID 列表，工具使用用户级 Header bearer 鉴权
     * @param traceCode 服务端生成的 MCP 调用链路追踪码
     * @return 提示词与鉴权参数独立存放的 biz_params
     */
    public static Map<String, Object> buildAgentPromptParams(String pageContextJson,
                                                              String businessArtifactJson,
                                                              String historySummary,
                                                              String mcpId,
                                                              String authorization,
                                                              List<String> userAuthToolIds,
                                                              String traceCode) {
        Map<String, Object> promptParams = new LinkedHashMap<String, Object>();
        promptParams.put(BAILIAN_VAR_PAGE_CONTEXT_JSON, pageContextJson);
        promptParams.put(BAILIAN_VAR_BUSINESS_ARTIFACT_JSON,
                businessArtifactJson == null ? "{}" : businessArtifactJson);
        promptParams.put(BAILIAN_VAR_HISTORY_SUMMARY, historySummary == null ? "" : historySummary);

        Map<String, Object> bizParams = new LinkedHashMap<String, Object>();
        bizParams.put(BAILIAN_AGENT_USER_PROMPT_PARAMS, promptParams);
        if (hasText(mcpId)) {
            // 已配置动态身份时必须携带本轮凭据，不能静默省略后使用平台固定身份。
            if (!hasText(authorization) || !authorization.startsWith("Bearer mcp_jwt_")) {
                throw new IllegalArgumentException("百炼 MCP 动态鉴权凭据缺失或无效");
            }
            Map<String, Object> headers = new LinkedHashMap<String, Object>();
            headers.put(MCP_AUTHORIZATION_HEADER, authorization);
            headers.put(MCP_TRACE_CODE_HEADER, traceCode);
            Map<String, Object> mcpParams = new LinkedHashMap<String, Object>();
            mcpParams.put(mcpId, headers);
            bizParams.put(BAILIAN_AGENT_USER_DEFINED_PARAMS, mcpParams);
        }
        appendUserTokens(bizParams, userAuthToolIds, authorization);
        return bizParams;
    }

    /**
     * 按用户级自定义插件协议传入裸 JWT，任何配置错误均拒绝发送。
     * @param bizParams 本轮业务参数，仅用于百炼请求，不得持久化或输出到日志
     * @param toolIds 管理员配置的插件 ID，不能由模型选择或改写
     * @param authorization 本轮已验证身份的工具凭据，空工具列表无需凭据
     */
    public static void appendUserTokens(Map<String, Object> bizParams, List<String> toolIds, String authorization) {
        if (toolIds == null || toolIds.isEmpty()) {
            return;
        }
        if (toolIds.size() > 10 || !hasText(authorization) || !authorization.startsWith("Bearer mcp_jwt_")) {
            throw new IllegalArgumentException("百炼用户级工具鉴权配置无效");
        }
        Map<String, Object> tokens = new LinkedHashMap<>();
        for (String toolId : toolIds) {
            if (!hasText(toolId) || !toolId.equals(toolId.trim()) || tokens.containsKey(toolId)) {
                throw new IllegalArgumentException("百炼用户级工具 ID 不能为空、重复或包含首尾空白");
            }
            Map<String, String> token = new LinkedHashMap<>();
            // Type=bearer 会由百炼添加 Bearer，防止形成重复前缀。
            token.put(BAILIAN_AGENT_USER_TOKEN, authorization.substring("Bearer ".length()));
            tokens.put(toolId, token);
        }
        bizParams.put(BAILIAN_AGENT_USER_DEFINED_TOKENS, tokens);
    }

    /**
     * 判断配置字符串是否包含有效文本，避免引入额外依赖到纯参数组装器。
     *
     * @param value 待检查字符串
     * @return {@code true} 表示包含非空白字符
     */
    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 组装群聊工作流自定义变量。
     *
     * @param agentCode 用户指定的智能体编码，组队编排或未指定时为空字符串
     * @param pageContextJson 页面上下文 JSON 字符串，只能承载交互提示，不能作为授权依据
     * @param businessArtifactJson 后端按当前用户权限查询并裁剪后的业务事实 JSON
     * @param historySummary 服务端从本地消息生成的有限历史摘要
     * @param traceCode 服务端生成的全链路追踪码
     * @param availableAgents 服务端从数据库目录加载并校验后的可用智能体
     * @return 仅包含工作流已声明字段、且所有值均为字符串的变量 Map
     */
    public static Map<String, Object> build(String agentCode, String pageContextJson, String businessArtifactJson,
                                            String historySummary,
                                            String traceCode, List<? extends AiGroupMember> availableAgents) {
        Map<String, Object> bizParams = new LinkedHashMap<String, Object>();
        bizParams.put(BAILIAN_VAR_AGENT_CODE, agentCode == null ? "" : agentCode);
        bizParams.put(BAILIAN_VAR_PAGE_CONTEXT_JSON, pageContextJson);
        bizParams.put(BAILIAN_VAR_BUSINESS_ARTIFACT_JSON,
                businessArtifactJson == null ? "{}" : businessArtifactJson);
        bizParams.put(BAILIAN_VAR_HISTORY_SUMMARY, historySummary == null ? "" : historySummary);
        bizParams.put(BAILIAN_VAR_TRACE_CODE, traceCode);
        bizParams.put(BAILIAN_VAR_AVAILABLE_AGENTS_JSON, buildAvailableAgentsJson(availableAgents));
        return bizParams;
    }

    /**
     * 构造页面上下文 JSON，字段由后端固定，避免页面扩展权限或伪造业务事实。
     *
     * @param mode 对话模式，取值 single 或 group
     * @param mapEnabled 普通对话是否请求地图结果；群聊可为空
     * @return 合法 JSON 对象字符串
     */
    public static String buildPageContextJson(String mode, Boolean mapEnabled) {
        Map<String, Object> context = new LinkedHashMap<String, Object>();
        context.put("pageCode", "group".equals(mode) ? PAGE_CODE_GROUP_CHAT : PAGE_CODE_CHAT);
        context.put("mode", mode);
        if (mapEnabled != null) {
            context.put("mapEnabled", mapEnabled);
        }
        return AiEngineJson.toJsonString(context);
    }

    private static String buildAvailableAgentsJson(List<? extends AiGroupMember> availableAgents) {
        List<Map<String, String>> agents = new ArrayList<Map<String, String>>();
        for (AiGroupMember availableAgent : availableAgents) {
            Map<String, String> agent = new LinkedHashMap<String, String>();
            agent.put("code", availableAgent.getCode());
            agent.put("name", availableAgent.getName());
            agent.put("role", availableAgent.getRole());
            agents.add(agent);
        }
        return AiEngineJson.toJsonString(agents);
    }

    private BailianWorkflowBizParams() {
    }
}

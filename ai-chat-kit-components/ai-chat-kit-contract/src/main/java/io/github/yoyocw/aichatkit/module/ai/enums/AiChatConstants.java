package io.github.yoyocw.aichatkit.module.ai.enums;

/**
 * AI 对话业务常量，统一约束消息角色、生成状态与默认展示值。
 */
public final class AiChatConstants {

    /** 用户消息角色编码。 */
    public static final String ROLE_USER = "user";
    /** AI 助手消息角色编码。 */
    public static final String ROLE_ASSISTANT = "assistant";

    /** 消息正在生成。 */
    public static final int STATUS_GENERATING = 0;
    /** 消息生成完成。 */
    public static final int STATUS_COMPLETED = 1;
    /** 消息已被用户停止。 */
    public static final int STATUS_STOPPED = 2;
    /** 消息生成失败。 */
    public static final int STATUS_FAILED = 3;

    /** 尚未发送首条消息时的默认会话标题。 */
    public static final String DEFAULT_TITLE = "新对话";
    /** 自动标题允许保留的最大 Unicode 字符数。 */
    public static final int AUTO_TITLE_MAX_LENGTH = 18;

    /** 普通智能对话页面编码，仅用于辅助百炼理解当前交互场景。 */
    public static final String PAGE_CODE_CHAT = "smart-ai-chat";
    /** 多智能体群聊页面编码，仅用于辅助百炼理解当前交互场景。 */
    public static final String PAGE_CODE_GROUP_CHAT = "smart-ai-group-chat";

    /** 百炼工作流变量：用户指定的智能体编码。 */
    public static final String BAILIAN_VAR_AGENT_CODE = "agent_code";
    /** 百炼工作流变量：页面和地图上下文 JSON 字符串。 */
    public static final String BAILIAN_VAR_PAGE_CONTEXT_JSON = "page_context_json";
    /** 百炼工作流变量：后端已授权业务事实 JSON 字符串。 */
    public static final String BAILIAN_VAR_BUSINESS_ARTIFACT_JSON = "business_artifact_json";
    /** 百炼工作流变量：服务端生成的历史摘要。 */
    public static final String BAILIAN_VAR_HISTORY_SUMMARY = "history_summary";
    /** 百炼工作流变量：本次请求链路追踪码。 */
    public static final String BAILIAN_VAR_TRACE_CODE = "trace_code";
    /** 百炼工作流变量：服务端授权的智能体白名单 JSON 字符串。 */
    public static final String BAILIAN_VAR_AVAILABLE_AGENTS_JSON = "available_agents_json";
    /** 百炼 Agent 2.0 提示词自定义变量的固定参数容器。 */
    public static final String BAILIAN_AGENT_USER_PROMPT_PARAMS = "user_prompt_params";
    /** 百炼 Agent 2.0 向指定 MCP 服务透传请求级参数的字段名。 */
    public static final String BAILIAN_AGENT_USER_DEFINED_PARAMS = "user_defined_params";
    /** 旧版自定义工具用户级鉴权参数，不得放入提示词变量。 */
    public static final String BAILIAN_AGENT_USER_DEFINED_TOKENS = "user_defined_tokens";
    /** 旧版工具用户级鉴权的裸令牌字段，由百炼 bearer 类型添加请求头前缀。 */
    public static final String BAILIAN_AGENT_USER_TOKEN = "user_token";
    /** 百炼调用 MCP 服务时携带当前用户访问令牌的请求头名称。 */
    public static final String MCP_AUTHORIZATION_HEADER = "Authorization";
    /** 百炼调用 MCP 服务时携带服务端链路追踪码的请求头名称。 */
    public static final String MCP_TRACE_CODE_HEADER = "X-Trace-Code";

    private AiChatConstants() {
    }
}

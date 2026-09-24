package io.github.yoyocw.aichatkit.module.ai.enums;

import io.github.yoyocw.aichatkit.compat.framework.common.exception.ErrorCode;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiExecutionError;

/**
 * AI 智能对话模块错误码，覆盖会话归属、消息状态与百炼调用边界。
 */
public interface ErrorCodeConstants {

    ErrorCode CHAT_CONVERSATION_NOT_EXISTS = new ErrorCode(1_509_000_001, "AI 对话不存在");
    ErrorCode CHAT_MESSAGE_NOT_EXISTS = new ErrorCode(AiExecutionError.CHAT_MESSAGE_NOT_EXISTS.getCode(), AiExecutionError.CHAT_MESSAGE_NOT_EXISTS.getMsg());
    ErrorCode CHAT_MESSAGE_GENERATING = new ErrorCode(1_509_000_003, "当前对话正在生成回复，请稍候");
    ErrorCode CHAT_MESSAGE_NOT_GENERATING = new ErrorCode(AiExecutionError.CHAT_MESSAGE_NOT_GENERATING.getCode(), AiExecutionError.CHAT_MESSAGE_NOT_GENERATING.getMsg());
    ErrorCode BAILIAN_CONFIG_INVALID = new ErrorCode(AiExecutionError.BAILIAN_CONFIG_INVALID.getCode(), AiExecutionError.BAILIAN_CONFIG_INVALID.getMsg());
    ErrorCode BAILIAN_CALL_FAILED = new ErrorCode(AiExecutionError.BAILIAN_CALL_FAILED.getCode(), AiExecutionError.BAILIAN_CALL_FAILED.getMsg());
    ErrorCode GROUP_CONVERSATION_NOT_EXISTS = new ErrorCode(1_509_000_007, "AI 群聊不存在");
    ErrorCode GROUP_MEMBER_INVALID = new ErrorCode(1_509_000_008, "群聊包含不支持的智能体成员：{}");
    ErrorCode GROUP_MEMBER_COUNT_INVALID = new ErrorCode(1_509_000_009, "群聊成员数量必须为 2 至 3 个");
    ErrorCode GROUP_MESSAGE_GENERATING = new ErrorCode(1_509_000_010, "当前群聊正在生成回复，请稍候");
    ErrorCode GROUP_MESSAGE_NOT_EXISTS = new ErrorCode(AiExecutionError.GROUP_MESSAGE_NOT_EXISTS.getCode(), AiExecutionError.GROUP_MESSAGE_NOT_EXISTS.getMsg());
    ErrorCode GROUP_MESSAGE_NOT_GENERATING = new ErrorCode(AiExecutionError.GROUP_MESSAGE_NOT_GENERATING.getCode(), AiExecutionError.GROUP_MESSAGE_NOT_GENERATING.getMsg());
    ErrorCode GROUP_WORKFLOW_OUTPUT_INVALID = new ErrorCode(AiExecutionError.GROUP_WORKFLOW_OUTPUT_INVALID.getCode(), AiExecutionError.GROUP_WORKFLOW_OUTPUT_INVALID.getMsg());
    ErrorCode CHAT_SHARE_NOT_EXISTS = new ErrorCode(AiExecutionError.CHAT_SHARE_NOT_EXISTS.getCode(), AiExecutionError.CHAT_SHARE_NOT_EXISTS.getMsg());
    ErrorCode GROUP_SHARE_NOT_EXISTS = new ErrorCode(AiExecutionError.GROUP_SHARE_NOT_EXISTS.getCode(), AiExecutionError.GROUP_SHARE_NOT_EXISTS.getMsg());
    ErrorCode SERVICE_API_KEY_NOT_EXISTS = new ErrorCode(1_509_000_016, "MCP 第三方调用方不存在");
    ErrorCode SERVICE_API_KEY_CLIENT_CODE_EXISTS = new ErrorCode(1_509_000_017, "MCP 调用方编码已存在");
    ErrorCode SERVICE_API_KEY_DIGEST_CONFLICT = new ErrorCode(1_509_000_018, "MCP 密钥摘要冲突，请重新生成");
    ErrorCode SERVICE_API_KEY_USER_INVALID = new ErrorCode(1_509_000_019, "MCP 服务账号不存在、已停用或不属于当前租户");
    ErrorCode SERVICE_API_KEY_DATA_SCOPE_INVALID = new ErrorCode(1_509_000_020, "MCP 服务账号必须配置非空且非全部的数据范围");
    ErrorCode SERVICE_API_KEY_EXPIRE_TIME_INVALID = new ErrorCode(1_509_000_021, "MCP 调用方失效时间必须晚于当前时间");
    ErrorCode SHARE_VALID_DAYS_INVALID = new ErrorCode(AiExecutionError.SHARE_VALID_DAYS_INVALID.getCode(), AiExecutionError.SHARE_VALID_DAYS_INVALID.getMsg());
    ErrorCode SHARE_CODE_GENERATE_FAILED = new ErrorCode(AiExecutionError.SHARE_CODE_GENERATE_FAILED.getCode(), AiExecutionError.SHARE_CODE_GENERATE_FAILED.getMsg());
    /** 账户欠费、免费额度耗尽等需管理员处理的计费限制。 */
    ErrorCode BAILIAN_QUOTA_UNAVAILABLE = new ErrorCode(AiExecutionError.BAILIAN_QUOTA_UNAVAILABLE.getCode(), AiExecutionError.BAILIAN_QUOTA_UNAVAILABLE.getMsg());
    /** 平台请求频率或 Token 速率达到上限，可由用户稍后重试。 */
    ErrorCode BAILIAN_RATE_LIMITED = new ErrorCode(AiExecutionError.BAILIAN_RATE_LIMITED.getCode(), AiExecutionError.BAILIAN_RATE_LIMITED.getMsg());
    /** 建连、读取或整次请求超过规定时间。 */
    ErrorCode BAILIAN_TIMEOUT = new ErrorCode(AiExecutionError.BAILIAN_TIMEOUT.getCode(), AiExecutionError.BAILIAN_TIMEOUT.getMsg());
    /** 未取得正常结束标记、响应中断或响应格式损坏，已有片段不能视为成功。 */
    ErrorCode BAILIAN_RESPONSE_INCOMPLETE = new ErrorCode(AiExecutionError.BAILIAN_RESPONSE_INCOMPLETE.getCode(), AiExecutionError.BAILIAN_RESPONSE_INCOMPLETE.getMsg());
    /** 会话过期仅供服务层清理会话并重试一次，禁止带入上游错误原文。 */
    String BAILIAN_SESSION_EXPIRED_MESSAGE = "AI 会话已失效，请重新发起对话";
}

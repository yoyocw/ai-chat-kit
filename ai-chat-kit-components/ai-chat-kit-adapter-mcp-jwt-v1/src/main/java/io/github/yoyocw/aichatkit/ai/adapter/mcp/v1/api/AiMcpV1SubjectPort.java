package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.api;

import io.github.yoyocw.aichatkit.mcp.jwt.McpJwtV1Identity;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiHostSession;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;

/** 宿主真实会话与历史数值身份的双向权威映射；不得解析、散列或伪造不透明标识。 */
public interface AiMcpV1SubjectPort {
    /**
     * 查询真实映射，仅使用返回值的 serviceUserId/accessTokenId/tenantId，不授予其中其他权限。
     * @param context 已认证宿主上下文
     * @param session 已由真实会话源核验且与上下文一致的会话
     * @return 真实的数值用户、会话、租户编号；不得返回另一个有效会话
     * @throws IllegalStateException 无映射、映射歧义、归属不符或真实源不可用
     */
    McpJwtV1Identity mapSession(AiInvocationContext context, AiHostSession session);

    /**
     * 将已经验签的数值三元组解析为原始真实宿主会话；调用方随后必须再次执行会话源检查及反向映射。
     * @param verifiedProof 已通过固定公钥、受众、用途和有效期校验的证明副本
     * @return 原会话的 namespace/tenant/actor/session 关联快照，不构成完整业务授权
     * @throws IllegalStateException 无映射、映射歧义或源不可用；不得创建替代会话
     */
    AiHostSession resolveSession(McpJwtV1Identity verifiedProof);
}

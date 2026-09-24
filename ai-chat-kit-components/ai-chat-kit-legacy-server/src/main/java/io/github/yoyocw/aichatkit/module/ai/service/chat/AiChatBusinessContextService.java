package io.github.yoyocw.aichatkit.module.ai.service.chat;

import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.identity.AiInvocationContext;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessContextRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;


/**
 * 单聊业务上下文服务，负责准备百炼调用前所需的已授权地图事实和 MCP 用户凭据。
 */
@Service
@RequiredArgsConstructor
public class AiChatBusinessContextService implements io.github.yoyocw.aichatkit.module.ai.contract.context.AiSingleChatBusinessPort {

    /** 宿主业务上下文端口，适配层继续验证身份和数据权限。 */
    private final AiBusinessContextPort businessContextPort;
    /** 当前入口的用户委托适配，私钥由认证侧管理。 */
    private final AiToolCredentialService toolCredentialService;

    /**
     * 查询当前用户有权查看的地图任务；未开启地图能力时不发起 RPC。
     *
     * @param mapEnabled 是否开启地图业务结果
     * @param question 当前用户问题正文
     * @param context 与授权共用的宿主身份快照
     * @return 本轮事实及可选展示快照；未开启时为 NOT_REQUESTED
     */
    public AiBusinessSnapshot prepareBusinessContext(boolean mapEnabled, String question, AiInvocationContext context) {
        return businessContextPort.prepare(new AiBusinessContextRequest(context, question,
                mapEnabled ? Collections.singleton("platform.map") : Collections.<String>emptySet()));
    }

    /**
     * 向认证侧获取本轮用户委托，业务服务按实际部门和角色计算数据权限。
     *
     * @param config 本轮应用与工具配置快照
     * @param context 宿主入口已验证的身份快照
     * @return MCP Authorization 请求头；未配置百炼 MCP 时返回 {@code null}
     * @throws IllegalStateException 已启用 MCP 但登录身份、端点范围或 RSA 私钥配置无效时抛出
     */
    public String getMcpAuthorization(AiApplicationConfig config, AiInvocationContext context) {
        return toolCredentialService.obtainAuthorization(config, context);
    }
}

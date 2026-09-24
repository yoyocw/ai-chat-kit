package io.github.yoyocw.aichatkit.ai.host.ruoyi.authentication;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 服务端固定操作映射，不依赖 Starter 类型，不允许请求注册任意权限。 */
public final class RuoyiHostPermissions {
    /** 首批普通单群聊的固定门面操作；工具及密钥管理不在本批支持范围。 */
    private static final Set<String> ACTIONS = new HashSet<>(Arrays.asList(
            "ai:chat:createConversation", "ai:chat:send", "ai:chat:stop", "ai:chat:get", "ai:chat:list",
            "ai:chat:updateConversation", "ai:chat:pinConversation", "ai:chat:delete",
            "ai:chat:shareConversation", "ai:chat:cancelConversationShare", "ai:group-chat:create",
            "ai:group-chat:send", "ai:group-chat:stop", "aigroup:chat:agent:list",
            "aigroup:chat:conversation:list", "aigroup:chat:message:list", "aigroup:chat:conversation:update",
            "aigroup:chat:conversation:pin", "aigroup:chat:conversation:delete",
            "aigroup:chat:conversation:updateMembers", "aigroup:chat:conversation:share",
            "aigroup:chat:conversation:cancelShare"));
    /** 部署映射快照，运行时不读取可变配置对象。 */
    private final Map<String, String> mappings = new LinkedHashMap<>();

    /** @param source 已部署若依菜单权限映射；空配置、未知操作及通配目标均拒绝 */
    public RuoyiHostPermissions(Map<String, String> source) {
        if (source == null || source.isEmpty() || source.size() > ACTIONS.size()) { throw invalid(); }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (!ACTIONS.contains(entry.getKey()) || entry.getValue() == null
                    || !entry.getValue().matches("[A-Za-z0-9._:-]{1,128}")) { throw invalid(); }
            mappings.put(entry.getKey(), entry.getValue());
        }
    }

    /** @param action 门面或无工具应用策略固定操作码 @return 已部署精确权限码，缺失明确拒绝 */
    public String resolve(String action) {
        String result = mappings.get(action);
        if (result == null) { throw invalid(); }
        return result;
    }

    /** @return 固定映射缺失属于配置错误，不伪装用户权限拒绝 */
    private static AiIdentityException invalid() { return new AiIdentityException(AiIdentityError.CONFIGURATION); }
}

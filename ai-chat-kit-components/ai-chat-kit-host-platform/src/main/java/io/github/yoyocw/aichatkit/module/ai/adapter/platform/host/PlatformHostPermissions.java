package io.github.yoyocw.aichatkit.module.ai.adapter.platform.host;

import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityError;
import io.github.yoyocw.aichatkit.module.ai.contract.error.AiIdentityException;

/** 固定映射到现有平台 Controller 权限；不从 YAML 或请求注册新的权限。 */
final class PlatformHostPermissions {
    /** 工具类不创建实例。 */
    private PlatformHostPermissions() { }

    /**
     * @param permission 中立门面操作码或已存在的平台操作码
     * @return 认证消费者仍须明确获准查询的平台权限码
     * @throws IllegalStateException 未知操作或没有既有权限映射
     */
    static String resolve(String permission) {
        if (permission == null) { throw new AiIdentityException(AiIdentityError.CONFIGURATION); }
        switch (permission) {
            case "ai:chat:send": return "ai:chat:seed";
            case "ai:chat:stop": return "ai:chat:stopSeed";
            case "ai:mcp:invoke":
            case "ai:signing-key:manage": return permission;
            case "ai:group-chat:create": return "aigroup:chat:conversation:create";
            case "ai:group-chat:send": return "aigroup:chat:message:send";
            case "ai:group-chat:stop": return "aigroup:chat:message:stop";
            case "ai:chat:seed":
            case "ai:chat:stopSeed":
            case "ai:chat:createConversation":
            case "ai:chat:updateConversation":
            case "ai:chat:pinConversation":
            case "ai:chat:shareConversation":
            case "ai:chat:cancelConversationShare":
            case "ai:chat:delete":
            case "ai:chat:get":
            case "ai:chat:list":
            case "aigroup:chat:conversation:create":
            case "aigroup:chat:agent:list":
            case "aigroup:chat:conversation:update":
            case "aigroup:chat:conversation:pin":
            case "aigroup:chat:conversation:share":
            case "aigroup:chat:conversation:cancelShare":
            case "aigroup:chat:conversation:updateMembers":
            case "aigroup:chat:conversation:delete":
            case "aigroup:chat:conversation:list":
            case "aigroup:chat:message:list":
            case "aigroup:chat:message:send":
            case "aigroup:chat:message:stop": return permission;
            default: throw new AiIdentityException(AiIdentityError.CONFIGURATION);
        }
    }
}

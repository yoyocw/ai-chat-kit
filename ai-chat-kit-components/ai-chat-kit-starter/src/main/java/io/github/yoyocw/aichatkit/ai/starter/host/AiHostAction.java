package io.github.yoyocw.aichatkit.ai.starter.host;

/** 由服务端入口固定选择的操作；禁止把客户端参数直接转换为操作以决定授权强度。 */
public enum AiHostAction {
    /** 独立创建空单聊会话，不复用发送权限。 */
    CHAT_CREATE("ai:chat:createConversation", false),
    /** 查看真实可用成员目录，不复用群聊发送或创建权限。 */
    GROUP_AGENT_LIST("aigroup:chat:agent:list", false),
    /** 仅发起单聊，仍须检查会话及应用归属；群聊必须定义独立操作和权限编码，不能复用。 */
    CHAT_SEND("ai:chat:send", false),
    /** 仅停止单聊，仍须检查消息来源及当前生成状态；不授予群聊停止权限。 */
    CHAT_STOP("ai:chat:stop", false),
    /** 创建群聊，独立检查成员目录和成员可用性，不能由单聊发送权限替代。 */
    GROUP_CHAT_CREATE("ai:group-chat:create", false),
    /** 群聊发送，不能复用单聊发送权限。 */
    GROUP_CHAT_SEND("ai:group-chat:send", false),
    /** 群聊停止，不能复用单聊停止权限。 */
    GROUP_CHAT_STOP("ai:group-chat:stop", false),
    /** 单聊会话列表，不包含读取消息历史的权限。 */
    CHAT_CONVERSATION_LIST("ai:chat:get", false),
    /** 单聊消息历史，不使用发送权限替代。 */
    CHAT_MESSAGE_LIST("ai:chat:list", false),
    /** 单聊会话改名，仍需核对真实归属。 */
    CHAT_RENAME("ai:chat:updateConversation", false),
    /** 单聊会话置顶或取消置顶。 */
    CHAT_PIN("ai:chat:pinConversation", false),
    /** 删除单聊会话，提交后才取消对应生成任务。 */
    CHAT_DELETE("ai:chat:delete", false),
    /** 群聊会话列表，独立于单聊读取权限。 */
    GROUP_CONVERSATION_LIST("aigroup:chat:conversation:list", false),
    /** 群聊消息历史，独立于会话列表和发送权限。 */
    GROUP_MESSAGE_LIST("aigroup:chat:message:list", false),
    /** 群聊会话改名。 */
    GROUP_RENAME("aigroup:chat:conversation:update", false),
    /** 群聊会话置顶或取消置顶。 */
    GROUP_PIN("aigroup:chat:conversation:pin", false),
    /** 删除群聊会话，提交后才取消对应工作流。 */
    GROUP_DELETE("aigroup:chat:conversation:delete", false),
    /** 更新群聊成员；生成中必须拒绝，成功后清除旧模型会话。 */
    GROUP_MEMBERS_UPDATE("aigroup:chat:conversation:updateMembers", false),
    /** 创建单聊限时公开分享，不能由消息读取权限替代。 */
    CHAT_SHARE_CREATE("ai:chat:shareConversation", false),
    /** 撤销单聊分享，需要核对会话属于当前用户。 */
    CHAT_SHARE_REVOKE("ai:chat:cancelConversationShare", false),
    /** 创建群聊限时公开分享，独立于单聊分享权限。 */
    GROUP_SHARE_CREATE("aigroup:chat:conversation:share", false),
    /** 撤销群聊分享，独立于创建或读取权限。 */
    GROUP_SHARE_REVOKE("aigroup:chat:conversation:cancelShare", false),
    /** 调用工具的基础权限，不能替代具体工具及数据范围授权。 */
    MCP_INVOKE("ai:mcp:invoke", false),
    /** 管理签发密钥，同时要求宿主明确确认平台管理身份。 */
    SIGNING_KEY_MANAGE("ai:signing-key:manage", true);

    /** 宿主权限适配器负责映射到原生权限体系的稳定操作编码。 */
    private final String permission;
    /** 是否额外要求平台管理身份；不以固定角色或租户编号推断。 */
    private final boolean platformAdministratorRequired;

    AiHostAction(String permission, boolean platformAdministratorRequired) {
        this.permission = permission;
        this.platformAdministratorRequired = platformAdministratorRequired;
    }

    /** @return 服务端固定权限编码 */
    public String getPermission() { return permission; }

    /** @return 是否必须额外核验平台管理身份 */
    public boolean isPlatformAdministratorRequired() { return platformAdministratorRequired; }
}

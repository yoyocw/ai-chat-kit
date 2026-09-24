package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.module.ai.contract.share.AiSharedConversation;

/** HTTP 展示视图，时间使用Unix毫秒；不直接序列化引擎内部模型。 */
public final class AiWebSharedConversationResponse {
    /** 公开标题。 */
    private final String title;
    /** 群聊成员编码。 */
    private final java.util.List<String> memberCodes;
    /** 真实公开成员快照。 */
    private final java.util.List<io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot> members;
    /** 已完成的公开消息。 */
    private final java.util.List<AiWebSharedMessageResponse> messages;
    /** @param value 已经完成授权的引擎视图 */
    public AiWebSharedConversationResponse(AiSharedConversation value) {
        this.title = value.getTitle();
        this.memberCodes = value.getMemberCodes();
        this.members = value.getMembers();
        this.messages = value.getMessages().stream().map(AiWebSharedMessageResponse::new).collect(java.util.stream.Collectors.toList());
    }
    /** @return 公开标题 */
    public String getTitle() { return title; }
    /** @return 群聊成员编码 */
    public java.util.List<String> getMemberCodes() { return memberCodes; }
    /** @return 真实公开成员快照 */
    public java.util.List<io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot> getMembers() { return members; }
    /** @return 已完成的公开消息 */
    public java.util.List<AiWebSharedMessageResponse> getMessages() { return messages; }
}


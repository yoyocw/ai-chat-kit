package io.github.yoyocw.aichatkit.ai.adapter.web;

import io.github.yoyocw.aichatkit.module.ai.contract.storage.AiConversationView;

/** HTTP 展示视图，时间使用Unix毫秒；不直接序列化引擎内部模型。 */
public final class AiWebConversationResponse {
    /** 会话编号。 */
    private final Long id;
    /** 会话标题。 */
    private final String title;
    /** 是否置顶。 */
    private final boolean pinned;
    /** 置顶Unix毫秒或空。 */
    private final Long pinnedTime;
    /** 创建Unix毫秒，历史缺失保持空。 */
    private final Long createTime;
    /** 更新Unix毫秒。 */
    private final long updateTime;
    /** 有序成员编码。 */
    private final java.util.List<String> memberCodes;
    /** 真实成员快照，缺失保持空。 */
    private final java.util.List<io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot> members;
    /** 成员快照完整性。 */
    private final io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMemberSnapshotStatus memberSnapshotStatus;
    /** @param value 已经完成授权的引擎视图 */
    public AiWebConversationResponse(AiConversationView value) {
        this.id = value.getId();
        this.title = value.getTitle();
        this.pinned = value.isPinned();
        this.pinnedTime = value.getPinnedTimeMillis();
        this.createTime = value.getCreatedAtMillis();
        this.updateTime = value.getUpdateTimeMillis();
        this.memberCodes = value.getMemberCodes();
        this.members = value.getMembers();
        this.memberSnapshotStatus = value.getMemberSnapshotStatus();
    }
    /** @return 会话编号 */
    public Long getId() { return id; }
    /** @return 会话标题 */
    public String getTitle() { return title; }
    /** @return 是否置顶 */
    public boolean getPinned() { return pinned; }
    /** @return 置顶Unix毫秒或空 */
    public Long getPinnedTime() { return pinnedTime; }
    /** @return 创建Unix毫秒，历史缺失保持空 */
    public Long getCreateTime() { return createTime; }
    /** @return 更新Unix毫秒 */
    public long getUpdateTime() { return updateTime; }
    /** @return 有序成员编码 */
    public java.util.List<String> getMemberCodes() { return memberCodes; }
    /** @return 真实成员快照，缺失保持空 */
    public java.util.List<io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot> getMembers() { return members; }
    /** @return 成员快照完整性 */
    public io.github.yoyocw.aichatkit.module.ai.contract.storage.AiMemberSnapshotStatus getMemberSnapshotStatus() { return memberSnapshotStatus; }
}


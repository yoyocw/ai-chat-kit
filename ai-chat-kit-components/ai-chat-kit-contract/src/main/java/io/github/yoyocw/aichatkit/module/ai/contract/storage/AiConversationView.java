package io.github.yoyocw.aichatkit.module.ai.contract.storage;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiGroupMemberSnapshot;

/** 已按归属过滤的不可变读取视图，不含宿主身份、模型会话或认证凭据。 */
public final class AiConversationView {
    /** AI自有会话编号。 */
    private final Long id;
    /** 用户可见会话标题。 */
    private final String title;
    /** 是否置顶。 */
    private final Boolean pinned;
    /** 最近置顶UTC Unix毫秒时刻，未置顶为空。 */
    private final Long pinnedTimeMillis;
    /** 最近更新UTC Unix毫秒时刻。 */
    private final Long updateTimeMillis;
    /** 群聊有序成员编码，单聊为空；历史目录停用不影响列表读取。 */
    private final List<String> memberCodes;
    /** 真实会话创建 UTC 毫秒；旧适配未提供时为 null，不以更新时间伪造。 */
    private final Long createdAtMillis;
    /** 有序成员名称/职责；COMPLETE 为保存快照，CURRENT_DIRECTORY 为本次真实目录投影。 */
    private final List<AiGroupMemberSnapshot> members;
    /** 快照完整性；待补全时 members 为 null，不能解释为完整空成员列表。 */
    private final AiMemberSnapshotStatus memberSnapshotStatus;

    /** 是否携带宿主原始值，区分未提供与真实 SQL NULL。 */
    private final boolean sourceValuesPresent;
    /** 宿主原始创建时间，保留纳秒精度和真实空值。 */
    private final LocalDateTime sourceCreateTime;
    /** 宿主原始更新时间，保留纳秒精度和真实空值。 */
    private final LocalDateTime sourceUpdateTime;
    /** 宿主原始置顶时间，未置顶可为空。 */
    private final LocalDateTime sourcePinnedTime;
    /** 复制已授权查询结果；正文对象不得整体写入日志。 */
    public AiConversationView(Long id, String title, boolean pinned, Long pinnedTimeMillis, long updateTimeMillis, List<String> memberCodes) {
        this(id, title, pinned, pinnedTimeMillis, updateTimeMillis, memberCodes, null, Collections.emptyList());
    }

    /** 保存已授权读取的真实创建时间和成员快照，保留旧成员编码接口。 */
    public AiConversationView(Long id, String title, boolean pinned, Long pinnedTimeMillis, long updateTimeMillis,
            List<String> memberCodes, Long createdAtMillis, List<AiGroupMemberSnapshot> members) {
        this(id, title, pinned, pinnedTimeMillis, updateTimeMillis, memberCodes, createdAtMillis, members,
                memberCodes.isEmpty() ? AiMemberSnapshotStatus.NOT_APPLICABLE
                        : members.size() == memberCodes.size() ? AiMemberSnapshotStatus.COMPLETE : AiMemberSnapshotStatus.INCOMPLETE);
    }

    /** 保存逐会话快照状态；待补全会话仍可展示元信息并通过既有授权成员修改恢复。 */
    public AiConversationView(Long id, String title, boolean pinned, Long pinnedTimeMillis, long updateTimeMillis,
            List<String> memberCodes, Long createdAtMillis, List<AiGroupMemberSnapshot> members,
            AiMemberSnapshotStatus memberSnapshotStatus) {
        this(id, title, pinned, pinnedTimeMillis, updateTimeMillis, memberCodes, createdAtMillis, members,
                memberSnapshotStatus, false, null, null, null);
    }

    /** 内部统一构造；原构造保证 primitive 字段非空，源值构造允许真实 SQL NULL。 */
    private AiConversationView(Long id, String title, Boolean pinned, Long pinnedTimeMillis, Long updateTimeMillis,
            List<String> memberCodes, Long createdAtMillis, List<AiGroupMemberSnapshot> members,
            AiMemberSnapshotStatus memberSnapshotStatus, boolean sourceValuesPresent,
            LocalDateTime sourceCreateTime, LocalDateTime sourceUpdateTime, LocalDateTime sourcePinnedTime) {
        if (memberSnapshotStatus == null) { throw new IllegalArgumentException("成员快照状态不能为空"); }
        this.sourceValuesPresent = sourceValuesPresent;
        this.sourceCreateTime = sourceCreateTime;
        this.sourceUpdateTime = sourceUpdateTime;
        this.sourcePinnedTime = sourcePinnedTime;
        this.id = id;
        this.title = title;
        this.pinned = pinned;
        this.pinnedTimeMillis = pinnedTimeMillis;
        this.updateTimeMillis = updateTimeMillis;
        this.memberCodes = Collections.unmodifiableList(new ArrayList<String>(memberCodes));
        this.createdAtMillis = createdAtMillis;
        this.memberSnapshotStatus = memberSnapshotStatus;
        this.members = memberSnapshotStatus == AiMemberSnapshotStatus.INCOMPLETE ? null
                : Collections.unmodifiableList(new ArrayList<AiGroupMemberSnapshot>(members));
    }
    /** @return AI自有会话编号 */
    public Long getId() { return id; }
    /** @return 用户可见会话标题 */
    public String getTitle() { return title; }
    /** @return 是否置顶 */
    public boolean isPinned() { if (pinned == null) { throw new IllegalStateException("源置顶状态为空"); } return pinned; }
    /** @return 最近置顶UTC Unix毫秒时刻，未置顶为空 */
    public Long getPinnedTimeMillis() { return pinnedTimeMillis; }
    /** @return 最近更新UTC Unix毫秒时刻 */
    public long getUpdateTimeMillis() { if (updateTimeMillis == null) { throw new IllegalStateException("源更新时间为空"); } return updateTimeMillis; }
    /** @return 群聊有序成员编码，单聊为空；历史目录停用不影响列表读取 */
    public List<String> getMemberCodes() { return memberCodes; }
    /** @return 实际创建UTC毫秒；未升级的旧适配可为null */
    public Long getCreatedAtMillis() { return createdAtMillis; }
    /** @return 完整的真实快照；INCOMPLETE 返回 null，调用者应展示待补全状态 */
    public List<AiGroupMemberSnapshot> getMembers() { return members; }
    /** @return 当前会话的快照状态，不因其他会话历史缺字段而失败 */
    public AiMemberSnapshotStatus getMemberSnapshotStatus() { return memberSnapshotStatus; }
    /** 创建宿主读取投影；空时间和置顶值保持为空，不以当前时刻或默认值补齐。 */
    public static AiConversationView fromSource(Long id, String title, Boolean pinned,
            LocalDateTime pinnedTime, LocalDateTime updateTime, List<String> codes,
            LocalDateTime createTime, List<AiGroupMemberSnapshot> members, AiMemberSnapshotStatus status) {
        return new AiConversationView(id, title, pinned, millis(pinnedTime), millis(updateTime), codes,
                millis(createTime), members, status, true, createTime, updateTime, pinnedTime);
    }
    /** @return 是否确实携带宿主原始值 */
    public boolean hasSourceValues() { return sourceValuesPresent; }
    /** @return 原始可空置顶值，仅 hasSourceValues 为 true 时作为旧接口事实 */
    public Boolean getSourcePinned() { return pinned; }
    /** @return 原始可空创建时间 */
    public LocalDateTime getSourceCreateTime() { return sourceCreateTime; }
    /** @return 原始可空更新时间 */
    public LocalDateTime getSourceUpdateTime() { return sourceUpdateTime; }
    /** @return 原始可空置顶时间 */
    public LocalDateTime getSourcePinnedTime() { return sourcePinnedTime; }
    /** 非空时间才提供兼容毫秒视图，旧接口直接读取原始时间。 */
    private static Long millis(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
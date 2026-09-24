package io.github.yoyocw.aichatkit.module.ai.contract.storage;

/** 会话列表的成员快照可用状态，不代表调用者权限或当前目录启用状态。 */
public enum AiMemberSnapshotStatus {
    /** 单聊不使用群聊成员快照。 */
    NOT_APPLICABLE,
    /** 全部有序成员均有真实且完整的保存快照。 */
    COMPLETE,
    /** 保存快照缺失或无效；保留会话编号和成员编码供授权恢复。 */
    INCOMPLETE,
    /** 本次从宿主目录读取的成员信息，会随目录更新，不代表保存时的历史快照。 */
    CURRENT_DIRECTORY
}

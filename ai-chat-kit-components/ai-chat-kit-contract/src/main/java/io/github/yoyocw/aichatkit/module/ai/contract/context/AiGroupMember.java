package io.github.yoyocw.aichatkit.module.ai.contract.context;

/** 已由宿主验证的群聊候选成员视图，不依赖页面VO或模型SDK。 */
public interface AiGroupMember {
    /** @return 工作流识别的稳定成员编码 */
    String getCode();
    /** @return 展示名称 */
    String getName();
    /** @return 当前成员职责说明 */
    String getRole();
}

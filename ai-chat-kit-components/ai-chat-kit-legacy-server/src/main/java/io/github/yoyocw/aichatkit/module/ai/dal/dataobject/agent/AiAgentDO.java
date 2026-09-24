package io.github.yoyocw.aichatkit.module.ai.dal.dataobject.agent;

import io.github.yoyocw.aichatkit.compat.framework.common.enums.CommonStatusEnum;
import io.github.yoyocw.aichatkit.compat.framework.mybatis.core.dataobject.BaseDO;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 智能体目录持久化对象，维护本地业务编码与百炼已发布应用的映射关系。
 */
@TableName("ai_agent")
@KeySequence("ai_agent_seq")
@TenantIgnore
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AiAgentDO extends BaseDO {

    /** 智能体目录主键编号。 */
    @TableId
    private Long id;
    /** 服务端与百炼群聊工作流共同识别的稳定业务编码。 */
    private String code;
    /** 页面和历史群聊展示的智能体名称。 */
    private String name;
    /** 智能体在协同群聊中的职责说明。 */
    private String role;
    /** 百炼应用管理中已发布智能体的 APP ID。 */
    private String bailianAppId;
    /** 启停状态，枚举 {@link CommonStatusEnum}。 */
    private Integer status;
    /** 候选成员列表展示顺序，数值越小越靠前。 */
    private Integer sort;
}

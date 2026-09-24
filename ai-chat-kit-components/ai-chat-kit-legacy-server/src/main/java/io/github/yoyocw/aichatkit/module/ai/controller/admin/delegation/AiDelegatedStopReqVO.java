package io.github.yoyocw.aichatkit.module.ai.controller.admin.delegation;

import lombok.Getter;
import lombok.Setter;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/** 停止请求只指定助手消息；操作类型由固定路径决定，身份不从正文读取。 */
@Getter
@Setter
public class AiDelegatedStopReqVO {
    /** 正数助手消息编号，必须与一次性票据绑定一致。 */
    @NotNull
    @Min(1)
    private Long messageId;
}

package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "RPC 服务 - 补喷任务列表 Response DTO")
@Data
public class DashboardReflyTaskListRespDTO {

    @Schema(description = "任务ID")
    private Long missionId;

    @Schema(description = "任务名称")
    private String missionName;

    @Schema(description = "分场/林场名称")
    private String farmName;

    @Schema(description = "防治类型（来自任务表 prevention_type）")
    private String preventionType;

    @Schema(description = "计划作业日期段（来自补喷表）")
    private String planJobDateRange;
}

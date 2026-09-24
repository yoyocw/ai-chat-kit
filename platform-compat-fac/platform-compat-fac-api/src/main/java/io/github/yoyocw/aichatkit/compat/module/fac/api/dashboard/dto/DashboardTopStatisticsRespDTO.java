package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "RPC 服务 - 数据看板顶部统计卡片 Response DTO")
@Data
public class DashboardTopStatisticsRespDTO {

    @Schema(description = "任务总数")
    private Long totalCount;
    @Schema(description = "总作业面积（亩）")
    private BigDecimal totalJobArea;

    @Schema(description = "待作业任务数")
    private Long pendingJobCount;
    @Schema(description = "待作业任务面积（亩）")
    private BigDecimal pendingJobArea;

    @Schema(description = "作业中任务数")
    private Long inOperationCount;
    @Schema(description = "作业中任务面积（亩）")
    private BigDecimal inOperationArea;

    @Schema(description = "待验收任务数")
    private Long pendingAcceptanceCount;
    @Schema(description = "待验收任务面积（亩）")
    private BigDecimal pendingAcceptanceArea;

    @Schema(description = "已验收任务数")
    private Long acceptedCount;
    @Schema(description = "已验收任务面积（亩）")
    private BigDecimal acceptedArea;

    @Schema(description = "补防任务数")
    private Long reflyCount;
    @Schema(description = "补防任务面积（亩）")
    private BigDecimal reflyArea;

    @Schema(description = "验收失败任务数")
    private Long acceptanceFailedCount;
    @Schema(description = "验收失败任务面积（亩）")
    private BigDecimal acceptanceFailedArea;
}

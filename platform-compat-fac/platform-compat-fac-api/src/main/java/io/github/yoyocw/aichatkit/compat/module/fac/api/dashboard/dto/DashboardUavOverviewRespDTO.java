package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "RPC 服务 - 无人机概览 Response DTO")
@Data
public class DashboardUavOverviewRespDTO {

    @Schema(description = "总设备数")
    private Long totalCount;

    @Schema(description = "可用/空闲数")
    private Long availableCount;

    @Schema(description = "作业中数")
    private Long inOperationCount;
}

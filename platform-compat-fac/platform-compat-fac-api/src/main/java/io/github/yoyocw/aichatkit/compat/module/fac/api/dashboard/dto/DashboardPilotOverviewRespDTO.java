package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "RPC 服务 - 飞手概览 Response DTO")
@Data
public class DashboardPilotOverviewRespDTO {

    @Schema(description = "飞手总数")
    private Long totalCount;

    @Schema(description = "活跃/在线数")
    private Long activeCount;

    @Schema(description = "待命中/空闲数")
    private Long standbyCount;
}

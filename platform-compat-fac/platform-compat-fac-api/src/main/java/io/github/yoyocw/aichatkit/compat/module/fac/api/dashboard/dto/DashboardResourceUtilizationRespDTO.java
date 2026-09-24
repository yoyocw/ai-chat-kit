package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "RPC 服务 - 资源利用率 Response DTO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResourceUtilizationRespDTO {

    @Schema(description = "其他", example = "0")
    private BigDecimal other;

    @Schema(description = "车辆出勤率", example = "0")
    private BigDecimal vehicleRate;

    @Schema(description = "药剂使用率（库存总和/入库数量总和）", example = "132")
    private BigDecimal pesticideRate;

    @Schema(description = "飞手利用率（作业中飞手/已审核飞手总数）", example = "286")
    private BigDecimal pilotRate;

    @Schema(description = "无人机利用率（作业中/总数）", example = "425")
    private BigDecimal uavRate;
}

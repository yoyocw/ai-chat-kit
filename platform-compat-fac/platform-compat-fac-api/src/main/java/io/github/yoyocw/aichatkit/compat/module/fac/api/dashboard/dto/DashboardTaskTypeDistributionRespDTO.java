package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "RPC 服务 - 任务类型分布(饼图) Response DTO")
@Data
public class DashboardTaskTypeDistributionRespDTO {

    @Schema(description = "分布项列表")
    private List<DistributionItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistributionItem {
        @Schema(description = "类型名称（防治类型）")
        private String name;
        @Schema(description = "任务数量")
        private Long value;
        @Schema(description = "占比百分比")
        private BigDecimal percentage;
        @Schema(description = "作业面积")
        private BigDecimal area;
    }
}

package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "RPC 服务 - Top3飞手 Response DTO")
@Data
public class DashboardTop3PilotRespDTO {

    @Schema(description = "Top3飞手列表")
    private List<PilotItem> pilots;

    @Schema(description = "飞手信息项")
    @Data
    public static class PilotItem {
        @Schema(description = "飞手ID")
        private Long pilotId;

        @Schema(description = "飞手编号")
        private String pilotCode;

        @Schema(description = "飞手姓名")
        private String pilotName;

        @Schema(description = "作业数量")
        private Long missionCount;

        @Schema(description = "作业面积")
        private BigDecimal jobArea;
    }
}

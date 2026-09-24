package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "RPC 服务 - 药剂消耗分析 Response DTO")
@Data
public class DashboardPesticideConsumptionRespDTO {

    @Schema(description = "消耗明细列表")
    private List<ConsumptionItem> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsumptionItem {
        @Schema(description = "药剂名称")
        private String name;
        @Schema(description = "药剂类型")
        private String type;
        @Schema(description = "使用数量")
        private BigDecimal used;
        @Schema(description = "剩余库存")
        private Long remainingInventory;
    }
}

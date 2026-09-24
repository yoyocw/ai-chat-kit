package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "RPC 服务 - 药剂库存 Response DTO")
@Data
public class DashboardPesticideInventoryRespDTO {

    @Schema(description = "药剂种类总数")
    private Long totalCount;

    @Schema(description = "在库种类数")
    private Long inStockCategoryCount;

    @Schema(description = "总库存量（所有药剂汇总）")
    private Long totalQuantity;

    @Schema(description = "各药剂明细列表")
    private List<PesticideItem> details;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PesticideItem {
        @Schema(description = "药剂名称")
        private String name;
        @Schema(description = "药剂类型")
        private String type;
        @Schema(description = "库存量")
        private Long quantity;
    }
}

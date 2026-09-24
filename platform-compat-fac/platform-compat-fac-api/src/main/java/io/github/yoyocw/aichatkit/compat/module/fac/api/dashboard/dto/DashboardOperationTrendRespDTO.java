package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "RPC 服务 - 作业趋势分析 Response DTO")
@Data
public class DashboardOperationTrendRespDTO {

    @Schema(description = "月份列表，如 ['2026-01', '2026-02', ...]")
    private List<String> months;

    @Schema(description = "各月作业面积（亩）")
    private List<BigDecimal> areaList;

    @Schema(description = "各月无人机+药剂出库数量")
    private List<Long> outboundCountList;

    @Schema(description = "虫害趋势数据")
    private List<Integer> pestTrendList;
}

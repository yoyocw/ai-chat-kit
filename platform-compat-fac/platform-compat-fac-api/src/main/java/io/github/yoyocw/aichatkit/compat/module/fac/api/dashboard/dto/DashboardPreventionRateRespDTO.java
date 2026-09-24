package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "RPC 服务 - 防治达标率 Response DTO")
@Data
public class DashboardPreventionRateRespDTO {

    @Schema(description = "总达标率(0~100)")
    private BigDecimal totalRate;

    @Schema(description = "通过数")
    private Long passCount;

    @Schema(description = "通过率(0~100)")
    private BigDecimal passRate;

    @Schema(description = "未通过数")
    private Long failCount;

    @Schema(description = "未通过率(0~100)")
    private BigDecimal failRate;

    @Schema(description = "补防数")
    private Long reflyCount;

    @Schema(description = "补防率(0~100)")
    private BigDecimal resprayRate;

    @Schema(description = "较上月变化值")
    private BigDecimal monthChange;
}

package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "RPC 服务 - 设备维修状态 Response DTO")
@Data
public class DashboardEquipmentMaintenanceRespDTO {

    @Schema(description = "本月维修次数")
    private Long thisMonthRepairCount;

    @Schema(description = "正常状态数")
    private Long normalCount;

    @Schema(description = "维修中数")
    private Long repairingCount;
}

package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "RPC 服务 - 地图数据 Response DTO")
@Data
public class DashboardMapDataRespDTO {

    @Schema(description = "任务ID")
    private Long missionId;

    @Schema(description = "任务名称")
    private String missionName;

    @Schema(description = "林场名称")
    private String farmName;

    @Schema(description = "任务坐标(missionPosition)")
    private String missionPosition;

    @Schema(description = "作业地块GeoJSON")
    private String jobLandGeojson;

    @Schema(description = "任务状态")
    private Short missionState;

    @Schema(description = "作业面积")
    private BigDecimal jobArea;

    @Schema(description = "防治类型")
    private String preventionType;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

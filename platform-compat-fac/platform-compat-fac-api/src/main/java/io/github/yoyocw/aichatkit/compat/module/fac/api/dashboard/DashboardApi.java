package io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard;

import io.github.yoyocw.aichatkit.compat.framework.common.pojo.CommonResult;
import io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto.*;
import io.github.yoyocw.aichatkit.compat.module.fac.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import javax.validation.Valid;
import java.util.List;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - 数据看板")
public interface DashboardApi {

    String PREFIX = ApiConstants.PREFIX + "/dashboard";

    // ==================== 一、顶部统计卡片 ====================

    @GetMapping(PREFIX + "/top-statistics")
    @Operation(summary = "顶部7指标卡片")
    CommonResult<DashboardTopStatisticsRespDTO> getTopStatistics(
            @RequestParam(value = "farmIds", required = false) List<Long> farmIds);

    // ==================== 二、左侧区域 ====================

    @GetMapping(PREFIX + "/operation-trend")
    @Operation(summary = "作业趋势分析折线图")
    CommonResult<DashboardOperationTrendRespDTO> getOperationTrend(
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "farmIds", required = false) List<Long> farmIds);

    @GetMapping(PREFIX + "/task-type-distribution")
    @Operation(summary = "任务类型分布饼图")
    CommonResult<DashboardTaskTypeDistributionRespDTO> getTaskTypeDistribution(
            @RequestParam(value = "farmIds", required = false) List<Long> farmIds);

    @GetMapping(PREFIX + "/prevention-rate")
    @Operation(summary = "防治达标率仪表盘")
    CommonResult<DashboardPreventionRateRespDTO> getPreventionRate(
            @RequestParam(value = "farmIds", required = false) List<Long> farmIds);

    // ==================== 三、中间区域 — 地图 ====================

    @GetMapping(PREFIX + "/map-data")
    @Operation(summary = "地图GeoJSON数据（含missionPosition）")
    CommonResult<List<DashboardMapDataRespDTO>> getMapData(
            @RequestParam(value = "missionStates", required = false) List<Short> missionStates,
            @RequestParam(value = "farmIds", required = false) List<Long> farmIds);

    /**
     * 根据本次对话问题匹配当前用户有权查看的地图任务。
     *
     * @param reqDTO 用户本轮问题，必须明确包含任务编号、任务名称、林场名称、地块编码或作业地址
     * @return 最多十条带任务坐标或地块 GeoJSON 的匹配任务
     */
    @PostMapping(PREFIX + "/map-data/match")
    @Operation(summary = "根据对话问题匹配有权限的地图任务")
    CommonResult<List<DashboardMapDataRespDTO>> matchMapData(
            @Valid @RequestBody DashboardMapMatchReqDTO reqDTO);

    // ==================== 四、底部统计卡 ====================

    @GetMapping(PREFIX + "/uav-overview")
    @Operation(summary = "无人机概览")
    CommonResult<DashboardUavOverviewRespDTO> getUavOverview();

    @GetMapping(PREFIX + "/pilot-overview")
    @Operation(summary = "飞手概览")
    CommonResult<DashboardPilotOverviewRespDTO> getPilotOverview();

    @GetMapping(PREFIX + "/pesticide-inventory")
    @Operation(summary = "药剂库存")
    CommonResult<DashboardPesticideInventoryRespDTO> getPesticideInventory();

    @GetMapping(PREFIX + "/pesticide-consumption")
    @Operation(summary = "药剂消耗分析柱状图")
    CommonResult<DashboardPesticideConsumptionRespDTO> getPesticideConsumption(
            @RequestParam(value = "farmIds", required = false) List<Long> farmIds);

    @GetMapping(PREFIX + "/equipment-maintenance")
    @Operation(summary = "设备维修状态")
    CommonResult<DashboardEquipmentMaintenanceRespDTO> getEquipmentMaintenance();

    // ==================== 五、补喷任务列表 ====================

    @GetMapping(PREFIX + "/respray-task-list")
    @Operation(summary = "补喷任务列表（mission_state>=60且<110, mission_round>1，关联补喷表获取计划时间）")
    CommonResult<List<DashboardReflyTaskListRespDTO>> getReflyTaskList(
            @RequestParam(value = "farmIds", required = false) List<Long> farmIds);

    // ==================== 六、资源利用率 ====================

    @GetMapping(PREFIX + "/resource-utilization")
    @Operation(summary = "资源利用率（其他/车辆出勤率/药剂使用率/飞手利用率/无人机利用率）")
    CommonResult<DashboardResourceUtilizationRespDTO> getResourceUtilization();

    // ==================== 七、Top3飞手 ====================

    @GetMapping(PREFIX + "/top3-pilot")
    @Operation(summary = "Top3飞手信息")
    CommonResult<DashboardTop3PilotRespDTO> getTop3Pilot(
            @RequestParam(value = "farmIds", required = false) List<Long> farmIds);
}

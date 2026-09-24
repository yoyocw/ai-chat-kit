package io.github.yoyocw.aichatkit.module.ai.service.responsedata.bo;

import io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto.DashboardMapDataRespDTO;
import lombok.Data;

import java.util.List;

/**
 * 地图飞防任务列表扩展数据，内容来自后端完成权限校验后的任务查询结果。
 */
@Data
public class AiMapMissionListDataBO {

    /** 当前用户有权在地图中展示的飞防任务列表；无匹配任务时为空列表。 */
    private List<DashboardMapDataRespDTO> items;
}

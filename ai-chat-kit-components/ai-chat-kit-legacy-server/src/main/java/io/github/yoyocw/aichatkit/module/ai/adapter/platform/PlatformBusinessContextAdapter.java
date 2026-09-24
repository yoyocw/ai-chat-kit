package io.github.yoyocw.aichatkit.module.ai.adapter.platform;

import io.github.yoyocw.aichatkit.compat.framework.common.util.json.JsonUtils;
import io.github.yoyocw.aichatkit.compat.framework.security.core.LoginUser;
import io.github.yoyocw.aichatkit.compat.framework.security.core.util.SecurityFrameworkUtils;
import io.github.yoyocw.aichatkit.compat.framework.tenant.core.context.TenantContextHolder;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessContextPort;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessContextRequest;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessContextStatus;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessPresentation;
import io.github.yoyocw.aichatkit.module.ai.contract.context.AiBusinessSnapshot;
import io.github.yoyocw.aichatkit.module.ai.service.responsedata.bo.AiMapMissionListDataBO;
import io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.DashboardApi;
import io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto.DashboardMapDataRespDTO;
import io.github.yoyocw.aichatkit.compat.module.fac.api.dashboard.dto.DashboardMapMatchReqDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.github.yoyocw.aichatkit.module.ai.enums.AiResponseDataConstants.SCHEMA_VERSION_V1;
import static io.github.yoyocw.aichatkit.module.ai.enums.AiResponseDataConstants.TYPE_MAP_MISSION_LIST;

/** 林业宿主适配：同步核对当前身份并查询一次，林业 DTO 不穿过上下文端口。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformBusinessContextAdapter implements AiBusinessContextPort {
    /** 林业业务接口自身继续执行实时数据权限。 */
    private final DashboardApi dashboardApi;

    @Override
    public AiBusinessSnapshot prepare(AiBusinessContextRequest request) {
        validateActor(request);
        if (request.getCapabilities().isEmpty()) {
            return new AiBusinessSnapshot(AiBusinessContextStatus.NOT_REQUESTED,
                    "{\"matchedMissions\":[]}", null);
        }
        if (!Collections.singleton("platform.map").equals(request.getCapabilities())) {
            throw new IllegalArgumentException("不支持的林业上下文能力");
        }
        DashboardMapMatchReqDTO query = new DashboardMapMatchReqDTO();
        query.setQuestion(request.getQuestion());
        List<DashboardMapDataRespDTO> items = dashboardApi.matchMapData(query).getCheckedData();
        if (items == null) {
            throw new IllegalStateException("林业业务查询未返回有效结果");
        }
        // 两份数据都来自本次授权结果，不再次查询；事实失败不能被可选展示降级吞掉。
        String facts = buildFacts(items);
        return new AiBusinessSnapshot(AiBusinessContextStatus.READY, facts, buildPresentation(items));
    }

    /** 阶段一通过现有认证上下文验证显式标识；不接受仅靠构造 DTO 声明身份。 */
    private void validateActor(AiBusinessContextRequest request) {
        LoginUser actor = SecurityFrameworkUtils.getLoginUser();
        if (request == null || actor == null || actor.getId() == null || actor.getTenantId() == null
                || !"platform".equals(request.getNamespace())
                || !org.springframework.util.StringUtils.hasText(request.getInvocationId())
                || !actor.getId().toString().equals(request.getActorId())
                || !actor.getTenantId().toString().equals(request.getTenantId())
                || !Objects.equals(actor.getTenantId(), TenantContextHolder.getTenantId())) {
            throw new IllegalStateException("业务上下文身份不匹配");
        }
    }

    /** 保持原提示词六字段，不将地图完整数据传给模型。 */
    private String buildFacts(List<DashboardMapDataRespDTO> items) {
        List<Map<String, Object>> missions = new ArrayList<Map<String, Object>>();
        for (DashboardMapDataRespDTO item : items) {
            Map<String, Object> mission = new LinkedHashMap<String, Object>();
            mission.put("missionId", item.getMissionId());
            mission.put("missionName", item.getMissionName());
            mission.put("farmName", item.getFarmName());
            mission.put("missionState", item.getMissionState());
            mission.put("jobArea", item.getJobArea());
            mission.put("preventionType", item.getPreventionType());
            missions.add(mission);
        }
        return JsonUtils.toJsonString(Collections.singletonMap("matchedMissions", missions));
    }

    /** 保留既有 data.items 序列化规则；这里只生成 data，不提前生成 generatedAt。 */
    private AiBusinessPresentation buildPresentation(List<DashboardMapDataRespDTO> items) {
        try {
            AiMapMissionListDataBO data = new AiMapMissionListDataBO();
            data.setItems(new ArrayList<DashboardMapDataRespDTO>(items));
            return new AiBusinessPresentation(TYPE_MAP_MISSION_LIST, SCHEMA_VERSION_V1,
                    JsonUtils.toJsonString(data));
        } catch (RuntimeException ex) {
            log.warn("[prepare][可选地图展示转换失败 exceptionType={}]", ex.getClass().getName());
            return null;
        }
    }
}

package example.aienginehost;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅展示本地装配状态，不暴露模型调用、用户身份或配置凭据。 */
@RestController
@RequiredArgsConstructor
public class HostRuntimeController {

    /** 只读装配诊断，不要求任何宿主端口存在。 */
    private final HostRuntimeStatusService statusService;

    /** @return 宿主存活标记，不代表模型或数据库就绪 */
    @GetMapping("/host/health")
    public String health() {
        return "host-ready";
    }

    /**
     * 查询当前宿主的分层装配状态，不执行模型、身份捕获或数据库操作。
     *
     * @return 类型化装配状态及缺失依赖；Bean 存在不代表配置有效或业务验收通过
     */
    @GetMapping("/host/engine")
    public HostEngineStatusRespVO engine() {
        return statusService.getStatus();
    }
}

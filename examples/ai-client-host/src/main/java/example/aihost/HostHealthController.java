package example.aihost;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 宿主自己的端点，AI关闭时仍可用。 */
@RestController
public class HostHealthController {
    /** @return 仅代表宿主HTTP已启动，不代表外部AI可用 */
    @GetMapping("/host/health")
    public String health() { return "host-ready"; }
}

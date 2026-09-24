package example.aicomponent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** A host-owned endpoint proving that disabled AI does not prevent normal MVC startup. */
@RestController
public class HostHealthController {

    @GetMapping("/host/health")
    public String health() {
        return "host-ready";
    }
}

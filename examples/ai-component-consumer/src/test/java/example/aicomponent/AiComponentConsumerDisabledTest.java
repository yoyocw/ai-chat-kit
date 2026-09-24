package example.aicomponent;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResources;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service.AiMcpV1AuthorizationService;
import io.github.yoyocw.aichatkit.ai.adapter.web.AiWebErrorAdvice;
import io.github.yoyocw.aichatkit.ai.adapter.web.AiWebGroupController;
import io.github.yoyocw.aichatkit.ai.adapter.web.AiWebSingleController;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Candidate-artifact acceptance test: child switches cannot bypass the disabled engine. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiComponentConsumerDisabledTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ListableBeanFactory beans;

    @Test
    void disabledAiLeavesHostHealthyAndCreatesNoAiRuntimeSurface() {
        assertThat(http.getForObject("/host/health", String.class)).isEqualTo("host-ready");
        assertThat(beans.getBeansOfType(BailianClient.class)).isEmpty();
        assertThat(beans.getBeansOfType(AiJdbcResources.class)).isEmpty();
        assertThat(beans.getBeansOfType(AiWebSingleController.class)).isEmpty();
        assertThat(beans.getBeansOfType(AiWebGroupController.class)).isEmpty();
        assertThat(beans.getBeansOfType(AiWebErrorAdvice.class)).isEmpty();
        assertThat(beans.getBeansOfType(AiMcpV1AuthorizationService.class)).isEmpty();
    }
}

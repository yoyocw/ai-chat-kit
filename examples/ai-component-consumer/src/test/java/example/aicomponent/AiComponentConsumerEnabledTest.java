package example.aicomponent;

import io.github.yoyocw.aichatkit.ai.adapter.jdbc.config.AiJdbcResources;
import io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.service.AiMcpV1AuthorizationService;
import io.github.yoyocw.aichatkit.ai.adapter.web.AiWebSingleController;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostConversationManagementService;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostConversationShareService;
import io.github.yoyocw.aichatkit.ai.starter.host.AiHostSingleChatService;
import io.github.yoyocw.aichatkit.module.ai.framework.bailian.BailianClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/** Structural enabled acceptance against the isolated local PostgreSQL candidate environment. */
@EnabledIfEnvironmentVariable(named = "AI_TEST_POSTGRES_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ai-chat-kit.ai.engine.enabled=true",
        "ai-chat-kit.ai.web.enabled=true",
        "ai-chat-kit.ai.starter.namespace=consumer-lifecycle",
        "ai-chat-kit.ai.starter.modes[0]=SINGLE",
        "ai-chat-kit.ai.storage.type=postgresql",
        "ai-chat-kit.ai.storage.jdbc.mode=isolated",
        "ai-chat-kit.ai.storage.jdbc.jdbc-url=${AI_TEST_POSTGRES_URL}",
        "ai-chat-kit.ai.storage.jdbc.username=postgres",
        "ai-chat-kit.ai.storage.jdbc.password=",
        "ai-chat-kit.ai.execution.scope=jdbc-explicit",
        "ai-chat-kit.ai.bailian.api-key=structural-test-only",
        "ai-chat-kit.ai.applications.single.app-id=0123456789abcdef0123456789abcdef",
        "ai-chat-kit.ai.mcp-jwt-v1.authorization-enabled=true",
        "ai-chat-kit.ai.mcp-jwt-v1.signing-enabled=false",
        "ai-chat-kit.ai.mcp-jwt-v1.verification-enabled=false",
        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].app-id=0123456789abcdef0123456789abcdef",
        "ai-chat-kit.ai.mcp-jwt-v1.apps[0].permission=test:ai:invoke"
})
@Import(AiComponentConsumerModesLifecycleTest.HostIdentityFixture.class)
class AiComponentConsumerEnabledTest {

    @org.springframework.beans.factory.annotation.Autowired private AiJdbcResources jdbc;
    @org.springframework.beans.factory.annotation.Autowired private BailianClient bailian;
    @org.springframework.beans.factory.annotation.Autowired private AiHostSingleChatService single;
    @org.springframework.beans.factory.annotation.Autowired private AiHostConversationManagementService management;
    @org.springframework.beans.factory.annotation.Autowired private AiHostConversationShareService share;
    @org.springframework.beans.factory.annotation.Autowired private AiWebSingleController web;
    @org.springframework.beans.factory.annotation.Autowired private AiMcpV1AuthorizationService authorization;

    @Test
    void enabledSingleModeHasCompleteStructuralAssembly() {
        assertThat(jdbc).isNotNull();
        assertThat(bailian).isNotNull();
        assertThat(single).isNotNull();
        assertThat(management).isNotNull();
        assertThat(share).isNotNull();
        assertThat(web).isNotNull();
        assertThat(authorization).isNotNull();
    }
}

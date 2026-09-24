package io.github.yoyocw.aichatkit.module.ai.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.github.yoyocw.aichatkit.module.ai.adapter.config.YamlApplicationConfigAdapter;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiApplicationConfig;
import io.github.yoyocw.aichatkit.module.ai.contract.config.AiChatMode;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 隔离 YAML 样例的属性绑定与业务校验；不验证部署 profile 或 Nacos 加载优先级。 */
class AiApplicationsYamlBindingTest {
    private static final String SINGLE_APP = "11111111111111111111111111111111";
    private static final String GROUP_APP = "22222222222222222222222222222222";

    @Test
    void shortVariablesBindBothModesAndCommaSeparatedTools() throws IOException {
        Map<String, Object> variables = variables();
        variables.put("AI_GROUP_MCP_ID", "group-mcp");
        AiApplicationsProperties properties = bind(variables, new LinkedHashMap<String, Object>());
        assertEquals("group-mcp", properties.getGroup().getMcpId());
        YamlApplicationConfigAdapter adapter = new YamlApplicationConfigAdapter(properties);
        AiApplicationConfig single = adapter.load(null, AiChatMode.SINGLE);
        assertEquals(SINGLE_APP, single.getAppId());
        assertEquals("single-mcp", single.getMcpId());
        assertEquals(Arrays.asList("tool_single-a", "tool_single-b"), single.getUserAuthToolIds());
        assertEquals(GROUP_APP, properties.getGroup().getAppId());
        assertEquals(Arrays.asList("tool_group-a", "tool_group-b"), properties.getGroup().getUserAuthToolIds());
        // 群聊原生 MCP 仍在生产适配器中拒绝，不因配置能绑定而放开协议边界。
        assertThrows(IllegalStateException.class, () -> adapter.load(null, AiChatMode.GROUP));
    }

    @Test
    void groupPluginConfigurationLoadsWithEmptyMcpDefault() throws IOException {
        YamlApplicationConfigAdapter adapter = adapter(variables());
        AiApplicationConfig group = adapter.load(null, AiChatMode.GROUP);
        assertEquals(GROUP_APP, group.getAppId());
        assertEquals("", group.getMcpId());
        assertEquals(Arrays.asList("tool_group-a", "tool_group-b"), group.getUserAuthToolIds());
    }

    @Test
    void emptyConfigurationBindsButRejectsEachModeOnUse() throws IOException {
        AiApplicationsProperties properties = bind(new LinkedHashMap<String, Object>(), new LinkedHashMap<String, Object>());
        assertEquals("", properties.getSingle().getAppId());
        assertEquals("", properties.getGroup().getAppId());
        assertTrue(properties.getSingle().getUserAuthToolIds().isEmpty());
        assertTrue(properties.getGroup().getUserAuthToolIds().isEmpty());
        YamlApplicationConfigAdapter adapter = new YamlApplicationConfigAdapter(properties);
        assertThrows(IllegalStateException.class, () -> adapter.load(null, AiChatMode.SINGLE));
        assertThrows(IllegalStateException.class, () -> adapter.load(null, AiChatMode.GROUP));
    }

    @Test
    void missingModeDoesNotDisableOtherValidMode() throws IOException {
        Map<String, Object> singleOnly = variables();
        singleOnly.remove("AI_GROUP_APP_ID");
        YamlApplicationConfigAdapter singleAdapter = adapter(singleOnly);
        assertEquals(SINGLE_APP, singleAdapter.load(null, AiChatMode.SINGLE).getAppId());
        assertThrows(IllegalStateException.class, () -> singleAdapter.load(null, AiChatMode.GROUP));
        Map<String, Object> groupOnly = variables();
        groupOnly.remove("AI_SINGLE_APP_ID");
        YamlApplicationConfigAdapter groupAdapter = adapter(groupOnly);
        assertEquals(GROUP_APP, groupAdapter.load(null, AiChatMode.GROUP).getAppId());
        assertThrows(IllegalStateException.class, () -> groupAdapter.load(null, AiChatMode.SINGLE));
    }

    @Test
    void explicitHigherPriorityPropertiesOverrideBasePlaceholders() throws IOException {
        Map<String, Object> overrides = new LinkedHashMap<String, Object>();
        overrides.put("ai-chat-kit.ai.applications.single.app-id", GROUP_APP);
        overrides.put("ai-chat-kit.ai.applications.single.mcp-id", "explicit-mcp");
        overrides.put("ai-chat-kit.ai.applications.single.user-auth-tool-ids", "tool_override");
        AiApplicationConfig single = new YamlApplicationConfigAdapter(bind(variables(), overrides))
                .load(null, AiChatMode.SINGLE);
        assertEquals(GROUP_APP, single.getAppId());
        assertEquals("explicit-mcp", single.getMcpId());
        assertEquals(Arrays.asList("tool_override"), single.getUserAuthToolIds());
    }

    /** 合成短变量，不读取进程环境、系统属性或 dev/local 配置中的真实绑定。 */
    private Map<String, Object> variables() {
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        values.put("AI_SINGLE_APP_ID", SINGLE_APP);
        values.put("AI_SINGLE_MCP_ID", "single-mcp");
        values.put("AI_SINGLE_USER_AUTH_TOOL_IDS", "tool_single-a,tool_single-b");
        values.put("AI_GROUP_APP_ID", GROUP_APP);
        values.put("AI_GROUP_USER_AUTH_TOOL_IDS", "tool_group-a,tool_group-b");
        return values;
    }

    private YamlApplicationConfigAdapter adapter(Map<String, Object> variables) throws IOException {
        return new YamlApplicationConfigAdapter(bind(variables, new LinkedHashMap<String, Object>()));
    }

    /** 仅解析无凭据的测试资源；Environment 解析占位，Binder 完成生产属性类型转换。 */
    private AiApplicationsProperties bind(Map<String, Object> variables, Map<String, Object> overrides)
            throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        // 读取期间关闭 YAML 内容日志，结束后恢复原日志级别。
        Logger loaderLogger = (Logger) LoggerFactory.getLogger("org.springframework.boot.env.OriginTrackedYamlLoader");
        Level originalLevel = loaderLogger.getLevel();
        try {
            loaderLogger.setLevel(Level.OFF);
            for (PropertySource<?> source : new YamlPropertySourceLoader()
                    .load("ai-binding-fixture", new ClassPathResource("ai/applications-binding.yaml"))) {
                environment.getPropertySources().addFirst(source);
            }
        } finally {
            loaderLogger.setLevel(originalLevel);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("synthetic-variables", variables));
        environment.getPropertySources().addFirst(new MapPropertySource("explicit-overrides", overrides));
        return Binder.get(environment).bind("ai-chat-kit.ai.applications", AiApplicationsProperties.class).get();
    }
}

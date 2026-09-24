package io.github.yoyocw.aichatkit.module.ai.contract.config;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

/** 单轮应用与工具绑定快照，不含平台密钥；提供方规则由宿主适配器校验。 */
public final class AiApplicationConfig {
    /** 实际调用的应用标识。 */
    private final String appId;
    /** 可选MCP服务标识。 */
    private final String mcpId;
    /** 本轮用户鉴权工具标识，复制后不可变；null不能代替空列表。 */
    private final List<String> userAuthToolIds;
    /** @param appId 已验证的应用标识
     * @param mcpId 可选服务标识
     * @param userAuthToolIds 已验证工具列表，不得为null */
    public AiApplicationConfig(String appId, String mcpId, List<String> userAuthToolIds) {
        this.appId = appId;
        this.mcpId = mcpId;
        this.userAuthToolIds = Collections.unmodifiableList(
                new ArrayList<String>(Objects.requireNonNull(userAuthToolIds, "userAuthToolIds")));
    }
    public String getAppId() { return appId; }
    public String getMcpId() { return mcpId; }
    public List<String> getUserAuthToolIds() { return userAuthToolIds; }
}

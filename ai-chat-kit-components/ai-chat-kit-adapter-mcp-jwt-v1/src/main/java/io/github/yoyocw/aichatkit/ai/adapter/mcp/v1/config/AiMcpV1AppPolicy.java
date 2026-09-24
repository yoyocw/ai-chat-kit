package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config;

import java.util.ArrayList;
import java.util.List;

/** 部署配置中的应用授权，不接收客户端覆盖；工具集合必须与调用配置完全一致。 */
public class AiMcpV1AppPolicy {
    /** 唯一百炼应用 ID。 */
    private String appId;
    /** 签发前必须具备的宿主权限码。 */
    private String permission;
    /** 固定 MCP 服务 ID；无 MCP 时为空。 */
    private String mcpId;
    /** 固定用户级鉴权插件 ID 集合；空集合表示无插件。 */
    private List<String> toolIds = new ArrayList<>();
    /** 该应用可调用的资源接口码；有工具时必须非空。 */
    private List<String> endpointCodes = new ArrayList<>();
    /** 签入 JWT 的最大分页条数，1 到 200。 */
    private int maxPageSize = 50;

    /** @return 唯一百炼应用 ID。 */
    public String getAppId() { return appId; }
    /** @param value 唯一百炼应用 ID。 */
    public void setAppId(String value) { this.appId = value; }
    /** @return 签发前必须具备的宿主权限码。 */
    public String getPermission() { return permission; }
    /** @param value 签发前必须具备的宿主权限码。 */
    public void setPermission(String value) { this.permission = value; }
    /** @return 固定 MCP 服务 ID；无 MCP 时为空。 */
    public String getMcpId() { return mcpId; }
    /** @param value 固定 MCP 服务 ID；无 MCP 时为空。 */
    public void setMcpId(String value) { this.mcpId = value; }
    /** @return 固定用户级鉴权插件 ID 集合；空集合表示无插件。 */
    public List<String> getToolIds() { return toolIds; }
    /** @param value 固定用户级鉴权插件 ID 集合；空集合表示无插件。 */
    public void setToolIds(List<String> value) { this.toolIds = value; }
    /** @return 该应用可调用的资源接口码；有工具时必须非空。 */
    public List<String> getEndpointCodes() { return endpointCodes; }
    /** @param value 该应用可调用的资源接口码；有工具时必须非空。 */
    public void setEndpointCodes(List<String> value) { this.endpointCodes = value; }
    /** @return 签入 JWT 的最大分页条数，1 到 200。 */
    public int getMaxPageSize() { return maxPageSize; }
    /** @param value 签入 JWT 的最大分页条数，1 到 200。 */
    public void setMaxPageSize(int value) { this.maxPageSize = value; }
}

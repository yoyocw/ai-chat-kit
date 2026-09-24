package io.github.yoyocw.aichatkit.ai.adapter.mcp.v1.config;


/** 资源端按服务端接口码选择的权限和分页约束。 */
public class AiMcpV1EndpointPolicy {
    /** 唯一接口码，必须由服务端路由固定选择。 */
    private String endpointCode;
    /** 资源调用必须具备的实际宿主权限码。 */
    private String permission;
    /** 分页接口必须提供正整数 pageSize；详情接口不得提供 pageSize。 */
    private boolean pageable = false;
    /** 资源端最大分页条数，1 到 200。 */
    private int maxPageSize = 50;

    /** @return 唯一接口码，必须由服务端路由固定选择。 */
    public String getEndpointCode() { return endpointCode; }
    /** @param value 唯一接口码，必须由服务端路由固定选择。 */
    public void setEndpointCode(String value) { this.endpointCode = value; }
    /** @return 资源调用必须具备的实际宿主权限码。 */
    public String getPermission() { return permission; }
    /** @param value 资源调用必须具备的实际宿主权限码。 */
    public void setPermission(String value) { this.permission = value; }
    /** @return 分页接口必须提供正整数 pageSize；详情接口不得提供 pageSize。 */
    public boolean getPageable() { return pageable; }
    /** @param value 分页接口必须提供正整数 pageSize；详情接口不得提供 pageSize。 */
    public void setPageable(boolean value) { this.pageable = value; }
    /** @return 资源端最大分页条数，1 到 200。 */
    public int getMaxPageSize() { return maxPageSize; }
    /** @param value 资源端最大分页条数，1 到 200。 */
    public void setMaxPageSize(int value) { this.maxPageSize = value; }
}

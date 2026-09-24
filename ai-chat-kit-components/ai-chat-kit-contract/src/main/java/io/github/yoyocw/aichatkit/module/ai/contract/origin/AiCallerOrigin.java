package io.github.yoyocw.aichatkit.module.ai.contract.origin;

/** 认证成功后复制的不可变来源，不含令牌、权限对象或正文。 */
public final class AiCallerOrigin {
    /** 通用宿主不透明租户标识。 */
    private final String tenantIdentifier;
    /** 通用宿主不透明用户标识。 */
    private final String actorIdentifier;
    /** 通用宿主不透明调用方记录标识。 */
    private final String clientRecordIdentifier;
    /** 可信租户编号，允许零；仅旧数字身份构造路径可用。 */
    private final Long tenantId;
    /** 可信用户编号，必须为正。 */
    private final Long userId;
    /** 当前核验客户端实体主键，必须为正。 */
    private final Long clientRecordId;
    /** 当前核验客户端标识，最长64字符。 */
    private final String clientId;
    /** 绑定业务系统，最长64字符。 */
    private final String businessSystem;
    /** 绑定环境，最长64字符。 */
    private final String environment;

    /** 创建可信来源快照。
     * @param tenantId 可信租户编号，允许零。
     * @param userId 可信用户编号，必须为正。
     * @param clientRecordId 当前核验客户端实体主键，必须为正。
     * @param clientId 当前核验客户端标识，最长64字符。
     * @param businessSystem 绑定业务系统，最长64字符。
     * @param environment 绑定环境，最长64字符。
     */
    public AiCallerOrigin(Long tenantId, Long userId, Long clientRecordId,
                          String clientId, String businessSystem, String environment) {
        if (tenantId == null || tenantId < 0 || userId == null || userId <= 0
                || clientRecordId == null || clientRecordId <= 0
                || !valid(clientId) || !valid(businessSystem) || !valid(environment)) {
            throw new IllegalStateException("委托来源身份缺失或无效");
        }
        this.tenantIdentifier = tenantId.toString();
        this.actorIdentifier = userId.toString();
        this.clientRecordIdentifier = clientRecordId.toString();
        this.tenantId = tenantId;
        this.userId = userId;
        this.clientRecordId = clientRecordId;
        this.clientId = clientId;
        this.businessSystem = businessSystem;
        this.environment = environment;
    }

    /**
     * 为通用宿主创建来源，数字身份旧 getter 不适用于此路径，禁止强制转换或截断标识。
     * @param tenantIdentifier 已核验租户标识
     * @param actorIdentifier 已核验用户标识
     * @param clientRecordIdentifier 已核验调用方记录标识
     * @param clientId 已绑定调用方编码
     * @param businessSystem 已绑定来源系统
     * @param environment 已绑定来源环境
     * @return 不含认证令牌的来源快照
     */
    public static AiCallerOrigin forIdentifiers(String tenantIdentifier, String actorIdentifier, String clientRecordIdentifier,
            String clientId, String businessSystem, String environment) {
        return new AiCallerOrigin(tenantIdentifier, actorIdentifier, clientRecordIdentifier, clientId, businessSystem, environment, true);
    }

    /** 单独构造路径避免与原 Long 构造函数发生 null 重载歧义。 */
    private AiCallerOrigin(String tenant, String actor, String clientRecord, String client, String system, String environment, boolean opaque) {
        if (!validIdentifier(tenant) || !validIdentifier(actor) || !validIdentifier(clientRecord)
                || !valid(client) || !valid(system) || !valid(environment)) {
            throw new IllegalStateException("委托来源身份缺失或无效");
        }
        this.tenantIdentifier = tenant; this.actorIdentifier = actor; this.clientRecordIdentifier = clientRecord;
        this.tenantId = null; this.userId = null; this.clientRecordId = null;
        this.clientId = client; this.businessSystem = system; this.environment = environment;
    }

    /** 非空不透明标识原样保留，不做数字解析。 */
    private static boolean validIdentifier(String value) {
        return value != null && !value.trim().isEmpty() && value.length() <= 128;
    }

    /** @return 通用宿主租户标识 */
    public String getTenantIdentifier() { return tenantIdentifier; }
    /** @return 通用宿主用户标识 */
    public String getActorIdentifier() { return actorIdentifier; }
    /** @return 通用宿主调用方记录标识 */
    public String getClientRecordIdentifier() { return clientRecordIdentifier; }

    /** 只接受明确的标识，不自动修正可信配置。 */
    private static boolean valid(String value) {
        return value != null && value.matches("[a-zA-Z0-9_-]{1,64}");
    }

    /** @return 可信租户编号，允许零。 */
    public Long getTenantId() { if (tenantId == null) { throw new IllegalStateException("不透明身份不能读取林业数字编号"); } return tenantId; }

    /** @return 可信用户编号，必须为正。 */
    public Long getUserId() { if (userId == null) { throw new IllegalStateException("不透明身份不能读取林业数字编号"); } return userId; }

    /** @return 当前核验客户端实体主键，必须为正。 */
    public Long getClientRecordId() { if (clientRecordId == null) { throw new IllegalStateException("不透明身份不能读取林业数字编号"); } return clientRecordId; }

    /** @return 当前核验客户端标识，最长64字符。 */
    public String getClientId() { return clientId; }

    /** @return 绑定业务系统，最长64字符。 */
    public String getBusinessSystem() { return businessSystem; }

    /** @return 绑定环境，最长64字符。 */
    public String getEnvironment() { return environment; }
}

package io.github.yoyocw.aichatkit.ai.adapter.jdbc.dal;

/** 随机分享码命中的内部记录归属，只用于查询能力授权内容，不构造或冒充宿主登录身份。 */
final class AiJdbcShareOwner {
    /** 数据库会话编号，不公开。 */
    final Long conversationId;
    /** 分享记录所属部署，必须等于固定配置。 */
    final String namespace;
    /** 数据库绑定租户，不从访客输入获取。 */
    final String tenant;
    /** 数据库绑定所有者，不从访客输入获取。 */
    final String actor;
    /** 当前可公开会话标题。 */
    final String title;

    /** 复制已按固定namespace/mode/随机码验证的记录，不具有宿主认证能力。 */
    AiJdbcShareOwner(Long id, String namespace, String tenant, String actor, String title) {
        this.conversationId = id; this.namespace = namespace; this.tenant = tenant; this.actor = actor; this.title = title;
    }

    /** 固定归属前缀只能来自分享记录，后续查询仍包含完整隔离条件。 */
    Object[] args(Object... rest) {
        Object[] values = new Object[rest.length + 3];
        values[0] = namespace; values[1] = tenant; values[2] = actor;
        System.arraycopy(rest, 0, values, 3, rest.length); return values;
    }
}

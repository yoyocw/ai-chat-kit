-- 阶段一增量：在 AI 消息表所在数据源/schema 执行，核对 search_path。
-- 先部署表并升级认证侧 clientRecordId 响应，再启用来源记录；混合旧实例须排空在途请求。
-- 不回填未知来源，不覆盖已有记录，不随消息/审计清理来源；回滚关闭能力并保留表和数据。
-- 不代表停止入口已开放，也不解决旧机器令牌在客户端删除重建后的实体绑定问题。
BEGIN;
SET LOCAL lock_timeout = '5s';
CREATE TABLE IF NOT EXISTS ai_message_origin (
    tenant_id bigint NOT NULL,
    user_id bigint NOT NULL,
    mode varchar(6) NOT NULL,
    message_id bigint NOT NULL,
    client_record_id bigint NOT NULL,
    client_id varchar(64) NOT NULL,
    business_system varchar(64) NOT NULL,
    environment varchar(64) NOT NULL,
    app_id varchar(32) NOT NULL,
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_message_origin_pkey PRIMARY KEY (tenant_id, mode, message_id),
    CONSTRAINT ai_message_origin_ids_check CHECK (tenant_id >= 0 AND user_id > 0 AND message_id > 0 AND client_record_id > 0),
    CONSTRAINT ai_message_origin_mode_check CHECK (mode IN ('single', 'group')),
    CONSTRAINT ai_message_origin_names_check CHECK (client_id ~ '^[a-zA-Z0-9_-]{1,64}$'
        AND business_system ~ '^[a-zA-Z0-9_-]{1,64}$' AND environment ~ '^[a-zA-Z0-9_-]{1,64}$'),
    CONSTRAINT ai_message_origin_app_check CHECK (app_id ~ '^[a-fA-F0-9]{32}$')
);
COMMENT ON TABLE ai_message_origin IS '不可变委托消息来源；与助手占位同事务写入，不存凭据，不软删除，不upsert或自动清理';
COMMENT ON COLUMN ai_message_origin.tenant_id IS '认证侧核验租户，与AI消息租户一致';
COMMENT ON COLUMN ai_message_origin.user_id IS '认证侧核验真实用户编号';
COMMENT ON COLUMN ai_message_origin.mode IS 'single单聊或group群聊，确定助手占位所在表';
COMMENT ON COLUMN ai_message_origin.message_id IS '本轮请求助手占位编号，群聊不使用后续成员回复编号';
COMMENT ON COLUMN ai_message_origin.client_record_id IS '当前核验OAuth2客户端实体主键，不代表令牌已绑定实体代次';
COMMENT ON COLUMN ai_message_origin.client_id IS '当前核验客户端字符串标识';
COMMENT ON COLUMN ai_message_origin.business_system IS '认证侧批准的业务系统标识';
COMMENT ON COLUMN ai_message_origin.environment IS '认证侧批准的部署环境标识';
COMMENT ON COLUMN ai_message_origin.app_id IS '本轮实际使用且被调用方授权的应用编号';
COMMENT ON COLUMN ai_message_origin.create_time IS '来源首次写入时间，后续不更新';
COMMENT ON CONSTRAINT ai_message_origin_pkey ON ai_message_origin IS '同租户同模式同助手占位只允许一个来源，重复写入失败，不因消息删除重建';
COMMENT ON CONSTRAINT ai_message_origin_ids_check ON ai_message_origin IS '租户可为零，其余身份与消息主键必须为正';
COMMENT ON CONSTRAINT ai_message_origin_mode_check ON ai_message_origin IS '仅两类已定义消息模式，应用校验对应消息归属，不建立多态外键';
COMMENT ON CONSTRAINT ai_message_origin_names_check ON ai_message_origin IS '限定来源标识格式，与可信绑定校验一致';
COMMENT ON CONSTRAINT ai_message_origin_app_check ON ai_message_origin IS '应用必须为32位十六进制标识';
COMMIT;

-- 执行后仅核对表结构，不读取用户来源或凭据；既有同名表需另核对约束，不能仅凭IF NOT EXISTS视为兼容。
SELECT column_name, data_type, is_nullable FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'ai_message_origin'
ORDER BY ordinal_position;

-- AI 模块 MCP 多调用方服务密钥表。
-- 执行范围：ai-server 使用的 PostgreSQL 业务库。

CREATE SEQUENCE IF NOT EXISTS ai_service_api_key_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS ai_service_api_key (
    id                      BIGINT       PRIMARY KEY DEFAULT nextval('ai_service_api_key_seq'),
    client_code             VARCHAR(64)  NOT NULL,
    client_name             VARCHAR(128) NOT NULL,
    active_key_sha256       CHAR(64)     NOT NULL,
    previous_key_sha256     CHAR(64),
    service_user_id         BIGINT       NOT NULL,
    max_page_size           INTEGER      NOT NULL DEFAULT 50,
    expire_time             TIMESTAMP,
    status                  SMALLINT     NOT NULL DEFAULT 0,
    tenant_id               BIGINT       NOT NULL,
    creator                 VARCHAR(64)  NOT NULL DEFAULT '',
    create_time             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                 VARCHAR(64)  NOT NULL DEFAULT '',
    update_time             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_service_api_key_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_ai_service_api_key_page_size CHECK (max_page_size BETWEEN 1 AND 200),
    CONSTRAINT ck_ai_service_api_key_active_digest CHECK (active_key_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ai_service_api_key_previous_digest CHECK
        (previous_key_sha256 IS NULL OR previous_key_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ai_service_api_key_distinct_digest CHECK
        (previous_key_sha256 IS NULL OR previous_key_sha256 <> active_key_sha256)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_service_api_key_client_code
    ON ai_service_api_key (tenant_id, client_code) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_service_api_key_active_digest
    ON ai_service_api_key (active_key_sha256) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_service_api_key_previous_digest
    ON ai_service_api_key (previous_key_sha256)
    WHERE deleted = 0 AND previous_key_sha256 IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_ai_service_api_key_tenant_status
    ON ai_service_api_key (tenant_id, status, id) WHERE deleted = 0;

COMMENT ON SEQUENCE ai_service_api_key_seq IS 'MCP 第三方服务密钥调用方主键序列';
COMMENT ON TABLE ai_service_api_key IS 'AI 模块 MCP 多调用方服务密钥表，仅保存原始密钥 SHA-256 摘要';
COMMENT ON COLUMN ai_service_api_key.id IS '调用方主键编号';
COMMENT ON COLUMN ai_service_api_key.client_code IS '租户内唯一稳定调用方编码，例如 bailian-platform';
COMMENT ON COLUMN ai_service_api_key.client_name IS '调用方显示名称，例如百炼林业智能体';
COMMENT ON COLUMN ai_service_api_key.active_key_sha256 IS '当前完整 MCP 原始密钥的 SHA-256 小写十六进制摘要';
COMMENT ON COLUMN ai_service_api_key.previous_key_sha256 IS '轮换窗口内上一把 MCP 密钥摘要，清除后旧密钥立即失效';
COMMENT ON COLUMN ai_service_api_key.service_user_id IS '绑定的后台只读服务账号用户编号';
COMMENT ON COLUMN ai_service_api_key.max_page_size IS '调用方单次分页最大返回条数，允许范围1至200';
COMMENT ON COLUMN ai_service_api_key.expire_time IS '调用方整体失效时间，为空表示长期有效';
COMMENT ON COLUMN ai_service_api_key.status IS '启停状态：0启用，1停用';
COMMENT ON COLUMN ai_service_api_key.tenant_id IS '调用方和服务账号固定绑定的租户编号';
COMMENT ON COLUMN ai_service_api_key.creator IS '创建管理员用户编号字符串';
COMMENT ON COLUMN ai_service_api_key.create_time IS '调用方记录创建时间';
COMMENT ON COLUMN ai_service_api_key.updater IS '最后更新管理员用户编号字符串';
COMMENT ON COLUMN ai_service_api_key.update_time IS '调用方记录最后更新时间';
COMMENT ON COLUMN ai_service_api_key.deleted IS '逻辑删除标记：0未删除，1已删除';
COMMENT ON CONSTRAINT ck_ai_service_api_key_status ON ai_service_api_key IS '限制调用方状态只能为启用或停用';
COMMENT ON CONSTRAINT ck_ai_service_api_key_page_size ON ai_service_api_key IS '限制调用方分页上限为1至200条';
COMMENT ON CONSTRAINT ck_ai_service_api_key_active_digest ON ai_service_api_key IS '保证当前摘要为64位小写十六进制';
COMMENT ON CONSTRAINT ck_ai_service_api_key_previous_digest ON ai_service_api_key IS '保证上一摘要为空或64位小写十六进制';
COMMENT ON CONSTRAINT ck_ai_service_api_key_distinct_digest ON ai_service_api_key IS '禁止同一调用方当前和上一摘要相同';
COMMENT ON INDEX uk_ai_service_api_key_client_code IS '保证同一租户内未删除调用方编码唯一';
COMMENT ON INDEX uk_ai_service_api_key_active_digest IS '防止当前密钥摘要重复绑定多个调用方';
COMMENT ON INDEX uk_ai_service_api_key_previous_digest IS '防止轮换窗口内上一密钥摘要重复';
COMMENT ON INDEX idx_ai_service_api_key_tenant_status IS '支持管理端按租户和状态分页查询调用方';

CREATE SEQUENCE IF NOT EXISTS ai_service_api_key_endpoint_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS ai_service_api_key_endpoint (
    id                      BIGINT       PRIMARY KEY DEFAULT nextval('ai_service_api_key_endpoint_seq'),
    service_api_key_id      BIGINT       NOT NULL,
    endpoint_code           VARCHAR(128) NOT NULL,
    tenant_id               BIGINT       NOT NULL,
    creator                 VARCHAR(64)  NOT NULL DEFAULT '',
    create_time             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                 VARCHAR(64)  NOT NULL DEFAULT '',
    update_time             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_service_api_key_endpoint_client FOREIGN KEY (service_api_key_id)
        REFERENCES ai_service_api_key (id),
    CONSTRAINT ck_ai_service_api_key_endpoint_code CHECK
        (endpoint_code ~ '^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*){1,7}$')
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_service_api_key_endpoint
    ON ai_service_api_key_endpoint (service_api_key_id, endpoint_code) WHERE deleted = 0;

COMMENT ON SEQUENCE ai_service_api_key_endpoint_seq IS 'MCP 调用方端点授权关系主键序列';
COMMENT ON TABLE ai_service_api_key_endpoint IS 'AI 模块 MCP 调用方与稳定只读端点编码授权关系表';
COMMENT ON COLUMN ai_service_api_key_endpoint.id IS '端点授权关系主键编号';
COMMENT ON COLUMN ai_service_api_key_endpoint.service_api_key_id IS '所属 MCP 第三方调用方主键编号';
COMMENT ON COLUMN ai_service_api_key_endpoint.endpoint_code IS '代码注解声明的全局稳定 MCP 端点编码';
COMMENT ON COLUMN ai_service_api_key_endpoint.tenant_id IS '授权关系所属租户编号，必须与调用方固定租户一致';
COMMENT ON COLUMN ai_service_api_key_endpoint.creator IS '创建管理员用户编号字符串';
COMMENT ON COLUMN ai_service_api_key_endpoint.create_time IS '端点授权关系创建时间';
COMMENT ON COLUMN ai_service_api_key_endpoint.updater IS '最后更新管理员用户编号字符串';
COMMENT ON COLUMN ai_service_api_key_endpoint.update_time IS '端点授权关系最后更新时间';
COMMENT ON COLUMN ai_service_api_key_endpoint.deleted IS '逻辑删除标记：0未删除，1已删除';
COMMENT ON CONSTRAINT fk_ai_service_api_key_endpoint_client ON ai_service_api_key_endpoint
    IS '保证端点授权只能归属于已存在的 MCP 第三方调用方';
COMMENT ON CONSTRAINT ck_ai_service_api_key_endpoint_code ON ai_service_api_key_endpoint
    IS '限制端点编码使用稳定的小写点分业务格式';
COMMENT ON INDEX uk_ai_service_api_key_endpoint IS '保证同一调用方的未删除端点编码授权唯一并支持鉴权查询';

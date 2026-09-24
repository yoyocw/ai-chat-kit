-- 仅在 AI 使用 DATABASE 密钥来源时，在 AI 签发服务实际连接的数据库和 schema 执行。
-- AiManagedSigningKeyMapper 使用未限定 schema 的表名，必须核对 AI 数据源的 search_path；同库同 schema 可复用既有表。
-- 分库或不同 schema：由密钥管理员通过受控渠道迁移既有密钥及 issuer/audience/enabled，先核对目标记录。
-- 不得重新生成密钥冒充迁移，不得覆盖目标已有密钥；保持业务验签公钥与迁移密钥配对。
-- issuer/audience 保持既有信任标识，与 AI 签发配置及业务验签配置一致，不能随模块名称更改。
-- AI 签发运行账号仅授予所需 SELECT 权限；使用初始化接口时才授予 INSERT。建表/授权由管理员完成。
-- 限制备份访问，禁止在 SQL 控制台结果、日志或工单输出私钥；system 退出签发后由管理员核对并回收其密钥访问权限。
-- CONFIGURATION 来源无需此表；DATABASE 来源缺少密钥时拒绝签发，不回退到 system 或重新生成密钥。
-- 本脚本仅创建表，不生成、迁移或替换密钥；不自动修改真实环境账号权限。
BEGIN;
CREATE TABLE IF NOT EXISTS ai_mcp_jwt_signing_key (
    issuer varchar(128) NOT NULL,
    audience varchar(128) NOT NULL,
    private_key text NOT NULL,
    public_key text NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ai_mcp_jwt_signing_key_pkey PRIMARY KEY (issuer, audience),
    CONSTRAINT ai_mcp_jwt_signing_key_nonempty CHECK (
        length(trim(issuer)) > 0 AND length(trim(audience)) > 0
        AND length(trim(private_key)) > 0 AND length(trim(public_key)) > 0)
);
COMMENT ON TABLE ai_mcp_jwt_signing_key IS '部署级 MCP JWT 签发密钥；仅签发服务和密钥管理员访问，不属于用户租户业务数据，不通过参数管理公开';
COMMENT ON COLUMN ai_mcp_jwt_signing_key.issuer IS '可信签发方标识，必须与 AI 签发服务及业务验签服务 issuer 一致';
COMMENT ON COLUMN ai_mcp_jwt_signing_key.audience IS '目标服务标识，必须与业务服务 audience 一致';
COMMENT ON COLUMN ai_mcp_jwt_signing_key.private_key IS 'PKCS#8 RSA 私钥 PEM；明文敏感材料，限制数据库及备份读取权限，禁止查询输出和日志记录';
COMMENT ON COLUMN ai_mcp_jwt_signing_key.public_key IS '与私钥配对的 X.509 RSA 公钥 PEM，供部署到业务验签服务';
COMMENT ON COLUMN ai_mcp_jwt_signing_key.enabled IS '是否允许新签发；停用不撤销已经签发的 JWT';
COMMENT ON COLUMN ai_mcp_jwt_signing_key.create_time IS '密钥记录创建时间';
COMMENT ON COLUMN ai_mcp_jwt_signing_key.update_time IS '密钥或启停状态变更时间，修改记录时同步更新';
COMMENT ON CONSTRAINT ai_mcp_jwt_signing_key_pkey ON ai_mcp_jwt_signing_key IS '每个签发方和目标服务仅保留一组当前密钥，避免签发查询多行';
COMMENT ON CONSTRAINT ai_mcp_jwt_signing_key_nonempty ON ai_mcp_jwt_signing_key IS '拒绝空标识和空密钥；RSA 格式由签发服务校验';
REVOKE ALL ON ai_mcp_jwt_signing_key FROM PUBLIC;
COMMIT;

-- 只核对状态，不输出密钥。建表后未导入时返回零行。
SELECT issuer, audience, enabled, length(private_key) > 0 AS has_private_key,
       length(public_key) > 0 AS has_public_key
FROM ai_mcp_jwt_signing_key;

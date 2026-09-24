-- AI 智能对话模块：仅保存业务平台会话和消息，模型、知识库、工具及智能体编排由阿里云百炼维护。

CREATE SEQUENCE IF NOT EXISTS ai_chat_conversation_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS ai_chat_conversation (
    id          BIGINT       PRIMARY KEY DEFAULT nextval('ai_chat_conversation_seq'),
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(100) NOT NULL,
    bailian_session_id VARCHAR(128),
    memory_summary TEXT,
    memory_cursor_message_id BIGINT,
    pinned      BOOLEAN      NOT NULL DEFAULT FALSE,
    pinned_time TIMESTAMP,
    share_code  VARCHAR(64),
    share_status SMALLINT    NOT NULL DEFAULT 0,
    share_expire_time TIMESTAMP,
    share_access_count BIGINT NOT NULL DEFAULT 0,
    share_last_access_time TIMESTAMP,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    creator     VARCHAR(64)  NOT NULL DEFAULT '',
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater     VARCHAR(64)  NOT NULL DEFAULT '',
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_chat_conversation_title CHECK (length(btrim(title)) > 0)
);

-- 兼容早期带 agent_code 的试用表结构；保留旧列数据但解除新代码插入时的非空限制。
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS bailian_session_id VARCHAR(128);
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS bailian_app_id VARCHAR(32);
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS bailian_turn_id BIGINT;
COMMENT ON COLUMN ai_chat_conversation.bailian_app_id IS '远端会话所属百炼应用，空值表示来源未知，不得直接复用';
COMMENT ON COLUMN ai_chat_conversation.bailian_turn_id IS '当前远端调用的助手消息编号，阻止迟到结果覆盖新一轮会话';
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS memory_summary TEXT;
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS memory_cursor_message_id BIGINT;
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS pinned_time TIMESTAMP;
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS share_code VARCHAR(64);
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS share_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS share_expire_time TIMESTAMP;
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS share_access_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS share_last_access_time TIMESTAMP;

-- 仅把升级前没有过期时间的永久链接收敛为 30 天有效；重复执行不会延长已治理分享。
UPDATE ai_chat_conversation
SET share_status = 1, share_expire_time = CURRENT_TIMESTAMP + INTERVAL '30 days'
WHERE share_code IS NOT NULL AND share_status = 0 AND share_expire_time IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'ck_ai_chat_conversation_share_status_v1'
                     AND conrelid = 'ai_chat_conversation'::regclass) THEN
        ALTER TABLE ai_chat_conversation ADD CONSTRAINT ck_ai_chat_conversation_share_status_v1
            CHECK (share_status IN (0, 1));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'ck_ai_chat_conversation_share_count_v1'
                     AND conrelid = 'ai_chat_conversation'::regclass) THEN
        ALTER TABLE ai_chat_conversation ADD CONSTRAINT ck_ai_chat_conversation_share_count_v1
            CHECK (share_access_count >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'ck_ai_chat_conversation_share_active_v1'
                     AND conrelid = 'ai_chat_conversation'::regclass) THEN
        ALTER TABLE ai_chat_conversation ADD CONSTRAINT ck_ai_chat_conversation_share_active_v1
            CHECK (share_status <> 1 OR (share_code IS NOT NULL AND share_expire_time IS NOT NULL));
    END IF;
END $$;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'ai_chat_conversation'
                 AND column_name = 'agent_code') THEN
        ALTER TABLE ai_chat_conversation ALTER COLUMN agent_code DROP NOT NULL;
    END IF;
END $$;

-- 兼容已执行旧脚本的数据库：MyBatis-Plus 使用 0/1 生成逻辑删除条件，统一转换为 SMALLINT。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'ai_chat_conversation'
                 AND column_name = 'deleted' AND data_type = 'boolean') THEN
        DROP INDEX IF EXISTS idx_ai_chat_conversation_user_time;
        DROP INDEX IF EXISTS idx_ai_chat_conversation_user_pin_time;
        DROP INDEX IF EXISTS uk_ai_chat_conversation_share_code;
        ALTER TABLE ai_chat_conversation ALTER COLUMN deleted DROP DEFAULT;
        ALTER TABLE ai_chat_conversation ALTER COLUMN deleted TYPE SMALLINT
            USING CASE WHEN deleted THEN 1 ELSE 0 END;
        ALTER TABLE ai_chat_conversation ALTER COLUMN deleted SET DEFAULT 0;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ai_chat_conversation_user_time
    ON ai_chat_conversation (tenant_id, user_id, update_time DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ai_chat_conversation_user_pin_time
    ON ai_chat_conversation (tenant_id, user_id, pinned DESC, pinned_time DESC, update_time DESC)
    WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_chat_conversation_share_code
    ON ai_chat_conversation (share_code) WHERE deleted = 0 AND share_code IS NOT NULL;

COMMENT ON SEQUENCE ai_chat_conversation_seq IS 'AI 对话会话主键序列';
COMMENT ON TABLE ai_chat_conversation IS 'AI 智能对话会话表，按租户和登录用户隔离历史对话';
COMMENT ON COLUMN ai_chat_conversation.id IS '会话主键编号';
COMMENT ON COLUMN ai_chat_conversation.user_id IS '会话所属后台用户编号';
COMMENT ON COLUMN ai_chat_conversation.title IS '会话标题，首次提问自动生成，最长100字符';
COMMENT ON COLUMN ai_chat_conversation.bailian_session_id IS '百炼 Agent 2.0 短期会话标识，空闲一小时后由平台失效';
COMMENT ON COLUMN ai_chat_conversation.memory_summary IS '已折叠旧消息形成的抽取式滚动记忆，不包含最近原文窗口';
COMMENT ON COLUMN ai_chat_conversation.memory_cursor_message_id IS '已进入滚动记忆的最大消息编号，用于避免重复折叠';
COMMENT ON COLUMN ai_chat_conversation.pinned IS '是否在用户历史会话列表中置顶';
COMMENT ON COLUMN ai_chat_conversation.pinned_time IS '最近一次置顶时间，取消置顶时为空';
COMMENT ON COLUMN ai_chat_conversation.share_code IS '限时公开分享码，取消分享时为空；全局唯一且不可枚举';
COMMENT ON COLUMN ai_chat_conversation.share_status IS '分享状态：0关闭，1启用；启用时仍需校验过期时间';
COMMENT ON COLUMN ai_chat_conversation.share_expire_time IS '分享过期时间，启用分享时非空';
COMMENT ON COLUMN ai_chat_conversation.share_access_count IS '公开分享成功访问次数，重新生成分享时归零';
COMMENT ON COLUMN ai_chat_conversation.share_last_access_time IS '最近一次公开分享成功访问时间';
COMMENT ON COLUMN ai_chat_conversation.tenant_id IS '租户编号，由多租户框架自动填充和过滤';
COMMENT ON COLUMN ai_chat_conversation.creator IS '创建者用户编号字符串，由审计字段处理器自动填充';
COMMENT ON COLUMN ai_chat_conversation.create_time IS '会话创建时间';
COMMENT ON COLUMN ai_chat_conversation.updater IS '最后更新者用户编号字符串';
COMMENT ON COLUMN ai_chat_conversation.update_time IS '会话最后更新时间，用于历史列表倒序排列';
COMMENT ON COLUMN ai_chat_conversation.deleted IS '逻辑删除标记：0未删除，1已删除';
COMMENT ON INDEX idx_ai_chat_conversation_user_time IS '按租户和用户查询未删除历史会话并按最近更新时间排序';
COMMENT ON INDEX idx_ai_chat_conversation_user_pin_time IS '按租户、用户、置顶状态、置顶时间和更新时间查询历史会话';
COMMENT ON INDEX uk_ai_chat_conversation_share_code IS '保证有效公开分享码全局唯一，支持匿名跨租户精确查询';
COMMENT ON CONSTRAINT ck_ai_chat_conversation_share_status_v1 ON ai_chat_conversation
    IS '限制会话分享状态仅允许关闭或启用';
COMMENT ON CONSTRAINT ck_ai_chat_conversation_share_count_v1 ON ai_chat_conversation
    IS '保证会话分享访问次数非负';
COMMENT ON CONSTRAINT ck_ai_chat_conversation_share_active_v1 ON ai_chat_conversation
    IS '启用会话分享时必须同时具备分享码和过期时间';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint
               WHERE conname = 'ck_ai_chat_conversation_title'
                 AND conrelid = 'ai_chat_conversation'::regclass) THEN
        COMMENT ON CONSTRAINT ck_ai_chat_conversation_title ON ai_chat_conversation
            IS '保证会话标题去除首尾空格后非空';
    END IF;
END $$;

CREATE SEQUENCE IF NOT EXISTS ai_chat_message_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id              BIGINT       PRIMARY KEY DEFAULT nextval('ai_chat_message_seq'),
    conversation_id BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    content         TEXT         NOT NULL DEFAULT '',
    map_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    status          SMALLINT     NOT NULL,
    request_id      VARCHAR(128),
    response_data   TEXT,
    error_message   VARCHAR(1000),
    tenant_id       BIGINT       NOT NULL DEFAULT 0,
    creator         VARCHAR(64)  NOT NULL DEFAULT '',
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(64)  NOT NULL DEFAULT '',
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_chat_message_conversation FOREIGN KEY (conversation_id) REFERENCES ai_chat_conversation(id),
    CONSTRAINT ck_ai_chat_message_role CHECK (role IN ('user', 'assistant')),
    CONSTRAINT ck_ai_chat_message_status CHECK (status IN (0, 1, 2, 3))
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'ai_chat_message'
                 AND column_name = 'agent_code') THEN
        ALTER TABLE ai_chat_message ALTER COLUMN agent_code DROP NOT NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'ai_chat_message'
                 AND column_name = 'deleted' AND data_type = 'boolean') THEN
        DROP INDEX IF EXISTS idx_ai_chat_message_conversation;
        DROP INDEX IF EXISTS uk_ai_chat_message_generating;
        ALTER TABLE ai_chat_message ALTER COLUMN deleted DROP DEFAULT;
        ALTER TABLE ai_chat_message ALTER COLUMN deleted TYPE SMALLINT
            USING CASE WHEN deleted THEN 1 ELSE 0 END;
        ALTER TABLE ai_chat_message ALTER COLUMN deleted SET DEFAULT 0;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ai_chat_message_conversation
    ON ai_chat_message (tenant_id, user_id, conversation_id, id) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_chat_message_generating
    ON ai_chat_message (tenant_id, user_id, conversation_id)
    WHERE deleted = 0 AND role = 'assistant' AND status = 0;

COMMENT ON SEQUENCE ai_chat_message_seq IS 'AI 对话消息主键序列';
COMMENT ON TABLE ai_chat_message IS 'AI 智能对话消息表，保存用户问题、百炼回复和扩展业务结果';
COMMENT ON COLUMN ai_chat_message.id IS '消息主键编号';
COMMENT ON COLUMN ai_chat_message.conversation_id IS '所属会话编号，关联 ai_chat_conversation.id';
COMMENT ON COLUMN ai_chat_message.user_id IS '消息所属后台用户编号，用于异步回写归属校验';
COMMENT ON COLUMN ai_chat_message.role IS '消息角色：user用户消息，assistant模型回复';
COMMENT ON COLUMN ai_chat_message.content IS '问题或回复完整正文，生成中助手消息允许为空字符串';
COMMENT ON COLUMN ai_chat_message.map_enabled IS '是否要求百炼应用返回地图业务结果';
COMMENT ON COLUMN ai_chat_message.status IS '生成状态：0生成中，1已完成，2已停止，3失败；用户消息固定为1';
COMMENT ON COLUMN ai_chat_message.request_id IS '百炼平台请求编号，用于调用链问题追踪';
COMMENT ON COLUMN ai_chat_message.response_data IS '百炼最后一个 output 节点原始JSON，用于来源、地图或业务卡片展示';
COMMENT ON COLUMN ai_chat_message.error_message IS '回复生成失败原因，成功或用户消息为空';
COMMENT ON COLUMN ai_chat_message.tenant_id IS '租户编号，由多租户框架自动填充和过滤';
COMMENT ON COLUMN ai_chat_message.creator IS '创建者用户编号字符串，由审计字段处理器自动填充';
COMMENT ON COLUMN ai_chat_message.create_time IS '消息创建时间';
COMMENT ON COLUMN ai_chat_message.updater IS '最后更新者用户编号字符串';
COMMENT ON COLUMN ai_chat_message.update_time IS '消息最后更新时间';
COMMENT ON COLUMN ai_chat_message.deleted IS '逻辑删除标记：0未删除，1已删除';
COMMENT ON INDEX idx_ai_chat_message_conversation IS '按租户、用户和会话顺序查询未删除消息历史';
COMMENT ON INDEX uk_ai_chat_message_generating IS '限制同一用户会话同一时刻最多存在一条生成中的助手消息';

-- 旧版 IF NOT EXISTS 表可能没有这些约束；只对实际存在的约束添加注释，保证升级脚本可重复执行。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint
               WHERE conname = 'ck_ai_chat_message_role'
                 AND conrelid = 'ai_chat_message'::regclass) THEN
        COMMENT ON CONSTRAINT ck_ai_chat_message_role ON ai_chat_message
            IS '限制消息角色只能是用户或助手';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint
               WHERE conname = 'ck_ai_chat_message_status'
                 AND conrelid = 'ai_chat_message'::regclass) THEN
        COMMENT ON CONSTRAINT ck_ai_chat_message_status ON ai_chat_message
            IS '限制消息状态为生成中、已完成、已停止或失败';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_constraint
               WHERE conname = 'fk_ai_chat_message_conversation'
                 AND conrelid = 'ai_chat_message'::regclass) THEN
        COMMENT ON CONSTRAINT fk_ai_chat_message_conversation ON ai_chat_message
            IS '保证消息必须归属于已存在的AI对话会话';
    END IF;
END $$;

-- 为已存在的 AI 对话菜单补充后端接口权限，普通角色可通过菜单授权获得使用权限。
DO $$
BEGIN
    IF to_regclass('system_menu') IS NOT NULL THEN
        UPDATE system_menu SET permission = 'ai:chat:use' WHERE id = 2759 AND deleted = 0;
    END IF;
END $$;

-- AI 智能体全局目录：本地业务编码映射百炼已发布应用，停用代替删除以保留历史展示。
CREATE SEQUENCE IF NOT EXISTS ai_agent_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS ai_agent (
    id             BIGINT       PRIMARY KEY DEFAULT nextval('ai_agent_seq'),
    code           VARCHAR(64)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    role           VARCHAR(255) NOT NULL,
    bailian_app_id VARCHAR(64),
    status         SMALLINT     NOT NULL DEFAULT 0,
    sort           INTEGER      NOT NULL DEFAULT 0,
    creator        VARCHAR(64)  NOT NULL DEFAULT '',
    create_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        VARCHAR(64)  NOT NULL DEFAULT '',
    update_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_ai_agent_code UNIQUE (code),
    CONSTRAINT uk_ai_agent_bailian_app UNIQUE (bailian_app_id),
    CONSTRAINT ck_ai_agent_code CHECK (length(btrim(code)) > 0),
    CONSTRAINT ck_ai_agent_name CHECK (length(btrim(name)) > 0),
    CONSTRAINT ck_ai_agent_status CHECK (status IN (0, 1)),
    CONSTRAINT ck_ai_agent_sort CHECK (sort >= 0)
);

COMMENT ON SEQUENCE ai_agent_seq IS 'AI 智能体目录主键序列';
COMMENT ON TABLE ai_agent IS 'AI 智能体全局目录，维护本地业务编码与百炼已发布应用的映射';
COMMENT ON COLUMN ai_agent.id IS '智能体目录主键编号';
COMMENT ON COLUMN ai_agent.code IS '服务端与百炼群聊工作流共同识别的稳定业务编码';
COMMENT ON COLUMN ai_agent.name IS '页面和历史群聊展示的智能体名称';
COMMENT ON COLUMN ai_agent.role IS '智能体在协同群聊中的职责说明';
COMMENT ON COLUMN ai_agent.bailian_app_id IS '百炼应用管理中已发布智能体的APP ID';
COMMENT ON COLUMN ai_agent.status IS '启停状态：0启用，1停用；停用后不可新建或继续执行群聊';
COMMENT ON COLUMN ai_agent.sort IS '候选成员展示顺序，数值越小越靠前';
COMMENT ON COLUMN ai_agent.creator IS '创建者用户编号字符串';
COMMENT ON COLUMN ai_agent.create_time IS '目录记录创建时间';
COMMENT ON COLUMN ai_agent.updater IS '最后更新者用户编号字符串';
COMMENT ON COLUMN ai_agent.update_time IS '目录记录最后更新时间';
COMMENT ON COLUMN ai_agent.deleted IS '逻辑删除标记：0未删除，1已删除；业务下线应优先修改status';
COMMENT ON CONSTRAINT uk_ai_agent_code ON ai_agent IS '保证百炼工作流业务编码全局唯一且可稳定关联历史群聊';
COMMENT ON CONSTRAINT uk_ai_agent_bailian_app ON ai_agent IS '防止同一百炼智能体应用重复映射多个业务编码';
COMMENT ON CONSTRAINT ck_ai_agent_code ON ai_agent IS '保证智能体业务编码去除首尾空格后非空';
COMMENT ON CONSTRAINT ck_ai_agent_name ON ai_agent IS '保证智能体展示名称去除首尾空格后非空';
COMMENT ON CONSTRAINT ck_ai_agent_status ON ai_agent IS '限制智能体状态只能为启用或停用';
COMMENT ON CONSTRAINT ck_ai_agent_sort ON ai_agent IS '限制智能体展示顺序不能为负数';

INSERT INTO ai_agent (code, name, role, bailian_app_id, status, sort, creator, updater)
VALUES
    ('FOREST_MAP', '林业问图智能体', '空间与资源查询', '409520c1d0224a8f9af1b3e27180c1a5', 0, 10, 'system', 'system'),
    ('AERIAL_PLAN', '飞防方案智能体', '飞防方案与资源编排', 'd99b8bb781a242ceaf093c1cdbf947ec', 0, 20, 'system', 'system'),
    ('KNOWLEDGE_OPS', '知识维护助手', '知识样本与来源治理', '7469e9f53f9b4b34aa9dcc75526c4c6a', 0, 30, 'system', 'system')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    role = EXCLUDED.role,
    bailian_app_id = EXCLUDED.bailian_app_id,
    sort = EXCLUDED.sort,
    updater = EXCLUDED.updater,
    update_time = CURRENT_TIMESTAMP,
    deleted = 0;

-- AI 多智能体群聊：前端选择候选成员，百炼工作流决定实际发言成员及顺序。
CREATE SEQUENCE IF NOT EXISTS ai_group_chat_conversation_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS ai_group_chat_conversation (
    id                 BIGINT       PRIMARY KEY DEFAULT nextval('ai_group_chat_conversation_seq'),
    user_id            BIGINT       NOT NULL,
    title              VARCHAR(30)  NOT NULL,
    bailian_session_id VARCHAR(128),
    memory_summary     TEXT,
    memory_cursor_message_id BIGINT,
    pinned             BOOLEAN      NOT NULL DEFAULT FALSE,
    pinned_time        TIMESTAMP,
    share_code         VARCHAR(64),
    share_status       SMALLINT     NOT NULL DEFAULT 0,
    share_expire_time  TIMESTAMP,
    share_access_count BIGINT       NOT NULL DEFAULT 0,
    share_last_access_time TIMESTAMP,
    tenant_id          BIGINT       NOT NULL DEFAULT 0,
    creator            VARCHAR(64)  NOT NULL DEFAULT '',
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater            VARCHAR(64)  NOT NULL DEFAULT '',
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted            SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_group_chat_conversation_title CHECK (length(btrim(title)) > 0)
);

ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS pinned BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS bailian_app_id VARCHAR(32);
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS bailian_turn_id BIGINT;
COMMENT ON COLUMN ai_group_chat_conversation.bailian_app_id IS '群聊远端会话所属百炼应用，空值表示来源未知，不得直接复用';
COMMENT ON COLUMN ai_group_chat_conversation.bailian_turn_id IS '当前群聊助手占位消息编号，阻止迟到结果覆盖新一轮会话';
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS memory_summary TEXT;
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS memory_cursor_message_id BIGINT;
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS pinned_time TIMESTAMP;
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS share_code VARCHAR(64);
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS share_status SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS share_expire_time TIMESTAMP;
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS share_access_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS share_last_access_time TIMESTAMP;

-- 仅把升级前没有过期时间的永久链接收敛为 30 天有效；重复执行不会延长已治理分享。
UPDATE ai_group_chat_conversation
SET share_status = 1, share_expire_time = CURRENT_TIMESTAMP + INTERVAL '30 days'
WHERE share_code IS NOT NULL AND share_status = 0 AND share_expire_time IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'ck_ai_group_chat_conversation_share_status_v1'
                     AND conrelid = 'ai_group_chat_conversation'::regclass) THEN
        ALTER TABLE ai_group_chat_conversation ADD CONSTRAINT ck_ai_group_chat_conversation_share_status_v1
            CHECK (share_status IN (0, 1));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'ck_ai_group_chat_conversation_share_count_v1'
                     AND conrelid = 'ai_group_chat_conversation'::regclass) THEN
        ALTER TABLE ai_group_chat_conversation ADD CONSTRAINT ck_ai_group_chat_conversation_share_count_v1
            CHECK (share_access_count >= 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'ck_ai_group_chat_conversation_share_active_v1'
                     AND conrelid = 'ai_group_chat_conversation'::regclass) THEN
        ALTER TABLE ai_group_chat_conversation ADD CONSTRAINT ck_ai_group_chat_conversation_share_active_v1
            CHECK (share_status <> 1 OR (share_code IS NOT NULL AND share_expire_time IS NOT NULL));
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'ai_group_chat_conversation'
                 AND column_name = 'deleted' AND data_type = 'boolean') THEN
        DROP INDEX IF EXISTS idx_ai_group_chat_conversation_user_time;
        DROP INDEX IF EXISTS idx_ai_group_chat_conversation_user_pin_time;
        DROP INDEX IF EXISTS uk_ai_group_chat_conversation_share_code;
        ALTER TABLE ai_group_chat_conversation ALTER COLUMN deleted DROP DEFAULT;
        ALTER TABLE ai_group_chat_conversation ALTER COLUMN deleted TYPE SMALLINT
            USING CASE WHEN deleted THEN 1 ELSE 0 END;
        ALTER TABLE ai_group_chat_conversation ALTER COLUMN deleted SET DEFAULT 0;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ai_group_chat_conversation_user_time
    ON ai_group_chat_conversation (tenant_id, user_id, update_time DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ai_group_chat_conversation_user_pin_time
    ON ai_group_chat_conversation (tenant_id, user_id, pinned DESC, pinned_time DESC, update_time DESC)
    WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_group_chat_conversation_share_code
    ON ai_group_chat_conversation (share_code) WHERE deleted = 0 AND share_code IS NOT NULL;

COMMENT ON SEQUENCE ai_group_chat_conversation_seq IS 'AI 群聊会话主键序列';
COMMENT ON TABLE ai_group_chat_conversation IS 'AI 多智能体群聊会话表，按租户和后台用户隔离';
COMMENT ON COLUMN ai_group_chat_conversation.id IS '群聊会话主键编号';
COMMENT ON COLUMN ai_group_chat_conversation.user_id IS '群聊所属后台用户编号';
COMMENT ON COLUMN ai_group_chat_conversation.title IS '群聊展示标题，最长30字符';
COMMENT ON COLUMN ai_group_chat_conversation.bailian_session_id IS '百炼编排工作流短期会话标识，过期后清空重建';
COMMENT ON COLUMN ai_group_chat_conversation.memory_summary IS '已折叠旧消息形成的抽取式滚动记忆，不包含最近原文窗口';
COMMENT ON COLUMN ai_group_chat_conversation.memory_cursor_message_id IS '已进入滚动记忆的最大消息编号，用于避免重复折叠';
COMMENT ON COLUMN ai_group_chat_conversation.pinned IS '是否置顶显示：false普通排序，true置顶优先';
COMMENT ON COLUMN ai_group_chat_conversation.pinned_time IS '最近一次置顶时间，未置顶时为空';
COMMENT ON COLUMN ai_group_chat_conversation.share_code IS '不可枚举的公开分享码，取消分享时为空';
COMMENT ON COLUMN ai_group_chat_conversation.share_status IS '分享状态：0关闭，1启用；启用时仍需校验过期时间';
COMMENT ON COLUMN ai_group_chat_conversation.share_expire_time IS '分享过期时间，启用分享时非空';
COMMENT ON COLUMN ai_group_chat_conversation.share_access_count IS '公开分享成功访问次数，重新生成分享时归零';
COMMENT ON COLUMN ai_group_chat_conversation.share_last_access_time IS '最近一次公开分享成功访问时间';
COMMENT ON COLUMN ai_group_chat_conversation.tenant_id IS '租户编号，由多租户框架自动填充和过滤';
COMMENT ON COLUMN ai_group_chat_conversation.creator IS '创建者用户编号字符串';
COMMENT ON COLUMN ai_group_chat_conversation.create_time IS '群聊创建时间';
COMMENT ON COLUMN ai_group_chat_conversation.updater IS '最后更新者用户编号字符串';
COMMENT ON COLUMN ai_group_chat_conversation.update_time IS '群聊最后更新时间，用于历史列表倒序排列';
COMMENT ON COLUMN ai_group_chat_conversation.deleted IS '逻辑删除标记：0未删除，1已删除';
COMMENT ON CONSTRAINT ck_ai_group_chat_conversation_title ON ai_group_chat_conversation
    IS '保证群聊标题去除首尾空格后非空';
COMMENT ON INDEX idx_ai_group_chat_conversation_user_time
    IS '按租户和用户查询未删除群聊并按最近更新时间排序';
COMMENT ON INDEX idx_ai_group_chat_conversation_user_pin_time
    IS '按租户和用户查询未删除群聊，并按置顶状态、置顶时间和更新时间排序';
COMMENT ON INDEX uk_ai_group_chat_conversation_share_code
    IS '保证所有租户中有效群聊公开分享码全局唯一';
COMMENT ON CONSTRAINT ck_ai_group_chat_conversation_share_status_v1 ON ai_group_chat_conversation
    IS '限制群聊分享状态仅允许关闭或启用';
COMMENT ON CONSTRAINT ck_ai_group_chat_conversation_share_count_v1 ON ai_group_chat_conversation
    IS '保证群聊分享访问次数非负';
COMMENT ON CONSTRAINT ck_ai_group_chat_conversation_share_active_v1 ON ai_group_chat_conversation
    IS '启用群聊分享时必须同时具备分享码和过期时间';

CREATE SEQUENCE IF NOT EXISTS ai_group_chat_member_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS ai_group_chat_member (
    id              BIGINT      PRIMARY KEY DEFAULT nextval('ai_group_chat_member_seq'),
    conversation_id BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    agent_code      VARCHAR(64) NOT NULL,
    sort_order      INTEGER     NOT NULL,
    tenant_id       BIGINT      NOT NULL DEFAULT 0,
    creator         VARCHAR(64) NOT NULL DEFAULT '',
    create_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(64) NOT NULL DEFAULT '',
    update_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_group_chat_member_conversation FOREIGN KEY (conversation_id)
        REFERENCES ai_group_chat_conversation(id),
    CONSTRAINT ck_ai_group_chat_member_order_max3 CHECK (sort_order BETWEEN 0 AND 2)
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'ai_group_chat_member'
                 AND column_name = 'deleted' AND data_type = 'boolean') THEN
        DROP INDEX IF EXISTS idx_ai_group_chat_member_conversation;
        DROP INDEX IF EXISTS uk_ai_group_chat_member_agent;
        DROP INDEX IF EXISTS uk_ai_group_chat_member_order;
        ALTER TABLE ai_group_chat_member ALTER COLUMN deleted DROP DEFAULT;
        ALTER TABLE ai_group_chat_member ALTER COLUMN deleted TYPE SMALLINT
            USING CASE WHEN deleted THEN 1 ELSE 0 END;
        ALTER TABLE ai_group_chat_member ALTER COLUMN deleted SET DEFAULT 0;
    END IF;
END $$;

DO $$
DECLARE
    constraint_needs_repair BOOLEAN;
    column_is_nullable BOOLEAN;
BEGIN
    SELECT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema()
                     AND table_name = 'ai_group_chat_member'
                     AND column_name = 'sort_order'
                     AND is_nullable = 'YES')
        INTO column_is_nullable;
    SELECT NOT EXISTS (SELECT 1 FROM pg_constraint c
                       JOIN pg_class t ON t.oid = c.conrelid
                       JOIN pg_namespace n ON n.oid = t.relnamespace
                       WHERE n.nspname = current_schema()
                         AND t.relname = 'ai_group_chat_member'
                         AND c.conname = 'ck_ai_group_chat_member_order_max3'
                         AND c.contype = 'c'
                         AND c.convalidated)
           OR EXISTS (SELECT 1 FROM pg_constraint c
                      JOIN pg_class t ON t.oid = c.conrelid
                      JOIN pg_namespace n ON n.oid = t.relnamespace
                      WHERE n.nspname = current_schema()
                        AND t.relname = 'ai_group_chat_member'
                        AND c.conname = 'ck_ai_group_chat_member_order')
        INTO constraint_needs_repair;
    IF constraint_needs_repair OR column_is_nullable THEN
        LOCK TABLE ai_group_chat_member IN SHARE ROW EXCLUSIVE MODE;
        IF EXISTS (SELECT 1 FROM ai_group_chat_member
                   WHERE sort_order IS NULL OR sort_order < 0 OR sort_order > 2) THEN
            RAISE EXCEPTION 'ai_group_chat_member.sort_order contains NULL or values outside the required range 0..2';
        END IF;
        IF constraint_needs_repair THEN
            ALTER TABLE ai_group_chat_member DROP CONSTRAINT IF EXISTS ck_ai_group_chat_member_order;
            ALTER TABLE ai_group_chat_member DROP CONSTRAINT IF EXISTS ck_ai_group_chat_member_order_max3;
        END IF;
        IF column_is_nullable THEN
            ALTER TABLE ai_group_chat_member ALTER COLUMN sort_order SET NOT NULL;
        END IF;
        IF constraint_needs_repair THEN
            ALTER TABLE ai_group_chat_member
                ADD CONSTRAINT ck_ai_group_chat_member_order_max3 CHECK (sort_order BETWEEN 0 AND 2);
        END IF;
        COMMENT ON CONSTRAINT ck_ai_group_chat_member_order_max3 ON ai_group_chat_member
            IS '限制成员展示顺序为0至2，对应最多3个成员';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ai_group_chat_member_conversation
    ON ai_group_chat_member (tenant_id, user_id, conversation_id, sort_order) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_group_chat_member_agent
    ON ai_group_chat_member (tenant_id, conversation_id, agent_code) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_group_chat_member_order
    ON ai_group_chat_member (tenant_id, conversation_id, sort_order) WHERE deleted = 0;

COMMENT ON SEQUENCE ai_group_chat_member_seq IS 'AI 群聊成员主键序列';
COMMENT ON TABLE ai_group_chat_member IS 'AI 群聊候选智能体成员表，实际发言顺序由百炼工作流决定';
COMMENT ON COLUMN ai_group_chat_member.id IS '群聊成员记录主键编号';
COMMENT ON COLUMN ai_group_chat_member.conversation_id IS '所属群聊会话编号';
COMMENT ON COLUMN ai_group_chat_member.user_id IS '群聊所属后台用户编号';
COMMENT ON COLUMN ai_group_chat_member.agent_code IS '百炼工作流识别的稳定智能体编码';
COMMENT ON COLUMN ai_group_chat_member.sort_order IS '成员页面展示顺序，从0开始';
COMMENT ON COLUMN ai_group_chat_member.tenant_id IS '租户编号，由多租户框架自动填充和过滤';
COMMENT ON COLUMN ai_group_chat_member.creator IS '创建者用户编号字符串';
COMMENT ON COLUMN ai_group_chat_member.create_time IS '成员加入群聊时间';
COMMENT ON COLUMN ai_group_chat_member.updater IS '最后更新者用户编号字符串';
COMMENT ON COLUMN ai_group_chat_member.update_time IS '成员配置最后更新时间';
COMMENT ON COLUMN ai_group_chat_member.deleted IS '逻辑删除标记：0未删除，1已删除';
COMMENT ON CONSTRAINT fk_ai_group_chat_member_conversation ON ai_group_chat_member
    IS '保证群聊成员归属于已存在的群聊会话';
COMMENT ON CONSTRAINT ck_ai_group_chat_member_order_max3 ON ai_group_chat_member
    IS '限制成员展示顺序为0至2，对应最多3个成员';
COMMENT ON INDEX idx_ai_group_chat_member_conversation
    IS '按租户、用户和群聊查询未删除成员并保持展示顺序';
COMMENT ON INDEX uk_ai_group_chat_member_agent IS '同一租户群聊不能重复添加同一有效智能体';
COMMENT ON INDEX uk_ai_group_chat_member_order IS '同一租户群聊有效成员展示顺序不能重复';

CREATE SEQUENCE IF NOT EXISTS ai_group_chat_message_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS ai_group_chat_message (
    id              BIGINT        PRIMARY KEY DEFAULT nextval('ai_group_chat_message_seq'),
    conversation_id BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    role            VARCHAR(16)   NOT NULL,
    speaker_code    VARCHAR(64),
    speaker_name    VARCHAR(100),
    round_no        INTEGER,
    content         TEXT          NOT NULL DEFAULT '',
    status          SMALLINT      NOT NULL,
    request_id      VARCHAR(128),
    response_data   TEXT,
    error_message   VARCHAR(1000),
    tenant_id       BIGINT        NOT NULL DEFAULT 0,
    creator         VARCHAR(64)   NOT NULL DEFAULT '',
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         VARCHAR(64)   NOT NULL DEFAULT '',
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    CONSTRAINT fk_ai_group_chat_message_conversation FOREIGN KEY (conversation_id)
        REFERENCES ai_group_chat_conversation(id),
    CONSTRAINT ck_ai_group_chat_message_role CHECK (role IN ('user', 'assistant')),
    CONSTRAINT ck_ai_group_chat_message_status CHECK (status IN (0, 1, 2, 3)),
    CONSTRAINT ck_ai_group_chat_message_round_max4 CHECK (round_no IS NULL OR round_no BETWEEN 1 AND 4)
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'ai_group_chat_message'
                 AND column_name = 'deleted' AND data_type = 'boolean') THEN
        DROP INDEX IF EXISTS idx_ai_group_chat_message_conversation;
        DROP INDEX IF EXISTS uk_ai_group_chat_message_generating;
        ALTER TABLE ai_group_chat_message ALTER COLUMN deleted DROP DEFAULT;
        ALTER TABLE ai_group_chat_message ALTER COLUMN deleted TYPE SMALLINT
            USING CASE WHEN deleted THEN 1 ELSE 0 END;
        ALTER TABLE ai_group_chat_message ALTER COLUMN deleted SET DEFAULT 0;
    END IF;
END $$;

DO $$
DECLARE
    constraint_needs_repair BOOLEAN;
BEGIN
    SELECT NOT EXISTS (SELECT 1 FROM pg_constraint c
                       JOIN pg_class t ON t.oid = c.conrelid
                       JOIN pg_namespace n ON n.oid = t.relnamespace
                       WHERE n.nspname = current_schema()
                         AND t.relname = 'ai_group_chat_message'
                         AND c.conname = 'ck_ai_group_chat_message_round_max4'
                         AND c.contype = 'c'
                         AND c.convalidated)
           OR EXISTS (SELECT 1 FROM pg_constraint c
                      JOIN pg_class t ON t.oid = c.conrelid
                      JOIN pg_namespace n ON n.oid = t.relnamespace
                      WHERE n.nspname = current_schema()
                        AND t.relname = 'ai_group_chat_message'
                        AND c.conname = 'ck_ai_group_chat_message_round')
        INTO constraint_needs_repair;
    IF constraint_needs_repair THEN
        LOCK TABLE ai_group_chat_message IN SHARE ROW EXCLUSIVE MODE;
        IF EXISTS (SELECT 1 FROM ai_group_chat_message
                   WHERE round_no IS NOT NULL AND (round_no < 1 OR round_no > 4)) THEN
            RAISE EXCEPTION 'ai_group_chat_message.round_no contains values outside the required range 1..4';
        END IF;
        ALTER TABLE ai_group_chat_message DROP CONSTRAINT IF EXISTS ck_ai_group_chat_message_round;
        ALTER TABLE ai_group_chat_message DROP CONSTRAINT IF EXISTS ck_ai_group_chat_message_round_max4;
        ALTER TABLE ai_group_chat_message
            ADD CONSTRAINT ck_ai_group_chat_message_round_max4
            CHECK (round_no IS NULL OR round_no BETWEEN 1 AND 4);
        COMMENT ON CONSTRAINT ck_ai_group_chat_message_round_max4 ON ai_group_chat_message
            IS '限制单轮工作流发言顺序为1至4，最后一条可为ORCHESTRATOR汇总';
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_ai_group_chat_message_conversation
    ON ai_group_chat_message (tenant_id, user_id, conversation_id, id) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_group_chat_message_generating
    ON ai_group_chat_message (tenant_id, user_id, conversation_id)
    WHERE deleted = 0 AND role = 'assistant' AND status = 0;

COMMENT ON SEQUENCE ai_group_chat_message_seq IS 'AI 群聊消息主键序列';
COMMENT ON TABLE ai_group_chat_message IS 'AI 群聊消息表，保存用户问题和工作流编排后的各智能体回复';
COMMENT ON COLUMN ai_group_chat_message.id IS '群聊消息主键编号，生成中首条助手消息同时作为停止任务编号';
COMMENT ON COLUMN ai_group_chat_message.conversation_id IS '所属群聊会话编号';
COMMENT ON COLUMN ai_group_chat_message.user_id IS '消息所属后台用户编号';
COMMENT ON COLUMN ai_group_chat_message.role IS '消息角色：user用户，assistant智能体';
COMMENT ON COLUMN ai_group_chat_message.speaker_code IS '实际发言智能体编码，用户消息和生成占位消息为空';
COMMENT ON COLUMN ai_group_chat_message.speaker_name IS '实际发言智能体展示名称，由服务端目录生成';
COMMENT ON COLUMN ai_group_chat_message.round_no IS '本轮工作流中的发言顺序，从1开始';
COMMENT ON COLUMN ai_group_chat_message.content IS '用户问题或智能体回复正文';
COMMENT ON COLUMN ai_group_chat_message.status IS '生成状态：0生成中、1已完成、2已停止、3失败';
COMMENT ON COLUMN ai_group_chat_message.request_id IS '百炼平台请求编号，用于调用链追踪';
COMMENT ON COLUMN ai_group_chat_message.response_data IS '工作流地图、来源或业务卡片等结构化扩展结果JSON';
COMMENT ON COLUMN ai_group_chat_message.error_message IS '工作流生成失败原因，成功时为空';
COMMENT ON COLUMN ai_group_chat_message.tenant_id IS '租户编号，由多租户框架自动填充和过滤';
COMMENT ON COLUMN ai_group_chat_message.creator IS '创建者用户编号字符串';
COMMENT ON COLUMN ai_group_chat_message.create_time IS '群聊消息创建时间';
COMMENT ON COLUMN ai_group_chat_message.updater IS '最后更新者用户编号字符串';
COMMENT ON COLUMN ai_group_chat_message.update_time IS '群聊消息最后更新时间';
COMMENT ON COLUMN ai_group_chat_message.deleted IS '逻辑删除标记：0未删除，1已删除';
COMMENT ON CONSTRAINT fk_ai_group_chat_message_conversation ON ai_group_chat_message
    IS '保证群聊消息归属于已存在的群聊会话';
COMMENT ON CONSTRAINT ck_ai_group_chat_message_role ON ai_group_chat_message
    IS '限制群聊消息角色只能为用户或助手';
COMMENT ON CONSTRAINT ck_ai_group_chat_message_status ON ai_group_chat_message
    IS '限制群聊消息生成终态取值';
COMMENT ON CONSTRAINT ck_ai_group_chat_message_round_max4 ON ai_group_chat_message
    IS '限制单轮工作流发言顺序为1至4，最后一条可为ORCHESTRATOR汇总';
COMMENT ON INDEX idx_ai_group_chat_message_conversation
    IS '按租户、用户、群聊和消息编号查询未删除历史消息';
COMMENT ON INDEX uk_ai_group_chat_message_generating
    IS '限制同一用户群聊同一时刻最多一条生成中的工作流任务';

-- AI 模型请求级执行审计：单聊和群聊共用一张表，mode 决定会话及消息编号的业务来源。
CREATE SEQUENCE IF NOT EXISTS ai_model_execution_seq START WITH 1000 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS ai_model_execution (
    id                BIGINT         PRIMARY KEY DEFAULT nextval('ai_model_execution_seq'),
    conversation_id   BIGINT         NOT NULL,
    message_id        BIGINT         NOT NULL,
    user_id           BIGINT         NOT NULL,
    mode              VARCHAR(16)    NOT NULL,
    trace_code        VARCHAR(64)    NOT NULL,
    app_id            VARCHAR(64)    NOT NULL,
    app_version       VARCHAR(64),
    model_names       VARCHAR(500),
    request_id        VARCHAR(128),
    status            SMALLINT       NOT NULL,
    retry_count       INTEGER        NOT NULL DEFAULT 0,
    input_tokens      INTEGER,
    output_tokens     INTEGER,
    tool_call_count   INTEGER,
    first_token_ms    BIGINT,
    total_duration_ms BIGINT,
    estimated_cost    NUMERIC(18, 6),
    error_code        VARCHAR(64),
    instance_id       VARCHAR(255)   NOT NULL,
    tenant_id         BIGINT         NOT NULL DEFAULT 0,
    creator           VARCHAR(64)    NOT NULL DEFAULT '',
    create_time       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater           VARCHAR(64)    NOT NULL DEFAULT '',
    update_time       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted           SMALLINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_model_execution_mode CHECK (mode IN ('single', 'group')),
    CONSTRAINT ck_ai_model_execution_status CHECK (status IN (0, 1, 2, 3)),
    CONSTRAINT ck_ai_model_execution_retry_count CHECK (retry_count >= 0),
    CONSTRAINT ck_ai_model_execution_input_tokens CHECK (input_tokens IS NULL OR input_tokens >= 0),
    CONSTRAINT ck_ai_model_execution_output_tokens CHECK (output_tokens IS NULL OR output_tokens >= 0),
    CONSTRAINT ck_ai_model_execution_tool_call_count CHECK (tool_call_count IS NULL OR tool_call_count >= 0),
    CONSTRAINT ck_ai_model_execution_first_token_ms CHECK (first_token_ms IS NULL OR first_token_ms >= 0),
    CONSTRAINT ck_ai_model_execution_total_duration_ms CHECK (total_duration_ms IS NULL OR total_duration_ms >= 0),
    CONSTRAINT ck_ai_model_execution_estimated_cost CHECK (estimated_cost IS NULL OR estimated_cost >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_model_execution_message
    ON ai_model_execution (tenant_id, mode, message_id) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_model_execution_trace
    ON ai_model_execution (tenant_id, trace_code) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ai_model_execution_conversation
    ON ai_model_execution (tenant_id, mode, conversation_id, create_time DESC) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ai_model_execution_request
    ON ai_model_execution (tenant_id, request_id) WHERE deleted = 0 AND request_id IS NOT NULL;

COMMENT ON SEQUENCE ai_model_execution_seq IS 'AI 模型请求级执行审计主键序列';
COMMENT ON TABLE ai_model_execution IS 'AI 模型请求级执行审计表，关联单聊或群聊消息并记录百炼调用指标与终态';
COMMENT ON COLUMN ai_model_execution.id IS '执行审计主键编号';
COMMENT ON COLUMN ai_model_execution.conversation_id IS '业务会话编号，需结合mode判断单聊或群聊会话表';
COMMENT ON COLUMN ai_model_execution.message_id IS '本轮助手占位消息编号，需结合mode判断单聊或群聊消息表';
COMMENT ON COLUMN ai_model_execution.user_id IS '发起本轮模型执行的后台用户编号';
COMMENT ON COLUMN ai_model_execution.mode IS '执行模式：single普通对话，group多智能体群聊';
COMMENT ON COLUMN ai_model_execution.trace_code IS '服务端生成并透传给百炼或MCP的请求链路追踪码';
COMMENT ON COLUMN ai_model_execution.app_id IS '本轮实际调用的已发布百炼应用ID';
COMMENT ON COLUMN ai_model_execution.app_version IS '百炼应用版本，上游未提供可靠版本时为空';
COMMENT ON COLUMN ai_model_execution.model_names IS '百炼usage返回的模型ID，多个模型按英文逗号连接';
COMMENT ON COLUMN ai_model_execution.request_id IS '百炼平台请求编号，用于关联平台侧调用日志';
COMMENT ON COLUMN ai_model_execution.status IS '执行状态：0执行中、1完成、2停止、3失败';
COMMENT ON COLUMN ai_model_execution.retry_count IS '百炼短期会话失效后的重试次数，当前最多1次';
COMMENT ON COLUMN ai_model_execution.input_tokens IS '本轮所有模型合计输入Token数，上游未返回时为空';
COMMENT ON COLUMN ai_model_execution.output_tokens IS '本轮所有模型合计输出Token数，上游未返回时为空';
COMMENT ON COLUMN ai_model_execution.tool_call_count IS '百炼thoughts中可识别的工具调用步骤数量';
COMMENT ON COLUMN ai_model_execution.first_token_ms IS '发起百炼HTTP调用至收到首段正文的耗时，单位毫秒';
COMMENT ON COLUMN ai_model_execution.total_duration_ms IS '开始流式执行至消息进入终态的总耗时，单位毫秒';
COMMENT ON COLUMN ai_model_execution.estimated_cost IS '预估调用费用，未配置可信计价规则时为空';
COMMENT ON COLUMN ai_model_execution.error_code IS '失败、停止或超时的稳定错误编码，成功时为空';
COMMENT ON COLUMN ai_model_execution.instance_id IS '实际执行实例，格式为Spring应用名@主机名';
COMMENT ON COLUMN ai_model_execution.tenant_id IS '租户编号，由多租户框架自动填充和过滤';
COMMENT ON COLUMN ai_model_execution.creator IS '创建者用户编号字符串，由审计字段处理器自动填充';
COMMENT ON COLUMN ai_model_execution.create_time IS '执行审计创建时间，即本轮请求本地准备完成时间';
COMMENT ON COLUMN ai_model_execution.updater IS '最后更新者用户编号字符串';
COMMENT ON COLUMN ai_model_execution.update_time IS '执行审计最后更新时间';
COMMENT ON COLUMN ai_model_execution.deleted IS '逻辑删除标记：0未删除，1已删除';
COMMENT ON CONSTRAINT ck_ai_model_execution_mode ON ai_model_execution IS '限制执行模式只能为单聊或群聊';
COMMENT ON CONSTRAINT ck_ai_model_execution_status ON ai_model_execution IS '限制执行状态为执行中、完成、停止或失败';
COMMENT ON CONSTRAINT ck_ai_model_execution_retry_count ON ai_model_execution IS '保证重试次数为非负整数';
COMMENT ON CONSTRAINT ck_ai_model_execution_input_tokens ON ai_model_execution IS '保证输入Token数为空或非负';
COMMENT ON CONSTRAINT ck_ai_model_execution_output_tokens ON ai_model_execution IS '保证输出Token数为空或非负';
COMMENT ON CONSTRAINT ck_ai_model_execution_tool_call_count ON ai_model_execution IS '保证工具调用次数为空或非负';
COMMENT ON CONSTRAINT ck_ai_model_execution_first_token_ms ON ai_model_execution IS '保证首字延迟为空或非负';
COMMENT ON CONSTRAINT ck_ai_model_execution_total_duration_ms ON ai_model_execution IS '保证总耗时为空或非负';
COMMENT ON CONSTRAINT ck_ai_model_execution_estimated_cost ON ai_model_execution IS '保证预估费用为空或非负';
COMMENT ON INDEX uk_ai_model_execution_message IS '保证每个租户内每条单聊或群聊占位消息只有一条有效执行审计';
COMMENT ON INDEX uk_ai_model_execution_trace IS '保证每个租户内链路追踪码唯一';
COMMENT ON INDEX idx_ai_model_execution_conversation IS '按租户、模式、会话和时间查询请求级执行历史';
COMMENT ON INDEX idx_ai_model_execution_request IS '按百炼request_id定位平台调用记录';

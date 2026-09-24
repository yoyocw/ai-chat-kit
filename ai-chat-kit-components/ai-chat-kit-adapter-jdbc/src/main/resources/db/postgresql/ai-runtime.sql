-- PostgreSQL 专用，部署者显式审核后执行；不由应用自动执行，不迁移旧林业数据。
BEGIN;
CREATE TABLE ai_runtime_conversation (
    id bigserial PRIMARY KEY,
    namespace varchar(128) NOT NULL,
    tenant_id varchar(128) NOT NULL,
    actor_id varchar(128) NOT NULL,
    mode varchar(8) NOT NULL CHECK (mode IN ('single', 'group')),
    title varchar(256) NOT NULL DEFAULT '新对话',
    pinned boolean NOT NULL DEFAULT false,
    pinned_at timestamp with time zone,
    share_code varchar(32),
    share_status smallint NOT NULL DEFAULT 0,
    share_expire_at timestamp with time zone,
    share_access_count bigint NOT NULL DEFAULT 0,
    share_last_access_at timestamp with time zone,
    app_id varchar(128),
    remote_session_id text,
    turn_id bigint,
    memory_summary text,
    memory_cursor bigint,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted boolean NOT NULL DEFAULT false
 );
COMMENT ON TABLE ai_runtime_conversation IS 'AI自有单聊群聊会话，按命名空间租户用户隔离';
COMMENT ON COLUMN ai_runtime_conversation.id IS 'AI会话主键';
COMMENT ON COLUMN ai_runtime_conversation.namespace IS '部署命名空间';
COMMENT ON COLUMN ai_runtime_conversation.tenant_id IS '宿主不透明租户标识';
COMMENT ON COLUMN ai_runtime_conversation.actor_id IS '宿主不透明用户标识';
COMMENT ON COLUMN ai_runtime_conversation.mode IS '聊天模式single或group';
COMMENT ON COLUMN ai_runtime_conversation.title IS '用户可见会话标题';
COMMENT ON COLUMN ai_runtime_conversation.pinned IS '是否置顶，会话列表优先返回置顶记录';
COMMENT ON COLUMN ai_runtime_conversation.pinned_at IS '最近置顶绝对时刻，取消置顶时必须清空';
COMMENT ON COLUMN ai_runtime_conversation.share_code IS '128位安全随机值的32位小写hex公开能力码，撤销清空，不记录日志';
COMMENT ON COLUMN ai_runtime_conversation.share_status IS '分享状态0关闭1启用，启用仍须校验到期时刻及父会话未删除';
COMMENT ON COLUMN ai_runtime_conversation.share_expire_at IS '数据库确定的分享绝对失效时刻，撤销清空';
COMMENT ON COLUMN ai_runtime_conversation.share_access_count IS '本轮成功公开访问累计次数，重建分享重置，撤销保留';
COMMENT ON COLUMN ai_runtime_conversation.share_last_access_at IS '最近一次通过最终有效性复核的公开访问绝对时刻';
COMMENT ON COLUMN ai_runtime_conversation.app_id IS '当前绑定模型应用';
COMMENT ON COLUMN ai_runtime_conversation.remote_session_id IS '当前模型会话标识，不记录日志';
COMMENT ON COLUMN ai_runtime_conversation.turn_id IS '当前绑定助手占位编号，防止迟到响应覆盖';
COMMENT ON COLUMN ai_runtime_conversation.memory_summary IS '本地滚动记忆正文';
COMMENT ON COLUMN ai_runtime_conversation.memory_cursor IS '已归并记忆的最大消息编号';
COMMENT ON COLUMN ai_runtime_conversation.created_at IS '记录创建绝对时刻，数据库时钟含时区';
COMMENT ON COLUMN ai_runtime_conversation.updated_at IS '最近更新绝对时刻，数据库时钟含时区';
COMMENT ON COLUMN ai_runtime_conversation.deleted IS '逻辑删除标志，所有业务查询过滤false';
CREATE TABLE ai_runtime_message (
    id bigserial PRIMARY KEY,
    namespace varchar(128) NOT NULL,
    tenant_id varchar(128) NOT NULL,
    actor_id varchar(128) NOT NULL,
    mode varchar(8) NOT NULL CHECK (mode IN ('single', 'group')),
    conversation_id bigint NOT NULL,
    role varchar(16) NOT NULL CHECK (role IN ('user','assistant')),
    status smallint NOT NULL CHECK (status BETWEEN 0 AND 3),
    content text NOT NULL DEFAULT '',
    map_enabled boolean NOT NULL DEFAULT false,
    round_no integer,
    speaker_code varchar(128),
    speaker_name varchar(256),
    request_id varchar(256),
    response_data text,
    error_message text,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted boolean NOT NULL DEFAULT false
 );
COMMENT ON TABLE ai_runtime_message IS 'AI自有消息与生成占位，终态仅由生成态CAS转换';
COMMENT ON COLUMN ai_runtime_message.id IS 'AI消息主键';
COMMENT ON COLUMN ai_runtime_message.namespace IS '部署命名空间';
COMMENT ON COLUMN ai_runtime_message.tenant_id IS '宿主不透明租户标识';
COMMENT ON COLUMN ai_runtime_message.actor_id IS '宿主不透明用户标识';
COMMENT ON COLUMN ai_runtime_message.mode IS '聊天模式single或group';
COMMENT ON COLUMN ai_runtime_message.conversation_id IS '同一身份作用域的会话编号';
COMMENT ON COLUMN ai_runtime_message.role IS 'user用户或assistant助手';
COMMENT ON COLUMN ai_runtime_message.status IS '0生成中1完成2停止3失败';
COMMENT ON COLUMN ai_runtime_message.content IS '消息正文，禁止日志输出';
COMMENT ON COLUMN ai_runtime_message.map_enabled IS '本轮请求地图展示意图';
COMMENT ON COLUMN ai_runtime_message.round_no IS '同轮群聊回复顺序，从1开始；单聊为空';
COMMENT ON COLUMN ai_runtime_message.speaker_code IS '群聊已授权发言者编码';
COMMENT ON COLUMN ai_runtime_message.speaker_name IS '群聊发言者名称';
COMMENT ON COLUMN ai_runtime_message.request_id IS '提供方请求追踪标识';
COMMENT ON COLUMN ai_runtime_message.response_data IS '已过滤展示JSON';
COMMENT ON COLUMN ai_runtime_message.error_message IS '失败时安全文案';
COMMENT ON COLUMN ai_runtime_message.created_at IS '记录创建绝对时刻，数据库时钟含时区';
COMMENT ON COLUMN ai_runtime_message.updated_at IS '最近更新绝对时刻，数据库时钟含时区';
COMMENT ON COLUMN ai_runtime_message.deleted IS '逻辑删除标志，所有业务查询过滤false';
CREATE TABLE ai_runtime_origin (
    id bigserial PRIMARY KEY,
    namespace varchar(128) NOT NULL,
    tenant_id varchar(128) NOT NULL,
    actor_id varchar(128) NOT NULL,
    mode varchar(8) NOT NULL CHECK (mode IN ('single', 'group')),
    message_id bigint NOT NULL,
    client_record_id varchar(128) NOT NULL,
    client_id varchar(64) NOT NULL,
    business_system varchar(64) NOT NULL,
    environment varchar(64) NOT NULL,
    app_id varchar(128) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted boolean NOT NULL DEFAULT false
 );
COMMENT ON TABLE ai_runtime_origin IS 'AI助手占位的不可变委托来源，普通入口不写入';
COMMENT ON COLUMN ai_runtime_origin.id IS '来源记录主键';
COMMENT ON COLUMN ai_runtime_origin.namespace IS '部署命名空间';
COMMENT ON COLUMN ai_runtime_origin.tenant_id IS '宿主不透明租户标识';
COMMENT ON COLUMN ai_runtime_origin.actor_id IS '宿主不透明用户标识';
COMMENT ON COLUMN ai_runtime_origin.mode IS '聊天模式';
COMMENT ON COLUMN ai_runtime_origin.message_id IS '本轮助手占位编号';
COMMENT ON COLUMN ai_runtime_origin.client_record_id IS '可信调用方记录标识';
COMMENT ON COLUMN ai_runtime_origin.client_id IS '可信调用方编码';
COMMENT ON COLUMN ai_runtime_origin.business_system IS '可信来源业务系统';
COMMENT ON COLUMN ai_runtime_origin.environment IS '可信来源环境';
COMMENT ON COLUMN ai_runtime_origin.app_id IS '实际发送模型应用';
COMMENT ON COLUMN ai_runtime_origin.created_at IS '记录创建绝对时刻，数据库时钟含时区';
COMMENT ON COLUMN ai_runtime_origin.updated_at IS '最近更新绝对时刻，数据库时钟含时区';
COMMENT ON COLUMN ai_runtime_origin.deleted IS '逻辑删除标志，所有业务查询过滤false';
CREATE TABLE ai_runtime_execution (
    id bigserial PRIMARY KEY,
    namespace varchar(128) NOT NULL,
    tenant_id varchar(128) NOT NULL,
    actor_id varchar(128) NOT NULL,
    mode varchar(8) NOT NULL CHECK (mode IN ('single', 'group')),
    conversation_id bigint NOT NULL,
    message_id bigint NOT NULL,
    app_id varchar(128) NOT NULL,
    trace_code varchar(256),
    status smallint NOT NULL CHECK (status BETWEEN 0 AND 3),
    retry_count integer NOT NULL DEFAULT 0,
    request_id varchar(256),
    model_names text,
    input_tokens integer,
    output_tokens integer,
    tool_call_count integer,
    first_token_ms bigint,
    total_duration_ms bigint,
    error_code varchar(128),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted boolean NOT NULL DEFAULT false
 );
COMMENT ON TABLE ai_runtime_execution IS 'AI请求执行审计，消息终态与执行状态分别CAS收口';
COMMENT ON COLUMN ai_runtime_execution.id IS '执行审计主键';
COMMENT ON COLUMN ai_runtime_execution.namespace IS '部署命名空间';
COMMENT ON COLUMN ai_runtime_execution.tenant_id IS '宿主不透明租户标识';
COMMENT ON COLUMN ai_runtime_execution.actor_id IS '宿主不透明用户标识';
COMMENT ON COLUMN ai_runtime_execution.mode IS '聊天模式';
COMMENT ON COLUMN ai_runtime_execution.conversation_id IS '会话编号';
COMMENT ON COLUMN ai_runtime_execution.message_id IS '助手占位编号';
COMMENT ON COLUMN ai_runtime_execution.app_id IS '实际模型应用';
COMMENT ON COLUMN ai_runtime_execution.trace_code IS '内部链路编号';
COMMENT ON COLUMN ai_runtime_execution.status IS '0执行中1完成2停止3失败';
COMMENT ON COLUMN ai_runtime_execution.retry_count IS '会话失效重试次数';
COMMENT ON COLUMN ai_runtime_execution.request_id IS '提供方请求编号';
COMMENT ON COLUMN ai_runtime_execution.model_names IS '实际模型名称';
COMMENT ON COLUMN ai_runtime_execution.input_tokens IS '输入Token数，未知为空';
COMMENT ON COLUMN ai_runtime_execution.output_tokens IS '输出Token数，未知为空';
COMMENT ON COLUMN ai_runtime_execution.tool_call_count IS '工具调用数，未知为空';
COMMENT ON COLUMN ai_runtime_execution.first_token_ms IS '首段延迟毫秒，未知为空';
COMMENT ON COLUMN ai_runtime_execution.total_duration_ms IS '开始到终态总耗时毫秒';
COMMENT ON COLUMN ai_runtime_execution.error_code IS '稳定终态原因编码';
COMMENT ON COLUMN ai_runtime_execution.created_at IS '记录创建绝对时刻，数据库时钟含时区';
COMMENT ON COLUMN ai_runtime_execution.updated_at IS '最近更新绝对时刻，数据库时钟含时区';
COMMENT ON COLUMN ai_runtime_execution.deleted IS '逻辑删除标志，所有业务查询过滤false';
CREATE TABLE ai_runtime_member (
    id bigserial PRIMARY KEY,
    namespace varchar(128) NOT NULL,
    tenant_id varchar(128) NOT NULL,
    actor_id varchar(128) NOT NULL,
    mode varchar(8) NOT NULL DEFAULT 'group' CHECK (mode = 'group'),
    conversation_id bigint NOT NULL,
    agent_code varchar(128) NOT NULL,
    agent_name varchar(256) NOT NULL,
    agent_role varchar(4000) NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted boolean NOT NULL DEFAULT false
 );
COMMENT ON TABLE ai_runtime_member IS '群聊会话选择的AI成员，发送时还必须核对目录启用状态';
COMMENT ON COLUMN ai_runtime_member.id IS '成员关系主键';
COMMENT ON COLUMN ai_runtime_member.namespace IS '部署命名空间';
COMMENT ON COLUMN ai_runtime_member.tenant_id IS '宿主不透明租户标识';
COMMENT ON COLUMN ai_runtime_member.actor_id IS '宿主不透明用户标识';
COMMENT ON COLUMN ai_runtime_member.mode IS '仅群聊关系';
COMMENT ON COLUMN ai_runtime_member.conversation_id IS '同作用域群聊会话编号';
COMMENT ON COLUMN ai_runtime_member.agent_code IS '选择的AI成员编码';
COMMENT ON COLUMN ai_runtime_member.agent_name IS '创建或调整成员时由真实目录解析并保存的名称快照，不以编码补值';
COMMENT ON COLUMN ai_runtime_member.agent_role IS '创建或调整成员时由真实目录解析并保存的职责快照，历史查询不访问匿名目录';
COMMENT ON COLUMN ai_runtime_member.sort_order IS '成员有序位置';
COMMENT ON COLUMN ai_runtime_member.created_at IS '记录创建绝对时刻，数据库时钟含时区';
COMMENT ON COLUMN ai_runtime_member.updated_at IS '最近更新绝对时刻，数据库时钟含时区';
COMMENT ON COLUMN ai_runtime_member.deleted IS '逻辑删除标志，所有业务查询过滤false';
ALTER TABLE ai_runtime_member ADD CONSTRAINT ck_ai_runtime_member_snapshot
    CHECK (length(btrim(agent_name)) > 0 AND length(btrim(agent_role)) > 0);
COMMENT ON CONSTRAINT ck_ai_runtime_member_snapshot ON ai_runtime_member IS '成员名称和职责必须是非空真实目录快照';
CREATE UNIQUE INDEX uk_ai_runtime_conversation_scope ON ai_runtime_conversation (namespace, tenant_id, actor_id, mode, id);
COMMENT ON INDEX uk_ai_runtime_conversation_scope IS '供消息和成员外键核对完整会话归属';
CREATE UNIQUE INDEX uk_ai_runtime_message_scope ON ai_runtime_message (namespace, tenant_id, actor_id, mode, id);
COMMENT ON INDEX uk_ai_runtime_message_scope IS '供来源及执行审计核对完整消息归属';
CREATE UNIQUE INDEX uk_ai_runtime_origin_turn ON ai_runtime_origin (namespace, tenant_id, actor_id, mode, message_id);
COMMENT ON INDEX uk_ai_runtime_origin_turn IS '同助手占位仅允许一个不可变来源';
CREATE UNIQUE INDEX uk_ai_runtime_execution_turn ON ai_runtime_execution (namespace, tenant_id, actor_id, mode, message_id);
COMMENT ON INDEX uk_ai_runtime_execution_turn IS '同助手占位仅允许一个执行审计';
CREATE INDEX idx_ai_runtime_message_history ON ai_runtime_message (namespace, tenant_id, actor_id, mode, conversation_id, id);
COMMENT ON INDEX idx_ai_runtime_message_history IS '按身份会话有序读取历史';
CREATE UNIQUE INDEX uk_ai_runtime_member_code ON ai_runtime_member (namespace, tenant_id, actor_id, conversation_id, agent_code) WHERE deleted=false;
COMMENT ON INDEX uk_ai_runtime_member_code IS '同会话不重复选择同一成员';
CREATE UNIQUE INDEX uk_ai_runtime_generating ON ai_runtime_message(namespace,tenant_id,actor_id,mode,conversation_id) WHERE role='assistant' AND status=0 AND deleted=false;
COMMENT ON INDEX uk_ai_runtime_generating IS '每个身份会话只允许一个生成中的助手占位，防并发重复发送';
ALTER TABLE ai_runtime_message ADD CONSTRAINT fk_ai_runtime_message_scope FOREIGN KEY(namespace,tenant_id,actor_id,mode,conversation_id) REFERENCES ai_runtime_conversation(namespace,tenant_id,actor_id,mode,id);
COMMENT ON CONSTRAINT fk_ai_runtime_message_scope ON ai_runtime_message IS '禁止关联到其他命名空间租户用户或模式的数据';
ALTER TABLE ai_runtime_member ADD CONSTRAINT fk_ai_runtime_member_scope FOREIGN KEY(namespace,tenant_id,actor_id,mode,conversation_id) REFERENCES ai_runtime_conversation(namespace,tenant_id,actor_id,mode,id);
COMMENT ON CONSTRAINT fk_ai_runtime_member_scope ON ai_runtime_member IS '禁止关联到其他命名空间租户用户或模式的数据';
ALTER TABLE ai_runtime_origin ADD CONSTRAINT fk_ai_runtime_origin_scope FOREIGN KEY(namespace,tenant_id,actor_id,mode,message_id) REFERENCES ai_runtime_message(namespace,tenant_id,actor_id,mode,id);
COMMENT ON CONSTRAINT fk_ai_runtime_origin_scope ON ai_runtime_origin IS '禁止关联到其他命名空间租户用户或模式的数据';
ALTER TABLE ai_runtime_execution ADD CONSTRAINT fk_ai_runtime_execution_scope FOREIGN KEY(namespace,tenant_id,actor_id,mode,message_id) REFERENCES ai_runtime_message(namespace,tenant_id,actor_id,mode,id);
COMMENT ON CONSTRAINT fk_ai_runtime_execution_scope ON ai_runtime_execution IS '禁止关联到其他命名空间租户用户或模式的数据';
COMMENT ON CONSTRAINT ai_runtime_conversation_pkey ON ai_runtime_conversation IS 'AI自有记录唯一主键，不等同宿主用户或租户标识';
COMMENT ON CONSTRAINT ai_runtime_conversation_mode_check ON ai_runtime_conversation IS '仅允许表定义的AI单聊或群聊模式，防止模式混用';
COMMENT ON CONSTRAINT ai_runtime_message_pkey ON ai_runtime_message IS 'AI自有记录唯一主键，不等同宿主用户或租户标识';
COMMENT ON CONSTRAINT ai_runtime_message_mode_check ON ai_runtime_message IS '仅允许表定义的AI单聊或群聊模式，防止模式混用';
COMMENT ON CONSTRAINT ai_runtime_origin_pkey ON ai_runtime_origin IS 'AI自有记录唯一主键，不等同宿主用户或租户标识';
COMMENT ON CONSTRAINT ai_runtime_origin_mode_check ON ai_runtime_origin IS '仅允许表定义的AI单聊或群聊模式，防止模式混用';
COMMENT ON CONSTRAINT ai_runtime_execution_pkey ON ai_runtime_execution IS 'AI自有记录唯一主键，不等同宿主用户或租户标识';
COMMENT ON CONSTRAINT ai_runtime_execution_mode_check ON ai_runtime_execution IS '仅允许表定义的AI单聊或群聊模式，防止模式混用';
COMMENT ON CONSTRAINT ai_runtime_member_pkey ON ai_runtime_member IS 'AI自有记录唯一主键，不等同宿主用户或租户标识';
COMMENT ON CONSTRAINT ai_runtime_member_mode_check ON ai_runtime_member IS '仅允许表定义的AI单聊或群聊模式，防止模式混用';
COMMENT ON CONSTRAINT ai_runtime_message_status_check ON ai_runtime_message IS '仅允许生成中完成停止失败四种状态，终态由生成态CAS转换';
COMMENT ON CONSTRAINT ai_runtime_execution_status_check ON ai_runtime_execution IS '仅允许生成中完成停止失败四种状态，终态由生成态CAS转换';
COMMENT ON CONSTRAINT ai_runtime_message_role_check ON ai_runtime_message IS '消息角色只允许用户或助手';
ALTER TABLE ai_runtime_conversation ADD CONSTRAINT ck_ai_runtime_conversation_pin CHECK ((pinned AND pinned_at IS NOT NULL) OR (NOT pinned AND pinned_at IS NULL));
COMMENT ON CONSTRAINT ck_ai_runtime_conversation_pin ON ai_runtime_conversation IS '置顶状态与置顶时间必须一致，取消置顶不能保留旧排序时间';
CREATE UNIQUE INDEX uk_ai_runtime_share_code ON ai_runtime_conversation(share_code) WHERE share_code IS NOT NULL;
COMMENT ON INDEX uk_ai_runtime_share_code IS '非空分享能力码全局唯一，碰撞必须回滚后开启新事务重试';
ALTER TABLE ai_runtime_conversation ADD CONSTRAINT ck_ai_runtime_share_state CHECK ((share_status=0 AND share_code IS NULL AND share_expire_at IS NULL) OR (share_status=1 AND share_code IS NOT NULL AND share_expire_at IS NOT NULL));
COMMENT ON CONSTRAINT ck_ai_runtime_share_state ON ai_runtime_conversation IS '分享开关码和到期时间一致，撤销必须清除公开入口';
ALTER TABLE ai_runtime_conversation ADD CONSTRAINT ck_ai_runtime_share_code CHECK (share_code IS NULL OR share_code ~ '^[0-9a-f]{32}$');
COMMENT ON CONSTRAINT ck_ai_runtime_share_code ON ai_runtime_conversation IS '保持旧公开接口兼容的32位小写hex分享码';
ALTER TABLE ai_runtime_conversation ADD CONSTRAINT ck_ai_runtime_share_count CHECK (share_access_count >= 0);
COMMENT ON CONSTRAINT ck_ai_runtime_share_count ON ai_runtime_conversation IS '累计成功访问次数不得为负';
COMMIT;

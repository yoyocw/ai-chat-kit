-- AI 自有表最小增量；在应用使用新列之前执行。仅改变 ai_runtime_member，不操作旧林业表。
-- PowerShell 执行示例（使用受控连接环境，不将密码写入命令）：
-- 从仓库根目录执行：psql -X -v ON_ERROR_STOP=1 -f "ai-chat-kit-components/ai-chat-kit-adapter-mybatis-plus/src/main/resources/db/postgresql/upgrade-ai-runtime-member-snapshots.sql"
-- 本脚本可重复执行；旧行保持 NULL，不以 agent_code 或虚构职责填充。
-- 旧活跃会话须由管理员依据真实目录/历史资料逐行核对后回填；也可经已授权成员更新入口重新选择成员，形成新的真实快照。
-- 未补全前列表逐会话标记 INCOMPLETE，成员详情为空并保留原编码；已授权成员更新可重新选择真实成员恢复。
-- 公开分享和发送仍拒绝缺失快照。禁止未经核对将当前目录当作历史事实批量覆盖。
BEGIN;
ALTER TABLE ai_runtime_member ADD COLUMN IF NOT EXISTS agent_name varchar(256);
ALTER TABLE ai_runtime_member ADD COLUMN IF NOT EXISTS agent_role varchar(4000);
COMMENT ON COLUMN ai_runtime_member.agent_name IS '创建或调整成员时由真实目录解析并保存的名称快照；升级旧行NULL需真实数据补全';
COMMENT ON COLUMN ai_runtime_member.agent_role IS '创建或调整成员时由真实目录解析并保存的职责快照；升级旧行NULL需真实数据补全';
DO $migration$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'ai_runtime_member'::regclass AND conname = 'ck_ai_runtime_member_snapshot') THEN
        ALTER TABLE ai_runtime_member ADD CONSTRAINT ck_ai_runtime_member_snapshot
            CHECK ((agent_name IS NULL AND agent_role IS NULL)
                OR (agent_name IS NOT NULL AND agent_role IS NOT NULL
                    AND length(btrim(agent_name)) > 0 AND length(btrim(agent_role)) > 0));
    END IF;
END;
$migration$;
COMMENT ON CONSTRAINT ck_ai_runtime_member_snapshot ON ai_runtime_member IS '快照必须完整非空；升级历史允许成对NULL待真实资料补全，读取不提供默认值';
COMMIT;

-- 执行后验证：确认两个列存在且类型正确。
SELECT column_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name = 'ai_runtime_member'
  AND column_name IN ('agent_name', 'agent_role') ORDER BY column_name;
-- 查找需补全的活跃关系，仅显示归属和成员编码，不输出职责正文。
SELECT namespace, tenant_id, actor_id, conversation_id, agent_code
FROM ai_runtime_member
WHERE deleted = false AND (agent_name IS NULL OR agent_role IS NULL
    OR length(btrim(agent_name)) = 0 OR length(btrim(agent_role)) = 0)
ORDER BY namespace, tenant_id, actor_id, conversation_id, sort_order;

-- 先执行本增量再部署 AI；保留旧消息，未知应用的远端会话在下次发送时自动重建。
BEGIN;
SET LOCAL lock_timeout = '5s';
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS bailian_app_id VARCHAR(32);
ALTER TABLE ai_chat_conversation ADD COLUMN IF NOT EXISTS bailian_turn_id BIGINT;
COMMENT ON COLUMN ai_chat_conversation.bailian_app_id IS '远端会话所属百炼应用，空值表示旧会话来源未知，不得直接复用';
COMMENT ON COLUMN ai_chat_conversation.bailian_turn_id IS '当前远端调用的助手消息编号，用于阻止超时旧调用清除或覆盖新会话';
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS bailian_app_id VARCHAR(32);
ALTER TABLE ai_group_chat_conversation ADD COLUMN IF NOT EXISTS bailian_turn_id BIGINT;
COMMENT ON COLUMN ai_group_chat_conversation.bailian_app_id IS '群聊远端会话所属百炼应用，空值表示来源未知，不得直接复用';
COMMENT ON COLUMN ai_group_chat_conversation.bailian_turn_id IS '当前群聊助手占位消息编号，阻止迟到结果覆盖新一轮会话';
COMMIT;

-- 不读取消息或凭据，仅验证升级列定义。
SELECT table_name, column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = current_schema() AND table_name IN ('ai_chat_conversation', 'ai_group_chat_conversation')
  AND column_name IN ('bailian_app_id', 'bailian_turn_id')
ORDER BY table_name, column_name;

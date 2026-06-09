-- ============================================================
-- 聊天记忆表 —— 用于持久化 Spring AI 的对话消息
-- ============================================================
-- 【设计思路】
--
-- Spring AI 的 Message 是一个接口，有 4 个子类：
--   UserMessage、AssistantMessage、SystemMessage、ToolResponseMessage
--
-- 如果用 JSON 或 Kryo 序列化整个 Message 对象来存储，会面临多态反序列化的复杂性。
-- 因此采用 Spring AI 官方 JdbcChatMemoryRepository 相同的策略：
--   把 Message 拆解为简单字段（文本内容 + 类型标记），存入关系表。
--   读取时根据 type 列手动还原正确的 Message 子类。
--
-- 这样做的优点：
--   1. 无需任何序列化框架，不存在多态序列化问题
--   2. 数据可读性强，可以直接在数据库中查看和修改
--   3. 方便按会话、时间等维度查询和分析
-- ============================================================

CREATE TABLE IF NOT EXISTS `chat_memory` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    `conversation_id` VARCHAR(100) NOT NULL             COMMENT '会话 ID，用于区分不同对话',
    `content`         TEXT                                COMMENT '消息文本内容（Message.getText()）',
    `type`            VARCHAR(20)  NOT NULL              COMMENT '消息类型：USER / ASSISTANT / SYSTEM / TOOL（Message.getMessageType().name()）',
    `created_at`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消息创建时间',

    -- 按会话查询是最频繁的操作，建立索引加速查询
    INDEX `idx_conversation_id` (`conversation_id`),

    -- 按时间排序也是常见需求（同一会话内消息需要按时间顺序返回）
    INDEX `idx_conversation_time` (`conversation_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 对话记忆持久化表';

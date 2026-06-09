package com.muzi.muaiagent.chatmemory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ============================================================
 * 自定义数据库持久化聊天记忆 —— 学习版
 * ============================================================
 *
 * 实现 {@link ChatMemoryRepository} 接口，将对话消息保存到 MySQL 数据库。
 *
 * 【核心设计：如何解决 Message 接口的多态序列化问题？】
 *
 * Spring AI 的 Message 是接口，有 UserMessage / AssistantMessage / SystemMessage / ToolResponseMessage
 * 四种实现。如果直接序列化整个对象（如 JSON），反序列化时无法知道该还原为哪个子类。
 *
 * 本类采用 Spring AI 官方 JdbcChatMemoryRepository 的策略：
 *
 *   存储时：把 Message 拆解为两个简单字段
 *     - content（String）：消息的文本内容，来自 message.getText()
 *     - type（String）：消息类型枚举名，来自 message.getMessageType().name()
 *       值为 "USER" / "ASSISTANT" / "SYSTEM" / "TOOL"
 *
 *   读取时：根据 type 字段的值，用 switch 手动构造正确的 Message 子类
 *     "USER"      → new UserMessage(content)
 *     "ASSISTANT" → new AssistantMessage(content)
 *     "SYSTEM"    → new SystemMessage(content)
 *     "TOOL"      → new ToolResponseMessage(List.of())
 *
 * 这种方式完全绕开了多态序列化问题，是最简洁、最可控的持久化方案。
 *
 * 【与 Kryo 方案的对比】
 *
 *   | 特性         | Kryo（FileBasedChatMemory）| 数据库（本类）          |
 *   |-------------|---------------------------|------------------------|
 *   | 多态处理     | Kryo 自动嵌入类信息        | 手动拆解 + switch 还原  |
 *   | 数据可读性   | 二进制，不可读             | 明文 SQL，可直接查看     |
 *   | 字段完整性   | 完整保存所有字段            | 只保存 text 和 type     |
 *   | 分布式支持   | 不支持（本地文件）          | 天然支持（共享数据库）   |
 *   | 查询能力     | 只能按会话 ID 全量加载      | 可按时间、类型等灵活查询  |
 *
 * 【使用方式】
 *
 *   在 InterViewApp 构造器中替换 ChatMemoryRepository 即可：
 *
 *   // 方式一：文件持久化（Kryo）
 *   ChatMemoryRepository repository = new FileBasedChatMemory(fileDir);
 *
 *   // 方式二：数据库持久化（本类） ← 需要先创建 chat_memory 表
 *   ChatMemoryRepository repository = new DbChatMemoryRepository(jdbcTemplate);
 *
 *   // 方式三：内存（开发调试用）
 *   ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
 *
 * @see ChatMemoryRepository  —— Spring AI 定义的存储接口
 * @see FileBasedChatMemory   —— Kryo 文件持久化实现（对比参考）
 */
public class DbChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;

    // ========================================
    // SQL 语句常量 —— 对应 chat_memory_schema.sql 中的表结构
    // ========================================

    /** 查询所有会话 ID（去重） */
    private static final String SQL_FIND_CONVERSATION_IDS =
            "SELECT DISTINCT conversation_id FROM chat_memory";

    /** 查询指定会话的所有消息，按创建时间升序排列（保证消息顺序） */
    private static final String SQL_FIND_BY_CONVERSATION =
            "SELECT content, type FROM chat_memory WHERE conversation_id = ? ORDER BY created_at";

    /** 插入一条消息记录 */
    private static final String SQL_INSERT_MESSAGE =
            "INSERT INTO chat_memory (conversation_id, content, type, created_at) VALUES (?, ?, ?, ?)";

    /** 删除指定会话的所有消息 */
    private static final String SQL_DELETE_BY_CONVERSATION =
            "DELETE FROM chat_memory WHERE conversation_id = ?";

    /**
     * 构造方法，注入 JdbcTemplate。
     * JdbcTemplate 是 Spring 对 JDBC 的封装，简化了数据库操作。
     *
     * @param jdbcTemplate Spring 的 JdbcTemplate Bean
     */
    public DbChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 查询所有存在对话记录的会话 ID 列表。
     *
     * 对应 SQL: SELECT DISTINCT conversation_id FROM chat_memory
     *
     * @return 会话 ID 列表
     */
    @Override
    public List<String> findConversationIds() {
        return jdbcTemplate.queryForList(SQL_FIND_CONVERSATION_IDS, String.class);
    }

    /**
     * 根据会话 ID 查询该会话的所有消息。
     *
     * 【这是理解多态还原的关键方法】
     *
     * 数据库中存储的是 content（文本）和 type（类型字符串），
     * 这里需要把每一行还原为正确的 Message 子类。
     *
     * 核心逻辑在 mapRow() 的 switch 语句中：
     *   - 读取 type 列 → 转换为 MessageType 枚举
     *   - 根据枚举值 → new 出对应的 Message 子类
     *
     * 这就是 Spring AI 官方 JdbcChatMemoryRepository.MessageRowMapper 的做法。
     *
     * @param conversationId 会话 ID
     * @return 该会话的消息列表，已按时间排序
     */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        return jdbcTemplate.query(SQL_FIND_BY_CONVERSATION, (rs, rowNum) -> {
            // 从数据库读取两个简单字段
            String content = rs.getString("content");
            String typeStr = rs.getString("type");

            // 将字符串转回 MessageType 枚举
            MessageType type = MessageType.valueOf(typeStr);

            // ★ 核心：根据类型手动构造正确的 Message 子类 ★
            // 这就是解决多态序列化问题的关键 —— 不依赖任何序列化框架的类型推断，
            // 而是用显式的 switch 分支来确保类型正确
            return switch (type) {
                case USER -> new UserMessage(content);
                case ASSISTANT -> new AssistantMessage(content);
                case SYSTEM -> new SystemMessage(content);
                // ToolResponseMessage 比较特殊：它的实际内容（工具调用结果）
                // 结构复杂，在简化存储中丢失了。这里创建一个空实现。
                // 对于聊天记忆场景，工具调用记录通常不需要完整还原。
                case TOOL -> new ToolResponseMessage(List.of());
            };
        }, conversationId);
    }

    /**
     * 保存指定会话的所有消息（全量替换）。
     *
     * 【保存策略：先删后插】
     * 和 Spring AI 官方实现一样，采用"先删除旧消息，再插入新消息"的策略。
     * 这是因为 MessageWindowChatMemory 会做滑动窗口截断，
     * 每次保存的消息列表可能就是截断后的结果，需要覆盖而非追加。
     *
     * 【时间戳策略】
     * 使用 AtomicLong 递增的时间戳，确保同一批次插入的消息有严格的时间顺序。
     * 起始值是当前时间的毫秒数，每条消息 +1ms。
     *
     * @param conversationId 会话 ID
     * @param messages       要保存的消息列表
     */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        // 步骤 1：删除该会话的旧消息（全量替换策略）
        deleteByConversationId(conversationId);

        // 步骤 2：批量插入新消息
        // 使用 AtomicLong 保证每条消息的时间戳严格递增
        // 起始时间为当前时间，每插入一条 +1ms
        AtomicLong timestampSeq = new AtomicLong(Instant.now().toEpochMilli());

        jdbcTemplate.batchUpdate(SQL_INSERT_MESSAGE, messages, messages.size(), (ps, message) -> {
            ps.setString(1, conversationId);
            ps.setString(2, message.getText());                    // 存文本内容
            ps.setString(3, message.getMessageType().name());      // 存类型："USER"/"ASSISTANT"/...
            ps.setTimestamp(4, new Timestamp(timestampSeq.getAndIncrement()));  // 递增时间戳
        });
    }

    /**
     * 删除指定会话的所有消息。
     *
     * @param conversationId 会话 ID
     */
    @Override
    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update(SQL_DELETE_BY_CONVERSATION, conversationId);
    }
}

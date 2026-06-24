package com.muzi.muaiagent.app;

import com.muzi.muaiagent.advisor.MyLoggerAdvisor;
import com.muzi.muaiagent.advisor.SensitiveWordAdvisor;
import com.muzi.muaiagent.chatmemory.FileBasedChatMemory;
import com.muzi.muaiagent.filter.SensitiveWordFilter;
import com.muzi.muaiagent.service.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
/**
 * 面试助手应用组件。
 *
 * 【Advisor 链的完整执行流程】
 *
 * 当调用 doChat() 时，请求会依次经过以下 Advisor：
 *
 *   用户输入
 *     ↓
 *   ① SensitiveWordAdvisor.before()  ← 最先执行（order=0），过滤敏感词
 *     ↓
 *   ② MessageChatMemoryAdvisor       ← 将用户消息追加到对话记忆中
 *     ↓
 *   ③ MyLoggerAdvisor                ← 记录请求日志
 *     ↓
 *   DashScope AI 模型（qwen-plus）
 *     ↓
 *   ③ MyLoggerAdvisor.after()        ← 记录响应日志
 *     ↓
 *   ② MessageChatMemoryAdvisor       ← （对响应无特殊处理）
 *     ↓
 *   ① SensitiveWordAdvisor.after()   ← 透传，不修改 AI 输出
 *     ↓
 *   返回给用户
 */
@Slf4j
@Component
public class InterViewApp {

    private final ChatClient chatClient;

    /**
     * 系统提示词模板名称（对应 resources/prompts/interview-system.txt）。
     * 将系统提示词外置到资源文件中，便于运营人员随时调整话术，无需修改代码和重新编译。
     */
    private static final String SYSTEM_PROMPT_TEMPLATE = "interview-system";

    /**
     * 构造器注入所有依赖。
     *
     * 【ChatMemoryRepository 的可替换实现】
     * Spring AI 提供了多种 ChatMemoryRepository 实现，可灵活切换：
     *   - InMemoryChatMemoryRepository：内存存储，重启丢失（开发调试用）
     *   - FileBasedChatMemory：文件持久化（本项目自定义实现，用 Kryo 序列化）
     *   - DbChatMemoryRepository：数据库持久化（本项目自定义实现，手动拆解 Message 字段）
     *   - JdbcChatMemoryRepository：Spring AI 官方数据库实现（生产环境推荐）
     *
     * 三种存储方式的核心区别在于如何处理 Message 接口的多态序列化：
     *   - Kryo：二进制序列化，自动嵌入类信息，反序列化时自动还原正确子类
     *   - 数据库（Db/Jdbc）：拆解为 content + type 两个字段存储，读取时 switch 手动还原子类
     *
     * @param dashscopeChatModel    DashScope 聊天模型
     * @param sensitiveWordFilter   敏感词过滤器 Bean
     * @param promptTemplateService Prompt 模板加载服务，用于从资源文件读取系统提示词
     */
    public InterViewApp(ChatModel dashscopeChatModel, SensitiveWordFilter sensitiveWordFilter,
                        PromptTemplateService promptTemplateService) {
        // ========================================
        // 存储方式一：文件持久化（FileBasedChatMemory + Kryo）
        // 对话记录以 Kryo 二进制格式保存到 chat-memory/ 目录，重启不丢失
        // Kryo 自动处理 Message 接口的多态序列化（UserMessage/AssistantMessage 等）
        // ========================================
        String fileDir = System.getProperty("user.dir") + "/chat-memory";
        ChatMemoryRepository repository = new FileBasedChatMemory(fileDir);

        // ========================================
        // 存储方式二：内存存储（InMemoryChatMemoryRepository）
        // 简单轻量，但重启后对话历史丢失。适合开发调试。
        // ========================================
        // ChatMemoryRepository repository = new InMemoryChatMemoryRepository();

        // ========================================
        // 存储方式三：数据库持久化（DbChatMemoryRepository）
        // 需要先执行 db/chat_memory_schema.sql 创建表，并配置数据源。
        // 该实现手动拆解 Message 为 content + type 字段，读取时 switch 还原子类，
        // 完全绕开了多态序列化问题。
        //
        // 使用方式（需要注入 JdbcTemplate，并移除 DataSourceAutoConfiguration 排除）：
        // ChatMemoryRepository repository = new DbChatMemoryRepository(jdbcTemplate);
        // ========================================

        // MessageWindowChatMemory：滑动窗口式的记忆管理
        // maxMessages=10 表示只保留最近 10 条消息，防止上下文过长导致 token 超限
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();

        // 构建 ChatClient —— Spring AI 的核心对话客户端
        chatClient = ChatClient.builder(dashscopeChatModel)
                // 从资源文件加载系统提示词（而非硬编码），修改提示词只需编辑 txt 文件
                .defaultSystem(promptTemplateService.loadTemplate(SYSTEM_PROMPT_TEMPLATE))
                .defaultAdvisors(
                        // Advisor 1：对话记忆 —— 让 AI 能"记住"之前的对话上下文
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),

                        // Advisor 2：敏感词过滤（order=0，最先执行）
                        new SensitiveWordAdvisor(sensitiveWordFilter),

                        // Advisor 3：自定义日志拦截器
                        MyLoggerAdvisor.builder().build()
                )
                .build();
    }

    /**
     * 发送用户消息并获取 AI 回复（同步调用）。
     *
     * 【调用链路】
     *   1. chatClient.prompt()     → 创建一个新的 Prompt 构建器
     *   2. .user(message)          → 设置用户消息
     *   3. .advisors(a -> ...)     → 设置运行时参数（如会话 ID，用于关联对话记忆）
     *   4. .call()                 → 发起同步调用（阻塞等待 AI 响应）
     *   5. .chatResponse()         → 获取 ChatResponse 对象
     *
     * 在这个过程中，所有已注册的 Advisor 会自动按 order 顺序执行 before/after。
     *
     * @param message 用户输入的消息文本
     * @param chatId  会话 ID（用于区分不同用户的对话，ChatMemory 根据此 ID 隔离消息）
     * @return AI 回复的文本内容
     */
    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = null;
        if (response != null) {
            content = response.getResult().getOutput().getText();
        }
        log.info("content: {}", content);
        return content;


    }


}

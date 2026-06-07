package com.muzi.muaiagent.app;

import com.muzi.muaiagent.advisor.MyLoggerAdvisor;
import com.muzi.muaiagent.advisor.SensitiveWordAdvisor;
import com.muzi.muaiagent.filter.SensitiveWordFilter;
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

    private static final String SYSTEM_PROMPT = "你是面试助手，专注于 Java 后端工程师求职面试的复习陪练。\n" +
            "采用\"以教促学\"策略，通过**纯提示 + 逐题交互**模式帮助用户掌握面试知识点。";

    /**
     * 构造器注入所有依赖。
     *
     * 【依赖注入说明】
     * Spring 会自动注入以下 Bean：
     *   - dashscopeChatModel：阿里云 DashScope 的 AI 模型（由 spring-ai-alibaba-starter-dashscope 自动配置）
     *   - sensitiveWordFilter：我们自定义的敏感词过滤器（@Component 标注的 SensitiveWordFilter Bean）
     *
     * @param dashscopeChatModel DashScope 聊天模型
     * @param sensitiveWordFilter 敏感词过滤器 Bean
     */
    public InterViewApp(ChatModel dashscopeChatModel, SensitiveWordFilter sensitiveWordFilter) {
        // 初始化基于内存的对话记忆
        // ChatMemoryRepository 是消息存储的抽象，InMemoryChatMemoryRepository 使用内存存储
        // （生产环境可替换为 Redis/MySQL 实现持久化）
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();

        // MessageWindowChatMemory：滑动窗口式的记忆管理
        // maxMessages=10 表示只保留最近 10 条消息，防止上下文过长导致 token 超限
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();

        // 构建 ChatClient —— Spring AI 的核心对话客户端
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)       // 设置系统提示词（角色设定）
                .defaultAdvisors(
                        // Advisor 1：对话记忆 —— 让 AI 能"记住"之前的对话上下文
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),

                        // Advisor 2：敏感词过滤（order=0，最先执行）
                        // 在请求到达模型之前，先清洗用户输入中的敏感词
                        // sensitiveWordFilter 是 Spring 自动注入的 Bean
                        new SensitiveWordAdvisor(sensitiveWordFilter),

                        // Advisor 3：自定义日志拦截器 —— 记录请求和响应的详细内容
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

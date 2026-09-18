package com.muzi.muaiagent.rag.app;

import com.muzi.muaiagent.advisor.MyLoggerAdvisor;
import com.muzi.muaiagent.rag.loader.DocumentLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * RAG 问答应用：结合向量检索 + ChatClient 实现知识库问答。
 *
 * 【执行流程】
 *   用户提问
 *     ↓
 *   ① MessageChatMemoryAdvisor      ← 对话记忆（多轮上下文），最先执行
 *     ↓
 *   ② RetrievalAugmentationAdvisor  ← 预检索加工 → 向量库检索 → 上下文拼入 prompt
 *     ↓
 *   ③ MyLoggerAdvisor               ← 记录请求/响应日志，最后执行
 *     ↓
 *   DashScope AI 模型（qwen-plus）
 *     ↓
 *   返回基于知识库的回答
 *
 * <h2>关于 advisor 的顺序</h2>
 * <p>
 * 三个 advisor 都显式指定了 order，因为<b>顺序在这里是有语义的，不是风格问题</b>：
 * <ul>
 *     <li>记忆 advisor 必须最先。它把对话历史写进 prompt，而
 *         {@code RetrievalAugmentationAdvisor} 在 {@code before} 阶段正是从
 *         {@code prompt.getInstructions()} 里读历史去做多轮指代消解的。
 *         顺序反了不会报错，只是「它和实体有什么区别？」里的「它」永远指不明白，
 *         检索质量悄悄退化 —— 所以这里显式钉住，不依赖框架默认值凑巧正确。</li>
 *     <li>检索增强居中，order 由 {@code PreRetrievalConfig} 从配置读取后设置。</li>
 *     <li>日志 advisor 最后。它记录的是「模型真正看到的请求」，也就包含了检索注入的上下文；
 *         若排在检索之前，日志里只有用户原话，排查「检索到了什么」时无从下手。</li>
 * </ul>
 */
@Slf4j
@Component
public class RagApp {

    /**
     * 对话记忆 advisor 的执行顺序。取框架默认值（已从字节码确认为 {@code -2147482648}），
     * 语义是「最先执行」——它得先把对话历史写进 prompt，预检索才能做多轮指代消解。
     * 提为常量是为了让 {@code PreRetrievalConfigTest} 能与预检索 advisor 的 order
     * 做相对比较，从而把「顺序不能反」这条不变量固化成断言。
     */
    public static final int MEMORY_ADVISOR_ORDER = Ordered.HIGHEST_PRECEDENCE + 1000;

    /**
     * 日志 advisor 的执行顺序。取 1 表示「最后执行」，
     * 这样日志记录到的是已经注入知识库上下文的最终请求。
     * 提为常量的理由同上。
     */
    public static final int LOGGER_ADVISOR_ORDER = 1;

    private final ChatClient chatClient;
    private final DocumentLoader documentLoader;
    private final VectorStore vectorStore;

    public RagApp(ChatModel chatModel,
                  VectorStore vectorStore,
                  DocumentLoader documentLoader,
                  RetrievalAugmentationAdvisor retrievalAugmentationAdvisor) {
        this.vectorStore = vectorStore;
        this.documentLoader = documentLoader;

        // 对话记忆：内存存储 + 滑动窗口
        var chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(10)
                .build();

        chatClient = ChatClient.builder(chatModel)
                .defaultSystem("你是一个专业的技术问答助手，请根据提供的知识库文档回答用户问题。如果知识库中没有相关信息，请如实告知。")
                .defaultAdvisors(
                        // Advisor 1：对话记忆 —— 多轮对话上下文。
                        // order 写死为「最先执行」，理由见类注释：预检索要靠它注入的历史才能消解代词。
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .order(MEMORY_ADVISOR_ORDER)
                                .build(),
                        // Advisor 2：检索增强 —— 预检索加工 + 向量检索 + 上下文注入。
                        // order 由 PreRetrievalConfig 显式设置（默认 0），必须落在记忆之后、日志之前。
                        retrievalAugmentationAdvisor,
                        // Advisor 3：日志记录 —— 放在最后，记录到的是注入知识库上下文之后的最终请求
                        MyLoggerAdvisor.builder().order(LOGGER_ADVISOR_ORDER).build()
                )
                .build();
    }

    /**
     * 初始化知识库：解析文档并写入向量存储。
     * 应在调用 doChatWithRag 之前调用。
     *
     * @return 导入的文档数量
     */
    public int initKnowledgeBase() throws IOException {
        int count = documentLoader.loadDddDocuments();
        log.info("知识库初始化完成，共导入 {} 个文档", count);
        return count;
    }

    /**
     * RAG 问答：结合知识库检索 + AI 对话。
     *
     * @param message 用户问题
     * @param chatId  会话 ID（用于对话记忆隔离）
     * @return AI 基于知识库的回答
     */
    public String doChatWithRag(String message, String chatId) {
        ChatResponse chatResponse = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();

        String content = null;
        if (chatResponse != null && chatResponse.getResult() != null) {
            content = chatResponse.getResult().getOutput().getText();
        }
        log.info("RAG 回答: {}", content);
        return content;
    }
}

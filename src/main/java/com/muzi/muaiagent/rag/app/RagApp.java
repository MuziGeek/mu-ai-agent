package com.muzi.muaiagent.rag.app;

import com.muzi.muaiagent.advisor.MyLoggerAdvisor;
import com.muzi.muaiagent.rag.loader.DocumentLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * RAG 问答应用：结合向量检索 + ChatClient 实现知识库问答。
 *
 * 【执行流程】
 *   用户提问
 *     ↓
 *   ① MessageChatMemoryAdvisor  ← 对话记忆（多轮上下文）
 *     ↓
 *   ② QuestionAnswerAdvisor     ← 从向量存储检索相关文档片段，拼入 prompt
 *     ↓
 *   ③ MyLoggerAdvisor           ← 记录请求/响应日志
 *     ↓
 *   DashScope AI 模型（qwen-plus）
 *     ↓
 *   返回基于知识库的回答
 */
@Slf4j
@Component
public class RagApp {

    private final ChatClient chatClient;
    private final DocumentLoader documentLoader;
    private final VectorStore vectorStore;

    public RagApp(ChatModel chatModel, VectorStore vectorStore, DocumentLoader documentLoader) {
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
                        // Advisor 1：对话记忆 —— 多轮对话上下文
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // Advisor 2：RAG 检索 —— 自动从向量存储中检索相关文档拼入 prompt
                        QuestionAnswerAdvisor.builder(vectorStore).build(),
                        // Advisor 3：日志记录
                        MyLoggerAdvisor.builder().build()
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

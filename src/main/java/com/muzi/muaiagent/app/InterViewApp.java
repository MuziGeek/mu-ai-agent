package com.muzi.muaiagent.app;

import com.muzi.muaiagent.advisor.MyLoggerAdvisor;
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
@Slf4j
@Component
public class InterViewApp {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "你是面试助手，专注于 Java 后端工程师求职面试的复习陪练。\n" +
            "采用\"以教促学\"策略，通过**纯提示 + 逐题交互**模式帮助用户掌握面试知识点。";

    public InterViewApp(ChatModel dashscopeChatModel) {
        // 初始化基于内存的对话记忆
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        //自定义日志拦截器
                        MyLoggerAdvisor.builder().build()
                )
                .build();
    }

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

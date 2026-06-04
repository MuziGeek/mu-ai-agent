package com.muzi.muaiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Spring AI框架调用ai大模型
 */
@Component
public class SpringAiInvoke implements CommandLineRunner {

    @Resource
    private ChatModel dashscopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        String text = dashscopeChatModel.call(new Prompt("你好 ，我是木子！")).getResult().getOutput().getText();
        System.out.println(text);
    }


}

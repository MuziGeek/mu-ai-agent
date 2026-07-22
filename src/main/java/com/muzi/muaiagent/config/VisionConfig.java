package com.muzi.muaiagent.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多模态视觉模型配置。
 *
 * 通过 DashScopeChatOptions 在请求级别切换到 Qwen-VL 模型，
 * 并开启 multiModel=true 将请求路由到多模态生成端点。
 * 无需创建第二个 ChatModel Bean，复用现有的 dashscopeChatModel 即可。
 */
@Configuration
public class VisionConfig {

    @Value("${spring.ai.dashscope.vision.model:qwen-vl-max}")
    private String visionModel;

    /**
     * 视觉多模态请求选项。
     * <p>
     * 关键设置：withMultiModel(true)
     * 没有这个标志，DashScope SDK 会将请求发送到文本生成端点，
     * 该端点不接受图片载荷，会返回 "url error" 错误。
     */
    @Bean
    public DashScopeChatOptions visionChatOptions() {
        return DashScopeChatOptions.builder()
                .withModel(visionModel)
                .withMultiModel(true)
                .build();
    }
}

package com.muzi.muaiagent.app;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.Base64;
import java.util.List;

/**
 * 多模态视觉理解应用组件。
 *
 * 复用现有 dashscopeChatModel 的 ChatModel Bean，通过请求级别的
 * DashScopeChatOptions 切换到 Qwen-VL 模型并开启 multiModel=true。
 *
 * 支持三种图片输入方式：
 *   1. 公网 URL —— 直接传入图片链接
 *   2. 文件上传 —— 通过 MultipartFile 上传
 *   3. Base64 —— 传入 Base64 编码的图片数据
 *
 * 无状态设计，不使用 Advisor 和聊天记忆，每次请求独立处理。
 */
@Slf4j
@Component
public class MultimodalApp {

    private final ChatClient chatClient;
    private final DashScopeChatOptions visionChatOptions;

    public MultimodalApp(ChatModel dashscopeChatModel,
                         DashScopeChatOptions visionChatOptions) {
        this.visionChatOptions = visionChatOptions;
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    /**
     * 通过公网 URL 分析图片（自动检测 MIME 类型）。
     *
     * @param imageUrl 图片的公网可访问 URL
     * @param question 提问内容
     * @return VL 模型的回答文本
     */
    public String analyzeImageUrl(String imageUrl, String question) {
        // 根据 URL 后缀自动检测 MIME 类型
        MimeType mimeType = detectImageMimeType(imageUrl);
        return analyzeImageUrl(imageUrl, mimeType, question);
    }

    /**
     * 通过公网 URL 分析图片（指定 MIME 类型）。
     *
     * @param imageUrl 图片的公网可访问 URL
     * @param mimeType 图片 MIME 类型
     * @param question 提问内容
     * @return VL 模型的回答文本
     */
    public String analyzeImageUrl(String imageUrl, MimeType mimeType, String question) {
        Media media = new Media(mimeType, URI.create(imageUrl));

        UserMessage message = UserMessage.builder()
                .text(question)
                .media(List.of(media))
                .build();

        ChatResponse response = chatClient.prompt()
                .messages(message)
                .options(visionChatOptions)
                .call()
                .chatResponse();

        return extractContent(response);
    }

    /**
     * 分析上传的图片文件。
     *
     * @param file     上传的图片文件（MultipartFile）
     * @param question 提问内容
     * @return VL 模型的回答文本
     */
    public String analyzeImageFile(MultipartFile file, String question) throws Exception {
        MimeType mimeType = MimeType.valueOf(
                file.getContentType() != null ? file.getContentType() : "image/jpeg");

        Media media = new Media(mimeType, file.getResource());

        UserMessage message = UserMessage.builder()
                .text(question)
                .media(List.of(media))
                .build();

        ChatResponse response = chatClient.prompt()
                .messages(message)
                .options(visionChatOptions)
                .call()
                .chatResponse();

        return extractContent(response);
    }

    /**
     * 分析 Base64 编码的图片。
     *
     * @param base64Image Base64 编码字符串（不含 "data:image/...;base64," 前缀）
     * @param mimeTypeStr MIME 类型字符串，如 "image/png"，为 null 时默认 "image/jpeg"
     * @param question    提问内容
     * @return VL 模型的回答文本
     */
    public String analyzeImageBase64(String base64Image, String mimeTypeStr, String question) {
        MimeType mimeType = MimeType.valueOf(mimeTypeStr != null ? mimeTypeStr : "image/jpeg");
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);

        Media media = new Media(mimeType, new ByteArrayResource(imageBytes));

        UserMessage message = UserMessage.builder()
                .text(question)
                .media(List.of(media))
                .build();

        ChatResponse response = chatClient.prompt()
                .messages(message)
                .options(visionChatOptions)
                .call()
                .chatResponse();

        return extractContent(response);
    }

    /**
     * 根据 URL 后缀检测图片 MIME 类型，默认 image/jpeg。
     */
    private MimeType detectImageMimeType(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".png")) return MimeTypeUtils.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MimeTypeUtils.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MimeType.valueOf("image/webp");
        if (lower.endsWith(".bmp")) return MimeType.valueOf("image/bmp");
        return MimeTypeUtils.IMAGE_JPEG;
    }

    private String extractContent(ChatResponse response) {
        if (response != null && response.getResult() != null) {
            String content = response.getResult().getOutput().getText();
            log.info("VL response: {}", content);
            return content;
        }
        return null;
    }
}

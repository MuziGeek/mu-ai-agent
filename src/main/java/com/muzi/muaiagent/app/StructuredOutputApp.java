package com.muzi.muaiagent.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muzi.muaiagent.model.InterviewQuestion;
import com.muzi.muaiagent.model.InterviewStudyPlan;
import com.muzi.muaiagent.service.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 结构化输出演示服务。
 * <p>
 * 演示 Spring AI 中 5 种将大模型返回转换为 Java 对象的方式：
 * <pre>
 *   方式 1：entity(Class)               → 单个 Bean
 *   方式 2：entity(ParameterizedTypeReference) → List
 *   方式 3：entity(ParameterizedTypeReference) → Map
 *   方式 4：entity(Class) 嵌套对象       → 复杂嵌套 Bean
 *   方式 5：手动 Prompt + Jackson 解析   → 自定义控制
 * </pre>
 * <p>
 * 每种方式对应一个方法，配合单元测试逐一验证。
 */
@Slf4j
@Component
public class StructuredOutputApp {

    private final ChatClient chatClient;

    /**
     * Prompt 模板加载服务，用于从 resources/prompts/ 目录读取模板文件并填充变量。
     * 将 prompt 文本从 Java 代码中分离出来，便于独立维护和调优。
     */
    private final PromptTemplateService promptTemplateService;

    /**
     * JSON 格式描述常量，供"手动解析"模式的模板变量 {formatInstruction} 使用。
     * JSON 中的大括号 {} 会和 PromptTemplate 的 ST4 变量占位符冲突，
     * 所以将 JSON 格式作为变量值传入，而非写在模板文件中。
     */
    private static final String JSON_FORMAT_INSTRUCTION = """
            请严格按照以下 JSON 格式返回，不要包含任何额外说明或 markdown 代码块标记：
            {
              "question": "面试问题",
              "referenceAnswer": "参考答案",
              "keyPoints": ["要点1", "要点2"],
              "difficultyLevel": "入门/中级/高级",
              "followUpDirections": ["追问方向1"],
              "techCategory": "技术分类"
            }""";

    /**
     * @param dashscopeChatModel    DashScope 聊天模型（由 spring-ai-alibaba 自动注入）
     * @param promptTemplateService Prompt 模板加载服务
     */
    public StructuredOutputApp(ChatModel dashscopeChatModel, PromptTemplateService promptTemplateService) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
        this.promptTemplateService = promptTemplateService;
    }

    // ============================================================
    // 方式 1：单个 Bean —— entity(Class<T>)
    // ============================================================

    /**
     * 获取单个面试题的结构化输出。
     * <p>
     * 原理：
     *   1. Spring AI 根据 InterviewQuestion 的字段自动生成 JSON Schema
     *   2. 在 prompt 末尾追加格式指令（告诉模型"你必须返回符合这个 Schema 的 JSON"）
     *   3. 模型返回 JSON 字符串
     *   4. BeanOutputConverter 用 Jackson 将 JSON 反序列化为 InterviewQuestion 对象
     *
     * @param topic 面试主题（如：JVM 垃圾回收）
     * @return 结构化的面试题对象
     */
    public InterviewQuestion getSingleQuestion(String topic) {
        // 从模板文件 prompts/single-question.txt 加载 prompt，并替换 {topic} 变量
        String promptText = promptTemplateService.render("single-question",
                Map.of("topic", topic));
        return chatClient.prompt()
                .user(promptText)
                .call()
                .entity(InterviewQuestion.class);
    }

    // ============================================================
    // 方式 2：List 输出 —— entity(ParameterizedTypeReference<List<T>>)
    // ============================================================

    /**
     * 获取多个面试题（列表形式）。
     * <p>
     * 为什么不能写 entity(List.class)？
     *   因为 Java 的泛型擦除 —— List.class 在运行时丢失了元素类型信息，
     *   Jackson 无法知道 List 里面应该放 InterviewQuestion 还是 String。
     *   ParameterizedTypeReference 通过匿名子类保留泛型信息来解决这个问题。
     *
     * @param topic 面试主题
     * @param count 题目数量
     * @return 面试题列表
     */
    public List<InterviewQuestion> getQuestionList(String topic, int count) {
        // 从模板文件 prompts/question-list.txt 加载 prompt，替换 {topic} 和 {count}
        String promptText = promptTemplateService.render("question-list",
                Map.of("topic", topic, "count", count));
        return chatClient.prompt()
                .user(promptText)
                .call()
                .entity(new ParameterizedTypeReference<List<InterviewQuestion>>() {});
    }

    // ============================================================
    // 方式 3：Map 输出 —— entity(ParameterizedTypeReference<Map<String, T>>)
    // ============================================================

    /**
     * 获取按分类组织的面试题（Map 形式）。
     * <p>
     * Map 的 key 是分类名称（如"JVM"、"并发"），value 是该分类下的一道题。
     * 同样需要 ParameterizedTypeReference 保留泛型信息。
     * <p>
     * 注意：Map 结构对模型来说更容易"偷懒"返回字符串而非完整对象，
     * 所以 prompt 中需要明确强调 value 必须是包含所有字段的对象。
     *
     * @param categories 分类列表（如：["JVM", "并发", "Spring"]）
     * @return 分类 → 面试题 的映射
     */
    public Map<String, InterviewQuestion> getQuestionMap(List<String> categories) {
        String categoryStr = String.join("、", categories);
        // 从模板文件 prompts/question-map.txt 加载 prompt，替换 {categories}
        String promptText = promptTemplateService.render("question-map",
                Map.of("categories", categoryStr));
        return chatClient.prompt()
                .user(promptText)
                .call()
                .entity(new ParameterizedTypeReference<Map<String, InterviewQuestion>>() {});
    }

    // ============================================================
    // 方式 4：复杂嵌套对象 —— entity(Class<T>) with nested structures
    // ============================================================

    /**
     * 获取完整的面试复习计划（包含嵌套的题目列表）。
     * <p>
     * InterviewStudyPlan 内部嵌套了 List&lt;InterviewQuestion&gt;，
     * Spring AI 会递归生成完整的 JSON Schema（包括嵌套层的字段定义）。
     * 这是最接近真实业务场景的用法。
     *
     * @param techDirection 技术方向
     * @param focusAreas    考察重点
     * @return 结构化的复习计划
     */
    public InterviewStudyPlan getStudyPlan(String techDirection, List<String> focusAreas) {
        String focusStr = String.join("、", focusAreas);
        // 从模板文件 prompts/study-plan.txt 加载 prompt，替换 {techDirection} 和 {focusAreas}
        String promptText = promptTemplateService.render("study-plan",
                Map.of("techDirection", techDirection, "focusAreas", focusStr));
        return chatClient.prompt()
                .user(promptText)
                .call()
                .entity(InterviewStudyPlan.class);
    }

    // ============================================================
    // 方式 5：手动 Prompt + Jackson 解析 —— 自定义控制
    // ============================================================

    /**
     * 手动控制结构化输出：自己在 prompt 中描述 JSON 格式，手动用 Jackson 解析。
     * <p>
     * 与 entity() 的区别：
     *   - entity() 自动生成 JSON Schema 并附加格式指令（全自动）
     *   - 手动方式自己描述格式、自己解析（完全可控）
     * <p>
     * 适用场景：
     *   - 需要更精细的 prompt 控制
     *   - 模型对自动生成的 Schema 理解不好时手动优化
     *   - 需要将 AI 输出嵌入到更大的业务结构中
     *
     * @param topic 面试主题
     * @return 解析后的面试题对象
     */
    public InterviewQuestion getQuestionManual(String topic) {
        // 从模板文件 prompts/question-manual.txt 加载 prompt
        // 模板中使用 {topic} 和 {formatInstruction} 两个变量
        // JSON 格式描述作为 formatInstruction 变量传入，避免大括号与 ST4 模板语法冲突
        String promptText = promptTemplateService.render("question-manual",
                Map.of("topic", topic, "formatInstruction", JSON_FORMAT_INSTRUCTION));

        String rawJson = chatClient.prompt()
                .user(promptText)
                .call()
                .content();

        // 手动清理可能的 markdown 代码块标记
        if (rawJson != null) {
            rawJson = rawJson.trim();
            if (rawJson.startsWith("```json")) {
                rawJson = rawJson.substring(7);
            } else if (rawJson.startsWith("```")) {
                rawJson = rawJson.substring(3);
            }
            if (rawJson.endsWith("```")) {
                rawJson = rawJson.substring(0, rawJson.length() - 3);
            }
            rawJson = rawJson.trim();
        }

        // 手动用 Jackson 反序列化
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(rawJson, InterviewQuestion.class);
        } catch (Exception e) {
            log.error("手动解析 JSON 失败，原始内容: {}", rawJson, e);
            throw new RuntimeException("JSON 解析失败", e);
        }
    }
}

package com.muzi.muaiagent.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muzi.muaiagent.model.InterviewQuestion;
import com.muzi.muaiagent.model.InterviewStudyPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 直接使用 Spring AI {@link PromptTemplate} 的结构化输出演示服务。
 * <p>
 * 与 {@link StructuredOutputApp}（使用自定义 PromptTemplateService）对比：
 * <pre>
 *   StructuredOutputApp        → 通过 PromptTemplateService 间接使用（封装了缓存 + 文件加载）
 *   PromptTemplateDirectApp    → 直接使用 Spring AI 的 PromptTemplate，不依赖自定义工具类
 * </pre>
 * <p>
 * 【PromptTemplate 核心 API】
 * <pre>
 *   // 方式 A：从 Resource 构造（推荐，直接从文件加载）
 *   PromptTemplate pt = new PromptTemplate(new ClassPathResource("prompts/xxx.txt"));
 *   String text   = pt.render(Map.of("key", value));   // 返回替换后的纯文本 String
 *   Prompt prompt = pt.create(Map.of("key", value));   // 返回 Prompt 对象（内含 UserMessage）
 *
 *   // 方式 B：Builder 模式（适合模板字符串来自变量/常量的场景）
 *   PromptTemplate pt = PromptTemplate.builder()
 *           .template("模板文本 {var}")
 *           .variables(Map.of("var", value))
 *           .build();
 *   String text = pt.render();   // 无需再传变量，build 时已绑定
 * </pre>
 * <p>
 * 本类中的 5 个方法均直接使用 PromptTemplate + ClassPathResource，
 * 展示如何将 prompt 模板外置到资源文件，同时保持类型安全的结构化输出。
 */
@Slf4j
@Component
public class PromptTemplateDirectApp {

    private final ChatClient chatClient;

    /**
     * JSON 格式描述模板，供"手动解析"模式（方式 5）使用。
     * <p>
     * 为什么单独定义为一个字符串常量？
     * 因为 question-manual.txt 模板中用 {formatInstruction} 引用它，
     * 而 JSON 里的大括号 {} 会和 PromptTemplate 的变量占位符冲突，
     * 所以将 JSON 格式作为变量传入，避免 ST4 引擎误解析。
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

    public PromptTemplateDirectApp(ChatModel dashscopeChatModel) {
        this.chatClient = ChatClient.builder(dashscopeChatModel).build();
    }

    // ============================================================
    // 方式 1：单个 Bean —— PromptTemplate(Resource).create() → entity(Class)
    // ============================================================

    /**
     * 获取单个面试题的结构化输出。
     * <p>
     * 核心流程：
     * <pre>
     *   1. new PromptTemplate(Resource)  → 从 classpath 加载模板文件
     *   2. .create(variables)            → 填充变量，返回 Prompt 对象
     *   3. chatClient.prompt(prompt)     → 将 Prompt 传给 ChatClient
     *   4. .entity(Class)                → Spring AI 自动附加 JSON Schema，反序列化为 Bean
     * </pre>
     *
     * @param topic 面试主题（如：JVM 垃圾回收）
     * @return 结构化的面试题对象
     */
    public InterviewQuestion getSingleQuestion(String topic) {
        // ① 从 classpath 资源文件创建 PromptTemplate
        //    模板内容：请围绕「{topic}」出一道 Java 后端面试题...
        PromptTemplate template = new PromptTemplate(
                new ClassPathResource("prompts/single-question.txt"));

        // ② create() = render() + 封装为 Prompt 对象
        //    等价于：new Prompt(template.render(Map.of("topic", topic)))
        Prompt prompt = template.create(Map.of("topic", topic));

        // ③ 将 Prompt 传给 ChatClient，entity() 自动处理 JSON Schema + 反序列化
        return chatClient.prompt(prompt)
                .call()
                .entity(InterviewQuestion.class);
    }

    // ============================================================
    // 方式 2：List 输出 —— PromptTemplate.render() → user(String) → entity(List)
    // ============================================================

    /**
     * 获取多个面试题（列表形式）。
     * <p>
     * 与方式 1 的区别：这里演示先用 render() 拿到纯文本，
     * 再通过 .user(renderedText) 设置用户消息。
     * 两种写法效果完全相同，可根据习惯选用。
     * <p>
     * 为什么不能写 entity(List.class)？
     *   因为 Java 泛型擦除，需要用 ParameterizedTypeReference 保留类型信息。
     *
     * @param topic 面试主题
     * @param count 题目数量
     * @return 面试题列表
     */
    public List<InterviewQuestion> getQuestionList(String topic, int count) {
        // ① 从资源文件创建 PromptTemplate
        PromptTemplate template = new PromptTemplate(
                new ClassPathResource("prompts/question-list.txt"));

        // ② render() 返回替换变量后的纯文本 String
        //    模板：请围绕「{topic}」出 {count} 道 Java 后端面试题...
        String promptText = template.render(Map.of("topic", topic, "count", count));

        // ③ 用 .user(text) 方式设置用户消息，效果等同于 .prompt(new Prompt(text))
        return chatClient.prompt()
                .user(promptText)
                .call()
                .entity(new ParameterizedTypeReference<List<InterviewQuestion>>() {});
    }

    // ============================================================
    // 方式 3：Map 输出 —— PromptTemplate + 变量预处理
    // ============================================================

    /**
     * 获取按分类组织的面试题（Map 形式）。
     * <p>
     * 演示一个常见场景：模板中的某个变量需要先在 Java 中预处理
     * （比如将 List 拼接为字符串），再传入 PromptTemplate。
     *
     * @param categories 分类列表（如：["JVM", "并发", "Spring"]）
     * @return 分类 → 面试题 的映射
     */
    public Map<String, InterviewQuestion> getQuestionMap(List<String> categories) {
        // 预处理：将 List 拼接为中文顿号分隔的字符串，供模板使用
        String categoryStr = String.join("、", categories);

        // 从资源文件加载模板并渲染变量
        PromptTemplate template = new PromptTemplate(
                new ClassPathResource("prompts/question-map.txt"));
        Prompt prompt = template.create(Map.of("categories", categoryStr));

        return chatClient.prompt(prompt)
                .call()
                .entity(new ParameterizedTypeReference<Map<String, InterviewQuestion>>() {});
    }

    // ============================================================
    // 方式 4：复杂嵌套对象 —— PromptTemplate(Resource) + entity(嵌套 Class)
    // ============================================================

    /**
     * 获取完整的面试复习计划（包含嵌套的题目列表）。
     * <p>
     * InterviewStudyPlan 内部嵌套了 List&lt;InterviewQuestion&gt;，
     * Spring AI 会递归生成完整的 JSON Schema（包括嵌套层的字段定义）。
     *
     * @param techDirection 技术方向
     * @param focusAreas    考察重点
     * @return 结构化的复习计划
     */
    public InterviewStudyPlan getStudyPlan(String techDirection, List<String> focusAreas) {
        String focusStr = String.join("、", focusAreas);

        // 从资源文件加载模板，填充多个变量
        PromptTemplate template = new PromptTemplate(
                new ClassPathResource("prompts/study-plan.txt"));
        Prompt prompt = template.create(
                Map.of("techDirection", techDirection, "focusAreas", focusStr));

        return chatClient.prompt(prompt)
                .call()
                .entity(InterviewStudyPlan.class);
    }

    // ============================================================
    // 方式 5：手动 Prompt + Jackson 解析 —— PromptTemplate 组合多个变量
    // ============================================================

    /**
     * 手动控制结构化输出：模板文件 + JSON 格式描述组合使用，手动用 Jackson 解析。
     * <p>
     * 【重点】演示 PromptTemplate 处理"模板中嵌入 JSON"的技巧：
     *   - question-manual.txt 模板中用 {topic} 和 {formatInstruction} 两个占位符
     *   - JSON 格式描述作为变量 formatInstruction 传入（而非写在模板文件中）
     *   - 这样避免了 JSON 大括号 {} 被 ST4 引擎误识别为变量占位符
     * <p>
     * 与 entity() 的区别：
     *   - entity() 自动生成 JSON Schema 并附加格式指令（全自动）
     *   - 手动方式自己描述格式、自己解析（完全可控）
     *
     * @param topic 面试主题
     * @return 解析后的面试题对象
     */
    public InterviewQuestion getQuestionManual(String topic) {
        // ① 从资源文件加载模板
        //    模板内容：请围绕「{topic}」出一道 Java 后端面试题。\n{formatInstruction}
        PromptTemplate template = new PromptTemplate(
                new ClassPathResource("prompts/question-manual.txt"));

        // ② 将 JSON 格式描述作为变量传入，避免大括号与 ST4 语法冲突
        String promptText = template.render(Map.of(
                "topic", topic,
                "formatInstruction", JSON_FORMAT_INSTRUCTION
        ));

        // ③ 调用 AI 获取原始 JSON 文本
        String rawJson = chatClient.prompt()
                .user(promptText)
                .call()
                .content();

        // ④ 手动清理可能的 markdown 代码块标记
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

        // ⑤ 手动用 Jackson 反序列化为 Java 对象
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(rawJson, InterviewQuestion.class);
        } catch (Exception e) {
            log.error("手动解析 JSON 失败，原始内容: {}", rawJson, e);
            throw new RuntimeException("JSON 解析失败", e);
        }
    }
}

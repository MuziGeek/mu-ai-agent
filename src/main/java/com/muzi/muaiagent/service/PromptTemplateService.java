package com.muzi.muaiagent.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板加载与渲染服务 —— 基于 Spring AI 的 {@link PromptTemplate}。
 * <p>
 * 【核心职责】
 *   1. 从 classpath 资源文件加载 Prompt 模板
 *   2. 借助 Spring AI 的 {@link PromptTemplate} 将模板中的 {variable} 占位符渲染为实际值
 *   3. 缓存已创建的 PromptTemplate 实例，避免重复读取磁盘文件
 * <p>
 * 【与 Spring AI PromptTemplate 的关系】
 *   Spring AI 的 PromptTemplate 支持两种初始化方式：
 *   <pre>{@code
 *     // 方式 A：从字符串创建（模板硬编码在 Java 代码中）
 *     PromptTemplate pt = new PromptTemplate("请围绕「{topic}」出一道题");
 *
 *     // 方式 B：从 Resource 创建（模板存放在资源文件中）← 本服务采用此方式
 *     PromptTemplate pt = new PromptTemplate(new ClassPathResource("prompts/single-question.txt"));
 *
 *     // 渲染变量，得到替换后的纯文本
 *     String text = pt.render(Map.of("topic", "JVM"));
 *
 *     // 渲染变量，得到 Prompt 对象（可直接传给 ChatClient）
 *     Prompt prompt = pt.create(Map.of("topic", "JVM"));
 *   }</pre>
 *   本服务封装了方式 B，将模板文件统一放在 resources/prompts/ 目录下，
 *   使 Prompt 文本可以独立于 Java 代码进行编辑和调优，无需重新编译。
 * <p>
 * 【模板文件规范】
 *   - 存放路径：src/main/resources/prompts/
 *   - 文件格式：纯文本（.txt），UTF-8 编码
 *   - 变量语法：{变量名}，与 Spring AI PromptTemplate 完全一致（底层使用 StringTemplate ST4 引擎）
 *   - 变量名建议使用 snake_case 或 camelCase 命名
 * <p>
 * 【使用示例】
 * <pre>{@code
 * @Autowired
 * private PromptTemplateService promptTemplateService;
 *
 * // 方式 1：渲染模板为字符串
 * String text = promptTemplateService.render("single-question", Map.of("topic", "JVM 垃圾回收"));
 *
 * // 方式 2：渲染为 Spring AI 的 Prompt 对象
 * Prompt prompt = promptTemplateService.renderAsPrompt("question-list",
 *         Map.of("topic", "Spring", "count", 3));
 * chatClient.prompt(prompt).call().entity(MyClass.class);
 *
 * // 方式 3：仅加载模板原文（不做变量替换），用于系统提示词等无变量场景
 * String systemPrompt = promptTemplateService.loadTemplate("interview-system");
 * }</pre>
 *
 * @see PromptTemplate Spring AI 内置的模板引擎，底层使用 StringTemplate（ST4）
 * @see Prompt         Spring AI 的 Prompt 对象，PromptTemplate.create() 的返回值
 */
@Slf4j
@Component
public class PromptTemplateService {

    /**
     * 模板文件所在的 classpath 目录前缀。
     * 所有模板文件统一放在 resources/prompts/ 下，方便管理和查找。
     */
    private static final String TEMPLATE_DIR = "prompts/";

    /**
     * 模板文件的扩展名。
     * 使用 .txt 而非自定义后缀，便于 IDE 直接打开编辑。
     */
    private static final String TEMPLATE_EXT = ".txt";

    /**
     * PromptTemplate 实例缓存。
     * <p>
     * key   = 模板名称（不含路径和扩展名），如 "single-question"
     * value = 已加载模板文件的 PromptTemplate 实例
     * <p>
     * 使用 ConcurrentHashMap 而非 HashMap，因为 Spring Bean 可能被多线程并发调用。
     * computeIfAbsent() 保证每个模板只被加载一次（原子操作）。
     * <p>
     * 缓存的是 PromptTemplate 实例本身（而非原始字符串），
     * 因为 PromptTemplate 内部持有模板文本，每次只需调用 render(variables) 或 create(variables)
     * 传入不同的变量即可，无需重新读取文件。
     */
    private final ConcurrentHashMap<String, PromptTemplate> templateCache = new ConcurrentHashMap<>();

    // ============================================================
    // 公开方法
    // ============================================================

    /**
     * 渲染模板 —— 从文件加载模板并通过 Spring AI 的 PromptTemplate 填充变量，返回纯文本。
     * <p>
     * 这是最常用的方法：一行调用即可完成"加载 + 渲染"两步操作。
     * <p>
     * 内部流程：
     *   1. 从缓存获取 PromptTemplate 实例（未命中则通过 Resource 构造器从 classpath 加载）
     *   2. 调用 PromptTemplate.render(variables)，将 {key} 占位符替换为实际值
     *   3. 返回渲染后的纯文本字符串
     * <p>
     * PromptTemplate.render() 方法说明：
     *   - 返回类型：String（纯文本）
     *   - 参数：Map&lt;String, Object&gt; 变量键值对
     *   - 底层使用 StringTemplate（ST4）引擎执行变量替换
     *
     * @param templateName 模板名称，对应 resources/prompts/ 下的文件名（不含 .txt 后缀）
     * @param variables    变量键值对，key 对应模板中的 {key} 占位符
     * @return 渲染后的文本字符串
     * @throws RuntimeException 如果模板文件不存在或读取失败
     */
    public String render(String templateName, Map<String, Object> variables) {
        // 获取缓存的 PromptTemplate 实例，然后调用 render(variables) 得到纯文本
        return getPromptTemplate(templateName).render(variables);
    }

    /**
     * 渲染模板并直接返回 Spring AI 的 {@link Prompt} 对象。
     * <p>
     * 这是对 PromptTemplate.create(variables) 的直接封装。
     * <p>
     * PromptTemplate 的两个核心渲染方法对比：
     * <pre>
     *   .render(variables)  →  返回 String（纯文本）
     *   .create(variables)  →  返回 Prompt 对象（内含 UserMessage，可直接传给 ChatClient）
     * </pre>
     * <p>
     * 使用示例：
     * <pre>{@code
     *   Prompt prompt = promptTemplateService.renderAsPrompt("single-question", Map.of("topic", "JVM"));
     *   chatClient.prompt(prompt).call().entity(InterviewQuestion.class);
     * }</pre>
     *
     * @param templateName 模板名称
     * @param variables    变量键值对
     * @return Spring AI 的 Prompt 对象，包含渲染后的文本作为 UserMessage
     */
    public Prompt renderAsPrompt(String templateName, Map<String, Object> variables) {
        // PromptTemplate.create(variables) 直接返回 Prompt 对象
        // 内部先调用 render() 得到文本，再封装为 new Prompt(new UserMessage(text))
        return getPromptTemplate(templateName).create(variables);
    }

    /**
     * 加载模板原文 —— 不做变量替换，直接返回模板文件的完整内容。
     * <p>
     * 适用场景：
     *   - 加载无变量的系统提示词（如 interview-system.txt）
     *   - 调试时查看模板原文
     *   - 需要在外部自行处理变量替换的特殊场景
     * <p>
     * 实现方式：通过 PromptTemplate.getTemplate() 获取模板原文字符串，
     * 而非手动读取文件，确保与 render/create 使用同一数据源。
     *
     * @param templateName 模板名称（不含 .txt 后缀）
     * @return 模板文件的原始文本内容
     * @throws RuntimeException 如果模板文件不存在或读取失败
     */
    public String loadTemplate(String templateName) {
        // PromptTemplate.getTemplate() 返回构造时传入的原始模板文本
        return getPromptTemplate(templateName).getTemplate();
    }

    // ============================================================
    // 内部方法
    // ============================================================

    /**
     * 获取 PromptTemplate 实例（优先从缓存读取，未命中则从 classpath 创建）。
     * <p>
     * 利用 PromptTemplate 的 {@link ClassPathResource} 构造器直接从资源文件加载模板：
     * <pre>{@code
     *   // PromptTemplate 原生支持 Resource 参数，无需手动读取文件内容
     *   PromptTemplate pt = new PromptTemplate(new ClassPathResource("prompts/single-question.txt"));
     * }</pre>
     * 这比手动读文件 → 传字符串给 builder 更简洁，且由 Spring AI 统一处理 IO 和编码。
     *
     * @param templateName 模板名称
     * @return 已加载模板的 PromptTemplate 实例
     */
    private PromptTemplate getPromptTemplate(String templateName) {
        return templateCache.computeIfAbsent(templateName, name -> {
            String path = TEMPLATE_DIR + name + TEMPLATE_EXT;
            ClassPathResource resource = new ClassPathResource(path);

            // 预检查：如果文件不存在，提前抛出明确的异常信息
            if (!resource.exists()) {
                throw new RuntimeException("Prompt 模板文件不存在: classpath:" + path
                        + "，请确认 src/main/resources/" + path + " 文件已创建");
            }

            log.debug("已加载 Prompt 模板: classpath:{}", path);

            // 使用 PromptTemplate 的 Resource 构造器，直接从 classpath 加载模板文件
            // 这是 Spring AI 提供的原生能力，比手动读文件更简洁
            return new PromptTemplate(resource);
        });
    }
}

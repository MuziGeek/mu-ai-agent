package com.muzi.muaiagent.advisor;

import com.muzi.muaiagent.filter.SensitiveWordFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;

/**
 * ============================================================================
 * Spring AI 敏感词过滤 Advisor（拦截器 / 切面）
 * ============================================================================
 *
 * 【什么是 Advisor？—— Spring AI 的核心扩展机制】
 *
 * Advisor 是 Spring AI 中 ChatClient 的"拦截器"，类似于 Spring MVC 的 Interceptor
 * 或 Servlet 的 Filter。它可以在 AI 请求的"前"和"后"插入自定义逻辑。
 *
 * 你可以把它理解为一条流水线上的"质检工位"：
 *   用户输入 → [Advisor1.before] → [Advisor2.before] → ... → 发送给 AI 模型
 *   AI 响应 → [AdvisorN.after] → ... → [Advisor2.after] → [Advisor1.after] → 返回给用户
 *
 * 【Spring AI 中的 Advisor 类型体系】
 *
 * Spring AI 提供了多种 Advisor 接口，按粒度从粗到细：
 *
 *   1. BaseAdvisor（本项目使用）
 *      - 最简单、最常用，只需实现 before() 和 after() 两个方法
 *      - 同时支持同步调用（call）和流式调用（stream）
 *      - 适合大多数场景：修改请求、修改响应、日志记录等
 *
 *   2. CallAdvisor / StreamAdvisor
 *      - 更细粒度的控制，分别处理同步和流式场景
 *      - 可以访问 AdvisorChain（责任链），手动决定是否传递给下一个 Advisor
 *      - 适合需要精确控制的场景，比如项目中的 MyLoggerAdvisor
 *
 *   3. RequestResponseAdvisor
 *      - 介于两者之间，可以在底层操作 ChatRequest 和 ChatResponse
 *
 * 本项目的选择：
 *   - SensitiveWordAdvisor → 使用 BaseAdvisor（只需在请求前修改用户文本）
 *   - ReReadingAdvisor     → 使用 BaseAdvisor（在请求前增强 prompt）
 *   - MyLoggerAdvisor      → 使用 CallAdvisor + StreamAdvisor（需要分别处理同步/流式日志）
 *
 * 【Advisor 的执行顺序 —— order 字段】
 *
 * 多个 Advisor 通过 getOrder() 返回值决定执行顺序：
 *   - order 值越小，越先执行 before()，越后执行 after()
 *   - 敏感词过滤应该最先执行（order=0），在请求到达模型之前就清洗数据
 *   - 日志 Advisor 通常最后执行（order 较大），记录"清洗后"的请求
 *
 * 【使用示例】
 * <pre>
 * // 方式一：直接 new（本项目 InterViewApp 中的用法）
 * new SensitiveWordAdvisor(sensitiveWordFilter)
 *
 * // 方式二：指定 order
 * new SensitiveWordAdvisor(sensitiveWordFilter, 0)
 *
 * // 方式三：链式调用
 * new SensitiveWordAdvisor(sensitiveWordFilter).withOrder(0)
 * </pre>
 */
public class SensitiveWordAdvisor implements BaseAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveWordAdvisor.class);

    /**
     * 敏感词过滤器实例（通过构造器注入）。
     *
     * 这里体现了"依赖注入"和"组合优于继承"的设计原则：
     *   - Advisor 本身不实现过滤逻辑，而是委托给 SensitiveWordFilter
     *   - 过滤器可以被多个 Advisor 复用（比如将来做一个"输出过滤 Advisor"）
     *   - 过滤逻辑变更时只需修改 SensitiveWordFilter，Advisor 无需改动
     */
    private final SensitiveWordFilter sensitiveWordFilter;

    /**
     * Advisor 的执行顺序。
     * 默认值 0 = 最高优先级 = 最先执行。
     * 敏感词过滤理应最先执行，确保后续所有 Advisor 和 AI 模型看到的都是"干净"的输入。
     */
    private int order = 0;

    /**
     * 构造器 —— 注入敏感词过滤器
     *
     * @param sensitiveWordFilter Spring 容器中的 SensitiveWordFilter Bean
     */
    public SensitiveWordAdvisor(SensitiveWordFilter sensitiveWordFilter) {
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    /**
     * 带 order 的构造器
     *
     * @param sensitiveWordFilter 敏感词过滤器
     * @param order               执行顺序，值越小优先级越高
     */
    public SensitiveWordAdvisor(SensitiveWordFilter sensitiveWordFilter, int order) {
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.order = order;
    }

    /**
     * 请求发送前的拦截点 —— 核心过滤逻辑在这里。
     *
     * 【在 Advisor 链中的位置】
     *   用户输入 → [本方法 before()] → 下一个 Advisor → ... → AI 模型
     *
     * 【方法参数说明】
     * @param chatClientRequest 封装了完整的请求信息，包括：
     *                          - prompt：包含系统消息、历史消息、用户消息
     *                          - chatOptions：模型参数（温度、topP 等）
     * @param advisorChain      责任链，调用 advisorChain.nextBefore() 可以将请求传递给下一个 Advisor
     *                          （BaseAdvisor 的 before/after 模式中，框架会自动调用链的传递）
     *
     * 【返回值说明】
     * @return 修改后的 ChatClientRequest（不可变对象，需要 mutate + build 创建新实例）
     *
     * 【不可变对象模式】
     * ChatClientRequest 是不可变的（Immutable），不能直接修改它的字段。
     * 要修改请求，必须：
     *   1. chatClientRequest.mutate()   → 获取一个可变副本（Builder）
     *   2. .prompt(newPrompt)           → 设置新的 Prompt
     *   3. .build()                     → 构建新的 ChatClientRequest
     * 这种模式在 Java 中很常见（如 String、LocalDateTime），好处是线程安全、可预测。
     *
     * 【augmentUserMessage 的作用】
     * prompt.augmentUserMessage(text) 会创建一个新的 Prompt，其中用户消息被替换为 text，
     * 其他部分（系统消息、历史消息）保持不变。
     */
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 1. 从请求中提取用户输入的文本
        //    调用链：request → prompt → userMessage → text
        String userText = chatClientRequest.prompt().getUserMessage().getText();

        // 2. 检查是否包含敏感词（快速判断，无敏感词则直接放行）
        if (sensitiveWordFilter.containsSensitiveWord(userText)) {

            // 3. 执行替换：将敏感词替换为 "***"
            String filteredText = sensitiveWordFilter.replaceSensitiveWords(userText);

            // 4. 记录警告日志，方便运维排查
            //    注意：这里只记录长度信息，不记录原文，避免日志中泄露敏感内容
            logger.warn("检测到敏感词，已进行替换。原始输入长度: {}, 替换后长度: {}",
                    userText.length(), filteredText.length());

            // 5. 构建新的请求对象（不可变模式：mutate → 修改 → build）
            return chatClientRequest.mutate()
                    .prompt(chatClientRequest.prompt().augmentUserMessage(filteredText))
                    .build();
        }

        // 没有敏感词 → 原样返回请求，不做任何修改
        return chatClientRequest;
    }

    /**
     * AI 响应返回后的拦截点。
     *
     * 【在 Advisor 链中的位置】
     *   AI 模型响应 → [本方法 after()] → 返回给用户
     *
     * 当前需求是"仅检查用户输入"，所以 after() 直接透传，不做任何处理。
     *
     * 【扩展思路】
     * 如果将来需要检查 AI 的输出是否也包含敏感词（比如防止模型"幻觉"生成不当内容），
     * 可以在这里添加输出过滤逻辑：
     *
     * <pre>
     * // 示例：检查 AI 输出中的敏感词
     * String aiText = chatClientResponse.chatResponse()
     *     .getResult().getOutput().getText();
     * if (sensitiveWordFilter.containsSensitiveWord(aiText)) {
     *     // 替换 AI 输出中的敏感词并构建新响应...
     * }
     * </pre>
     *
     * @param chatClientResponse AI 模型的响应结果
     * @param advisorChain       责任链
     * @return 响应（可直接返回或修改后返回）
     */
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 当前策略：仅检查用户输入，AI 输出直接透传
        return chatClientResponse;
    }

    /**
     * 返回当前 Advisor 的执行顺序。
     *
     * Spring AI 框架会调用此方法对所有 Advisor 排序。
     * 值越小 → 越先执行 before() → 越后执行 after()。
     *
     * 类比理解：
     *   - 保安检查（order=0）→ 前台登记（order=1）→ 进入会议室（AI 模型）
     *   - 出来时反过来：会议室 → 前台（order=1 的 after）→ 保安（order=0 的 after）
     */
    @Override
    public int getOrder() {
        return this.order;
    }

    /**
     * 链式调用方式设置 order，提升 API 的流畅性。
     *
     * 使用示例：
     * <pre>
     * new SensitiveWordAdvisor(filter).withOrder(0)
     * </pre>
     *
     * 这种"Fluent API"风格在 Java 中很常见，
     * 比如 StringBuilder.append().append()、Stream.filter().map() 等。
     *
     * @param order 执行顺序
     * @return this（支持链式调用）
     */
    public SensitiveWordAdvisor withOrder(int order) {
        this.order = order;
        return this;
    }
}

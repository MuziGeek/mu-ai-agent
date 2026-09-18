package com.muzi.muaiagent.rag.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 预检索（pre-retrieval）链路装配。
 *
 * <h2>这个类解决了什么问题</h2>
 * <p>
 * 改造前用的是 {@code QuestionAnswerAdvisor}：拿用户的原始 query 直接去向量库检索，
 * 检索参数全是框架默认值。它对「多轮追问」几乎无能为力 ——
 * 用户说「它和实体有什么区别？」，向量库收到的就是这一整句，没有主语，检索结果自然稀散。
 * <p>
 * 本类把链路换成 {@code RetrievalAugmentationAdvisor}，并在检索之前插入四个可独立开关的加工环节
 * （压缩 / 改写 / 翻译 / 扩展），同时把检索参数从「框架默认」变成「显式配置」。
 *
 * <h2>一次问答的完整执行顺序</h2>
 * <pre>
 * 用户提问
 *   ↓
 * ① MessageChatMemoryAdvisor（order = HIGHEST_PRECEDENCE + 1000，最先）
 *      把对话历史注入 prompt
 *   ↓
 * ② RetrievalAugmentationAdvisor（order = 配置项 advisor-order，默认 0）
 *      从 prompt 取「用户最新发言」当 query、取「prompt 全部 instructions」当 history
 *      → 串行跑完转换器链（默认只有压缩）
 *      → 展开成多条查询（默认 3 条，含原文）
 *      → 每条查询各自检索一次，合并去重
 *      → 用中文模板把上下文拼进用户消息
 *   ↓
 * ③ MyLoggerAdvisor（order = 1，最后）
 *      此时请求里已带知识库上下文，日志记录的是「模型真正看到的输入」
 *   ↓
 * qwen-plus 基于知识库作答
 * </pre>
 * <b>顺序不是随意的</b>：压缩/改写需要的对话历史由 ① 注入，所以 ② 必须排在 ① 之后；
 * 把 ③ 放在最后，是为了让日志能反映注入上下文之后的最终请求
 * （放在前面的话，日志里只有干巴巴的用户原话，排查检索问题时什么都看不到）。
 *
 * <h2>为什么转换器不单独注册成 Bean</h2>
 * <p>
 * 四个组件都实现/继承自 {@code QueryTransformer} 或 {@code QueryExpander}。
 * 若把它们都注册成 Bean，「哪些开关生效」就变成了「容器里有哪些 Bean」，
 * 注入时既可能出现多候选歧义，测试也只能靠反射去 {@code RetrievalAugmentationAdvisor} 里翻私有字段。
 * 因此这里改为：组件在 {@link #preRetrievalPipeline} 里按需构建，
 * 装配结果收敛到 {@link PreRetrievalPipeline} 一个 Bean 上，链路内容一目了然且可直接断言。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PreRetrievalProperties.class)
public class PreRetrievalConfig {

    /**
     * 上下文注入模板（对 {@code ContextualQueryAugmenter} 默认英文模板的等价中文改写，
     * 不新增也不删减任何规则）。
     *
     * <p>
     * <b>为什么必须换掉默认模板</b>：默认模板是英文的，而本项目全程中文问答。
     * 英文模板夹在中文 prompt 里会让模型在不同语言指令间摇摆，实测表现为回答风格突变、
     * 偶尔夹带「Based on the context...」这类英文腔调。规则本身完全一致，只是换了语言。
     *
     * <p>
     * 模板里的 {@code {context}} 与 {@code {query}} 是框架回填的占位符，
     * 两个都必须保留，否则渲染时会抛错。
     */
    private static final String CONTEXT_PROMPT_TEMPLATE = """
            以下是检索到的上下文信息。

            ---------------------
            {context}
            ---------------------

            请严格依据上述上下文回答问题，不要使用上下文之外的知识。

            请遵守以下规则：

            1. 如果上下文中没有答案，直接说明你不知道。
            2. 不要出现 "根据上下文"、"提供的资料" 这类说法。

            问题：{query}

            回答：
            """;

    /**
     * 空上下文兜底模板。
     *
     * <p>
     * 只在 {@code mu-ai.rag.preretrieval.augmenter.allow-empty-context=false} 时才会被用到 ——
     * 此时检索结果为空，框架直接用这段提示词让模型拒答，而不是把空上下文交给模型自由发挥。
     * 本项目默认 {@code allowEmptyContext=true}（拒答交给 system 提示词），
     * 但这里仍把模板中文化，避免以后有人把开关翻过来时又掉进英文模板的坑。
     */
    private static final String EMPTY_CONTEXT_PROMPT_TEMPLATE = """
            用户的问题超出了知识库范围。
            请礼貌地告知用户你无法回答该问题。
            """;

    /**
     * 装配预检索链路：决定「哪些组件进链路」以及「以什么顺序执行」。
     *
     * <p>
     * 转换器顺序的约定是<b>「先消解指代，再润色措辞，最后换语言」</b>：
     * <ol>
     *     <li>压缩最优先 —— 它负责把「它 / 这个 / 那怎么办」补全成完整问题。
     *         如果放在改写之后，改写面对的仍是一个没有主语的残缺句子，等于白跑。</li>
     *     <li>改写居中 —— 此时句子已经完整，可以专注优化措辞与术语。</li>
     *     <li>翻译最后 —— 它的输出语言与输入不同，必须放在所有「同语言内加工」之后，
     *         否则后面几步都要在非母语文本上工作。</li>
     * </ol>
     *
     * @param props             预检索配置（决定各开关是否生效）
     * @param chatClientBuilder 供各组件内部自行调用大模型；由 DashScope starter 自动配置提供
     * @return 已装配好的链路（转换器有序列表 + 可选的扩展器）
     */
    @Bean
    public PreRetrievalPipeline preRetrievalPipeline(PreRetrievalProperties props,
                                                     ChatClient.Builder chatClientBuilder) {
        // 三个转换器一律先建出来，再由 composeQueryTransformers 按开关决定谁进链路。
        // 不在这里做「关了就不建」的分支，是为了让「开关 → 链路内容」的映射
        // 完全由那一个静态纯函数决定，可以被单元测试直接覆盖，不依赖 Spring 上下文。
        QueryTransformer compression = CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();

        QueryTransformer rewrite = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                // 告诉模型这条 query 将用于检索向量库，改写时朝这个方向优化措辞
                .targetSearchSystem(props.getRewrite().getTargetSearchSystem())
                .build();

        QueryTransformer translation = TranslationQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .targetLanguage(props.getTranslation().getTargetLanguage())
                .build();

        List<QueryTransformer> transformers =
                composeQueryTransformers(props, compression, rewrite, translation);

        // 扩展器关掉时传 null：已确认框架内部对它是 if (queryExpander != null) 判断，
        // 传 null 只会跳过扩展，不会 NPE。
        QueryExpander expander = props.getExpansion().isEnabled()
                ? MultiQueryExpander.builder()
                        .chatClientBuilder(chatClientBuilder)
                        .numberOfQueries(props.getExpansion().getNumberOfQueries())
                        .includeOriginal(props.getExpansion().isIncludeOriginal())
                        .build()
                : null;

        PreRetrievalPipeline pipeline = new PreRetrievalPipeline(transformers, expander);
        // 启动时把生效的链路打出来：这类「靠配置开关拼装」的组件最容易出现
        // 「以为开了其实没开」的静默失效，日志里留一行能省掉大量排查
        log.info("预检索链路已装配：{}", pipeline.describe());
        return pipeline;
    }

    /**
     * 按开关把转换器拼成有序链路。
     *
     * <p>
     * 抽成不依赖 Spring 的静态纯函数，是为了让「某个开关打开后，链路里到底有没有它」
     * 这件事能被<b>纯逻辑单元测试</b>直接断言 —— 不需要启动容器，更不需要真的调大模型。
     * 这类测试运行成本极低，却恰好覆盖了最容易出错的地方（配置读取与装配顺序）。
     *
     * @param props       预检索配置
     * @param compression 压缩转换器实例（开关关闭时会被忽略，可以为 null）
     * @param rewrite     改写转换器实例（开关关闭时会被忽略，可以为 null）
     * @param translation 翻译转换器实例（开关关闭时会被忽略，可以为 null）
     * @return 按「压缩 → 改写 → 翻译」排列的转换器列表；全部关闭时为空列表
     */
    public static List<QueryTransformer> composeQueryTransformers(PreRetrievalProperties props,
                                                                  QueryTransformer compression,
                                                                  QueryTransformer rewrite,
                                                                  QueryTransformer translation) {
        List<QueryTransformer> chain = new ArrayList<>();
        if (props.getCompression().isEnabled() && compression != null) {
            chain.add(compression);
        }
        if (props.getRewrite().isEnabled() && rewrite != null) {
            chain.add(rewrite);
        }
        if (props.getTranslation().isEnabled() && translation != null) {
            chain.add(translation);
        }
        return chain;
    }

    /**
     * 向量检索器：把用户的（可能已被预检索加工过的）查询送去向量库取回文档片段。
     *
     * <p>
     * <b>为什么要显式写 {@code topK} 而不吃默认值</b>：默认值属于框架的实现细节，
     * 升级 Spring AI 时可能变化，而它直接决定「给模型喂多少上下文」。
     * 写死在配置里，行为才可复现、可对比。
     */
    @Bean
    public DocumentRetriever documentRetriever(VectorStore vectorStore, PreRetrievalProperties props) {
        Double threshold = (props.getRetrieval().getSimilarityThreshold() > 0)
                ? props.getRetrieval().getSimilarityThreshold()
                : null; // 非正数解释为「不设阈值」，交给检索器用自己的默认行为

        log.info("向量检索参数：topK={}, similarityThreshold={}",
                props.getRetrieval().getTopK(), threshold == null ? "不设阈值" : threshold);

        return VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .topK(props.getRetrieval().getTopK())
                .similarityThreshold(threshold)
                .build();
    }

    /**
     * 上下文注入器：把检索到的文档片段按模板拼进用户消息。
     *
     * <p>
     * 换掉默认英文模板的原因见 {@link #CONTEXT_PROMPT_TEMPLATE}。
     */
    @Bean
    public ContextualQueryAugmenter contextualQueryAugmenter(PreRetrievalProperties props) {
        return ContextualQueryAugmenter.builder()
                .promptTemplate(PromptTemplate.builder().template(CONTEXT_PROMPT_TEMPLATE).build())
                .emptyContextPromptTemplate(PromptTemplate.builder().template(EMPTY_CONTEXT_PROMPT_TEMPLATE).build())
                .allowEmptyContext(props.getAugmenter().isAllowEmptyContext())
                .build();
    }

    /**
     * 检索增强 advisor：整条预检索 + 检索 + 上下文注入链路的载体。
     *
     * <p>
     * 它实现的是 {@code BaseAdvisor}，与项目里既有的记忆、日志 advisor 是同一种东西，
     * 因此可以直接替换掉原来的 {@code QuestionAnswerAdvisor}，装配方式不变。
     *
     * <p>
     * {@code order} 从配置读取并显式设置。不设的话框架默认 0，虽然数值上也能排在记忆 advisor 之后，
     * 但那是「碰巧对」；显式写出来，将来有人调整配置时才不会因为顺序变化而静默丢掉对话历史。
     */
    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(PreRetrievalProperties props,
                                                                    PreRetrievalPipeline pipeline,
                                                                    DocumentRetriever documentRetriever,
                                                                    ContextualQueryAugmenter queryAugmenter) {
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(pipeline.transformers())
                .queryExpander(pipeline.expander())
                .documentRetriever(documentRetriever)
                .queryAugmenter(queryAugmenter)
                .order(props.getAdvisorOrder())
                .build();
    }
}

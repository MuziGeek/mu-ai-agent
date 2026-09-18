package com.muzi.muaiagent.rag.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 预检索（pre-retrieval）链路的配置。
 *
 * <h2>预检索是什么</h2>
 * <p>
 * 传统的 RAG 是「用户问什么，就拿什么去检索」。但用户的原话往往并不适合直接拿去检索：
 * 多轮对话里的「那它呢」没有主语、口语化提问用词和文档不一致、长会话里
 * 关键信息被淹没在闲聊中。预检索就是在<b>向量检索发生之前</b>先对 query 做加工，
 * 让它变成一条「检索友好」的完整问题。加工手段有四类，对应下面四组开关。
 *
 * <h2>为什么用 {@code @ConfigurationProperties} 而不是逐条 {@code @Value}</h2>
 * <p>
 * 这里共有 11 个配置项、5 个分组。用 {@code @Value} 要写 11 行参数、逐个拼默认值；
 * 用 {@code @ConfigurationProperties} 则让字段名与 YAML 树一一对应，嵌套结构天然映射，
 * 且能直接 {@code new} 出来喂给纯逻辑测试，不必启动 Spring 上下文。
 * （既有的 {@code EmbeddingBatchingConfig} 只有 3 个平铺参数，用 {@code @Value} 是合适的；
 * 配置一多，两者的取舍就反过来了。）
 *
 * <h2>默认值的取舍</h2>
 * <p>
 * 四项能力全部实现且全部可单独开关，但<b>默认只开「压缩 + 扩展」</b>：
 * <ul>
 *     <li><b>压缩默认开</b>：本项目有对话记忆，多轮追问是最常见的交互形态，收益最直接。</li>
 *     <li><b>重写默认关</b>：它与压缩职责重叠（都在解决多轮指代与措辞问题），
 *         串行执行会白白多出一次大模型调用，而收益增量有限。
 *         需要时把 {@code rewrite.enabled} 设为 true 即可，代码无需改动。</li>
 *     <li><b>翻译默认关</b>：知识库文档与提问都是中文，翻译成英文再去检索中文库
 *         <b>必然降低召回</b>。它只在「知识库是外文语料」的场景下才有意义。</li>
 *     <li><b>扩展默认开</b>：一条查询扩成多条变体分别检索再合并，对召回提升最明显，
 *         代价是每次问答多一次大模型调用（扩展本身）——这是可接受的。</li>
 * </ul>
 * <p>
 * 也就是说，默认链路每次问答比改造前多 2 次大模型调用（压缩 1 次 + 扩展 1 次）。
 * 想进一步省成本，把 {@code expansion.enabled} 也设为 false，恢复到只多 1 次。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mu-ai.rag.preretrieval")
public class PreRetrievalProperties {

    /** 查询压缩：把依赖前文的指代（「它 / 这个 / 那怎么办」）补全成可独立检索的完整问题。 */
    private Compression compression = new Compression();

    /** 查询重写：把口语化、含糊的提问润色成检索友好的措辞。 */
    private Rewrite rewrite = new Rewrite();

    /** 查询翻译：把提问翻译成目标语言后再去检索。 */
    private Translation translation = new Translation();

    /** 多查询扩展：一条问题扩成若干变体分别检索，再合并去重。 */
    private Expansion expansion = new Expansion();

    /** 向量检索参数。 */
    private Retrieval retrieval = new Retrieval();

    /** 上下文注入：把检索到的文档片段拼进 prompt。 */
    private Augmenter augmenter = new Augmenter();

    /**
     * {@code RetrievalAugmentationAdvisor} 在 advisor 链中的顺序。
     *
     * <p>
     * <b>这个值不能随便调</b>：该 advisor 在 {@code before} 阶段是从
     * {@code request.prompt().getInstructions()} 里取对话历史来构造检索用的 {@code Query} 的，
     * 而历史是对话记忆 advisor（默认 order = {@code HIGHEST_PRECEDENCE + 1000}，即最先执行）
     * 注入进 prompt 的。所以本值必须<b>大于</b>记忆 advisor 的 order，
     * 否则压缩/改写拿到的是空历史，多轮指代消解会静默失效——不报错，只是不生效。
     *
     * <p>
     * 默认 0：大于记忆 advisor 的 -2147482648，小于日志 advisor 的 1，
     * 正好落在「记忆之后、日志之前」，即框架自身的默认值，保持与改造前一致的行为。
     */
    private int advisorOrder = 0;

    /**
     * 查询压缩配置。
     *
     * <p>
     * 实现类是 {@code CompressionQueryTransformer}，它会把<b>整段对话历史压缩成一句检索用的问题</b>：
     * 历史「什么是聚合根？」+ 当前「它和实体有什么区别？」→
     * 「聚合根和实体在 DDD 中的区别是什么？」
     */
    @Getter
    @Setter
    public static class Compression {

        /**
         * 是否启用查询压缩。默认 true —— 项目有对话记忆，多轮追问是主要交互形态。
         */
        private boolean enabled = true;
    }

    /**
     * 查询重写配置。
     *
     * <p>
     * 实现类是 {@code RewriteQueryTransformer}，它把用户原话改写成更适合向量检索的表述
     * （补全术语、去掉口语化表达）。
     */
    @Getter
    @Setter
    public static class Rewrite {

        /**
         * 是否启用查询重写。默认 false —— 与压缩职责重叠，避免每次问答多跑一次大模型调用。
         */
        private boolean enabled = false;

        /**
         * 目标检索系统的名称，用于填充重写提示词里的「该 query 将用于检索 {@code {targetSearchSystem}}」。
         * 默认 "vector store"：本项目检索的是向量库，改写时就该朝这个方向优化措辞。
         */
        private String targetSearchSystem = "vector store";
    }

    /**
     * 查询翻译配置。
     *
     * <p>
     * 实现类是 {@code TranslationQueryTransformer}。
     */
    @Getter
    @Setter
    public static class Translation {

        /**
         * 是否启用查询翻译。默认 false —— 中文提问 + 中文知识库，翻译只会降低召回。
         * 仅当知识库是外文语料时才应打开。
         */
        private boolean enabled = false;

        /**
         * 翻译的目标语言。默认 "English"：
         * 绝大多数跨语言检索场景的目标都是英文语料，需要时改成对应语言名即可。
         */
        private String targetLanguage = "English";
    }

    /**
     * 多查询扩展配置。
     *
     * <p>
     * 实现类是 {@code MultiQueryExpander}，它让大模型把一条问题改写成若干个语义相近但措辞不同的变体，
     * 每个变体各自去检索，最后合并去重 —— 相当于用多种问法「撒网」，提高命中率。
     */
    @Getter
    @Setter
    public static class Expansion {

        /**
         * 是否启用多查询扩展。默认 true —— 对召回提升最明显，代价是每次问答多一次大模型调用。
         */
        private boolean enabled = true;

        /**
         * 生成多少个查询变体。默认 3（框架默认值）：再多收益递减，而检索次数线性增长。
         */
        private int numberOfQueries = 3;

        /**
         * 是否把用户原始查询也保留在变体列表里。默认 true ——
         * 这是<b>召回率的保险</b>：大模型改写有可能跑偏，留下原查询能保证「至少不比不做扩展更差」。
         */
        private boolean includeOriginal = true;
    }

    /**
     * 向量检索参数。
     *
     * <p>
     * 这些值与改造前 {@code QuestionAnswerAdvisor} 的默认值保持一致（{@code SearchRequest} 的
     * {@code DEFAULT_TOP_K = 4}、{@code SIMILARITY_THRESHOLD_ACCEPT_ALL = 0.0}），
     * 目的是让「换 advisor」这件事本身不改变检索结果，行为差异只来自新增的预检索环节。
     */
    @Getter
    @Setter
    public static class Retrieval {

        /**
         * 每条查询返回的文档数上限。默认 4，与改造前一致。
         *
         * <p>
         * 注意这是<b>每条查询</b>的上限：启用多查询扩展后，最终合并进上下文的总条数
         * 可能是它的若干倍（变体条数 × topK，再去重）。关掉扩展即可回到单倍。
         */
        private int topK = 4;

        /**
         * 相似度阈值，低于该值的检索结果被丢弃。默认 0.0 —— 表示不过滤。
         *
         * <p>
         * 与改造前一致，是因为改用余弦距离后，本项目实测的相似度绝对值偏高
         * （命中结果 0.82、勉强相关也有 0.43），拿绝对阈值做拦截很容易把有用内容一起丢掉。
         * 真要设阈值，建议先用日志观察一批真实查询的相似度分布再定；
         * {@code <= 0} 会被解释为「不设阈值」。
         */
        private double similarityThreshold = 0.0;
    }

    /**
     * 上下文注入配置：把检索到的文档片段拼进用户消息。
     */
    @Getter
    @Setter
    public static class Augmenter {

        /**
         * 检索结果为空时是否仍继续把问题交给大模型。默认 true。
         *
         * <p>
         * 框架默认是 false —— 此时会用「该问题超出知识库范围，请礼貌拒答」的兜底提示词，
         * 相当于由框架来把关。本项目设为 true，是因为 system 提示词里已经写了
         * 「如果知识库中没有相关信息，请如实告知」，拒答的职责统一由 system 提示词承担；
         * 两处都管会造成提示词重复，也让回答风格多了一层不可控的模板影响。
         */
        private boolean allowEmptyContext = true;
    }
}

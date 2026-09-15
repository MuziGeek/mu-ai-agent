package com.muzi.muaiagent.rag.config;

import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 嵌入模型批处理策略配置：用自定义的 {@link DashScopeBatchingStrategy} 覆盖 Spring AI 的默认策略。
 *
 * <h2>覆盖是怎么生效的</h2>
 * <p>
 * {@code PgVectorStoreAutoConfiguration} 里那个默认策略 Bean 带 {@code @ConditionalOnMissingBean}：
 * <pre>
 * &#64;Bean
 * &#64;ConditionalOnMissingBean
 * BatchingStrategy pgVectorStoreBatchingStrategy() { ... }
 * </pre>
 * 自动配置类总是在用户自定义 Bean 之后处理，所以本类一旦声明了 {@code BatchingStrategy}，
 * 默认策略就会自动退让，{@code vectorStore(...)} 方法注入到的就是本类提供的实例。
 * <b>如果本类被删掉或条件不满足，程序不会报错，而是静默退回默认策略</b> ——
 * 这正是 {@code EmbeddingBatchingStrategyTest} 要断言「当前生效的确实是我们这个实现」的原因。
 *
 * <h2>可调参数</h2>
 * <p>
 * 三个参数都带默认值，不配置也能跑；需要调优时在 {@code application.yml} 里覆盖即可：
 * <pre>
 * mu-ai:
 *   rag:
 *     batching:
 *       max-documents-per-request: 10     # 单次嵌入请求最多几条文本（DashScope 厂商限制）
 *       max-input-tokens: 8000            # 单批 token 预算
 *       reserve-percentage: 0.1           # 预留比例
 * </pre>
 *
 * <h2>取值依据</h2>
 * <ul>
 *     <li>{@code CL100K_BASE}：DashScope 未公开自己的分词规则，借用 OpenAI 的编码做近似估算。
 *         估算偏大不会出错（只是多切几批），偏小才危险，所以这里宁可保守。</li>
 *     <li>{@code 8000} 而非默认的 8191：留一点余量。真正的硬约束是文档条数（10 条），
 *         token 预算只用于兜住「少数几段超长文本」。本项目实测 12 个知识库分块合计约 3000 tokens，
 *         条数上限才是先被触发的那一个。</li>
 *     <li>{@code 0.1}：沿用 Spring AI 默认的预留比例，实际可用预算 = (1 - 0.1) × 8000 = 7200 tokens。</li>
 * </ul>
 *
 * <p>
 * {@code EncodingType} 来自 {@code com.knuddels:jtokkit}，当前是经
 * {@code spring-ai-tika-document-reader → spring-ai-commons} 传递引入的（compile 作用域，可直接使用）。
 *
 * @see DashScopeBatchingStrategy
 */
@Configuration
public class EmbeddingBatchingConfig {

    /**
     * 注册自定义批处理策略，覆盖 Spring AI 的默认 {@code TokenCountBatchingStrategy}。
     *
     * @param maxDocumentsPerRequest 单批文档条数上限，对应 DashScope 的单次请求条数限制
     * @param maxInputTokens         单批 token 预算（未扣预留之前的值）
     * @param reservePercentage      预留 token 比例，取值 [0, 1)
     */
    @Bean
    public BatchingStrategy batchingStrategy(
            @Value("${mu-ai.rag.batching.max-documents-per-request:10}") int maxDocumentsPerRequest,
            @Value("${mu-ai.rag.batching.max-input-tokens:8000}") int maxInputTokens,
            @Value("${mu-ai.rag.batching.reserve-percentage:0.1}") double reservePercentage) {
        return new DashScopeBatchingStrategy(
                EncodingType.CL100K_BASE, maxInputTokens, reservePercentage, maxDocumentsPerRequest);
    }
}

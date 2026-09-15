package com.muzi.muaiagent.rag.config;

import com.knuddels.jtokkit.api.EncodingType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 面向 DashScope（阿里云百炼）文本嵌入接口的批处理策略：<b>同时</b>受「单批文档条数」和「单批 token 预算」两个上限约束。
 *
 * <h2>为什么要自定义这个策略</h2>
 * <p>
 * 不自定义时，{@code PgVectorStoreAutoConfiguration} 会装配它自带的默认策略：
 * <pre>
 * &#64;Bean
 * &#64;ConditionalOnMissingBean
 * BatchingStrategy pgVectorStoreBatchingStrategy() {
 *     return new TokenCountBatchingStrategy();   // CL100K_BASE + 8191 tokens + 保留 10%
 * }
 * </pre>
 * 它<b>只按 token 预算切批，对单批的文档条数没有任何上限</b>。而 DashScope 的 text-embedding-v3
 * 接口对「单次请求的文本条数」有硬限制（本项目按 10 条控制），条数超限会被服务端直接拒绝。
 * <p>
 * 条数与 token 是两把不同的尺子：10 段很短的文本可能远不到 token 预算，但条数已经越界；
 * 反过来 2 段超长文本条数没超，token 却可能已经爆掉。所以两个上限必须同时生效。
 *
 * <h2>它与调用方的关系（单一事实来源）</h2>
 * <p>
 * 在此之前，{@code DocumentLoader} 是在业务代码里手动按 10 条切片来绕开这个限制，
 * 相当于把「厂商限制」这种基础设施细节渗透到了调用方。交给 BatchingStrategy 之后：
 * <ol>
 *     <li>厂商限制只在这一处声明，改限制只改这里；</li>
 *     <li>所有经由 {@code EmbeddingModel} 的调用（灌库、检索、其它 VectorStore）都自动遵守；</li>
 *     <li>调用方直接 {@code vectorStore.add(整个列表)} 即可，不必再关心批次大小。</li>
 * </ol>
 *
 * <h2>切批方式：两级切分</h2>
 * <ol>
 *     <li><b>第一级 —— 按条数分组</b>：把文档按 {@code maxDocumentsPerRequest} 个一组切开；</li>
 *     <li><b>第二级 —— 按 token 预算细分</b>：每组再交给 {@link TokenCountBatchingStrategy}，
 *         由它保证组内每个子批的预估 token 数不超过预算（单个文档就超预算时抛异常）。</li>
 * </ol>
 *
 * <h2>顺序保证（重要）</h2>
 * <p>
 * {@code EmbeddingModel.embed(...)} 是按<b>位置</b>把返回的向量回填给文档的，因此
 * {@link BatchingStrategy#batch(List)} 的约定是：返回的批次展开后必须与原列表顺序完全一致，
 * 不允许重排或去重。本类用 List 顺序拼接实现，{@link TokenCountBatchingStrategy} 内部也用
 * LinkedHashMap 保序，两者行为一致。
 *
 * @see BatchingStrategy
 * @see EmbeddingBatchingConfig 在这里注册为 Bean
 */
@Slf4j
public class DashScopeBatchingStrategy implements BatchingStrategy {

    /**
     * 单次嵌入请求允许包含的最大文档条数（DashScope text-embedding-v3 的厂商限制）。
     */
    private final int maxDocumentsPerRequest;

    /**
     * token 维度的切批委托器：复用 Spring AI 现成实现，不重复造轮子。
     * 它内部持有 token 估算器、token 预算与内容格式化器。
     */
    private final BatchingStrategy tokenCountDelegate;

    /**
     * 仅用于日志输出与异常定位的 token 估算器。
     * <p>
     * 为什么不直接问 {@code tokenCountDelegate} 要？因为它不对外暴露「这批预估花了多少 token」，
     * 而那恰恰是排查「为什么一次请求这么慢」时最想看的信息，所以这里按同样的编码独立估算一份。
     * 估算口径与委托器保持一致：{@link Document#DEFAULT_CONTENT_FORMATTER} + {@link MetadataMode#NONE}。
     */
    private final TokenCountEstimator tokenCountEstimator;

    /**
     * @param encodingType              token 编码类型，决定估算口径（DashScope 没有公开自己的分词规则，
     *                                  业界惯例是借用 OpenAI 的 CL100K_BASE 做近似估算）
     * @param maxInputTokenCount        单个批次的 token 预算上限（含被 {@code reservePercentage} 预留的部分）
     * @param reservePercentage         预留给 prompt / 元数据等的 token 比例，取值 [0, 1)；
     *                                  实际可用预算 = (1 - reservePercentage) × maxInputTokenCount
     * @param maxDocumentsPerRequest    单个批次的文档条数上限，必须大于 0
     */
    public DashScopeBatchingStrategy(EncodingType encodingType,
                                     int maxInputTokenCount,
                                     double reservePercentage,
                                     int maxDocumentsPerRequest) {
        Assert.notNull(encodingType, "encodingType 不能为空");
        Assert.isTrue(maxDocumentsPerRequest > 0, "maxDocumentsPerRequest 必须大于 0");

        this.maxDocumentsPerRequest = maxDocumentsPerRequest;
        // 三参构造内部会校验 maxInputTokenCount > 0 且 reservePercentage ∈ [0, 1)，无需重复校验
        this.tokenCountDelegate = new TokenCountBatchingStrategy(encodingType, maxInputTokenCount, reservePercentage);
        this.tokenCountEstimator = new JTokkitTokenCountEstimator(encodingType);
    }

    /**
     * 把输入的文档列表切成若干批次：先按条数分组，再按 token 预算细分。
     *
     * @param documents 待嵌入的文档，允许为空
     * @return 批次列表，展开后与原列表顺序一致；入参为空时返回空列表（不是 null）
     * @throws IllegalArgumentException 单个文档的内容本身就超过 token 预算时抛出，
     *                                  该文档无法被切分（切了上下文就断了），只能抛给调用方处理
     */
    @Override
    public List<List<Document>> batch(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            // 返回不可变空列表，避免调用方误改
            return Collections.emptyList();
        }

        int total = documents.size();
        int groupCount = (int) Math.ceil((double) total / maxDocumentsPerRequest);
        List<List<Document>> batches = new ArrayList<>();

        // 第一级：按条数上限分组。这里用下标步进而不是 ListUtils.partition，
        // 是为了能用 subList 做视图切片，避免复制出多余的中间列表。
        for (int start = 0; start < total; start += maxDocumentsPerRequest) {
            int end = Math.min(start + maxDocumentsPerRequest, total);
            List<Document> group = documents.subList(start, end);

            // 第二级：token 预算细分。委托器只在「单个文档超预算」时抛异常，
            // 这里捕获后补上「是哪个文档超了」的信息再抛出，否则排查时只能看到一句无上下文的报错。
            List<List<Document>> subBatches;
            try {
                subBatches = tokenCountDelegate.batch(group);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(buildOversizeMessage(group), ex);
            }
            batches.addAll(subBatches);

            log.debug("批处理：第 {}/{} 组共 {} 个文档，预估 {} tokens，token 维度细分后为 {} 个子批",
                    (start / maxDocumentsPerRequest) + 1, groupCount, group.size(),
                    group.stream().mapToInt(this::estimateTokens).sum(), subBatches.size());
        }

        log.info("批处理完成：{} 个文档 → {} 个批次（单批上限 {} 个文档）", total, batches.size(), maxDocumentsPerRequest);
        return batches;
    }

    /**
     * 估算单个文档在嵌入时会消耗的 token 数。
     * <p>
     * 先按内容格式化器渲染成真正要发给模型的字符串再估算，这样带上元数据的文档也能算准。
     */
    private int estimateTokens(Document document) {
        return tokenCountEstimator.estimate(document.getFormattedContent(MetadataMode.NONE));
    }

    /**
     * 拼装「单个文档超预算」的报错信息，尽可能指明是哪个文档、有多大。
     * <p>
     * 文档通常来自知识库文件，{@code source} 元数据里记录了来源文件名，优先用它定位；
     * 没有该元数据时退化为按索引定位。
     * <p>
     * 明细刻意拼成单行：异常信息常被整体塞进一行日志，带换行符会把日志格式打断、后续行失去时间戳与前缀。
     */
    private String buildOversizeMessage(List<Document> group) {
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < group.size(); i++) {
            Document document = group.get(i);
            Object source = document.getMetadata().get("source");
            detail.append("[第 ").append(i + 1).append(" 个文档")
                    .append(source == null ? "，无 source 元数据" : "，来源：" + source)
                    .append("，预估 ").append(estimateTokens(document)).append(" tokens]");
        }
        return "存在单个文档的 token 数超过批处理预算，无法再切分，请先在 TokenTextSplitter 阶段"
                + "把文档切得更细，或调大 maxInputTokens。涉及批次内文档：" + detail;
    }
}

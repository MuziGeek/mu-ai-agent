package com.muzi.muaiagent.rag;

import com.muzi.muaiagent.rag.config.DashScopeBatchingStrategy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * 批处理策略（BatchingStrategy）测试：验证自定义策略生效，以及它的切批规则。
 *
 * <h2>这个策略为什么值得测</h2>
 * <p>
 * 覆盖默认策略靠的是 {@code @ConditionalOnMissingBean} —— 这种机制的特点是
 * <b>「被覆盖」和「没被覆盖」两种情况下程序都能正常启动，只是切批行为不同</b>。
 * 一旦哪天配置类被挪走、条件不满足、或者引入别的 Starter 也声明了 BatchingStrategy，
 * 失败方式是静默的：请求照样发出去，只是可能因为单次条数超限被服务端拒绝。
 * 所以这里第一个用例专门断言「当前注入进来的确实是我们自己的实现」。
 *
 * <h2>为什么不需要 @Transactional</h2>
 * <p>
 * 本测试只调 {@link BatchingStrategy#batch(List)} 这个纯内存方法做切批计算，
 * 既不写向量库也不调大模型，因此没有需要回滚的东西。
 * 真正的「策略 → DashScope → 落库」端到端验证在
 * {@code PgVectorVectorStoreConfigTest#testInitKnowledgeBase} 里。
 *
 * <h2>日志</h2>
 * <p>
 * 策略内部会打印每个批次的条数与预估 token 数（DEBUG 级），
 * 需要观察切批细节时在 application-local.yml 里把本包日志级别调成 DEBUG 即可。
 */
@Slf4j
@SpringBootTest
public class EmbeddingBatchingStrategyTest {

    /**
     * 按接口类型注入：这样能验证「容器里最终生效的是哪个实现」，
     * 而不是直接绕过覆盖机制去 new 一个自己测自己。
     */
    @Resource
    private BatchingStrategy batchingStrategy;

    /** 单批文档条数上限，需与 {@code EmbeddingBatchingConfig} 的默认值保持一致 */
    private static final int MAX_DOCUMENTS_PER_REQUEST = 10;

    @Test
    @DisplayName("自定义批处理策略已生效，未被 Spring AI 默认策略覆盖")
    void testCustomStrategyIsActive() {
        log.info("当前注入的 BatchingStrategy 实现：{}", batchingStrategy.getClass().getName());

        // 默认策略是 TokenCountBatchingStrategy；这里的断言失败即说明覆盖机制没生效，
        // 此时 DashScope 的单次条数限制将无人守护
        Assertions.assertInstanceOf(DashScopeBatchingStrategy.class, batchingStrategy,
                "批处理策略未被自定义实现覆盖，请检查 EmbeddingBatchingConfig 是否被扫描到");
    }

    @Test
    @DisplayName("文档条数超过单批上限时被切成多批")
    void testSplitByMaxDocumentsPerRequest() {
        // 12 个短文档：token 预算远未触顶，所以唯一会生效的就是条数上限 → 期望切成 10 + 2
        List<Document> documents = buildDocuments(12);

        List<List<Document>> batches = batchingStrategy.batch(documents);

        List<Integer> sizes = batches.stream().map(List::size).toList();
        log.info("12 个文档被切分为 {} 批，各批条数：{}", batches.size(), sizes);

        Assertions.assertEquals(2, batches.size(), "12 个文档按上限 10 切批应得到 2 批");
        Assertions.assertEquals(List.of(MAX_DOCUMENTS_PER_REQUEST, 2), sizes, "各批条数应为 10 + 2");
        Assertions.assertTrue(batches.stream().allMatch(batch -> batch.size() <= MAX_DOCUMENTS_PER_REQUEST),
                "任何一批的文档数都不应超过单批上限");
    }

    @Test
    @DisplayName("切批不丢文档且保持原顺序")
    void testBatchPreservesOrderAndCompleteness() {
        // 顺序是硬性契约：EmbeddingModel.embed(...) 按位置把向量回填给文档，
        // 策略一旦重排或丢文档，向量就会与文档错配，而且不会报错，只会静默写错数据。
        List<Document> documents = buildDocuments(25);

        List<List<Document>> batches = batchingStrategy.batch(documents);

        List<String> flattened = batches.stream()
                .flatMap(List::stream)
                .map(Document::getText)
                .toList();
        List<String> expected = documents.stream().map(Document::getText).toList();

        log.info("25 个文档 → {} 批，各批条数：{}",
                batches.size(), batches.stream().map(List::size).toList());

        Assertions.assertEquals(expected.size(), flattened.size(), "切批后文档总数不应变化");
        Assertions.assertEquals(expected, flattened, "切批后必须保持原有顺序");
    }

    @Test
    @DisplayName("单个文档超过 token 预算时抛出异常并指明来源")
    void testOversizedDocumentThrows() {
        // TokenCountBatchingStrategy 对「单个文档就超预算」的情况无从下手：
        // 再切就破坏语义了，所以它抛异常。此处只放 1 条超长文档，便于断言异常信息。
        Document huge = new Document(
                "The quick brown fox jumps over the lazy dog. ".repeat(5000),
                Map.of("source", "模拟超长文档.md"));
        List<Document> documents = List.of(huge);

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                () -> batchingStrategy.batch(documents),
                "单个文档超过 token 预算时应抛出异常，而不是发出一个必然被服务端拒绝的请求");

        log.info("按预期抛出异常：{}", exception.getMessage());

        // 自定义策略在异常信息里补上了来源文件名，方便直接定位是哪个知识库文件没切细
        Assertions.assertTrue(exception.getMessage().contains("模拟超长文档.md"),
                "异常信息应指明是哪个来源文档超限，实际为：" + exception.getMessage());
        Assertions.assertNotNull(exception.getCause(), "应保留底层异常作为 cause，便于排查");
    }

    @Test
    @DisplayName("空或 null 输入返回空批次而不是抛异常")
    void testEmptyInput() {
        Assertions.assertTrue(batchingStrategy.batch(List.of()).isEmpty(), "空列表应返回空批次");
        Assertions.assertTrue(batchingStrategy.batch(null).isEmpty(), "null 应返回空批次");
    }

    /**
     * 构造若干条互不相同的短文档，文本里带序号是为了让顺序断言能区分具体是哪一条。
     */
    private static List<Document> buildDocuments(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new Document("这是第 " + i + " 个用于测试切批的短文档。"))
                .toList();
    }
}

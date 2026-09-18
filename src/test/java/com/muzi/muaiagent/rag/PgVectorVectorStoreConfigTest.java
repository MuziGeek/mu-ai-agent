package com.muzi.muaiagent.rag;

import com.muzi.muaiagent.rag.app.RagApp;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * PgVectorStore 集成测试：验证「写入文档 → 相似度检索 → 条件过滤/删除」以及「知识库初始化灌库」链路。
 *
 * 【前置条件】
 * 1. 默认激活 local profile，PgVectorStore 由 spring-ai-starter-vector-store-pgvector 自动配置注入；
 * 2. PostgreSQL 已安装 pgvector 扩展，vector_store 表由 initialize-schema=true 自动创建；
 * 3. 表中 embedding 维度必须是 1024（与 DashScope text-embedding-v3 一致）。
 *    若曾用别的维度建过表，需先 DROP TABLE vector_store 让其重新创建，否则插入会报维度不匹配；
 * 4. 知识库文档需存在于 src/main/resources/document/Java8Gu5/DDD/ 下（DocumentLoader 扫描该路径的 .md 文件）。
 *
 * 【关于 @Transactional】
 * 测试方法加了 @Transactional，写入的文档会在用例结束后自动回滚，不会污染向量库，可反复执行。
 * 这点对 testInitKnowledgeBase 尤其重要：否则每跑一次都会把整个知识库重复灌一遍。
 * 若想真实落库、便于连数据库查看，删掉方法上的 @Transactional 即可。
 *
 * 【关于注入】
 * 显式按 Bean 名 "vectorStore" 注入，避免与 simple-vector profile 下的 SimpleVectorStore 产生歧义。
 * 因此本测试必须在 PgVector 生效的 profile（默认 local）下运行。
 *
 * 【关于 @Tag("llm")】
 * testChatWithRag 与 testChatWithRagMultiTurn 会真实调用 DashScope 大模型，有网络延迟与 token 成本，
 * 回答内容也不确定。日常快速回归可排除它：mvn -B test -DexcludedGroups=llm
 * 只跑 RAG 问答链路：mvn -B test -Dtest=PgVectorVectorStoreConfigTest#testChatWithRag
 *
 * 【关于检索链路的现状】
 * RagApp 里的 advisor 已由 QuestionAnswerAdvisor 换成 RetrievalAugmentationAdvisor，
 * 走「预检索加工（压缩 + 扩展）→ 向量检索 → 上下文注入」的链路。
 * 本类的两个 RAG 用例断言不涉及回答内容（只断言非空与非空白），
 * 因此这次替换不影响它们 —— 这也正是当初把断言写宽松的收益。
 * 预检索组件自身的装配与开关验证在 PreRetrievalConfigTest / PreRetrievalPipelineTest，
 * 加工效果在 PreRetrievalTransformerLlmTest。
 */
@Slf4j
@SpringBootTest
public class PgVectorVectorStoreConfigTest {

    @Resource(name = "vectorStore")
    private VectorStore pgVectorVectorStore;

    /**
     * RAG 应用，用于触发知识库初始化（RagApp.initKnowledgeBase 目前只在测试中被调用）。
     */
    @Resource
    private RagApp ragApp;

    /**
     * 测试用文档，对齐 Spring AI 官方示例：
     * 第 1 条含 "Spring"，是查询 "Spring" 时预期最相关的一条；
     * 第 2 条不带元数据；第 1、3 条分别带 meta1 / meta2，用于验证元数据过滤与删除。
     */
    private static final List<Document> DOCUMENTS = List.of(
            new Document("Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!! Spring AI rocks!!",
                    Map.of("meta1", "meta1")),
            new Document("The World is Big and Salvation Lurks Around the Corner"),
            new Document("You walk forward facing the past and you turn back toward the future.",
                    Map.of("meta2", "meta2"))
    );

    @Test
    @DisplayName("写入文档 → 相似度检索")
    @Transactional
    void testAddAndSimilaritySearch() {
        // 添加文档
        pgVectorVectorStore.add(DOCUMENTS);
        log.info("已写入 {} 条文档到向量库", DOCUMENTS.size());

        // 相似度查询
        List<Document> results = pgVectorVectorStore.similaritySearch(
                SearchRequest.builder().query("Spring").topK(5).build());

        Assertions.assertNotNull(results);
        Assertions.assertFalse(results.isEmpty(), "相似度检索结果不应为空");
        Assertions.assertTrue(results.size() <= 5, "topK=5 时返回条数不应超过 5");

        results.forEach(doc -> log.info("相似度={} | 内容={}", formatScore(doc), doc.getText()));

        // 与查询最相关的一条应当是含 "Spring" 的文档
        Assertions.assertTrue(results.get(0).getText().contains("Spring"),
                "排名第一的文档应包含关键字 Spring，实际为：" + results.get(0).getText());
    }

    @Test
    @DisplayName("相似度阈值过滤")
    @Transactional
    void testSimilarityThreshold() {
        pgVectorVectorStore.add(DOCUMENTS);

        // 阈值按余弦相似度过滤，text-embedding-v3 的得分偏保守，
        // 若这里因结果为空而失败，说明阈值设高了，调低即可（可先跑上一个用例观察日志中的得分）。
        double threshold = 0.3;
        List<Document> results = pgVectorVectorStore.similaritySearch(
                SearchRequest.builder().query("Spring").topK(5).similarityThreshold(threshold).build());

        Assertions.assertNotNull(results);
        results.forEach(doc -> log.info("相似度={} | 内容={}", formatScore(doc), doc.getText()));

        Assertions.assertFalse(results.isEmpty(), "阈值 " + threshold + " 下应至少命中 1 条文档");
        Assertions.assertTrue(
                results.stream().allMatch(doc -> doc.getScore() != null && doc.getScore() >= threshold),
                "返回结果的相似度都应不低于阈值 " + threshold);
    }

    @Test
    @DisplayName("按元数据条件过滤检索")
    @Transactional
    void testMetadataFilter() {
        pgVectorVectorStore.add(DOCUMENTS);

        // 只检索 meta1 = "meta1" 的文档
        Filter.Expression filter = new FilterExpressionBuilder().eq("meta1", "meta1").build();
        List<Document> results = pgVectorVectorStore.similaritySearch(
                SearchRequest.builder().query("Spring").topK(5).filterExpression(filter).build());

        Assertions.assertNotNull(results);
        results.forEach(doc -> log.info("相似度={} | 元数据={} | 内容={}",
                formatScore(doc), doc.getMetadata(), doc.getText()));

        Assertions.assertFalse(results.isEmpty(), "按 meta1 过滤应至少命中刚写入的文档");
        Assertions.assertTrue(
                results.stream().allMatch(doc -> "meta1".equals(doc.getMetadata().get("meta1"))),
                "返回结果都应满足 meta1 = meta1");
    }

    @Test
    @DisplayName("按元数据条件删除文档")
    @Transactional
    void testDeleteByFilter() {
        pgVectorVectorStore.add(DOCUMENTS);

        Filter.Expression filter = new FilterExpressionBuilder().eq("meta1", "meta1").build();
        SearchRequest request = SearchRequest.builder()
                .query("Spring")
                .topK(5)
                .filterExpression(filter)
                .build();

        int before = pgVectorVectorStore.similaritySearch(request).size();
        Assertions.assertTrue(before > 0, "删除前应能按 meta1 检索到文档");
        log.info("删除前 meta1 命中文档数：{}", before);

        // 按条件删除
        pgVectorVectorStore.delete(filter);

        int after = pgVectorVectorStore.similaritySearch(request).size();
        log.info("删除后 meta1 命中文档数：{}", after);
        Assertions.assertEquals(0, after, "删除后按 meta1 应检索不到任何文档");
    }

    @Test
    @DisplayName("初始化知识库：解析 DDD 文档 → 灌入向量库 → 检索验证")
    @Transactional
    void testInitKnowledgeBase() throws IOException {
        // 触发扫描 classpath:document/Java8Gu5/DDD/**/*.md，Tika 解析 + 分块后写入向量库
        int fileCount = ragApp.initKnowledgeBase();
        log.info("知识库初始化完成，共扫描 {} 个文档文件", fileCount);
        Assertions.assertTrue(fileCount > 0,
                "未扫描到任何文档，请确认 src/main/resources/document/Java8Gu5/DDD/ 下存在 .md 文件");

        // 用 DDD 相关问题检索，验证文档已真正入库
        List<Document> results = pgVectorVectorStore.similaritySearch(
                SearchRequest.builder().query("什么是聚合根？").topK(3).build());

        Assertions.assertNotNull(results);
        Assertions.assertFalse(results.isEmpty(), "灌库后应能检索到 DDD 相关知识库文档");
        results.forEach(doc -> log.info("相似度={} | 来源={} | 内容={}",
                formatScore(doc), doc.getMetadata().get("source"), abbreviate(doc.getText())));

        // source 元数据由 DocumentLoader 写入，命中即说明结果确实来自本次灌入的知识库文档
        Assertions.assertTrue(
                results.stream().anyMatch(doc -> doc.getMetadata().containsKey("source")),
                "topK 结果中应至少有一条带 source 元数据的知识库文档");
    }

    /**
     * RAG 问答链路冒烟测试：知识库灌入 → 向量检索 → 大模型基于检索结果作答。
     *
     * 【断言为什么写得宽松】
     * 大模型输出不可预期，这里只断言确定性事实：「检索能命中知识库」+「回答非空非空白」。
     * 答案是否正确需要人工看日志判断，日志里会完整打印问题与回答。
     *
     * 【成本提示】
     * 会真实调用 DashScope qwen-plus，耗时数秒、产生 token 消耗，所以打了 @Tag("llm") 便于排除。
     */
    @Test
    @Tag("llm")
    @DisplayName("RAG 问答：知识库检索 + 大模型作答")
    @Transactional
    void testChatWithRag() throws IOException {
        // 1. 先灌库，保证检索有数据（同一事务内，前面写入的数据对后续检索可见）
        int fileCount = ragApp.initKnowledgeBase();
        Assertions.assertTrue(fileCount > 0, "未扫描到任何文档，无法验证 RAG 问答");

        // 2. 检索这一步是确定性的，先断言它必须命中，否则后面大模型拿不到上下文
        String question = "什么是聚合根？";
        List<Document> hits = pgVectorVectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(3).build());
        hits.forEach(doc -> log.info("检索命中 | 相似度={} | 来源={}",
                formatScore(doc), doc.getMetadata().get("source")));
        Assertions.assertFalse(hits.isEmpty(), "检索结果为空，RAG 问答拿不到上下文");

        // 3. 走完整 RAG 链路：对话记忆 → 知识库检索 → 日志 → qwen-plus 作答
        String chatId = "test-rag-" + System.currentTimeMillis();
        String answer = ragApp.doChatWithRag(question, chatId);
        log.info("【RAG 问答】问题：{}", question);
        log.info("【RAG 问答】回答：{}", answer);

        Assertions.assertNotNull(answer, "大模型未返回任何回答（chatResponse 为空）");
        Assertions.assertFalse(answer.isBlank(), "大模型返回的回答为空字符串");
    }

    /**
     * 多轮追问冒烟测试：这是预检索压缩环节唯一在真实链路里被走到的场景。
     *
     * <p>
     * {@link #testChatWithRag()} 用的是全新会话，历史为空，压缩组件拿不到任何上下文，
     * 等于没被真正执行。而多轮追问恰恰是压缩存在的理由 ——
     * 第二轮的「它和实体有什么区别？」里的「它」必须由第一轮补全，
     * 这依赖「记忆 advisor 先把历史注入 prompt，检索增强 advisor 才去读」这个顺序。
     *
     * <p>
     * 断言只写「回答非空非空白」，不校验答案质量：模型输出不可预期，
     * 这里要防的是「顺序配错导致链路抛异常或加工结果为空」这类结构性故障，
     * 质量判断靠看日志。
     *
     * <p>
     * 会真实调用 DashScope 两次（两轮问答），因此打了 {@code @Tag("llm")}。
     */
    @Test
    @Tag("llm")
    @DisplayName("RAG 多轮追问：第二轮依赖第一轮的历史做指代消解")
    @Transactional
    void testChatWithRagMultiTurn() throws IOException {
        int fileCount = ragApp.initKnowledgeBase();
        Assertions.assertTrue(fileCount > 0, "未扫描到任何文档，无法验证多轮追问");

        // 同一个 chatId 才会共享对话记忆；换 id 就等于新会话，历史为空
        String chatId = "test-rag-multiturn-" + System.currentTimeMillis();

        String firstQuestion = "什么是聚合根？";
        String firstAnswer = ragApp.doChatWithRag(firstQuestion, chatId);
        log.info("【多轮-第 1 轮】问题：{}", firstQuestion);
        log.info("【多轮-第 1 轮】回答：{}", firstAnswer);
        Assertions.assertNotNull(firstAnswer, "第 1 轮未返回回答");
        Assertions.assertFalse(firstAnswer.isBlank(), "第 1 轮返回的回答为空");

        // 第二轮的提问故意省略主语，只有拿到历史才可能被正确理解 —— 压缩环节就干这件事
        String secondQuestion = "它和实体有什么区别？";
        String secondAnswer = ragApp.doChatWithRag(secondQuestion, chatId);
        log.info("【多轮-第 2 轮】问题：{}", secondQuestion);
        log.info("【多轮-第 2 轮】回答：{}", secondAnswer);

        Assertions.assertNotNull(secondAnswer, "第 2 轮未返回回答（链路在带历史时可能出错）");
        Assertions.assertFalse(secondAnswer.isBlank(), "第 2 轮返回的回答为空");
    }

    /**
     * 相似度得分的可读化输出（非检索结果返回的 Document 可能没有得分）。
     */
    private static String formatScore(Document document) {
        Double score = document.getScore();
        return score == null ? "N/A" : String.format("%.4f", score);
    }

    /**
     * 知识库分块正文较长，日志中截断展示避免刷屏。
     */
    private static String abbreviate(String text) {
        if (text == null) {
            return "N/A";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 80 ? flat : flat.substring(0, 80) + "...";
    }
}

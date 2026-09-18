package com.muzi.muaiagent.rag;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * 四项预检索能力的真实效果验证：这里会<b>真的调用 qwen-plus</b>，每项能力各一个用例。
 *
 * <h2>为什么每个方法都打 {@code @Tag("llm")}</h2>
 * <p>
 * 这几个用例断言的是「模型加工出来的 query 长什么样」，既慢（每个都要等一次大模型往返）
 * 又带不确定性。日常回归用 {@code mvn -B test -DexcludedGroups=llm} 整类跳过，
 * 只在改了预检索相关代码后单独跑。
 *
 * <h2>为什么在用例内现场构建组件</h2>
 * <p>
 * 四个能力默认开关不同（压缩/扩展开，改写/翻译关），如果靠容器提供就得为四种组合
 * 反复启动 Spring 上下文。这里直接注入 {@link ChatClient.Builder} 现场组装 ——
 * 被测的是组件自身的行为，与「开关怎么配」无关，那部分已由
 * {@code PreRetrievalConfigTest} 覆盖。
 *
 * <h2>断言为什么这么松</h2>
 * <p>
 * 大模型的输出无法逐字预测，写死期望值只会得到一个天天红的测试。
 * 所以这里只断言「确定性事实」（输出非空、确实发生了变化、条数落在合理区间），
 * 把加工结果打印到日志里<b>供人工核对</b> —— 质量判断交给人，稳定性交给断言。
 *
 * <h2>为什么不加 {@code @Transactional}</h2>
 * <p>
 * 本类只做 query 加工，不写向量库，没有需要回滚的东西。
 */
@Slf4j
@SpringBootTest
@DisplayName("预检索四项能力的真实效果（调用大模型）")
public class PreRetrievalTransformerLlmTest {

    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Test
    @Tag("llm")
    @DisplayName("查询压缩：把「它」补全成带主语的完整问题")
    void testCompressionResolvesPronoun() {
        // 模拟真实的多轮场景：上一轮问了「什么是聚合根」，这一轮只说了个「它」
        Query query = Query.builder()
                .text("它和实体有什么区别？")
                .history(List.of(
                        new UserMessage("什么是聚合根？"),
                        new AssistantMessage("聚合根是 DDD 中一种特殊的实体，它是聚合的根节点，负责维护聚合内的一致性边界。")
                ))
                .build();

        CompressionQueryTransformer transformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();

        Query compressed = transformer.transform(query);
        log.info("【压缩】原始问题：{}", query.text());
        log.info("【压缩】加工结果：{}", compressed == null ? "null" : compressed.text());

        Assertions.assertNotNull(compressed, "压缩结果不应为 null");
        Assertions.assertFalse(compressed.text() == null || compressed.text().isBlank(),
                "压缩结果不应为空 —— 空 query 会让检索退化成「无意义提问」");
        // 压缩的语义就是「改写」，若与原文完全一致说明这一环没起作用；
        // 注意这里不校验「是否补出了聚合根」——那属于模型效果，靠人看日志判断
        Assertions.assertNotEquals(query.text(), compressed.text(),
                "压缩结果与原始问题完全相同，说明这一环没有生效");
    }

    @Test
    @Tag("llm")
    @DisplayName("查询重写：把口语化提问改写成检索友好的措辞")
    void testRewriteOptimizesQuery() {
        Query query = Query.builder()
                .text("那个啥，聚合根到底是个啥玩意儿，跟实体啥区别啊")
                .build();

        RewriteQueryTransformer transformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .targetSearchSystem("vector store")
                .build();

        Query rewritten = transformer.transform(query);
        log.info("【重写】原始问题：{}", query.text());
        log.info("【重写】加工结果：{}", rewritten == null ? "null" : rewritten.text());

        Assertions.assertNotNull(rewritten, "重写结果不应为 null");
        Assertions.assertFalse(rewritten.text() == null || rewritten.text().isBlank(),
                "重写结果不应为空");
        Assertions.assertNotEquals(query.text(), rewritten.text(),
                "重写结果与原始问题完全相同，说明这一环没有生效");
    }

    @Test
    @Tag("llm")
    @DisplayName("查询翻译：把中文提问翻成目标语言")
    void testTranslationSwitchesLanguage() {
        Query query = Query.builder()
                .text("什么是聚合根？它和实体有什么区别？")
                .build();

        TranslationQueryTransformer transformer = TranslationQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .targetLanguage("English")
                .build();

        Query translated = transformer.transform(query);
        log.info("【翻译】原始问题：{}", query.text());
        log.info("【翻译】加工结果：{}", translated == null ? "null" : translated.text());

        Assertions.assertNotNull(translated, "翻译结果不应为 null");
        Assertions.assertFalse(translated.text() == null || translated.text().isBlank(),
                "翻译结果不应为空");
        // 只断言「变了」。这里特意<b>不</b>断言「一定是英文」：
        // 模型偶尔会把专有名词原样保留，逐字校验语言很容易造成偶发失败。
        // 真要看效果，看上面两行日志即可 —— 这也是这个用例存在的主要价值。
        Assertions.assertNotEquals(query.text(), translated.text(),
                "翻译结果与原始问题完全相同，说明这一环没有生效");
    }

    @Test
    @Tag("llm")
    @DisplayName("多查询扩展：一条问题扩成多个变体")
    void testMultiQueryExpansionProducesMultipleQueries() {
        Query query = Query.builder()
                .text("聚合根和实体有什么区别？")
                .build();

        MultiQueryExpander expander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(3)
                .includeOriginal(true)
                .build();

        List<Query> expanded = expander.expand(query);
        // 把变体逐条打出来：扩展的价值全在「变体是否换了角度」，只能靠人看
        if (expanded != null) {
            expanded.forEach(item -> log.info("【扩展】变体：{}", item.text()));
        }

        Assertions.assertNotNull(expanded, "扩展结果不应为 null");
        // 不写死等于 3：模型偶尔少给几条，框架此时会优雅降级，
        // 断言条数精确相等只会得到一个偶发失败。≥2 足以证明「扩展确实发生了」。
        Assertions.assertTrue(expanded.size() >= 2,
                "扩展后至少应有 2 条查询，实际为 " + expanded.size() + " 条");
        // includeOriginal=true 时原始查询必须在其中 —— 这是召回率的保险，
        // 验证它能保证「最坏情况也不比不做扩展差」
        Assertions.assertTrue(expanded.stream().anyMatch(item -> query.text().equals(item.text())),
                "开启 includeOriginal 后，原始查询应保留在变体列表中");
    }
}

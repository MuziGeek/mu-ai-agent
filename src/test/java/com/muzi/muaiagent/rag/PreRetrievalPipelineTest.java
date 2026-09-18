package com.muzi.muaiagent.rag;

import com.muzi.muaiagent.rag.config.PreRetrievalConfig;
import com.muzi.muaiagent.rag.config.PreRetrievalPipeline;
import com.muzi.muaiagent.rag.config.PreRetrievalProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

import java.util.List;

/**
 * 预检索链路装配测试：验证「开关 → 链路内容 → 执行顺序」这条映射关系。
 *
 * <h2>为什么这个测试不启动 Spring</h2>
 * <p>
 * 装配逻辑被刻意抽成了 {@link PreRetrievalConfig#composeQueryTransformers} 这个静态纯函数，
 * 只接收配置对象和几个组件实例。于是这里可以完全脱离容器做验证：
 * 不连数据库、不调大模型、毫秒级跑完。
 * <p>
 * 反过来，如果这层逻辑写在 {@code @Bean} 方法体里，就只能启动整个应用上下文、
 * 再把 {@code RetrievalAugmentationAdvisor} 的私有字段反射出来看 ——
 * 那才是「测试成本远高于被测逻辑本身」。
 * <p>
 * 组件实例用 {@code query -> query} 这种空实现的 lambda 充当桩：
 * {@code QueryTransformer} 的抽象方法只有一个，是函数式接口，用 lambda 最省事，
 * 而且用 {@code assertSame} 比对时靠对象身份判断，不会与别的实现混淆。
 *
 * @see PreRetrievalConfig#composeQueryTransformers
 */
@DisplayName("预检索链路装配（纯逻辑，不启 Spring、不调模型）")
public class PreRetrievalPipelineTest {

    /** 三个桩实例：只求「能被放链路里」，行为无关紧要 */
    private static final QueryTransformer COMPRESSION_STUB = query -> query;
    private static final QueryTransformer REWRITE_STUB = query -> query;
    private static final QueryTransformer TRANSLATION_STUB = query -> query;

    @Test
    @DisplayName("四个开关全关时链路为空，不会产生任何多余的模型调用")
    void testAllSwitchesOffProducesEmptyChain() {
        PreRetrievalProperties props = new PreRetrievalProperties();
        props.getCompression().setEnabled(false);
        props.getRewrite().setEnabled(false);
        props.getTranslation().setEnabled(false);

        List<QueryTransformer> chain = PreRetrievalConfig.composeQueryTransformers(
                props, COMPRESSION_STUB, REWRITE_STUB, TRANSLATION_STUB);

        Assertions.assertTrue(chain.isEmpty(),
                "全部关闭时链路必须为空；只要有一个漏进链路，每次问答就会白白多一次大模型调用");
    }

    @Test
    @DisplayName("四个开关全开时，顺序恰为「压缩 → 改写 → 翻译」")
    void testAllSwitchesOnKeepsExecutionOrder() {
        PreRetrievalProperties props = allEnabled();

        List<QueryTransformer> chain = PreRetrievalConfig.composeQueryTransformers(
                props, COMPRESSION_STUB, REWRITE_STUB, TRANSLATION_STUB);

        Assertions.assertEquals(3, chain.size(), "三个开关全开时应有三段链路");
        // 顺序是有语义的：压缩负责把残缺的指代补全成完整问题，必须最先；
        // 翻译会把文本换成另一种语言，必须最后，否则后面几步要在非母语文本上工作。
        Assertions.assertSame(COMPRESSION_STUB, chain.get(0), "压缩必须最先执行：它要把「它 / 这个」补全成完整问题");
        Assertions.assertSame(REWRITE_STUB, chain.get(1), "改写居中：此时句子已完整，可专注润色措辞");
        Assertions.assertSame(TRANSLATION_STUB, chain.get(2), "翻译必须最后：换语言后不应再有同语言加工");
    }

    @Test
    @DisplayName("只开改写时链路上只有改写")
    void testOnlyRewriteEnabled() {
        PreRetrievalProperties props = new PreRetrievalProperties();
        props.getCompression().setEnabled(false);
        props.getRewrite().setEnabled(true);
        props.getTranslation().setEnabled(false);

        List<QueryTransformer> chain = PreRetrievalConfig.composeQueryTransformers(
                props, COMPRESSION_STUB, REWRITE_STUB, TRANSLATION_STUB);

        Assertions.assertEquals(1, chain.size(), "只开一个开关时应只有一段链路");
        Assertions.assertSame(REWRITE_STUB, chain.get(0), "链路上应该是改写转换器");
    }

    @Test
    @DisplayName("只开翻译时链路上只有翻译")
    void testOnlyTranslationEnabled() {
        PreRetrievalProperties props = new PreRetrievalProperties();
        props.getCompression().setEnabled(false);
        props.getRewrite().setEnabled(false);
        props.getTranslation().setEnabled(true);

        List<QueryTransformer> chain = PreRetrievalConfig.composeQueryTransformers(
                props, COMPRESSION_STUB, REWRITE_STUB, TRANSLATION_STUB);

        Assertions.assertEquals(1, chain.size(), "只开一个开关时应只有一段链路");
        Assertions.assertSame(TRANSLATION_STUB, chain.get(0), "链路上应该是翻译转换器");
    }

    @Test
    @DisplayName("开关打开但组件实例为 null 时不把 null 放进链路")
    void testNullComponentNeverEntersChain() {
        // 框架对转换器列表调的是 Assert.noNullElements(...)，一旦链路里混入 null
        // 会直接在构造 RetrievalAugmentationAdvisor 时抛异常，且异常信息不指向真正的根因。
        // 这里守住「宁可少一段，也绝不放 null」。
        PreRetrievalProperties props = allEnabled();

        List<QueryTransformer> chain = PreRetrievalConfig.composeQueryTransformers(props, null, null, null);

        Assertions.assertTrue(chain.isEmpty(), "组件实例为 null 时不应进入链路");
    }

    @Test
    @DisplayName("默认配置：压缩与扩展开、改写与翻译关")
    void testDocumentedDefaults() {
        // 这组默认值是「性能与效果」权衡后的结论，写死在代码里（见 PreRetrievalProperties 类注释），
        // 因此可以脱离 YAML 单独断言。若将来要改默认值，这里会第一时间提醒同步更新文档。
        PreRetrievalProperties props = new PreRetrievalProperties();

        Assertions.assertTrue(props.getCompression().isEnabled(),
                "压缩默认开：项目有对话记忆，多轮追问是主要交互形态");
        Assertions.assertFalse(props.getRewrite().isEnabled(),
                "改写默认关：与压缩职责重叠，串行会白多一次大模型调用");
        Assertions.assertFalse(props.getTranslation().isEnabled(),
                "翻译默认关：中文提问 + 中文知识库，翻译必然降低召回");
        Assertions.assertTrue(props.getExpansion().isEnabled(),
                "扩展默认开：对召回的提升最明显");
        Assertions.assertEquals(3, props.getExpansion().getNumberOfQueries(),
                "变体数量默认 3：再多收益递减，而检索次数线性增长");
        Assertions.assertTrue(props.getExpansion().isIncludeOriginal(),
                "默认保留原查询：大模型改写跑偏时，至少不比不做扩展更差");
        Assertions.assertEquals(4, props.getRetrieval().getTopK(),
                "topK 默认 4，与改造前 QuestionAnswerAdvisor 的默认值保持一致");
        Assertions.assertTrue(props.getAugmenter().isAllowEmptyContext(),
                "默认允许空上下文：拒答交由 system 提示词统一负责，避免两处重复约束");
    }

    @Test
    @DisplayName("链路对象的转换器列表永不为 null，因为框架不接受 null 列表")
    void testPipelineNormalizesNullTransformerList() {
        // RetrievalAugmentationAdvisor.Builder.queryTransformers(List) 内部调
        // Assert.noNullElements(collection, ...)，而该方法要求集合本身非 null。
        // 归一化放在 record 的紧凑构造器里，调用方就不必各自判空。
        PreRetrievalPipeline pipeline = new PreRetrievalPipeline(null, null);

        Assertions.assertNotNull(pipeline.transformers(), "转换器列表应被归一成空列表，而不是保留 null");
        Assertions.assertTrue(pipeline.transformers().isEmpty(), "归一化结果应为空列表");
        Assertions.assertNull(pipeline.expander(), "扩展器未启用时保持 null（框架对该字段做了非空判断，跳过扩展）");
    }

    /** 构造一个四个能力全开的配置，供多个用例复用。 */
    private static PreRetrievalProperties allEnabled() {
        PreRetrievalProperties props = new PreRetrievalProperties();
        props.getCompression().setEnabled(true);
        props.getRewrite().setEnabled(true);
        props.getTranslation().setEnabled(true);
        props.getExpansion().setEnabled(true);
        return props;
    }
}

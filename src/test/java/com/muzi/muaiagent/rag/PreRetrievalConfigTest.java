package com.muzi.muaiagent.rag;

import com.muzi.muaiagent.rag.app.RagApp;
import com.muzi.muaiagent.rag.config.PreRetrievalPipeline;
import com.muzi.muaiagent.rag.config.PreRetrievalProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 预检索链路的容器装配测试：验证「配置 → 生效的组件」以及「advisor 顺序不变量」。
 *
 * <h2>为什么不需要 {@code @Tag("llm")}</h2>
 * <p>
 * 本类只做装配层面的断言：组件是不是自己那一个、链路内容跟开关是否一致、order 关系对不对。
 * 全部是读内存对象的属性，<b>不触发任何大模型调用，也不写数据库</b>，
 * 因此既不用加 {@code @Tag("llm")}，也不需要 {@code @Transactional}。
 * <p>
 * 「预检索加工出来的 query 到底对不对」属于模型效果问题，放在
 * {@code PreRetrievalTransformerLlmTest} 里单独验证。
 *
 * <h2>断言为什么按配置而不是按写死的默认值</h2>
 * <p>
 * 链路内容与 YAML 里的开关是同一份事实，若这里写死「默认恰好 1 个转换器」，
 * 那么以后有人调 YAML 就得跟着改测试 —— 测试变成配置的复读机，反而不如直接校验
 * 「链路内容与配置开关一致」。真正需要写死的默认值在
 * {@link PreRetrievalPipelineTest#testDocumentedDefaults} 里已经单独守住了。
 */
@Slf4j
@SpringBootTest
@DisplayName("预检索链路的容器装配")
public class PreRetrievalConfigTest {

    @Resource
    private PreRetrievalProperties preRetrievalProperties;

    @Resource
    private PreRetrievalPipeline preRetrievalPipeline;

    @Resource
    private DocumentRetriever documentRetriever;

    @Resource
    private RetrievalAugmentationAdvisor retrievalAugmentationAdvisor;

    @Resource
    private RagApp ragApp;

    /** 用于确认「旧的 QuestionAnswerAdvisor 已彻底退出」，而不只是代码里不再 new 它 */
    @Resource
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("检索走的是项目自己的向量库检索器")
    void testDocumentRetrieverIsVectorStoreBacked() {
        log.info("当前注入的 DocumentRetriever 实现：{}", documentRetriever.getClass().getName());

        // 断言具体实现而不是接口：接口为空实现留了口子，
        // 万一哪天有人塞进一个「什么都返回不了」的检索器，检索会静默失忆而不是报错
        Assertions.assertInstanceOf(VectorStoreDocumentRetriever.class, documentRetriever,
                "检索器不是基于 VectorStore 的实现，检索结果将不可预期");
    }

    @Test
    @DisplayName("链路内容与配置开关严格一致")
    void testPipelineContentMatchesSwitches() {
        log.info("生效的预检索链路：{}", preRetrievalPipeline.describe());

        Set<Class<?>> activeTransformers = preRetrievalPipeline.transformers().stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        // 四个方向都验：开关说开的必须真在链路里（漏装配），说关的必须真不在（装配了但没生效）。
        // 这类偏差不会报错，只会让「以为调没调」的问题在排查时变得极难定位。
        Assertions.assertEquals(preRetrievalProperties.getCompression().isEnabled(),
                activeTransformers.contains(CompressionQueryTransformer.class),
                "压缩开关与链路内容不一致，请检查 PreRetrievalConfig.composeQueryTransformers");

        Assertions.assertEquals(preRetrievalProperties.getRewrite().isEnabled(),
                activeTransformers.contains(RewriteQueryTransformer.class),
                "改写开关与链路内容不一致");

        Assertions.assertEquals(preRetrievalProperties.getTranslation().isEnabled(),
                activeTransformers.contains(TranslationQueryTransformer.class),
                "翻译开关与链路内容不一致");

        Assertions.assertEquals(preRetrievalProperties.getExpansion().isEnabled(),
                preRetrievalPipeline.expander() != null,
                "扩展开关与链路内容不一致");

        if (preRetrievalPipeline.expander() != null) {
            Assertions.assertInstanceOf(MultiQueryExpander.class, preRetrievalPipeline.expander(),
                    "扩展器不是 MultiQueryExpander 实现");
        }
    }

    @Test
    @DisplayName("检索增强 advisor 的 order 落在「记忆之后、日志之前」的安全区间")
    void testAdvisorOrderInvariant() {
        int order = retrievalAugmentationAdvisor.getOrder();
        log.info("advisor 顺序：记忆={}, 检索增强={}, 日志={}",
                RagApp.MEMORY_ADVISOR_ORDER, order, RagApp.LOGGER_ADVISOR_ORDER);

        Assertions.assertEquals(preRetrievalProperties.getAdvisorOrder(), order,
                "advisor 的 order 应与配置项一致");

        // 这条不变量是整条链路里最容易被静默破坏的一处：
        // RetrievalAugmentationAdvisor 在 before 阶段从 prompt 里读对话历史，
        // 而历史是记忆 advisor 注入的。order 反了不会抛异常，
        // 只是「它和实体有什么区别？」里的「它」永远指不明白 —— 检索质量悄悄退化。
        Assertions.assertTrue(order > RagApp.MEMORY_ADVISOR_ORDER,
                "检索增强 advisor 必须排在记忆 advisor 之后，否则读不到对话历史，多轮指代消解会静默失效");

        // 日志 advisor 要在最后，才能记录到「已注入知识库上下文」的最终请求；
        // 否则排查检索问题时，日志里只有用户原话，看不到检索到了什么。
        Assertions.assertTrue(order < RagApp.LOGGER_ADVISOR_ORDER,
                "检索增强 advisor 必须排在日志 advisor 之前，否则日志看不到注入后的上下文");
    }

    @Test
    @DisplayName("RagApp 已用检索增强 advisor 重构，旧的 QuestionAnswerAdvisor 不再出现在容器里")
    void testRagAppWiredWithoutLegacyAdvisor() {
        // RagApp 的构造参数里已经换成 RetrievalAugmentationAdvisor：
        // 它能被创建，本身就说明「检索增强 advisor 由容器提供」这条装配链是通的。
        Assertions.assertNotNull(ragApp, "RagApp 应能被正常创建");

        String[] legacyBeans = applicationContext.getBeanNamesForType(QuestionAnswerAdvisor.class);
        Assertions.assertEquals(0, legacyBeans.length,
                "容器里不应再有 QuestionAnswerAdvisor：旧链路若被某处重新注册，会绕过全部预检索环节");
    }
}

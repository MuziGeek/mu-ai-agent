package com.muzi.muaiagent.rag.config;

import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

import java.util.List;

/**
 * 预检索链路的装配结果：一串按执行顺序排好的查询转换器 + 一个（可选的）查询扩展器。
 *
 * <h2>为什么要单独包一层 record</h2>
 * <p>
 * 四个预检索组件是「开关 + 装配顺序」决定的，而不是简单的「Bean 存不存在」：
 * 关掉的组件不该出现在链路上，开着的组件必须按「先消解指代 → 再润色措辞 → 最后换语言」的顺序执行。
 * 如果把这些组件直接注册成 {@code List<QueryTransformer>} 类型的 Bean，
 * 就只能靠反射去窥探 {@code RetrievalAugmentationAdvisor} 的私有字段，才知道「哪些开关真正生效了」。
 * <p>
 * 把它们收进一个显式的 record 并单独注册成 Bean，
 * 测试就能直接注入并断言链路内容（有几个转换器、分别是谁、扩展器开没开），
 * 不必启动大模型，也不必碰反射。
 *
 * <h2>为什么 {@code expander} 允许为 null</h2>
 * <p>
 * 扩展器可以通过配置整体关闭，此时语义就是「不做多查询扩展」。
 * 已反编译确认 {@code RetrievalAugmentationAdvisor.before} 里对扩展器做了
 * {@code if (queryExpander != null)} 判断，传 null 是安全的（不会 NPE，只是跳过扩展）。
 * 而 {@code transformers} 一侧相反：框架对列表调的是 {@code Assert.noNullElements(...)}，
 * 它要求集合本身非 null，所以这里把 null 归一成空列表。
 *
 * @param transformers 已按执行顺序排好的查询转换器；关闭全部开关时为空列表
 * @param expander     多查询扩展器；未启用时为 null
 */
public record PreRetrievalPipeline(List<QueryTransformer> transformers, QueryExpander expander) {

    /**
     * 紧凑构造器：把 null 的转换器列表归一成空列表。
     * 框架侧对空列表是宽容的（无转换器就直接跳过），但对 null 列表会直接抛异常，
     * 归一化放在这里，调用方就不必再各自判空。
     */
    public PreRetrievalPipeline {
        transformers = (transformers == null) ? List.of() : List.copyOf(transformers);
    }

    /**
     * 生成可读的链路描述，供启动日志与测试断言使用。
     * 只打类名不打内容——组件内部都持有 ChatClient，直接 toString 会刷出大量无关信息。
     */
    public String describe() {
        String transformerNames = transformers.stream()
                .map(transformer -> transformer.getClass().getSimpleName())
                .toList()
                .toString();
        String expanderName = (expander == null) ? "未启用" : expander.getClass().getSimpleName();
        return "转换器=" + transformerNames + ", 扩展器=" + expanderName;
    }
}

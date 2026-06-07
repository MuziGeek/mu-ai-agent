package com.muzi.muaiagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 面试题详情实体 —— 用于演示"单个 Bean"结构化输出。
 * <p>
 * Spring AI 会根据这个类的字段自动生成 JSON Schema，
 * 并在 prompt 中附加格式指令，让大模型返回符合该 Schema 的 JSON。
 * <p>
 * 注意事项：
 *   1. 必须有无参构造器（Jackson 反序列化需要）
 *   2. 字段名会直接映射为 JSON key（可通过 @JsonProperty 自定义）
 *   3. 嵌套对象、List 等复杂类型都能正确生成 Schema
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewQuestion {

    /** 面试问题 */
    private String question;

    /** 参考答案 */
    private String referenceAnswer;

    /** 关键要点（列表） */
    private List<String> keyPoints;

    /** 难度等级：入门 / 中级 / 高级 */
    private String difficultyLevel;

    /** 常见追问方向 */
    private List<String> followUpDirections;

    /** 考察的技术方向（如：JVM、并发、Spring 等） */
    private String techCategory;
}

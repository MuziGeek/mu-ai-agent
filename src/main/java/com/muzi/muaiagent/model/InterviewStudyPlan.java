package com.muzi.muaiagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 面试复习计划实体 —— 用于演示"复杂嵌套对象"结构化输出。
 * <p>
 * 包含嵌套结构（内部类 Topic、内部引用 List&lt;InterviewQuestion&gt;），
 * 可以验证 Spring AI 的 JSON Schema 生成是否能正确处理多层嵌套。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterviewStudyPlan {

    /** 技术方向（如：Java 后端） */
    private String techDirection;

    /** 本次复习的核心考察重点 */
    private List<String> focusAreas;

    /** 本次复习涉及的题目列表（嵌套 InterviewQuestion） */
    private List<InterviewQuestion> questions;

    /** 给求职者的学习建议 */
    private String learningAdvice;

    /** 建议复习时长（分钟） */
    private Integer estimatedMinutes;
}

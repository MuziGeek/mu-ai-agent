package com.muzi.muaiagent.rag.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * RAG 配置类：支持 SimpleVectorStore 和 PgVectorStore 共存，通过 Profile 切换。
 * <p>
 * 使用方式：
 * <ul>
 *     <li>默认（仅 local profile）：PgVectorStore 自动配置，向量持久化到 PostgreSQL</li>
 *     <li>激活 simple-vector profile：使用内存版 SimpleVectorStore，无需数据库</li>
 * </ul>
 * 切换示例：spring.profiles.active=local,simple-vector
 */
@Configuration
public class RagConfig {

    /**
     * 内存版 SimpleVectorStore，仅在 simple-vector profile 下生效。
     * 适合本地快速调试，无需连接 PostgreSQL。
     */
    @Bean
    @Profile("simple-vector")
    public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}

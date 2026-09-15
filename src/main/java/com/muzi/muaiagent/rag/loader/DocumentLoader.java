package com.muzi.muaiagent.rag.loader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档加载器：扫描指定目录下的文档，使用 Tika 解析 + TokenTextSplitter 分块后写入向量存储。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentLoader {

    /**
     * DDD 知识库文档的 classpath 扫描路径。
     * 目录不存在或其中没有 .md 文件时，{@link #loadDddDocuments()} 只打 warn 日志并返回 0，不会抛异常。
     */
    private static final String DDD_DOCUMENT_PATTERN = "classpath:document/Java8Gu5/DDD/**/*.md";

    private final VectorStore vectorStore;

    /**
     * 加载并解析 DDD 目录下的所有文档，分块后写入向量数据库。
     *
     * @return 写入的文档总数
     */
    public int loadDddDocuments() throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(DDD_DOCUMENT_PATTERN);

        if (resources.length == 0) {
            log.warn("未找到 DDD 目录下的任何文档");
            return 0;
        }

        log.info("扫描到 {} 个文档文件", resources.length);

        List<Document> allChunks = new ArrayList<>();
        TokenTextSplitter splitter = new TokenTextSplitter();

        for (Resource resource : resources) {
            String filename = resource.getFilename();
            try {
                TikaDocumentReader reader = new TikaDocumentReader(resource);
                List<Document> documents = reader.get();
                // 为每个文档添加来源元数据
                documents.forEach(doc -> doc.getMetadata().put("source", filename));

                // 分块
                List<Document> chunks = splitter.apply(documents);
                allChunks.addAll(chunks);
                log.info("解析文件 [{}] → {} 个原始文档 → {} 个分块", filename, documents.size(), chunks.size());
            } catch (Exception e) {
                log.error("解析文件 [{}] 失败，跳过: {}", filename, e.getMessage());
            }
        }

        if (!allChunks.isEmpty()) {
            // 这里不再手动按 10 条切片：单次请求的文档条数与 token 预算统一由 BatchingStrategy
            // （见 DashScopeBatchingStrategy）负责，调用方只需一次性提交，批次的日志也在策略里输出。
            // 好处是「DashScope 单次最多 10 条文本」这个厂商限制只存在于一处，改限制不用改这里。
            vectorStore.add(allChunks);
            log.info("共写入 {} 个文档分块到向量存储", allChunks.size());
        }

        return resources.length;
    }
}

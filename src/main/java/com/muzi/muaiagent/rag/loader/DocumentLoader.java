package com.muzi.muaiagent.rag.loader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
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

    private final VectorStore vectorStore;

    /**
     * DDD 文档目录（classpath 路径）。
     */
    @Value("classpath:document/Java8Gu5/DDD")
    private Resource dddDir;

    /**
     * 加载并解析 DDD 目录下的所有文档，分块后写入向量数据库。
     *
     * @return 写入的文档总数
     */
    public int loadDddDocuments() throws IOException {
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:document/Java8Gu5/DDD/**/*.md");

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
            // DashScope text-embedding-v3 单次请求最多 10 个文档，分批写入
            int batchSize = 10;
            for (int i = 0; i < allChunks.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allChunks.size());
                List<Document> batch = allChunks.subList(i, end);
                vectorStore.add(batch);
                log.info("写入批次 {}/{} ({} 个分块)", (i / batchSize) + 1,
                        (int) Math.ceil((double) allChunks.size() / batchSize), batch.size());
            }
            log.info("共写入 {} 个文档分块到向量存储", allChunks.size());
        }

        return resources.length;
    }
}

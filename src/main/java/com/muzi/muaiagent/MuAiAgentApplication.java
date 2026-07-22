package com.muzi.muaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * mu-ai-agent 启动类。
 * <p>
 * 已启用 PgVectorStore，需要 DataSourceAutoConfiguration 创建 DataSource 和 JdbcTemplate Bean。
 */
@SpringBootApplication
public class MuAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MuAiAgentApplication.class, args);
    }

}

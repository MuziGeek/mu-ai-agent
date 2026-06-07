package com.muzi.muaiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 排除 DataSourceAutoConfiguration：
 * spring-ai-alibaba-starter-memory 会引入 JDBC 相关依赖，
 * 触发 Spring Boot 自动配置 DataSource，但本项目不需要数据库，
 * 所以在这里显式排除，避免启动时报 "Failed to determine a suitable driver class" 错误。
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class MuAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MuAiAgentApplication.class, args);
    }

}

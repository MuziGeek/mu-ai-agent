# mu-ai-agent 项目长期记忆

## 技术栈与版本（勿随意升级）
- Spring Boot 3.4.5 + Java 21，Maven 构建（`mvnw` 可用）
- Spring AI **1.1.8**（`spring-ai-bom` 管理版本，放在 `spring-ai-alibaba-bom 1.0.0.2` 之前）
- DashScope：chat `qwen-plus`、vision `qwen-vl-max`、embedding `text-embedding-v3`（**1024 维**）
- 已知坑：`jsonschema-generator` 被 alibaba BOM 锁到 4.31.1，需显式声明 4.37.0，
  否则 `BeanOutputConverter` 抛 `NoClassDefFoundError: AnnotationHelper`
- `spring-ai-alibaba-starter-dashscope` 与 `autoconfigure-dashscope` 都需显式写 1.1.2.1
- 已知坑：`maven-compiler-plugin` 的 `annotationProcessorPaths` 里 lombok 的 `<path>`
  **必须写 `<version>${lombok.version}</version>`**（1.18.38，属性由 spring-boot-dependencies 定义）
  - 不写版本时 IDEA 不回溯父 POM 的 dependencyManagement，会解析成 `unknown` 并生成
    不存在的路径 `.../lombok/unknown/lombok-unknown.jar` → 注解处理器不加载
    → `@Slf4j` 不生成 `log` 字段 → **IDEA 编译报「找不到符号 变量 log」**
  - Maven 侧能正常解析，所以症状是「命令行 `mvn compile` 成功、IDEA 编译失败」——
    不要误判为代码问题
  - 修复后需在 IDEA 里 Reload Maven 项目，让其重写 `.idea/compiler.xml` 的 processorPath

## 配置约定
- `application.yml` 为公共配置，用 `${DB_URL}/${DB_USERNAME}/${DB_PASSWORD}/${AI_DASHSCOPE_API_KEY}` 环境变量
- `application-local.yml` 覆盖为真实值（当前明文硬编码 RDS 地址/密码/DashScope Key，**属安全隐患，勿外传**）
- 默认 profile = `local`，即 PgVectorStore 生效；`simple-vector` profile 是内存版备选
- 服务端口 8123，context-path `/api`；Knife4j 文档 `/api/swagger-ui.html`

## pgvector 事实（Spring AI 1.1.8，字节码确认）
- 纯自动配置，无手写初始化代码：`PgVectorStoreAutoConfiguration` → `PgVectorStore.afterPropertiesSet()`
- 生效条件：`@ConditionalOnProperty(name=spring.ai.vectorstore.type, havingValue=pgvector, matchIfMissing=true)`
  → **不配该属性也默认生效**；想关掉应设 `spring.ai.vectorstore.type=none`，
  而不是 `spring.ai.vectorstore.pgvector.enabled`（1.1.8 无此属性，写了无效）
- 建表顺序：`CREATE EXTENSION vector/hstore/uuid-ossp` → `CREATE SCHEMA` → `CREATE TABLE` → `CREATE INDEX`，
  全部 `IF NOT EXISTS` → **已存在的表不会被改写**，改维度必须手动 DROP/ALTER
- 默认值：表 `vector_store`、schema `public`、索引 `spring_ai_vector_index`、
  id-type `uuid`、index-type `hnsw`、distance `cosine`、
  `initializeSchema=false`（项目显式设 true）、`schemaValidation=false`、fallback 维度 1536
- 坑：`simple-vector` profile 下 PgVector Bean 仍会创建（`@ConditionalOnMissingBean` 按返回类型
  `PgVectorStore` 判定，`SimpleVectorStore` 不是其子类）；而 `RagApp`/`DocumentLoader` 构造参数名
  恰好叫 `vectorStore`，与自动配置 Bean 同名 → 仍注入 PgVector，**该 profile 切换实际无效**

## 批处理策略（BatchingStrategy）事实（字节码确认）
- `PgVectorStoreAutoConfiguration` 有默认策略 Bean：`@Bean @ConditionalOnMissingBean
  BatchingStrategy pgVectorStoreBatchingStrategy() { return new TokenCountBatchingStrategy(); }`
  → 自定义 `BatchingStrategy` Bean 可覆盖它；默认值 = `CL100K_BASE` + 8191 tokens + 保留 0.1
- `DashScopeEmbeddingModel` **覆写** `embed(List<Document>, EmbeddingOptions, BatchingStrategy)`，
  但它只是补默认 options 后 `invokespecial` 到 `EmbeddingModel` 的 default 实现
  → **策略确实生效**，`for (batch : batchingStrategy.batch(docs)) call(new EmbeddingRequest(...))`
  → **每个批次 = 一次 DashScope HTTP 请求**
- `TokenCountBatchingStrategy` 只按 **token** 切批，**对文档条数无上限**
  → 这是 DashScope「单次最多 10 条文本」限制之前需要 `DocumentLoader` 手动切片的原因
- `batch()` 的返回结果**必须保持原列表顺序**（`embed()` 按位置回填向量），内部用 LinkedHashMap 保序
- 单文档超 token 预算 → 抛 `IllegalArgumentException`（无从再切）
- `TokenCountBatchingStrategy(EncodingType,int,double)` 三参构造内部用
  `Document.DEFAULT_CONTENT_FORMATTER` + `MetadataMode.NONE` 估算 token；实际预算 = `round((1-reserve)*max)`
- `jtokkit`（`EncodingType` 所在库）版本 1.1.0，已在 pom 中**显式声明**（compile 作用域）
  - `spring-ai-bom 1.1.8` **不管理**该版本 → 必须写 `<version>1.1.0</version>`，
    与原先传递引入的版本保持一致（来源链 `spring-ai-tika-document-reader → spring-ai-commons`）
  - 升级 Spring AI 时要留意传递版本是否已变，显式版本不会跟着漂移

## 本项目的批处理策略实现
- `rag/config/EmbeddingBatchingConfig.java`：注册 `@Bean BatchingStrategy batchingStrategy(...)`，
  参数可用 `mu-ai.rag.batching.max-documents-per-request(10)` / `max-input-tokens(8000)` /
  `reserve-percentage(0.1)` 覆盖
- `rag/config/DashScopeBatchingStrategy.java`：两级切分 —— 先按条数分组，再委托
  `TokenCountBatchingStrategy` 按 token 预算细分，并对超限文档补上 `source` 定位信息
- `DocumentLoader` 的手动 10 条切片循环**已删除**，改为 `vectorStore.add(allChunks)` 一次性提交
  （厂商限制现在只存在于策略一处）
- 实测：12 个分块 → 2 个批次；灌库结果与改造前完全一致（相似度 0.8209 / 0.4438 / 0.4339 未变）

## 知识库文档目录（已对齐）
- `DocumentLoader` 扫描路径：常量 `DDD_DOCUMENT_PATTERN = "classpath:document/Java8Gu5/DDD/**/*.md"`
- 实际目录：`src/main/resources/document/Java8Gu5/DDD/`，含 7 个 .md + `img/` 图片
  - 目录内有一层多余的嵌套 `DDD/DDD/`（疑似解压产生），`**` 通配符可覆盖，未清理
- 灌库实测：7 个文件 → 12 个分块；查询「什么是聚合根？」相似度 0.8209 命中对应文档

## 测试约定
- 集成测试放 `src/test/java/com/muzi/muaiagent/**`，`@SpringBootTest`
- 会写库的测试一律加 `@Transactional` 自动回滚，避免污染共享 RDS
  （已验证有效：连续多次运行，表内行数不增长）
- 注入 VectorStore 时用 `@Resource(name = "vectorStore")` 显式按名注入
- 断言避免过度约束（表里可能有历史数据），用 `allMatch` / `size() <= topK` 这类宽松写法
- 运行：`mvn -B test -Dtest=<类名>`（需先设 `JAVA_HOME=D:\Scoop\apps\temurin21-jdk\current`）
- **会调用大模型的用例一律加 `@Tag("llm")`**，便于快速回归时排除：
  `mvn -B test -DexcludedGroups=llm`（已验证：排除后只跑确定性用例）
  - 断言只能写「检索非空」「回答非空白」这类确定性事实，答案对错靠人看日志
- 测试类：`rag/PgVectorVectorStoreConfigTest`（6 个用例，1 个带 `@Tag("llm")`）、
  `rag/EmbeddingBatchingStrategyTest`（5 个用例，纯内存切批计算，无需 `@Transactional`）
- 知识库初始化与 RAG 问答的触发入口只存在于 `PgVectorVectorStoreConfigTest`
  （`testInitKnowledgeBase` / `testChatWithRag`），生产代码里没有 Runner/Controller 调用
  `RagApp.initKnowledgeBase()` 与 `RagApp.doChatWithRag()`（有意为之）

## 代码风格
- 注释、日志、`@DisplayName` 一律用中文，注释解释「为什么」而非「是什么」
- `@Slf4j` + `@RequiredArgsConstructor`（Lombok）为主
- 包结构：`advisor` / `app` / `chatmemory` / `config` / `controller` / `demo` / `filter` / `model` /
  `rag`（下分 `app` `config` `loader`）/ `service`
## 已知未修复问题
- `application-local.yml` 明文硬编码 RDS 密码与 DashScope Key，建议改环境变量
- `.gitignore` 只写了 `/src/main/resources/application-local.yml`（带路径锚点），
  根目录同名文件不受保护
- `RagApp.doChatWithRag()` 在 `chatResponse` 为 null 时返回 null（未抛异常），调用方需自行判空

## 运维小工具（临时脚本，需要时重建）
- 直连 RDS 排查 `vector_store`：用 Java 21 单文件启动器 + 本地仓库里的 postgresql 驱动，
  **不要把数据库配置写进项目代码**
  `java "-cp" D:\Scoop\apps\maven\3.9.16\rep\org\postgresql\postgresql\42.7.5\postgresql-42.7.5.jar <脚本>.java`
- 清理前务必先导出 INSERT 备份（`.workbuddy/backup/` 下已有 2026-09-15 的全表快照）
- 已知表状态（2026-09-15 清空后）：0 行，结构和索引完好

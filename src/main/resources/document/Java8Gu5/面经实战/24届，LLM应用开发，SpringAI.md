# 24届，LLM应用开发，SpringAI

# 面试者背景


:::warning
**<font style="color:#000000;">24届硕士毕业，上市公司，2年经验，AI平台构建，AI网关平台，大模型服务接入治理，10家厂商，百个大模型接入，企业级AI问答应用，也做传统后端开发。</font>**

**<font style="color:#000000;">AI</font>****<font style="color:#000000;">网关实现方案？</font>****<font style="color:#000000;">apisix</font>****<font style="color:#000000;">，</font>****<font style="color:#000000;">java</font>****<font style="color:#000000;">实现了哪些功能？费用计算，模型限流。</font>**

**<font style="color:#000000;">网关都有哪些功能介绍下？审批</font>****<font style="color:#000000;">&</font>****<font style="color:#000000;">开放</font>****<font style="color:#000000;">API</font>****<font style="color:#000000;">，</font>****<font style="color:#000000;">API</font>****<font style="color:#000000;">管理（限流、模型校验、</font>****<font style="color:#000000;">KEY</font>****<font style="color:#000000;">校验）</font>**

**<font style="color:#000000;">项目难点是什么？模型厂商的模型规范不一样需要定制，日志聚合分析，报表，权限校验。</font>**

**<font style="color:#000000;">计费如何实现的？</font>****<font style="color:#000000;">token</font>****<font style="color:#000000;">数如何计算的。如果调用中间异常了怎么办？自己计算，常见的文生文模型， 一个中文大概占几个</font>****<font style="color:#000000;">token</font>****<font style="color:#000000;">？</font>****<font style="color:#000000;">1.2-1.7</font>****<font style="color:#000000;">，</font>**

**<font style="color:#000000;">对</font>****<font style="color:#000000;">LLM</font>****<font style="color:#000000;">的</font>****<font style="color:#000000;">API</font>****<font style="color:#000000;">的语义了解么？</font>****<font style="color:#000000;">temperature</font>****<font style="color:#000000;">，取值范围？所有模型都是</font>****<font style="color:#000000;">0-1</font>****<font style="color:#000000;">吗？大部分模型的默认值是多少？</font>****<font style="color:#000000;">max-tokens</font>****<font style="color:#000000;">、</font>****<font style="color:#000000;">top-p</font>****<font style="color:#000000;">呢？</font>**

**<font style="color:#000000;">大模型本身支持工具调用和记忆的能力吗？</font>**

**<font style="color:#000000;">限流是怎么实现的？请求速率限流。用户限流怎么实现的？</font>****<font style="color:#000000;">clickhouse</font>****<font style="color:#000000;">到</font>****<font style="color:#000000;">mysql</font>****<font style="color:#000000;">是如何同步的？</font>****<font style="color:#000000;">1</font>****<font style="color:#000000;">小时同步一次。能接受</font>****<font style="color:#000000;">1</font>****<font style="color:#000000;">小时延迟。</font>**

**<font style="color:#000000;">都对接了哪些模型 ？文生文，文</font>****<font style="color:#000000;">+</font>****<font style="color:#000000;">图生文，生图模型。开源模型有对接吗？自己部署的，也会走网关。</font>**

**<font style="color:#000000;">Cluade</font>****<font style="color:#000000;">有对接么？封杀怎么处理。多账号。哪个模型用量最大？</font>****<font style="color:#000000;">cluade</font>****<font style="color:#000000;">，</font>****<font style="color:#000000;">ds</font>****<font style="color:#000000;">，</font>**

**<font style="color:#000000;">出口数据有做管控吗？</font>****<font style="color:#000000;">如何避免数据泄露。</font>****<font style="color:#000000;">用</font>****<font style="color:#000000;">deepseek</font>****<font style="color:#000000;">审查模型，部署用的什么方式？</font>****<font style="color:#000000;">ollama</font>****<font style="color:#000000;">，</font>****<font style="color:#000000;">vlm</font>**

**<font style="color:#000000;">用</font>****<font style="color:#000000;">ollama</font>****<font style="color:#000000;">做模型部署的原因是什么？</font>****<font style="color:#000000;">方便。模型部署在显卡上，</font>****<font style="color:#000000;">A100</font>****<font style="color:#000000;">、模型部署经验？</font>****<font style="color:#000000;">GPU</font>****<font style="color:#000000;">限制，上下文大小限制。</font>**

**<font style="color:#000000;">可监测性这方面做啦哪些事情？</font>****<font style="color:#000000;">grafana</font>****<font style="color:#000000;">，</font>****<font style="color:#000000;">Prometheus</font>****<font style="color:#000000;">，</font>****<font style="color:#000000;">CPU&</font>****<font style="color:#000000;">磁盘</font>****<font style="color:#000000;">&</font>****<font style="color:#000000;">内存，模型部署服务器的</font>****<font style="color:#000000;">GPU</font>****<font style="color:#000000;">相关。</font>**

**<font style="color:#000000;">如果对接的某个模型厂商的服务挂了，如何做故障转移？</font>**

**<font style="color:#000000;">AI</font>****<font style="color:#000000;">问答平台，技术栈是什么？</font>****<font style="color:#000000;">spring ai</font>****<font style="color:#000000;"> + spring boot</font>****<font style="color:#000000;">，主要是哪些内容的问答？</font>****<font style="color:#000000;">spring ai</font>****<font style="color:#000000;">用的哪个版本？</font>****<font style="color:#000000;">1.0.0</font>****<font style="color:#000000;">？</font>****<font style="color:#000000;">chatclient</font>****<font style="color:#000000;">和</font>****<font style="color:#000000;">chatmodel</font>****<font style="color:#000000;">区别是啥？</font>****<font style="color:#000000;">spring ai</font>****<font style="color:#000000;">中的</font>****<font style="color:#000000;">advisor</font>****<font style="color:#000000;">机制了解吗？有用过哪个</font>****<font style="color:#000000;">advisor</font>****<font style="color:#000000;">吗？</font>**

**<font style="color:#000000;">多轮对话是如何实现的？</font>****<font style="color:#000000;">redis+mysql</font>****<font style="color:#000000;">，为什么要存</font>****<font style="color:#000000;">redis</font>****<font style="color:#000000;">、和</font>****<font style="color:#000000;">mysql</font>****<font style="color:#000000;">两层、</font>****<font style="color:#000000;">redis</font>****<font style="color:#000000;">如何实现的只存最近的</font>****<font style="color:#000000;">N</font>****<font style="color:#000000;">轮对话？</font>****<font style="color:#000000;">zset</font>****<font style="color:#000000;">，手动维护最近</font>****<font style="color:#000000;">N</font>****<font style="color:#000000;">轮，</font>****<font style="color:#000000;">mysql</font>****<font style="color:#000000;">对话记忆表是如何设计的？用户问题、模型回答、用户信息、模型信息、对话</font>****<font style="color:#000000;">id</font>****<font style="color:#000000;">。会话</font>****<font style="color:#000000;">ID</font>****<font style="color:#000000;">起到了什么作用？回话</font>****<font style="color:#000000;">ID</font>****<font style="color:#000000;">如何保证唯一性 ？</font>****<font style="color:#000000;">redisson</font>****<font style="color:#000000;">？？？前缀</font>****<font style="color:#000000;">+</font>****<font style="color:#000000;">随机数</font>****<font style="color:#000000;">+username+</font>****<font style="color:#000000;">时间戳</font>****<font style="color:#000000;">----->base64</font>****<font style="color:#000000;">，为什么不考虑用</font>****<font style="color:#000000;">uuid</font>****<font style="color:#000000;">？如果并发更高场景，时间戳也可能重复怎么办？雪花算法，是怎么保证不重复的呢？数据库主键</font>****<font style="color:#000000;">id</font>****<font style="color:#000000;">、</font>**

**<font style="color:#000000;">长期记忆</font>****<font style="color:#000000;">&</font>****<font style="color:#000000;">短期记忆</font>**

**<font style="color:#000000;">线上问题遇到过吗？</font>****<font style="color:#000000;">CPU</font>****<font style="color:#000000;">飙升，</font>****<font style="color:#000000;">apisix</font>****<font style="color:#000000;">没配连接池，日志文件很大（按天拆分），</font>****<font style="color:#000000;">lua</font>****<font style="color:#000000;">脚本插件内存溢出？</font>**

**<font style="color:#000000;">调用大模型API超时有哪些优化方案？流式输出，换小模型，max-token，</font>**

:::

# 题目解析


:::color4
**<font style="color:#000000;">对</font>****<font style="color:#000000;">LLM</font>****<font style="color:#000000;">的</font>****<font style="color:#000000;">API</font>****<font style="color:#000000;">的语义了解么？</font>****<font style="color:#000000;">temperature</font>****<font style="color:#000000;">，取值范围？所有模型都是</font>****<font style="color:#000000;">0-1</font>****<font style="color:#000000;">吗？大部分模型的默认值是多少？</font>****<font style="color:#000000;">max-tokens</font>****<font style="color:#000000;">、</font>****<font style="color:#000000;">top-p</font>****<font style="color:#000000;">呢？</font>**

**<font style="color:#000000;">大模型本身支持工具调用和记忆的能力吗？</font>**

**<font style="color:#000000;">限流是怎么实现的？请求速率限流。用户限流怎么实现的？</font>****<font style="color:#000000;">clickhouse</font>****<font style="color:#000000;">到</font>****<font style="color:#000000;">mysql</font>****<font style="color:#000000;">是如何同步的？</font>****<font style="color:#000000;">1</font>****<font style="color:#000000;">小时同步一次。能接受</font>****<font style="color:#000000;">1</font>****<font style="color:#000000;">小时延迟。</font>**

**<font style="color:#000000;">都对接了哪些模型 ？文生文，文</font>****<font style="color:#000000;">+</font>****<font style="color:#000000;">图生文，生图模型。开源模型有对接吗？自己部署的，也会走网关。</font>**

**<font style="color:#000000;">Cluade</font>****<font style="color:#000000;">有对接么？封杀怎么处理。多账号。哪个模型用量最大？</font>****<font style="color:#000000;">cluade</font>****<font style="color:#000000;">，</font>****<font style="color:#000000;">ds</font>****<font style="color:#000000;">，</font>**

**<font style="color:#000000;">出口数据有做管控吗？</font>****<font style="color:#000000;">如何避免数据泄露。</font>****<font style="color:#000000;">用</font>****<font style="color:#000000;">deepseek</font>****<font style="color:#000000;">审查模型，部署用的什么方式？</font>****<font style="color:#000000;">ollama</font>****<font style="color:#000000;">，</font>****<font style="color:#000000;">vlm</font>**

**<font style="color:#000000;">用</font>****<font style="color:#000000;">ollama</font>****<font style="color:#000000;">做模型部署的原因是什么？</font>****<font style="color:#000000;">方便。模型部署在显卡上，</font>****<font style="color:#000000;">A100</font>****<font style="color:#000000;">、模型部署经验？</font>****<font style="color:#000000;">GPU</font>****<font style="color:#000000;">限制，上下文大小限制。</font>**

**<font style="color:#000000;">可监测性这方面做啦哪些事情？</font>****<font style="color:#000000;">grafana</font>****<font style="color:#000000;">，</font>****<font style="color:#000000;">Prometheus</font>****<font style="color:#000000;">，</font>****<font style="color:#000000;">CPU&</font>****<font style="color:#000000;">磁盘</font>****<font style="color:#000000;">&</font>****<font style="color:#000000;">内存，模型部署服务器的</font>****<font style="color:#000000;">GPU</font>****<font style="color:#000000;">相关。</font>**

**<font style="color:#000000;">如果对接的某个模型厂商的服务挂了，如何做故障转移？</font>**

**<font style="color:#000000;">AI</font>****<font style="color:#000000;">问答平台，技术栈是什么？</font>****<font style="color:#000000;">spring ai</font>****<font style="color:#000000;"> + spring boot</font>****<font style="color:#000000;">，主要是哪些内容的问答？</font>****<font style="color:#000000;">spring ai</font>****<font style="color:#000000;">用的哪个版本？</font>****<font style="color:#000000;">1.0.0</font>****<font style="color:#000000;">？</font>****<font style="color:#000000;">chatclient</font>****<font style="color:#000000;">和</font>****<font style="color:#000000;">chatmodel</font>****<font style="color:#000000;">区别是啥？</font>****<font style="color:#000000;">spring ai</font>****<font style="color:#000000;">中的</font>****<font style="color:#000000;">advisor</font>****<font style="color:#000000;">机制了解吗？有用过哪个</font>****<font style="color:#000000;">advisor</font>****<font style="color:#000000;">吗？</font>**

**<font style="color:#000000;">多轮对话是如何实现的？</font>****<font style="color:#000000;">redis+mysql</font>****<font style="color:#000000;">，为什么要存</font>****<font style="color:#000000;">redis</font>****<font style="color:#000000;">、和</font>****<font style="color:#000000;">mysql</font>****<font style="color:#000000;">两层、</font>****<font style="color:#000000;">redis</font>****<font style="color:#000000;">如何实现的只存最近的</font>****<font style="color:#000000;">N</font>****<font style="color:#000000;">轮对话？</font>****<font style="color:#000000;">zset</font>****<font style="color:#000000;">，手动维护最近</font>****<font style="color:#000000;">N</font>****<font style="color:#000000;">轮，</font>****<font style="color:#000000;">mysql</font>****<font style="color:#000000;">对话记忆表是如何设计的？用户问题、模型回答、用户信息、模型信息、对话</font>****<font style="color:#000000;">id</font>****<font style="color:#000000;">。会话</font>****<font style="color:#000000;">ID</font>****<font style="color:#000000;">起到了什么作用？回话</font>****<font style="color:#000000;">ID</font>****<font style="color:#000000;">如何保证唯一性 ？</font>****<font style="color:#000000;">redisson</font>****<font style="color:#000000;">？？？前缀</font>****<font style="color:#000000;">+</font>****<font style="color:#000000;">随机数</font>****<font style="color:#000000;">+username+</font>****<font style="color:#000000;">时间戳</font>****<font style="color:#000000;">----->base64</font>****<font style="color:#000000;">，为什么不考虑用</font>****<font style="color:#000000;">uuid</font>****<font style="color:#000000;">？如果并发更高场景，时间戳也可能重复怎么办？雪花算法，是怎么保证不重复的呢？数据库主键</font>****<font style="color:#000000;">id</font>****<font style="color:#000000;">、</font>**

**<font style="color:#000000;">长期记忆</font>****<font style="color:#000000;">&</font>****<font style="color:#000000;">短期记忆</font>**

**<font style="color:#000000;">线上问题遇到过吗？</font>****<font style="color:#000000;">CPU</font>****<font style="color:#000000;">飙升，</font>****<font style="color:#000000;">apisix</font>****<font style="color:#000000;">没配连接池，日志文件很大（按天拆分），</font>****<font style="color:#000000;">lua</font>****<font style="color:#000000;">脚本插件内存溢出？</font>**

**<font style="color:#000000;">调用大模型API超时有哪些优化方案？流式输出，换小模型，max-token，</font>**

:::



以上内容，建议通过AI项目课学习：

[AI课优惠券](https://www.yuque.com/hollis666/un6qyk/dkm70gxmurvgph0z)



> 更新: 2026-03-01 15:21:51  
> 原文: <https://www.yuque.com/hollis666/un6qyk/gv0swre78f5z93dh>
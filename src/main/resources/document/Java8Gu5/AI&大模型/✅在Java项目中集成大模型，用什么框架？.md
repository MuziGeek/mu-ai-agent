# ✅在Java项目中集成大模型，用什么框架？

# 典型回答
在Java应用中想要集成LLM，一般有以下几个选择：



1、自己手撕，直接调用API

2、使用Spring AI

3、使用LangChain4j

4、使用Spring AI Alibaba

5、使用agentscope-java



首先第一个肯定不建议，因为LLM开发的话，一般不仅需要对接模型，还需要做对话记忆、工具调用、RAG等等的，这些如果要自己对接模型API的话，都要从头写一遍，成本太高了。



另外几个框架，Spring AI是比较基础的一个框架，是Spring官方的一个LLM集成框架，但是他的功能非常有限，只有简单的chatMode、ChatClient等基础封装。适合做一些简单的项目使用。



LangChain4j是一个对标Python中的LangChain的框架，但是功能肯定不如LangChain，很多功能都是阉割的，但是他在RAG这方面的支持，比Spring AI要强得的多。如果你想在Java中做一个rag，建议使用LangChain4j



如果你要做Agent开发，尤其是多智能体、复杂的Agent的话，建议使用Spring AI Alibaba或者agentscope-java，这两个都是alibaba推出的智能体框架，方便java开发者构建智能体的，主要区别是Spring AI Alibaba是基于Spring AI做的扩展，主要是为了完善spring ai的能力。agentscope是致力于做智能体搭建的。



以下是我的[AI实战课](https://www.yuque.com/hollis666/un6qyk/dkm70gxmurvgph0z)中给大家做的简单总结（暂不包含agentscope）：



| | | | |
| --- | --- | --- | --- |
| **<font style="color:rgb(17, 17, 51);">能力</font>** | **<font style="color:rgb(17, 17, 51);">LangChain4j</font>** | **<font style="color:rgb(17, 17, 51);">Spring AI</font>** | **<font style="color:rgb(17, 17, 51);">Spring AI Alibaba</font>** |
| **<font style="color:rgb(17, 17, 51);">是否依赖 Spring</font>** | <font style="color:rgb(17, 17, 51);">❌</font><font style="color:rgb(17, 17, 51);"> 可独立使用</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 深度集成</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 深度集成</font> |
| **<font style="color:rgb(38, 38, 38);">模型调用复杂度</font>** | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> </font><font style="color:rgb(38, 38, 38);">支持高层次API，快速实现LLM调用</font> | <font style="color:rgb(17, 17, 51);">❌</font><font style="color:rgb(38, 38, 38);">只支持ChatModel和ChatClient</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 除Spring AI功能外，有更多增强API</font> |
| **<font style="color:rgb(17, 17, 51);">结构化输出</font>** | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 强（JSON Schema）</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 基础支持</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 增强版</font> |
| **<font style="color:rgb(17, 17, 51);">RAG 支持</font>** | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 全链路</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 基础</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 企业级增强</font> |
| **<font style="color:rgb(17, 17, 51);">智能体（Agent）</font>** | <font style="color:rgb(17, 17, 51);">❌</font><font style="color:rgb(17, 17, 51);"> 需手动实现</font> | <font style="color:rgb(17, 17, 51);">❌</font><font style="color:rgb(17, 17, 51);"> 需手动实现</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 基于Graph实现了ReAct，支持 Multi-Agent等。</font> |
| **<font style="color:rgb(17, 17, 51);">工作流编排</font>** | <font style="color:rgb(17, 17, 51);">⚠️</font><font style="color:rgb(17, 17, 51);"> 简单 Chain</font> | <font style="color:rgb(17, 17, 51);">❌</font><font style="color:rgb(17, 17, 51);"> 无</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> Graph 引擎（核心优势）</font> |
| **<font style="color:rgb(17, 17, 51);">阿里云集成</font>** | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> Qwen/DashScope</font> | <font style="color:rgb(17, 17, 51);">❌</font><font style="color:rgb(17, 17, 51);"> 无官方支持</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 深度集成（百炼、OSS、Nacos）</font> |
| **<font style="color:rgb(17, 17, 51);">生产可观测性</font>** | <font style="color:rgb(17, 17, 51);">⚠️</font><font style="color:rgb(17, 17, 51);"> 需自行集成</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> Micrometer</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> ARMS/SLS 原生</font> |
| **<font style="color:rgb(38, 38, 38);">生态依赖</font>** | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 我依赖要求</font> | <font style="color:rgb(17, 17, 51);">✔️</font><font style="color:rgb(17, 17, 51);"> 我依赖要求</font> | <font style="color:rgb(17, 17, 51);">⚠️</font><font style="color:rgb(38, 38, 38);">依赖阿里百炼</font> |


<font style="color:rgb(38, 38, 38);">  
</font>

**<font style="color:rgb(17, 17, 51);">非 Spring/或者Spring Boot版本低于3.0，纯Java项目项目</font>**<font style="color:rgb(17, 17, 51);">→ 选 </font>**<font style="color:rgb(17, 17, 51);">LangChain4j</font>**

<font style="color:rgb(38, 38, 38);">  
</font>

**<font style="color:rgb(17, 17, 51);">需要快速原型验证，且熟悉 LangChain 概念 </font>**<font style="color:rgb(17, 17, 51);">→ 选 </font>**<font style="color:rgb(17, 17, 51);">LangChain4j</font>**

<font style="color:rgb(38, 38, 38);">  
</font>

**<font style="color:rgb(17, 17, 51);">已有 Spring Boot 项目，只需简单 LLM 调用</font>**<font style="color:rgb(17, 17, 51);">→ 选 </font>**<font style="color:rgb(17, 17, 51);">Spring AI</font>**

<font style="color:rgb(38, 38, 38);">  
</font>

**<font style="color:rgb(38, 38, 38);">构建企业级RAG</font>****<font style="color:rgb(17, 17, 51);">→ 选LangChain4j</font>**

<font style="color:rgb(38, 38, 38);">  
</font>

**<font style="color:rgb(17, 17, 51);">构建企业级、多步骤、多角色协作的 AI 应用/Agent</font>**<font style="color:rgb(17, 17, 51);"> → 选 </font>**<font style="color:rgb(17, 17, 51);">Spring AI Alibaba</font>**

<font style="color:rgb(38, 38, 38);">  
</font>

**<font style="color:rgb(17, 17, 51);">想用 Java 但又想要接近 Python LangChain 的体验</font>**<font style="color:rgb(17, 17, 51);"> → </font>**<font style="color:rgb(17, 17, 51);">LangChain4j 是最佳选择</font>**



> 更新: 2026-03-01 14:25:16  
> 原文: <https://www.yuque.com/hollis666/un6qyk/vmwh69ou77uyrmwo>
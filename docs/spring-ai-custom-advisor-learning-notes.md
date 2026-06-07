## Spring AI 自定义 Advisor 学习总结

本文基于 `mu-ai-agent` 项目的实践，系统梳理 Spring AI 中 Advisor 机制的核心概念、源码原理和自定义实现方法。


### 一、Advisor 是什么

Advisor 是 Spring AI 中 ChatClient 的拦截器机制，类似于 Spring MVC 的 Interceptor 或 Servlet 的 Filter。它允许我们在 AI 请求发送前和响应返回后插入自定义逻辑，而无需修改业务代码。

可以把 Advisor 想象成一条流水线上的"质检工位"。用户的消息从入口进入，依次经过每个 Advisor 的检查或加工，最终到达 AI 模型；AI 的响应则沿原路反向返回，每个 Advisor 有机会再次处理。

```
用户输入 → [Advisor1.before] → [Advisor2.before] → ... → AI 模型
AI 响应 → [...after] → [Advisor2.after] → [Advisor1.after] → 返回用户
```


### 二、Advisor 的类型体系

Spring AI 提供了三种粒度的 Advisor 接口，适用于不同的场景。

**BaseAdvisor（推荐，最常用）** 只需实现 `before()` 和 `after()` 两个方法，同时支持同步调用（call）和流式调用（stream）。适合大多数场景：修改请求、修改响应、日志记录等。框架会自动处理链条传递，开发者只需关注"请求前做什么"和"响应后做什么"。

```java
public interface BaseAdvisor extends CallAdvisor, StreamAdvisor {
    ChatClientRequest before(ChatClientRequest request, AdvisorChain chain);
    ChatClientResponse after(ChatClientResponse response, AdvisorChain chain);
}
```

**CallAdvisor / StreamAdvisor（更细粒度）** 分别处理同步和流式场景，可以访问 AdvisorChain 手动决定是否传递给下一个 Advisor。适合需要精确控制执行流程的场景。例如项目中的 `MyLoggerAdvisor` 同时实现了这两个接口，以便在同步和流式场景下分别处理日志聚合。

```java
public interface CallAdvisor extends Advisor {
    ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain);
}
```

**如何选择？** 如果你只需要在请求前/后做处理，选 `BaseAdvisor`。如果你需要控制链条传递（比如短路、条件跳过），选 `CallAdvisor`。


### 三、执行顺序：order 字段

多个 Advisor 通过 `getOrder()` 返回值决定执行顺序：值越小越先执行 `before()`，越后执行 `after()`。这就像进入办公楼的安检流程 — 保安检查（order=0）→ 前台登记（order=1）→ 进入会议室（AI 模型）；出来时顺序反过来。

在本项目中，`SensitiveWordAdvisor` 的 order 设为 0（最先执行），确保后续所有 Advisor 和 AI 模型看到的都是清洗后的输入。

框架的排序逻辑在 `DefaultAroundAdvisorChain.Builder.reOrder()` 中实现：

```java
private void reOrder() {
    ArrayList<CallAdvisor> callAdvisors = new ArrayList<>(this.callAdvisors);
    OrderComparator.sort(callAdvisors);  // 按 order 升序排列
    this.callAdvisors.clear();
    callAdvisors.forEach(this.callAdvisors::addLast);
}
```


### 四、责任链的传递机制

这是理解 Advisor 最关键的部分。Spring AI 用 `DefaultAroundAdvisorChain` 实现链条传递，内部用 `Deque<CallAdvisor>`（双端队列）存储所有 Advisor。

**核心方法 `nextCall()`：**

```java
public ChatClientResponse nextCall(ChatClientRequest chatClientRequest) {
    // 从队列头部弹出一个 Advisor（弹出后就没了，不会重复执行）
    var advisor = this.callAdvisors.pop();
    // 让该 Advisor 处理请求，并把链条自身（this）传进去
    return advisor.adviseCall(chatClientRequest, this);
}
```

**BaseAdvisor 的 `adviseCall()` 实现：**

```java
default ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    ChatClientRequest processedRequest = before(request, chain);     // 你写的 before()
    ChatClientResponse response = chain.nextCall(processedRequest);  // 传给下一个！
    return after(response, chain);                                    // 你写的 after()
}
```

传递过程就像一个递归调用：每个 Advisor 调用 `chain.nextCall()` 时，链条 `pop()` 出下一个 Advisor 并执行。链条的末端是框架自动添加的 `ChatModelCallAdvisor`（order = `LOWEST_PRECEDENCE`），它不调用 `nextCall()`，而是真正调用 AI 模型，然后返回响应。响应沿 `after()` 方法反向回传。

**关于"多个新对象如何统一"的问题** — 答案是：不需要统一。这是一个管道（Pipeline），不是并行执行。每个 Advisor 在上一个 Advisor 的结果之上做修改，最后到达 AI 模型的那个对象已经自然包含了所有修改。就像接力赛，每一棒都把接力棒（Request 对象）传给下一个人，终点的那根接力棒已经经历了所有加工。


### 五、不可变对象模式

Spring AI 中的核心数据类都是不可变的（Immutable），这是理解 `mutate()` 和 `augmentUserMessage()` 的基础。

**ChatClientRequest 是一个 Java record：**

```java
public record ChatClientRequest(Prompt prompt, Map<String, Object> context) {
    // record 自动将所有字段声明为 final，无法修改
}
```

不能直接修改它的字段。要"修改"只能创建新对象：

```java
chatClientRequest.mutate()          // 获取 Builder（深拷贝了旧数据）
    .prompt(newPrompt)              // 在 Builder 上设置新值
    .build();                       // 构建一个全新的 ChatClientRequest
```

`mutate()` 的源码非常简洁：

```java
public Builder mutate() {
    return new Builder()
        .prompt(this.prompt.copy())           // 深拷贝 Prompt
        .context(new HashMap<>(this.context)); // 拷贝 context
}
```

这种模式的好处是线程安全和可预测 — 每个 Advisor 拿到的是原始对象的拷贝，互不干扰。

**注意：record 保证的是引用不可变，不是深层不可变。** 比如 `context` 是一个 `Map`，record 保证你不能把 `context` 字段指向另一个 Map，但如果这个 Map 本身是可变的，你仍然可以往里面 put 东西。所以 `mutate()` 里特意做了 `new HashMap<>(this.context)` 来防止引用共享。


### 六、augmentUserMessage 源码解析

`Prompt.augmentUserMessage()` 是修改用户消息的核心 API。"augment"直译是"增强/扩充"，实际效果是将最后一条 UserMessage 替换为新文本，其他消息保持不变。

**便捷重载版本（我们使用的）：**

```java
public Prompt augmentUserMessage(String newUserText) {
    return augmentUserMessage(
        userMessage -> userMessage.mutate().text(newUserText).build()
    );
}
```

**核心实现（接收 Function 参数的版本）：**

```java
public Prompt augmentUserMessage(Function<UserMessage, UserMessage> userMessageAugmenter) {
    // 1. 拷贝消息列表（浅拷贝 ArrayList，不修改原列表）
    var messagesCopy = new ArrayList<>(this.messages);

    // 2. 从后往前遍历，找到【最后一条】UserMessage
    for (int i = messagesCopy.size() - 1; i >= 0; i--) {
        Message message = messagesCopy.get(i);
        if (message instanceof UserMessage userMessage) {
            // 3. 用 augmenter 函数处理这条消息，替换到列表中
            messagesCopy.set(i, userMessageAugmenter.apply(userMessage));
            break;
        }
        // 4. 兜底：如果一条 UserMessage 都没有，创建一条新的
        if (i == 0) {
            messagesCopy.add(userMessageAugmenter.apply(new UserMessage("")));
        }
    }

    // 5. 用修改后的消息列表构建新 Prompt
    return new Prompt(messagesCopy,
        null == this.chatOptions ? null : this.chatOptions.copy());
}
```

关键设计点：从后往前找 UserMessage，因为一个 Prompt 中可能有多条用户消息（对话历史），而 `augmentUserMessage` 的语义是修改当前（最新）的用户输入。

类似地，Prompt 还提供了 `augmentSystemMessage()` 方法，用法完全对称。


### 七、实战：敏感词过滤 Advisor 的完整实现

本项目的敏感词过滤由三个组件协作完成。

**SensitiveWordFilter — DFA 敏感词过滤器**

使用确定有限自动机（DFA / Trie 前缀树）实现高效匹配。核心思想是将所有敏感词构建成一棵前缀树，扫描文本时沿树走路径，时间复杂度为 O(文本长度 × 最长词长度)，远优于逐词 `contains()` 的暴力匹配。

Trie 结构示例（词库：`"暴力"`, `"暴乱"`）：

```
root → [暴] → [力] → END
              [乱] → END
```

"暴力"和"暴乱"共享了"暴"这个前缀节点，节省内存。匹配时采用最长匹配策略 — 如果词库中有"中国"和"中国人"，输入"中国人"会匹配最长的"中国人"而非"中国"。

**SensitiveWordAdvisor — Spring AI Advisor 封装**

```java
public class SensitiveWordAdvisor implements BaseAdvisor {
    private final SensitiveWordFilter sensitiveWordFilter;

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String userText = request.prompt().getUserMessage().getText();
        if (sensitiveWordFilter.containsSensitiveWord(userText)) {
            String filtered = sensitiveWordFilter.replaceSensitiveWords(userText);
            return request.mutate()
                .prompt(request.prompt().augmentUserMessage(filtered))
                .build();
        }
        return request;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;  // 仅过滤输入，输出透传
    }
}
```

体现了两个设计原则：一是"组合优于继承"，Advisor 不实现过滤逻辑，委托给 SensitiveWordFilter；二是"关注点分离"，Advisor 只负责与 Spring AI 框架对接，过滤器只负责文本匹配。

**集成到 ChatClient**

```java
ChatClient.builder(dashscopeChatModel)
    .defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build(),
        new SensitiveWordAdvisor(sensitiveWordFilter),
        MyLoggerAdvisor.builder().build()
    )
    .build();
```


### 八、完整数据流图

参见 `docs/advisor-chain-flow.mermaid` 中的流程图。当用户输入 `"关于暴力的问题"` 时，请求对象在链条中的演进过程如下：

```
Request_0: UserMessage = "关于暴力的问题"
    │
    ▼  SensitiveWordAdvisor.before()
Request_1: UserMessage = "关于***的问题"    ← 敏感词被替换
    │
    ▼  MessageChatMemoryAdvisor.before()
Request_2: [SystemMessage, 历史消息..., UserMessage="关于***的问题"]  ← 追加了上下文
    │
    ▼  MyLoggerAdvisor.before()
Request_2: （不变，仅记录日志）
    │
    ▼  ChatModelCallAdvisor → AI 模型 → Response
```

每个 Request 都是独立的不可变对象，前一个 Advisor 的输出自然成为后一个 Advisor 的输入，不需要任何"统一"或"合并"操作。


### 九、关键概念速查表

| 概念 | 说明 |
|---|---|
| `BaseAdvisor` | 最简 Advisor 接口，实现 `before()` 和 `after()` |
| `CallAdvisor` / `StreamAdvisor` | 细粒度接口，可手动控制链条传递 |
| `order` | 执行优先级，值越小越先执行 before，越后执行 after |
| `ChatClientRequest` | 不可变 record，封装 Prompt + context |
| `mutate()` | 获取 Builder 副本，用于基于旧对象创建新对象 |
| `augmentUserMessage()` | 替换最后一条 UserMessage 的文本，返回新 Prompt |
| `augmentSystemMessage()` | 替换 SystemMessage 的文本，返回新 Prompt |
| `DefaultAroundAdvisorChain` | 链条调度器，内部用 Deque pop 出 Advisor 逐个执行 |
| `ChatModelCallAdvisor` | 链条终点，真正调用 AI 模型，order = LOWEST_PRECEDENCE |
| DFA / Trie | 确定有限自动机，用于敏感词高效匹配 |


### 十、扩展方向

基于当前的 Advisor 架构，有几个自然的扩展方向值得探索。

**输出过滤**：在 `SensitiveWordAdvisor.after()` 中添加对 AI 响应的敏感词检查，防止模型生成不当内容。

**热更新词库**：将敏感词从文件迁移到数据库或 Redis，提供管理接口动态增删词，无需重启应用。

**多级敏感策略**：为敏感词添加等级属性，高危词直接拒绝请求，低危词仅做替换并记录日志。

**异步流式适配**：当前 `BaseAdvisor` 已自动支持流式调用，但如果需要在流式场景中做聚合处理（如统计完整响应后再过滤），可以参考 `MyLoggerAdvisor` 中 `ChatClientMessageAggregator` 的用法。

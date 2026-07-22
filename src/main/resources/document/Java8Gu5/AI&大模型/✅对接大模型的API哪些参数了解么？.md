# ✅对接大模型的API哪些参数了解么？

```python
import os
from openai import OpenAI

try:
    client = OpenAI(
        # 替换成你自己的ak
        api_key="<your key>",
        base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
    )

    completion = client.chat.completions.create(
        model="qwen-plus",
        messages=[
            {"role": "system", "content": "You are a helpful assistant."},
            {"role": "user", "content": "你是谁？"},
        ],
    )
    print(completion.choices[0].message.content)
except Exception as e:
    print(f"错误信息：{e}")
```

以上其实就是一个标准的Open AI的API调用代码， 其中的重要参数我们也能发现有很多。

\*\*base\_url：\*\*模型服务器地址。

在各种智能体框架和低代码平台中，都会要求配置此参数以指定请求发送到哪个模型服务器。

**格式说明：**

**完整的 API 接口路径通常是：https://域名地址（或者ip+port）/v1/chat/completions**

但是大多数智能体框架（如 LangChain、Autogen等等）默认会拼接 **/chat/completions**，因此我们只需配置到：\*\*https://域名地址/v1/ \*\*即可。

而在我们课程中用的最多的SpringAI，他不太一样的点就是，他默认拼接的是：**/v1/chat/completions。所以在SpringAI框架中的配置是只到v1之前。**

***

**常见模型的base\_url：**

* \*\*Chatgpt：\*\*https://api.openai.com/v1
* \*\*通义千问：\*\*https://dashscope.aliyuncs.com/compatible-openai/v1
* \*\*DeepSeek：\*\*https://api.deepseek.com/v1
* \*\*本地模型：\*\*http://\<IP地址或域名>:<端口号>/v1 （如ollama：http://localhost:11434/v1）

| **<font style="color:rgb(27, 28, 29);">参数名称</font>** | **<font style="color:rgb(27, 28, 29);">是否必填</font>** | **<font style="color:rgb(27, 28, 29);">数据类型</font>** | **<font style="color:rgb(27, 28, 29);">作用</font>** | **<font style="color:rgb(27, 28, 29);">参数说明</font>** |
| --- | --- | --- | --- | --- |
| **api\_key** | **<font style="color:rgb(0, 0, 0);">必填</font>** | **<font style="color:rgb(0, 0, 0);">String</font>** | **<font style="color:rgb(0, 0, 0);">身份验证</font>** | **<font style="color:rgb(0, 0, 0);">在 HTTP 请求头 (Authorization: Bearer <API Key></font>**<br/>**<font style="color:rgb(0, 0, 0);">) 中设置，用于验证身份信息、授权访问、计费等。</font>** |
| **model** | **<font style="color:rgb(0, 0, 0);">必填</font>** | **<font style="color:rgb(0, 0, 0);">String</font>** | **<font style="color:rgb(0, 0, 0);">指定模型</font>** | **<font style="color:rgb(0, 0, 0);">确定使用哪一个具体的大模型（如 qwen-plus）。</font>** |
| **messages** | **<font style="color:rgb(0, 0, 0);">必填</font>** | **<font style="color:rgb(0, 0, 0);">Array</font>** | **<font style="color:rgb(0, 0, 0);">消息列表</font>** | **<font style="color:rgb(0, 0, 0);">传递给模型的对话历史或指令。包含 role（角色：system、user、assistant）和 content(内容)。</font>** |
| **<font style="color:rgb(0, 0, 0);">temperature</font>** | **<font style="color:rgb(0, 0, 0);">非必填</font>** | **<font style="color:rgb(0, 0, 0);">Float</font>** | **<font style="color:rgb(0, 0, 0);">温度系数</font>** | **<font style="color:rgb(0, 0, 0);">0.0 到 2.0 （部分模型是0.0-1.0）。控制大模型输出的随机程度。低值更稳定，高值更随机。</font>**<br/>**高多样性**<font style="color:rgb(24, 24, 24);">（示例</font>`temperature`<font style="color:rgb(24, 24, 24);">=0.9）：适用于需要创意、想象力和新颖表达的场景，如创意写作、头脑风暴或市场营销文案。</font><br/>**高确定性**<font style="color:rgb(24, 24, 24);">（示例</font>`temperature`<font style="color:rgb(24, 24, 24);">=0.1）：适用于要求内容准确、严谨和可预测的场景，如事实问答、代码生成或法律文本。</font><br/>**<font style="color:rgb(0, 0, 0);">通常建议默认0.7即可。</font>** |
| <font style="color:rgb(0, 0, 0);">top\_p</font> | <font style="color:rgb(0, 0, 0);">非必填</font> | <font style="color:rgb(0, 0, 0);">Float</font> | <font style="color:rgb(0, 0, 0);">核采样</font> | 与温度系数类似（但算法不同），<font style="color:rgb(0, 0, 0);">0.0 到 1.0 。</font><br/><font style="color:rgb(0, 0, 0);">控制大模型输出的随机程度。低值更稳定，高值更随机。</font><br/><font style="color:rgb(0, 0, 0);">高值的输出不会像温度系数那么极端。通常不要与温度系数同时调整，只需调整其中之一即可。</font> |
| **<font style="color:rgb(0, 0, 0);">max\_tokens</font>** | **<font style="color:rgb(0, 0, 0);">非必填</font>** | **<font style="color:rgb(0, 0, 0);">Integer</font>** | **<font style="color:rgb(0, 0, 0);">模型最大输出长度</font>** | **<font style="color:rgb(0, 0, 0);">限定模型单次响应可以生成的最大 Token 数量，影响大模型的回复长度。</font>** |
| n | <font style="color:rgb(0, 0, 0);">非必填</font> | <font style="color:rgb(0, 0, 0);">Integer</font> | <font style="color:rgb(0, 0, 0);">生成次数</font> | <font style="color:rgb(0, 0, 0);">请求 API 为给定的 Prompt 生成 n 个不同的回应。</font> |
| **stream** | **<font style="color:rgb(0, 0, 0);">非必填</font>** | **<font style="color:rgb(0, 0, 0);">Boolean</font>** | **<font style="color:rgb(0, 0, 0);">流式输出</font>** | **<font style="color:rgb(0, 0, 0);">默认为false，如果设置为 true，模型会流式返回 Token，而不是等待整个回复生成完毕。非常重要，用于提升用户体验。</font>** |
| stop | <font style="color:rgb(0, 0, 0);">非必填</font> | <font style="color:rgb(0, 0, 0);">String/Array</font> | <font style="color:rgb(0, 0, 0);">停止标记</font> | <font style="color:rgb(0, 0, 0);">字符串或字符串列表。模型一旦生成列表中的任一字符串，就会立即停止生成。</font> |
| <font style="color:rgb(0, 0, 0);">presence\_penalty</font> | <font style="color:rgb(0, 0, 0);">非必填</font> | <font style="color:rgb(0, 0, 0);">Float</font> | <font style="color:rgb(0, 0, 0);">出现惩罚</font> | <font style="color:rgb(0, 0, 0);">-2.0 到 2.0。</font><br/><font style="color:rgb(0, 0, 0);">较高的正值：鼓励模型引入新话题和新词汇，生成的文本会更加多样化。</font><br/><font style="color:rgb(0, 0, 0);">较低的负值：减少新话题的引入，生成的文本会更加集中和一致。</font> |
| <font style="color:rgb(0, 0, 0);">frequency\_penalty</font> | <font style="color:rgb(0, 0, 0);">非必填</font> | <font style="color:rgb(0, 0, 0);">Float</font> | <font style="color:rgb(0, 0, 0);">频率惩罚</font> | <font style="color:rgb(0, 0, 0);">-2.0 到 2.0。</font><br/><font style="color:rgb(0, 0, 0);">较高的正值：减少重复词语的出现，生成的文本会更加多样化。</font><br/><font style="color:rgb(0, 0, 0);">较低的负值：增加重复词语的出现，生成的文本会更加一致和连贯。</font> |
| seed | <font style="color:rgb(0, 0, 0);">非必填</font> | <font style="color:rgb(0, 0, 0);">Integer</font> | <font style="color:rgb(0, 0, 0);">复现性种子</font> | <font style="color:rgb(0, 0, 0);">如果设置，有助于在多次调用中复现相同的输出，提高结果的可预测性（并非 100% 保证）。</font> |

<font style="color:rgb(27, 28, 29);">以上就是OpenAI API的全部入参字段，</font>**<font style="color:rgb(27, 28, 29);">加粗标记的就是正常我们使用的最多的标准字段</font>**<font style="color:rgb(27, 28, 29);">，其他字段基本可以不用设置，因为设置的不好就非常可能会导致模型输出的不确定性，使用默认值即可。</font>


> 更新: 2026-03-01 15:07:57  
> 原文: <https://www.yuque.com/hollis666/un6qyk/gi42i4ed7eh3aegc>
# ReAct(Reasoning + Acting)

## 1. 核心思想

ReAct 由 Shunyu Yao 于 2022 年提出，核心是把 **推理(Thought)** 与 **行动(Action)** 显式地
串在一起，形成一个不断循环的三段式结构：

```
Thought(思考)  → 分析现状、拆解任务、规划下一步
Action(行动)   → 调用外部工具，或宣布得到最终答案
Observation(观察) → 工具返回的结果，被写回历史，供下一轮思考使用
```

用公式表达，在第 `t` 步，模型（策略 `π`）根据问题 `q` 与此前的 `(action, observation)`
轨迹生成新的思考和行动：

```
(th_t, a_t) = π(q, (a_1, o_1), ..., (a_{t-1}, o_{t-1}))
o_t          = T(a_t)
```

循环持续到某一步的 Thought 判断“任务已完成”为止。图示见同目录下的
`Reasoning and Acting 经典范式工作流程.png`。

这种“思考指导行动、行动的观察修正思考”的协同，是 ReAct 相比纯 Chain-of-Thought（不能碰
外部世界，容易幻觉）和纯 Action（没有规划与纠错能力）更强的地方。

## 2. 本项目里的两种实现

`chapter_04` 目前包含两套彼此独立、互不影响的 ReAct 实现，走的是两条不同的技术路线。

### 2.1 `ReActAgent`（本次新增，教材经典写法）

- 入口:`ReActController` → `POST /react`
- 组成:`agent/ReActAgent.java` + `tool/ToolExecutor.java` + `tool/SerpApiSearchTool.java`

完全照搬教材思路，靠 **提示词工程** 强迫模型吐出固定格式的纯文本：

```
Thought: ...
Action: Search[...] | Finish[...]
```

再用正则把 `Thought`/`Action` 从文本里抠出来(`THOUGHT_PATTERN`/`ACTION_PATTERN`)，
如果 `Action` 是 `Finish[...]` 就结束循环返回答案；否则按 `ToolName[ToolInput]` 解析出
工具名和输入(`ACTION_CALL_PATTERN`)，交给 `ToolExecutor` 分发执行，得到 `Observation`
后拼回 `history`，进入下一轮。`MAX_STEPS = 5` 是防止死循环的安全阀。

### 2.2 `ChatController`（既有实现，原生 function-calling）

- 入口:`POST /chat`
- 组成:`controller/ChatController.java` + `tool/SerpApiSearchTool.java`

没有走“文本提示词 + 正则解析”这条路，而是直接使用 OpenAI 的原生工具调用协议
(`addFunctionTool` / `toolCalls()`)。模型不再输出 `Thought:`/`Action:` 这种纯文本，
而是返回结构化的 `tool_calls`(函数名 + JSON 参数)；代码执行工具后把结果包装成
`ChatCompletionToolMessageParam` 回填给模型，模型基于这条“工具结果消息”继续推理，
直到某一轮不再请求工具调用为止。

## 3. 这两种实现是否符合 Thought / Action 范式？

**`ReActAgent`:完全符合，是字面意义上的 ReAct。**
它的每一步都强制模型输出显式的 `Thought:` 自然语言思考，再输出显式的 `Action:` 动作声明，
工具结果作为 `Observation` 被写回历史——三段式结构、命名、格式都与论文一致。

**`ChatController`:循环范式一致，但文本格式不符合，是“协议原生版”的 ReAct 变体。**

- 符合的部分:它同样是“模型决策 → 调用外部工具 → 把工具结果喂回模型 → 模型继续决策”的
  闭环，本质上仍然满足 `(th_t, a_t) = π(...)`、`o_t = T(a_t)` 这套关系——只是 Reasoning
  和 Acting 被折叠进了 OpenAI 的 `tool_calls` 协议里，而不是由我们自己的提示词/正则去驱动。
- 不符合的部分:function-calling 协议里，模型决定调用工具时，`content` 字段通常是空的，
  只有结构化的 `tool_calls`；也就是说模型的“思考过程”是隐性的，没有被要求以
  `Thought: ...` 这样的可读文本暴露出来。严格按论文的输出规范(`Thought:`/`Action:` 两行
  文本)去检验，`ChatController` 并不满足。

一句话总结:**两者都实现了 ReAct“推理驱动行动、行动反哺推理”的循环范式，但只有
`ReActAgent` 保留了论文里那种可读的、显式的 Thought/Action 文本轨迹；`ChatController`
用模型原生的工具调用协议达到了同样的效果，可解释性依赖的是 `tool_calls` 的结构化参数，
而不是一段人类可读的思考文本。** 这也解释了教材 4.2.4 节提到的 ReAct 局限性——
“提示词的脆弱性”“对格式遵循能力的强依赖”——只对 `ReActAgent` 这类手写循环成立，
`ChatController` 用协议层面的结构化输出规避了这两个问题，但代价是失去了逐步可读的
思考链。

## 4. ReAct 范式的优点与缺点

### 4.1 优点

- **高可解释性**:通过 `Thought` 链，可以清楚看到智能体每一步“为什么选这个工具、下一步
  打算做什么”，对理解、信任和调试智能体行为很关键。项目里的 `ReActAgent` 把这条链完整
  打印到控制台，是这一点最直接的体现；`ChatController` 因为思考被折叠进 `tool_calls`，
  这一优点会打折扣(见第 3 节)。
- **动态规划与纠错能力**:不是一次性生成完整计划，而是“走一步看一步”——根据每一步的
  `Observation` 动态调整后续 `Thought`/`Action`。如果一次 `Search` 结果不理想，下一轮可以
  换个关键词重新搜索，而不必推倒重来。
- **工具协同能力**:把 LLM 的推理能力和外部工具的执行能力结合起来，LLM 负责规划，工具
  负责解决具体问题(搜索、计算、调用 API)，弥补了单一 LLM 在时效性、精确计算等方面的
  固有短板——这也是 `SerpApiSearchTool` 存在的意义。

### 4.2 缺点

- **对 LLM 自身能力的强依赖**:成功与否高度依赖模型的推理、指令遵循和格式化输出能力。
  模型能力不足时，容易在 `Thought` 环节规划错误，或在 `Action` 环节输出不符合格式的指令，
  导致流程中断——这一点在 `ReActAgent` 里体现得最明显:一旦 `ACTION_PATTERN`/
  `ACTION_CALL_PATTERN` 解析失败，循环直接终止。
- **执行效率问题**:循序渐进的特性意味着完成一个任务通常要多次调用 LLM，每次调用都有
  网络延迟和成本；`MAX_STEPS = 5` 只是防止无限循环的安全阀，并不能消除这个开销。
- **提示词的脆弱性**:整个机制建立在一个精心设计的提示词模板上(`REACT_PROMPT_TEMPLATE`)，
  模板的微小改动、甚至用词差异都可能影响模型行为，且不是所有模型都能稳定遵循预设格式。
  这正是 `ChatController` 改走原生 function-calling 协议想规避的问题——把格式约束交给
  协议层而不是提示词，代价是丢掉了显式可读的 `Thought` 文本(见第 3 节)。
- **可能陷入局部最优**:步进式决策缺乏全局规划，可能因为眼前的 `Observation` 选择一条
  看似正确但并非最优的路径，甚至反复用相近的查询词“原地打转”而无法收敛，最终只能靠
  `MAX_STEPS` 兜底终止。

## 5. 调试提示

- `ReActAgent.run()` 每一步都会打印完整的 `prompt`(通过 `history` 拼接)以及模型原始
  `responseText`，解析失败时可以直接在控制台看到模型没有遵循格式的原始输出。
- 若 `ACTION_PATTERN`/`ACTION_CALL_PATTERN` 匹配不到，说明模型没有按 `Action: X[Y]` 或
  `Finish[Z]` 的格式输出，通常需要换更强的模型，或在 prompt 里补充 few-shot 示例。
- `ToolExecutor` 是两套实现共用的工具注册表(`SearchController` 也复用它做单独的
  Action → Observation 验证)，新增工具只需调用 `registerTool(name, description, func)`，
  两条 ReAct 路径都能立刻用上。

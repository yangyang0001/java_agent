# Plan-and-Solve

## 1. 核心思想

Plan-and-Solve Prompting 由 Lei Wang 于 2023 年提出，动机是解决 Chain-of-Thought 在
处理多步骤复杂问题时容易“中途偏题”的问题。

与 ReAct 把 Thought/Action 揉在每一步、走一步看一步不同，Plan-and-Solve 把流程**解耦成
两个独立阶段**：

```
Plan(规划)  → 一次性把问题拆解成 n 个有序子步骤
Solve(执行) → 严格按计划逐步求解，每一步的结果作为下一步的输入
```

如果说 ReAct 是“侦探式”的边观察边推理，Plan-and-Solve 就是“建筑师式”的先出图纸、
再按图施工。

## 2. 形式化表达

规划阶段：规划模型 `π_plan` 根据原始问题 `q` 一次性生成包含 `n` 个步骤的计划：

```
P = π_plan(q) = (p_1, p_2, ..., p_n)
```

执行阶段：执行模型 `π_solve` 逐一求解每个步骤。第 `i` 步的结果 `s_i` 依赖原始问题
`q`、完整计划 `P`，以及此前所有步骤的结果 `(s_1, ..., s_{i-1})`：

```
s_i = π_solve(q, P, (s_1, ..., s_{i-1}))
```

最终答案就是最后一步的结果 `s_n`。图示见教材图 4.2。

<span style="color:red"><strong>Plan 是不是只能规划一次？</strong> 按论文和公式 <code>P = π_plan(q)</code> 的定义，是的——整个任务只调用
一次规划模型，产出完整的 <code>n</code> 步计划，这是 Plan-and-Solve 区别于 ReAct 的核心特征。如果某个
子步骤本身很复杂，又在执行阶段临时把它拆成一个"子 Plan"再调用一次 <code>π_plan</code>，那本质上是又
做了一次规划——严格来说不再是原始定义里"只规划一次"的 Plan-and-Solve，而是变成了递归/
分层规划(hierarchical planning)，即在更细的粒度上又套了一层 Plan-and-Solve。这种嵌套写法
在工程上是合理的扩展，但不属于本节实现的 <code>Planner</code>/<code>Executor</code> 这个单层版本。</span>

## 3. 适用场景

结构性强、可以被清晰拆解成有序子任务的问题最能发挥这一范式的优势，例如：

- 多步数学应用题：先列出计算步骤，再逐一求解；
- 需要整合多个信息源的报告撰写：先规划结构（引言/数据源A/数据源B/总结），再逐段填充；
- 代码生成任务：先构思函数/类/模块结构，再逐一实现。

## 4. 与 ReAct 的关系

`chapter_04` 下已有的 ReAct 两种实现（见 `ReAct.md`）都是**边想边做**：每一步都要
重新决策“下一步做什么”，天然带有动态纠错能力，但也天然要多次往返调用模型、且容易
被提示词格式问题打断。Plan-and-Solve 反过来：**规划只做一次**，之后的执行阶段是在
“已知蓝图”下按部就班地跑，目标一致性更高，但缺点也很直观——如果第一步生成的计划
本身有逻辑错误（例如步骤拆分错了、遗漏了某个中间量），后面所有步骤都会在错误的蓝图
上越走越远，且执行阶段不会像 ReAct 那样根据中途的 Observation 反过来修正计划。

一句话总结：**ReAct 用循环换取纠错能力，Plan-and-Solve 用一次性规划换取目标一致性**，
两者是针对不同任务特点（探索型 vs. 结构分解型）的互补范式，而非谁取代谁。

## 5. Java 实现

`chapter_04` 已经落地了 Plan-and-Solve 的代码，都在 `agent` 包下，风格上与 `ReActAgent`
保持一致(各自独立持有 `OpenAIClient`/`CommonConfig`，不共享抽象的 LLM 调用工具类)：

- `agent/Planner.java` — 对应规划阶段。`PLANNER_PROMPT_TEMPLATE` 用 Java text block
  改写，要求模型输出 ` ```json\n["步骤1", "步骤2", ...]\n``` `。**唯一必须适配的地方**
  是解析:Python 版本用 `ast.literal_eval` 把 Python 列表字面量安全地转成 `list[str]`，
  Java 没有等价机制，所以这里把输出格式从 Python 列表换成 JSON 数组，用 Fastjson 的
  `JSONArray.parseArray(planStr)` + `toJavaList(String.class)` 解析成 `List<String>`，
  比用正则去拆 Python 语法可靠。解析失败时返回空列表，由 `PlanAndSolveAgent` 判断终止。
- `agent/Executor.java` — 对应执行阶段。`EXECUTOR_PROMPT_TEMPLATE` 同样是 text block，
  沿用 Python 版 `history` 字符串拼接的思路(`步骤 i: ... \n结果: ...`)，按顺序把每一步
  的结果喂给下一步的提示词，循环结束后最后一步的输出即最终答案。
- `agent/PlanAndSolveAgent.java` — 协调者，`run()` 先调用 `Planner.plan()`，计划为空则
  终止，否则调用 `Executor.execute()` 拿到最终答案。
- `controller/PlanAndSolveController.java` `POST /plan-and-solve` — 独立入口，和
  `ReActController`/`ChatController` 一样互不影响，复用同一份 `CommonConfig`/
  `OpenAIClient` 配置。

`Planner`/`Executor` 里各自维护一份私有 `think(prompt)` 方法，直接调用
`ChatCompletionCreateParams` 发起单轮补全，两个类之间没有相互依赖，也没有抽取共享的
LLM 调用封装——刻意保持了和 `ReActAgent` 一样的“各管各的”写法，避免为了消除几行重复
代码而引入额外的抽象层。

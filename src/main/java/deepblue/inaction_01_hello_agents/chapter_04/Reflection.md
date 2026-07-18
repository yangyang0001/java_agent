# Reflection

## 1. 核心思想

ReAct 和 Plan-and-Solve 都是"一次性交卷"：任务完成、循环结束，产物即最终答案。
Reflection 机制（灵感来自 Shinn, Noah 于 2023 年提出的 Reflexion 框架）引入了一个
**事后（post-hoc）自我校正循环**，让智能体像人类校对初稿、验算解题过程一样，审视自己
已经产出的结果，发现不足并迭代优化。

核心是一个三步循环：

```
Execution(执行)  → 生成一份初稿(答案/代码/行动轨迹)
Reflection(反思) → 让模型扮演"评审员"，从事实性错误、逻辑漏洞、效率问题、遗漏信息等
                    维度审查初稿，产出结构化反馈(Feedback)
Refinement(优化) → 把"初稿 + 反馈"作为上下文，生成更完善的修订稿
```

这个循环反复进行，直到反思阶段认为"无需改进"，或达到预设的最大迭代次数。

## 2. 形式化表达

第 `i` 轮反思模型 `π_reflect` 针对任务 `Task` 和当前输出 `O_i` 生成反馈 `F_i`：

```
F_i = π_reflect(Task, O_i)
```

优化模型 `π_refine` 结合任务、上一版输出与反馈，生成新一版输出 `O_{i+1}`：

```
O_{i+1} = π_refine(Task, O_i, F_i)
```

`O_0` 是初始执行阶段产出的第一版结果，循环终止时最新的 `O_i` 就是最终答案。图示见教材
图 4.3。

## 3. 与 ReAct / Plan-and-Solve 的关系

三种范式解决的是同一个大问题（如何让智能体的输出更可靠）的不同侧面：

- **ReAct**：纠错发生在**行动过程中**，靠外部工具的 `Observation` 修正下一步的 `Thought`，
  依赖的是外部世界的真实反馈。
- **Plan-and-Solve**：完全不纠错，靠一次性的高质量规划锁定路径，规划错了就一路错到底
  （见 `PlanSlove.md` 第 6 节）。
- **Reflection**：纠错发生在**产物完成之后**，不依赖外部工具，靠模型自己扮演"评审员"
  审视自己已经写好的答案，修正的是更高层次的逻辑/策略/效率问题，而不是某一步工具调用
  的对错。

三者可以组合使用：例如先用 ReAct 或 Plan-and-Solve 产出初稿，再用 Reflection 对这份初稿
做事后质量把关——教材原文也明确指出"执行"阶段可以直接复用 ReAct 或 Plan-and-Solve 的
产出作为 `O_0`。

## 4. 案例设定与记忆模块

教材选择的验证场景是代码生成与迭代优化："编写一个 Python 函数，找出 1 到 n 之间所有
的素数"——初版实现大概率是试除法（`O(n·√n)`），反思阶段应该能发现这个效率瓶颈，并
建议改用埃拉托斯特尼筛法（`O(n log log n)`）。

Reflection 天然需要"记得住之前的尝试和反馈"，所以引入了一个短期记忆模块 `Memory`：

- `addRecord(type, content)` — 按顺序记录每一条 `EXECUTION`(执行产物) 或
  `REFLECTION`(评审反馈)。
- `getTrajectory()` — 把所有记录序列化成一段文本，供需要完整轨迹上下文的场景使用。
- `getLastExecution()` — 取最近一次的执行产物(当前这一版代码)，用于反思和优化阶段的
  提示词填充。

## 5. Java 实现

`chapter_04` 下新增了 Reflection 的完整实现，风格与 `ReActAgent`/`Planner`/`Executor`
一致：各自独立持有 `OpenAIClient`/`CommonConfig`，用私有 `think(prompt)` 方法发起单轮
补全，不引入共享的 LLM 调用抽象。

- `agent/Memory.java` — 对应教材的 `Memory` 类。是一个**普通 POJO，不是 Spring Bean**：
  因为它是"一次 Reflection 运行"的状态载体，`ReflectionAgent.run()` 每次调用都会
  `new Memory()`，避免把运行时状态放进单例 Bean 里污染并发请求。用 `enum RecordType`
  代替教材里的字符串 `record_type`，避免拼错类型字符串。
- `agent/ReflectionAgent.java` — 三个阶段对应三份 Java text block 提示词
  (`INITIAL_PROMPT_TEMPLATE`/`REFLECT_PROMPT_TEMPLATE`/`REFINE_PROMPT_TEMPLATE`)。
  `run()` 先执行一次初始尝试写入 `Memory`，再循环最多 `MAX_ITERATIONS = 3` 轮"反思→优化"，
  一旦反思反馈里出现"无需改进"就提前 `break`，循环结束后 `Memory.getLastExecution()`
  即最终代码。
- `controller/ReflectionController.java` `POST /reflection` — 独立入口，和
  `ChatController`/`ReActController`/`PlanAndSolveController` 一样互不影响。

唯一需要注意的适配点：教材用 Python 字符串 `"无需改进" in feedback` 做终止判断，Java 里
对应 `feedback.contains(NO_IMPROVEMENT_MARKER)`，逻辑完全一致，只是要多判一次 `feedback`
非 `null`（因为 `think()` 在模型无响应时可能返回 `null`）。

## 6. 成本收益分析

### 6.1 主要成本

- **模型调用开销增加**：每多一轮迭代，至少要多调用两次模型(一次反思、一次优化)，
  迭代轮数越多，调用次数和成本越是成倍增长。
- **任务延迟显著提高**：`ReflectionAgent.run()` 是严格串行的——反思必须等执行完成，
  优化必须等反思完成，不适合对实时性要求高的场景。
- **提示工程复杂度上升**：三个阶段需要三份职责不同、措辞精心设计的提示词(`INITIAL`/
  `REFLECT`/`REFINE`)，比 ReAct/Plan-and-Solve 只维护一到两份模板的调试成本更高。

### 6.2 核心收益

- **解决方案质量的跃迁**：能把一个"功能正确"的初稿迭代成"性能优秀"的最终版——教材的
  素数案例里，试除法 `O(n·√n)` 经过一轮反思-优化直接跃迁为筛法 `O(n log log n)`。
- **鲁棒性与可靠性增强**：内部自我纠错循环能发现初版方案里的逻辑漏洞、事实性错误或
  边界情况处理不当，不必依赖外部工具或人工二次审查。

**适用场景**：对结果质量、准确性、可靠性要求高，且能接受更高延迟和成本的场景——生成
关键业务代码/技术报告、复杂逻辑推演、深度决策支持系统。反之，如果需要快速响应，或者
"大致正确"就已经足够，ReAct 或 Plan-and-Solve 通常性价比更高，不必上 Reflection。

## 7. 优点与缺点

### 7.1 优点

- **内部纠错，不依赖外部工具**：ReAct 的纠错要靠工具返回的 `Observation`，
  Plan-and-Solve 干脆没有纠错能力；Reflection 靠模型自己扮演"评审员"审视自己的产物，
  能修正逻辑、策略、效率层面的高层错误，不需要额外接入任何外部系统。
- **把一次性交付变成持续优化**：初稿只是起点而不是终点，`Memory` 记录下"初稿 → 反馈 →
  修订稿"的完整轨迹，最终质量随迭代轮数逐步逼近上限，而不是第一次采样的运气。
- **可迁移到多模态产物**：本项目/教材的案例是代码，但"执行 → 反思 → 优化"这套循环本身
  不限定产物类型，理论上同样适用于文本、图像等其他可被"评审"的输出。

### 7.2 缺点

- **模型调用开销随迭代轮数线性增长**：每多一轮至少多两次调用(反思 + 优化)，
  `MAX_ITERATIONS` 设得越大，成本涨得越快。
- **严格串行，延迟最高**：`ReflectionAgent.run()` 里反思必须等执行完成、优化必须等反思
  完成，三种范式里 Reflection 的端到端耗时通常是最高的。
- **提示工程复杂度最高**：需要维护三份职责分工明确的提示词(`INITIAL`/`REFLECT`/
  `REFINE`)，"评审员"角色的措辞质量直接决定反思能不能挑出真正有价值的问题——如果反思
  提示词不够犀利，很可能第一轮就草率给出"无需改进"，让整个循环形同虚设。
- **收敛性没有保证**：终止条件依赖模型在反馈里说出"无需改进"这个特定短语，如果模型
  表述方式不一致（比如换个说法表达"已经足够好"），循环会跑满 `MAX_ITERATIONS` 才强制
  停止，而不是在真正收敛的那一轮提前退出。

## 8. 三种范式对比

| 维度 | ReAct | Plan-and-Solve | Reflection |
|---|---|---|---|
| 核心循环 | Thought → Action → Observation，边想边做 | Plan(一次性规划) → Solve(逐步执行) | Execution → Reflection → Refinement，事后自我校正 |
| 纠错来源 | 外部工具返回的 Observation | 无——规划错了就一路错到底 | 模型自己扮演"评审员"，不依赖外部工具 |
| 是否有全局规划 | 无，走一步看一步 | 有，一次性生成完整步骤列表 | 无任务分解，针对已有产物做整体审查 |
| 典型模型调用次数 | 每步 1 次，共 O(步数) 次 | 1 次规划 + N 次执行(N=步骤数) | 1 次初稿 + 2×实际迭代轮数 次(反思+优化) |
| 延迟/成本 | 中等，随步数增长 | 中等，步骤数固定后基本可预估 | 三者中最高，严格串行且每轮至少两次调用 |
| 主要局限 | 提示词脆弱、可能原地打转、强依赖格式遵循能力 | 规划错误无法自愈、不适合探索型任务 | 调用开销大、延迟高、提示工程复杂度高、收敛性无保证 |
| 本项目实现 | `ChatController`(function-calling) / `ReActAgent`(经典提示词) | `Planner` + `Executor` + `PlanAndSolveAgent` | `Memory` + `ReflectionAgent` |
| 独立入口 | `/chat`、`/react` | `/plan-and-solve` | `/reflection` |
| 最适合的场景 | 需要外部知识/实时信息的任务(搜索、查数据) | 结构清晰、可预先完整拆解的任务(应用题、报告结构) | 对质量/可靠性要求高、可接受高延迟的任务(关键代码、深度分析) |

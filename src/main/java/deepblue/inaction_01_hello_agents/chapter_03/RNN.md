# 循环神经网络（Recurrent Neural Network, RNN）

## 1. 为什么需要 RNN

普通的前馈网络（如 MLP）每次输入都是独立的，互相之间没有「记忆」——处理第 5 个词
的时候，网络并不知道前面 4 个词说了什么。但语言、时间序列、音频这些数据本质上是
**有先后顺序、前后有依赖关系的序列**：一句话的下一个词往往取决于前面出现过的词。

RNN 的核心思路很简单：**引入一个隐藏状态（hidden state）h，像一条不断向前传递的
「记忆带」，每处理一个时间步就更新一次，把之前看到的信息压缩携带下去**。这也是它
名字里「循环」的含义——同一套参数在每个时间步被反复（循环）使用。

## 2. 核心结构

RNN 在概念上只有一个「细胞（cell）」，但沿着时间轴把它「展开（unroll）」之后，
就能看到信息是如何一步步向后传递的：

<svg viewBox="0 0 640 260" width="100%" style="max-width:640px" role="img" aria-label="RNN 沿时间轴展开示意图，输入 x 在底部，隐藏状态 h 在中间从左向右传递，输出 y 在顶部">
  <text x="10" y="26" font-size="12" fill="currentColor" opacity="0.55">h₀</text>
  <line x1="30" y1="130" x2="80" y2="130" stroke="currentColor" stroke-width="2" marker-end="url(#arrRNN)" opacity="0.6"/>

  <defs>
    <marker id="arrRNN" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
      <path d="M0,0 L6,3 L0,6 Z" fill="currentColor" opacity="0.7"/>
    </marker>
  </defs>

  <!-- t-1 -->
  <rect x="80" y="95" width="90" height="70" rx="10" fill="none" stroke="#3b82f6" stroke-width="2.5"/>
  <text x="125" y="135" font-size="14" fill="#3b82f6" text-anchor="middle" font-weight="600">Cell</text>
  <line x1="125" y1="165" x2="125" y2="205" stroke="currentColor" stroke-width="2" marker-end="url(#arrRNN)"/>
  <text x="125" y="222" font-size="13" fill="currentColor" text-anchor="middle">x₁</text>
  <line x1="125" y1="95" x2="125" y2="55" stroke="currentColor" stroke-width="2" marker-end="url(#arrRNN)"/>
  <text x="125" y="42" font-size="13" fill="currentColor" text-anchor="middle">y₁</text>
  <line x1="170" y1="130" x2="230" y2="130" stroke="currentColor" stroke-width="2" marker-end="url(#arrRNN)"/>
  <text x="198" y="122" font-size="12" fill="currentColor" opacity="0.6" text-anchor="middle">h₁</text>

  <!-- t -->
  <rect x="230" y="95" width="90" height="70" rx="10" fill="none" stroke="#3b82f6" stroke-width="2.5"/>
  <text x="275" y="135" font-size="14" fill="#3b82f6" text-anchor="middle" font-weight="600">Cell</text>
  <line x1="275" y1="165" x2="275" y2="205" stroke="currentColor" stroke-width="2" marker-end="url(#arrRNN)"/>
  <text x="275" y="222" font-size="13" fill="currentColor" text-anchor="middle">x₂</text>
  <line x1="275" y1="95" x2="275" y2="55" stroke="currentColor" stroke-width="2" marker-end="url(#arrRNN)"/>
  <text x="275" y="42" font-size="13" fill="currentColor" text-anchor="middle">y₂</text>
  <line x1="320" y1="130" x2="380" y2="130" stroke="currentColor" stroke-width="2" marker-end="url(#arrRNN)"/>
  <text x="350" y="122" font-size="12" fill="currentColor" opacity="0.6" text-anchor="middle">h₂</text>

  <!-- t+1 -->
  <rect x="380" y="95" width="90" height="70" rx="10" fill="none" stroke="#3b82f6" stroke-width="2.5"/>
  <text x="425" y="135" font-size="14" fill="#3b82f6" text-anchor="middle" font-weight="600">Cell</text>
  <line x1="425" y1="165" x2="425" y2="205" stroke="currentColor" stroke-width="2" marker-end="url(#arrRNN)"/>
  <text x="425" y="222" font-size="13" fill="currentColor" text-anchor="middle">x₃</text>
  <line x1="425" y1="95" x2="425" y2="55" stroke="currentColor" stroke-width="2" marker-end="url(#arrRNN)"/>
  <text x="425" y="42" font-size="13" fill="currentColor" text-anchor="middle">y₃</text>
  <line x1="470" y1="130" x2="510" y2="130" stroke="currentColor" stroke-width="2" stroke-dasharray="4,3" opacity="0.6"/>
  <text x="490" y="122" font-size="12" fill="currentColor" opacity="0.6" text-anchor="middle">h₃</text>
  <text x="530" y="135" font-size="18" fill="currentColor" opacity="0.5">…</text>

  <text x="275" y="250" font-size="12" fill="currentColor" opacity="0.55" text-anchor="middle">
    三个方框其实是「同一个」Cell（参数 W 共享），只是画在不同时间步上
  </text>
</svg>

关键点：**三个时间步画的 Cell 是同一套参数**（同一个 `Wxh`、`Whh`、`Why`），并不是
三个不同的网络。真正在向前流动、携带「记忆」的是水平方向的隐藏状态 `h`。

## 3. 前向传播公式

在时间步 `t`：

```
h_t = tanh( W_xh · x_t + W_hh · h_(t-1) + b_h )      ← 更新隐藏状态（记忆）
y_t =        W_hy · h_t + b_y                         ← 由当前记忆产生输出
```

- **x_t**：当前时间步的输入（比如第 t 个词的向量）
- **h_(t-1)**：上一步留下来的隐藏状态（记忆）
- **h_t**：融合了「新输入」和「旧记忆」之后的新隐藏状态
- **W_xh、W_hh、W_hy**：三组权重矩阵，在所有时间步之间**共享**
- **tanh**：把结果压缩到 `(-1, 1)`，防止隐藏状态数值无限增长

可以这样理解：`h_t` 永远是「这一步新看到的东西」和「之前积累的记忆」按权重混合后的
结果，然后再基于这个混合后的记忆去产生输出 `y_t`。

## 4. 手把手：一步步展开计算

光看公式容易「一眼扫过去」，下面用一组具体数字，把 `h_1 → h_2 → h_3` 每一步的计算
过程完整摊开。为了跟第 5 节的动态演示对得上号，这里用的就是演示里同一套参数：

```
输入维度 = 1，隐藏状态维度 = 2

W_xh = [0.6, -0.4]              b_h = [0.1, -0.1]
W_hh = [[0.5, -0.3],
        [0.2,  0.4]]
W_hy = [0.7, -0.6]               b_y = 0.05

h_0 = [0, 0]   （初始记忆为 0）
输入序列 x = 0.9, -0.6, 0.4, …
```

**t = 1（x₁ = 0.9，h₀ = [0, 0]）**

```
h₁[0] = tanh(0.6×0.9 + 0.5×0      + (-0.3)×0      + 0.1)  = tanh(0.640)  ≈ 0.565
h₁[1] = tanh(-0.4×0.9 + 0.2×0      + 0.4×0        + (-0.1)) = tanh(-0.460) ≈ -0.430

y₁ = 0.7×0.565 + (-0.6)×(-0.430) + 0.05 ≈ 0.703
```

**t = 2（x₂ = -0.6，h₁ = [0.565, -0.430]）**——注意这里 `h_(t-1)` 已经不再是 0，
上一步算出的记忆被真正代入了公式：

```
h₂[0] = tanh(0.6×(-0.6) + 0.5×0.565 + (-0.3)×(-0.430) + 0.1) = tanh(0.151) ≈ 0.150
h₂[1] = tanh(-0.4×(-0.6) + 0.2×0.565 + 0.4×(-0.430)   + (-0.1)) = tanh(0.081) ≈ 0.081

y₂ = 0.7×0.150 + (-0.6)×0.081 + 0.05 ≈ 0.106
```

**t = 3（x₃ = 0.4，h₂ = [0.150, 0.081]）**：

```
h₃[0] = tanh(0.6×0.4 + 0.5×0.150 + (-0.3)×0.081 + 0.1) = tanh(0.391) ≈ 0.372
h₃[1] = tanh(-0.4×0.4 + 0.2×0.150 + 0.4×0.081   + (-0.1)) = tanh(-0.198) ≈ -0.195

y₃ = 0.7×0.372 + (-0.6)×(-0.195) + 0.05 ≈ 0.427
```

汇总成表更容易对照：

| t | x_t | h_t[0] | h_t[1] | y_t |
|---|---|---|---|---|
| 1 | 0.9 | 0.565 | -0.430 | 0.703 |
| 2 | -0.6 | 0.150 | 0.081 | 0.106 |
| 3 | 0.4 | 0.372 | -0.195 | 0.427 |

可以看到每一步 `h_t` 都同时含有「新输入 `x_t` 贡献的一项」和「上一步 `h_(t-1)` 贡献的
一项」——这正是 RNN「记忆」的字面体现：`h_2` 的计算里能看到 `h_1` 的影子，`h_3` 的计算
里又能看到 `h_2`（也就间接包含了 `h_1`）的影子，一路传递下去。

## 5. 动态演示

下面的动态图把上一节的手工计算「演出来」了：输入 `x_t` 一步步从左侧进入，隐藏状态
`h` 的每个分量都以彩色柱状条表示，随着时间步推进不断被重新计算并高亮传向下一步；
右侧的公式面板会实时把当前这一步的具体数字代入 `tanh(...)` 公式（跟第 4 节表格里的
数字一一对应），可以直观看到旧记忆是如何和新输入融合、又是如何随时间逐渐衰减/被
覆盖的。

▶ [点击查看动态演示](./RNN动态演示.html)

## 6. 长期依赖问题：梯度消失/爆炸

朴素 RNN 有一个著名的缺陷：反向传播时梯度需要沿着时间步连乘 `W_hh`（以及 tanh 导数），
序列一长，这个连乘的结果很容易**指数级衰减（消失）或指数级增大（爆炸）**。结果就是
网络「记不住」很久以前的信息——比如在一段很长的文本里，开头的关键信息传到结尾时早已
被稀释得几乎不起作用。下面推导一下这个结论具体是怎么来的。

### 6.1 推导：BPTT（Backpropagation Through Time）

**根源就在第 2 节说的「参数共享」上**：`W_hh` 在每一个时间步都被用了一次，所以对
`W_hh` 求梯度时，必须把它在**每一个时间步的贡献都加起来**——这就是反向传播要沿着
时间「展开」的原因。为了让代数式子看得清楚，先用一个最简单的**标量版 RNN**（隐藏
状态只有 1 维）来推导：

```
z_t = w_hh · h_(t-1) + w_xh · x_t + b_h
h_t = tanh(z_t)
```

假设损失 `L` 只由最后一步 `h_T` 算出（`y_T = w_hy·h_T`，`L = ½(y_T - target)²`）。
因为 `w_hh` 在 `t = 1, 2, …, T` 每一步都出现过，链式法则要把每一步「显式」贡献的
梯度求和：

```
∂L/∂w_hh = Σ_{k=1}^{T}  (∂L/∂h_T) · (∂h_T/∂h_k) · (∂h_k/∂w_hh)|直接项
```

其中最关键的一项是 `∂h_T/∂h_k`——它衡量「第 k 步的隐藏状态，经过 T-k 步之后，还能
剩下多少影响力传到最后一步」。按链式法则逐步展开：

```
∂h_T/∂h_k = ∏_{t=k+1}^{T}  ∂h_t/∂h_(t-1)
          = ∏_{t=k+1}^{T}  tanh'(z_t) · w_hh
```

也就是说，**每往回跳一个时间步，就要再乘一次 `tanh'(z_t) · w_hh`**。这里有两件事同时
在起作用：

- `tanh'(z) = 1 - tanh²(z)`，取值范围 `(0, 1]`，本身就是「缩小」的
- `w_hh` 如果 `|w_hh| < 1`，也在不断缩小这个乘积；如果 `|w_hh| > 1`，则可能放大

**数值感受一下**：取 `w_hh = 0.5`，`tanh'(z)` 平均约 `0.9`，那么每一步的缩放因子约
`0.5 × 0.9 = 0.45`。序列长度 `T - k` 每增加 1，这个乘积就再乘一次 0.45：

| T - k（间隔的步数） | 累计因子 (0.45)^(T-k) |
|---|---|
| 1 | 0.45 |
| 3 | 0.091 |
| 6 | 0.0083 |
| 10 | 0.00034 |

只隔 10 步，最早的隐藏状态对最终损失的梯度贡献就已经衰减到万分之三——这就是**梯度
消失**：网络实际上学不到「第 1 步和第 11 步有关系」这种长距离依赖，因为反向传播时
这部分梯度信号早就小到可以忽略。

### 6.2 反过来：梯度爆炸是怎么回事

同一个连乘公式 `∂h_T/∂h_k = ∏ tanh'(z_t)·w_hh`，只要把 `w_hh` 换成绝对值大于 1 的数，
结论就完全反过来。取 `w_hh = 2.0`（`tanh'(z)` 仍取平均 `0.9`），单步缩放因子变成
`2.0 × 0.9 = 1.8`——大于 1，所以每往回跳一步，数值不是缩小而是被放大：

| T - k（间隔的步数） | 累计因子 (1.8)^(T-k) |
|---|---|
| 1 | 1.8 |
| 5 | 18.9 |
| 10 | 357 |
| 20 | 127,483 |
| 30 | 45,517,423 |

和梯度消失的表格几乎是镜像：**间隔步数每增加 1，梯度不是被压缩，而是被放大 1.8
倍**。序列稍微长一点（二三十步），这个数字就已经大到远超 `float`/`double` 能正常参与
计算的范围，很容易在训练时表现为：

- **损失（loss）曲线突然出现一个尖峰**，甚至直接跳变成 `NaN` / `Inf`
- **权重更新一步迈得太大**：参数更新公式是 `θ_new = θ_old - lr × 梯度`，梯度一旦是
  `10⁶` 量级，哪怕学习率 `lr` 很小（比如 `0.01`），这一步也会把权重猛地推到一个离谱的
  数值上，模型此前学到的东西直接被这一步「冲掉」
- 权重被推到极端值后，下一轮前向传播算出的 `z_t` 也会随之变得很极端，`tanh` 更容易
  进入饱和区，训练往往就此发散、无法恢复

这也是为什么梯度爆炸在实践中通常是「猝不及防」的：梯度消失是训练**缓慢地学不到东
西**，梯度爆炸则是训练**在某一步突然彻底失败**。

**梯度裁剪（Gradient Clipping）具体怎么做**：在梯度更新之前，先算出整个梯度向量的
模长 `‖g‖`，如果它超过设定的阈值 `threshold`，就把梯度按比例缩小，只保留方向、把长度
限制住：

```
if ‖g‖ > threshold:
    g = g × (threshold / ‖g‖)
```

这样即使反向传播算出的原始梯度是 `10⁶` 量级，真正用来更新参数的梯度也会被限制在
`threshold` 以内，权重就不会被一步推到离谱的数值——这是工程上最直接、最常用的「治标」
方法；真正「治本」的做法是换用 LSTM/GRU 这种用加法（而不是乘法）传递记忆的结构，从
源头上避免这个连乘效应。

真实模型里 `W_hh` 是矩阵而不是标量，结论仍然成立，只是要看 `W_hh` 的**谱半径（最大
特征值的模）**：谱半径持续小于 1 就趋向消失，持续大于 1 就趋向爆炸——这也是为什么
LSTM/GRU 要专门设计一条「加法式」更新的记忆通路（而不是像朴素 RNN 这样每步都相乘），
从根本上避免这个连乘效应。

下面这个动态图把上面的推导过程「跑」了一遍：一个脉冲从 `h_T` 出发向左回传，每跨过一
个时间步就按 `tanh'(z_t)·w_hh` 缩放一次，数值和小球大小同步变化；点击顶部按钮可以
切换「梯度消失（w_hh=0.5）」和「梯度爆炸（w_hh=2.0）」两种模式对比。

▶ [点击查看 BPTT 梯度消失/爆炸动态演示](./RNN反向传播动态演示.html)

工程上常见的应对方式：

| 方法 | 思路 |
|---|---|
| 梯度裁剪（Gradient Clipping） | 反向传播时把梯度的模长限制在一个阈值内，缓解「爆炸」 |
| LSTM（长短期记忆网络） | 引入输入门/遗忘门/输出门，显式控制记忆的读写和保留 |
| GRU（门控循环单元） | LSTM 的简化版，用更新门/重置门达到类似效果，参数更少 |
| Attention / Transformer | 干脆放弃「按时间步顺序传递」的方式，直接让任意两个位置的信息可以互相关注 |

这也是后续 LSTM、GRU、以及最终 Transformer 为什么会出现的直接动机——都是在解决
「长距离依赖记不住」这一个核心问题。

## 7. Java 实现示例

一个只有 1 维输入、2 维隐藏状态的最小 RNN 前向传播，帮助对照上面的公式：

```java
public class SimpleRNN {

    private final double[][] wxh; // (hiddenSize x inputSize)
    private final double[][] whh; // (hiddenSize x hiddenSize)
    private final double[]   bh;  // (hiddenSize)
    private final double[][] why; // (outputSize x hiddenSize)
    private final double[]   by;  // (outputSize)

    public SimpleRNN(double[][] wxh, double[][] whh, double[] bh,
                      double[][] why, double[] by) {
        this.wxh = wxh; this.whh = whh; this.bh = bh;
        this.why = why; this.by = by;
    }

    /** 对一个输入序列做一次完整的前向传播，返回每一步的隐藏状态和输出 */
    public double[][] forward(double[][] inputs) {
        int hiddenSize = bh.length;
        double[] h = new double[hiddenSize]; // h_0 初始化为 0
        double[][] outputs = new double[inputs.length][];

        for (int t = 0; t < inputs.length; t++) {
            double[] xt = inputs[t];
            double[] hNext = new double[hiddenSize];
            for (int i = 0; i < hiddenSize; i++) {
                double sum = bh[i];
                for (int j = 0; j < xt.length; j++) sum += wxh[i][j] * xt[j];
                for (int j = 0; j < hiddenSize; j++) sum += whh[i][j] * h[j];
                hNext[i] = Math.tanh(sum); // h_t = tanh(Wxh·x_t + Whh·h_(t-1) + b_h)
            }
            h = hNext;

            double[] yt = new double[by.length];
            for (int i = 0; i < yt.length; i++) {
                double sum = by[i];
                for (int j = 0; j < hiddenSize; j++) sum += why[i][j] * h[j];
                yt[i] = sum; // y_t = Why·h_t + b_y
            }
            outputs[t] = yt;
        }
        return outputs;
    }
}
```

### 7.1 每个参数是什么意思

| 参数 | 形状（shape） | 对应公式里谁 | 最简单的理解 |
|---|---|---|---|
| `wxh` | hiddenSize × inputSize | `W_xh` | 「这一瞬间看到了什么」——把当前输入 `x_t` 转成对隐藏状态的贡献 |
| `whh` | hiddenSize × hiddenSize | `W_hh` | 「旧记忆怎么保留/怎么混合」——把上一步的记忆 `h_(t-1)` 转成对新记忆的贡献，**是唯一真正携带历史信息的一组参数** |
| `bh` | hiddenSize | `b_h` | 隐藏层偏置，一个固定偏移量，跟输入、记忆都无关 |
| `why` | outputSize × hiddenSize | `W_hy` | 「怎么把记忆翻译成答案」——把当前记忆 `h_t` 转成输出 `y_t` |
| `by` | outputSize | `b_y` | 输出层偏置 |

`hiddenSize`/`inputSize`/`outputSize` 分别是隐藏状态、输入、输出各自的维度：`wxh` 的行
数一定等于 `hiddenSize`（它要生成 `hiddenSize` 维的隐藏状态），`why` 的列数也一定等于
`hiddenSize`（它要读 `hiddenSize` 维的隐藏状态）。

### 7.2 记忆到底是怎么「带」到下一步的

矩阵乘法只是「新输入和旧记忆怎么混合」，真正实现「记忆」这件事的，只有这两行代码：

```java
double[] h = new double[hiddenSize]; // ① 在 for 循环外面创建，整个前向传播只创建这一次
for (int t = 0; t < inputs.length; t++) {
    ...
    h = hNext; // ② 每一轮循环结束前，把这一步算出的新记忆写回 h
}
```

- **① `h` 在 `for` 循环外声明**——它不会随着每次循环重新创建，而是像一个贯穿整个时间
  轴、一直存在的「盒子」。
- **② 循环体最后一行 `h = hNext`**——把这一步刚算好的新隐藏状态塞回这个盒子，覆盖掉
  旧值。

于是下一轮循环（`t+1`）开始时，`whh[i][j] * h[j]` 那一行读到的 `h`，就是**上一轮循环
留下来的值**——它不是重新传进来的参数，也不是什么特殊机制，纯粹就是一个在循环外声明、
每轮结束时更新一次的普通变量。这和写一个累加器几乎是同一回事：

```java
double total = 0;             // 循环外声明，只有一份
for (int t = 0; t < n; t++) {
    total = total + x[t];     // 读「旧」total，写「新」total
}
```

`total` 是怎么在循环里被一步步带下去的，`h` 就是怎么被带下去的——唯一区别是 RNN 不是
简单相加，而是「新输入和旧记忆各自乘一个权重矩阵、加在一起、再过一次 `tanh` 压缩」。

## 8. 补充：tanh 的几何直觉 & 矩阵乘法怎么算

前面几节反复用到两个基本操作——`tanh` 和「矩阵 × 向量」。这一节把它们单独拆开看。

### 8.1 tanh 是一条「压缩」曲线

```
tanh(x) = (e^x - e^-x) / (e^x + e^-x)
```

<svg viewBox="0 0 400 300" width="100%" style="max-width:420px" role="img" aria-label="tanh 函数曲线，x 从 -4 到 4，y 被压缩在 -1 到 1 之间，中间接近直线，两端趋于饱和">
  <rect x="160" y="20" width="80" height="260" fill="currentColor" opacity="0.06"/>
  <line x1="20" y1="150" x2="380" y2="150" stroke="currentColor" stroke-width="1" opacity="0.35"/>
  <line x1="200" y1="20" x2="200" y2="280" stroke="currentColor" stroke-width="1" opacity="0.35"/>

  <line x1="20" y1="50" x2="380" y2="50" stroke="currentColor" stroke-width="1" stroke-dasharray="4,3" opacity="0.45"/>
  <line x1="20" y1="250" x2="380" y2="250" stroke="currentColor" stroke-width="1" stroke-dasharray="4,3" opacity="0.45"/>
  <text x="386" y="54" font-size="12" fill="currentColor" opacity="0.6">+1</text>
  <text x="386" y="254" font-size="12" fill="currentColor" opacity="0.6">-1</text>
  <text x="204" y="145" font-size="12" fill="currentColor" opacity="0.6">0</text>

  <polyline fill="none" stroke="#8b5cf6" stroke-width="2.5" points="
    40,249.9  60,249.8  80,249.5  100,248.7  120,246.4  140,240.5  160,226.2  180,196.2
    200,150   220,103.8 240,73.8  260,59.5   280,53.6   300,51.3   320,50.5   340,50.2  360,50.1"/>

  <circle cx="200" cy="150" r="3" fill="#8b5cf6"/>
  <text x="215" y="168" font-size="11" fill="currentColor" opacity="0.7">x=0 附近：斜率≈1，接近直线</text>
  <text x="240" y="40" font-size="11" fill="currentColor" opacity="0.7">x 越大：曲线越平，斜率→0（饱和）</text>
  <text x="30" y="270" font-size="11" fill="currentColor" opacity="0.7">x 越小：同样趋于饱和</text>
</svg>

几何上可以这样理解：**tanh 把整条数轴（`-∞` 到 `+∞`）「折」进了 `(-1, 1)` 这个开区间
里**——想象一根无限长的尺子，把两端往中间弯折，越往两端弯折得越厉害，永远贴近
`y=1`/`y=-1` 这两条「天花板」和「地板」，但永远碰不到。

三个关键几何性质：

- **过原点、中心对称**：`tanh(-x) = -tanh(x)`，图像绕原点旋转 180° 和自身重合
- **在 `x=0` 附近近似直线**：`tanh'(0) = 1`，输入的微小变化几乎原样传递到输出
- **在 `|x|` 较大处「饱和」**：曲线变得几乎水平，`tanh'(x) = 1 - tanh²(x) → 0`——
  这时候不管输入怎么变化，输出几乎不再变化

这条导数 `tanh'(z_t)` 正是第 6.1 节 BPTT 推导里连乘的那一项：**hidden state 一旦被推
到饱和区（数值接近 ±1），tanh' 就会非常接近 0，梯度沿时间反传时就会被这一项迅速压
扁**——这也是为什么梯度消失和 tanh 的饱和特性直接相关。

▶ [点击查看 tanh 动态演示](./tanh动态演示.html)：一个点沿着曲线来回滑动，实时显示
当前的 `tanh(x)`、切线斜率 `tanh'(x)`，可以直观看到「越靠近两端，切线越平」。

### 8.2 tanh 作用在向量上：逐元素（element-wise），不是矩阵运算

`h_t = tanh(z_t)` 里的 `z_t` 通常是一个向量（比如 2 维隐藏状态），`tanh` 在这里**不是
矩阵操作**，只是把向量里的每一个数字，各自独立地代入 tanh 函数，互相之间没有任何
交互：

```
z_t = [0.64, -0.46]
tanh(z_t) = [tanh(0.64), tanh(-0.46)] = [0.565, -0.430]
```

对照第 4 节 `t=1` 的手算结果，数值完全一致——`h_t` 每个分量的「压缩」都是独立完成的。

### 8.3 矩阵乘法怎么算：以 `W_xh · x_t` 为例

`《余弦相似度.md》` 第 4 节讲过点积的两种视角（代数展开 vs. 投影几何）；**矩阵乘以
向量，本质上就是把好几个「点积」叠在一起做**——矩阵的每一行分别和输入向量做一次
点积，结果拼成一个新向量。

以一个 2 维输入、2 维隐藏状态的例子（比第 7 节 `SimpleRNN` 里 1 维输入的例子更能看出
「矩阵」感）：

```
W_xh = [[0.6, -0.4],      x_t = [1.0]
        [0.3,  0.5]]             [0.5]
```

`W_xh` 是 `2×2`（hiddenSize=2 行，inputSize=2 列），`x_t` 是 `2×1` 的列向量。计算规则：
**结果向量的第 i 个分量 = `W_xh` 第 i 行，和 `x_t` 逐分量相乘再求和（也就是点积）**：

```
第 0 行 · x_t = 0.6×1.0 + (-0.4)×0.5 = 0.6 - 0.2 = 0.40
第 1 行 · x_t = 0.3×1.0 +   0.5×0.5 = 0.3 + 0.25 = 0.55

W_xh · x_t = [0.40, 0.55]
```

写成通用公式：

```
(W · x)_i = Σ_j  W[i][j] · x[j]      （第 i 个输出 = 第 i 行和 x 的点积）
```

对照 `SimpleRNN.forward` 里的这一行，会发现代码和公式是完全一样的：

```java
for (int j = 0; j < xt.length; j++) sum += wxh[i][j] * xt[j];
```

外层 `for (int i ...)` 循环 `hiddenSize` 次，每次都在算「`W_xh` 的第 `i` 行和输入向量
`x_t` 的点积」——所以整个双重循环做的事，就是「矩阵的每一行分别和输入向量点积一次」，
一共产出 `hiddenSize` 个数字，拼起来就是 `W_xh · x_t` 这个新向量。`W_hh · h_(t-1)` 的计
算方式完全一样，只是把 `x_t` 换成了 `h_(t-1)`。

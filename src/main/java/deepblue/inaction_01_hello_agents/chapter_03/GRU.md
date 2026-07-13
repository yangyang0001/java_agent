# 门控循环单元（Gated Recurrent Unit, GRU）

## 1. 为什么会有 GRU：LSTM 的简化版

`LSTM.md` 用「三个门 + 一条独立的细胞状态高速公路」解决了普通 RNN 的梯度消失问题，
但代价是**参数变多、结构变复杂**：四组权重（遗忘门、输入门、候选值、输出门），外加
一条单独维护的细胞状态 `c_t`。

GRU（2014 年提出）想问的问题是：**能不能用更少的门、更简单的结构，达到差不多的
效果？** 它做了两处简化：

1. **把细胞状态 `c_t` 和隐藏状态 `h_t` 合并成一条线**——GRU 只有 `h_t`，没有独立的
   `c_t`。
2. **把「遗忘」和「写入」这两件事用同一个门耦合起来**——LSTM 里遗忘门 `f_t` 和输入门
   `i_t` 是两个独立的门，理论上可以同时开、同时关；GRU 只用一个「更新门」`z_t`，
   通过 `(1-z_t)` 和 `z_t` 保证「旧记忆保留多少」和「新信息写入多少」此消彼长、加起
   来正好是 1（这是一个**凸组合/加权平均**，不会出现两者都很大导致数值爆炸的情况）。

## 2. 核心结构

<svg viewBox="0 0 620 300" width="100%" style="max-width:620px" role="img" aria-label="GRU 单元内部结构：重置门作用于上一步隐藏状态后参与候选值计算，更新门在旧隐藏状态和候选值之间做加权平均得到新隐藏状态">
  <defs>
    <marker id="arrGRU" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
      <path d="M0,0 L6,3 L0,6 Z" fill="currentColor" opacity="0.75"/>
    </marker>
  </defs>

  <text x="10" y="45" font-size="12" fill="currentColor" opacity="0.6">h_(t-1)</text>
  <line x1="60" y1="50" x2="480" y2="50" stroke="currentColor" stroke-width="3" opacity="0.85"/>
  <line x1="480" y1="50" x2="590" y2="50" stroke="currentColor" stroke-width="3" marker-end="url(#arrGRU)" opacity="0.85"/>
  <text x="598" y="45" font-size="12" fill="currentColor" opacity="0.6">h_t</text>
  <text x="270" y="30" font-size="11" fill="currentColor" opacity="0.55" text-anchor="middle">只有一条状态线（没有单独的 c_t）</text>

  <!-- 重置门 r_t：作用在 h_(t-1) 上，供候选值使用 -->
  <line x1="150" y1="65" x2="150" y2="130" stroke="#3b82f6" stroke-width="2" marker-end="url(#arrGRU)"/>
  <rect x="105" y="130" width="90" height="40" rx="9" fill="none" stroke="#3b82f6" stroke-width="2.5"/>
  <text x="150" y="153" font-size="13" fill="#3b82f6" text-anchor="middle" font-weight="600">r_t (σ)</text>
  <text x="150" y="185" font-size="11" fill="#3b82f6" text-anchor="middle" opacity="0.85">重置门：算候选值时，参考多少旧记忆</text>

  <circle cx="150" cy="90" r="12" fill="none" stroke="currentColor" stroke-width="2"/>
  <text x="150" y="95" font-size="12" fill="currentColor" text-anchor="middle">×</text>
  <line x1="150" y1="102" x2="150" y2="130" stroke="currentColor" stroke-width="1.6" opacity="0.6"/>

  <!-- 候选隐藏状态 h~_t -->
  <line x1="150" y1="170" x2="290" y2="220" stroke="#8b5cf6" stroke-width="2" marker-end="url(#arrGRU)"/>
  <rect x="245" y="220" width="100" height="40" rx="9" fill="none" stroke="#8b5cf6" stroke-width="2.5"/>
  <text x="295" y="243" font-size="13" fill="#8b5cf6" text-anchor="middle" font-weight="600">h̃_t (tanh)</text>
  <text x="295" y="275" font-size="11" fill="#8b5cf6" text-anchor="middle" opacity="0.85">候选新隐藏状态</text>

  <!-- 更新门 z_t：在 h_(t-1) 和 h~_t 之间做加权平均 -->
  <line x1="330" y1="65" x2="330" y2="130" stroke="#f59e0b" stroke-width="2" marker-end="url(#arrGRU)"/>
  <rect x="285" y="130" width="90" height="40" rx="9" fill="none" stroke="#f59e0b" stroke-width="2.5"/>
  <text x="330" y="153" font-size="13" fill="#f59e0b" text-anchor="middle" font-weight="600">z_t (σ)</text>
  <text x="330" y="185" font-size="11" fill="#f59e0b" text-anchor="middle" opacity="0.85">更新门：新旧各占多少比例</text>

  <line x1="345" y1="220" x2="345" y2="170" stroke="#f59e0b" stroke-width="1.6" opacity="0.6" marker-end="url(#arrGRU)"/>

  <circle cx="480" cy="50" r="15" fill="none" stroke="currentColor" stroke-width="2"/>
  <text x="480" y="55" font-size="13" fill="currentColor" text-anchor="middle">Σ</text>
  <line x1="345" y1="220" x2="470" y2="60" stroke="#8b5cf6" stroke-width="2" stroke-dasharray="3,3" opacity="0.7" marker-end="url(#arrGRU)"/>
  <text x="420" y="150" font-size="11" fill="currentColor" opacity="0.6" text-anchor="middle">(1-z_t)·h_(t-1) + z_t·h̃_t</text>

  <!-- 输入总线 -->
  <line x1="90" y1="290" x2="400" y2="290" stroke="currentColor" stroke-width="1.6" opacity="0.5"/>
  <text x="40" y="295" font-size="12" fill="currentColor" opacity="0.65">x_t</text>
  <line x1="150" y1="290" x2="150" y2="170" stroke="currentColor" stroke-width="1.2" marker-end="url(#arrGRU)" opacity="0.5"/>
  <line x1="330" y1="290" x2="330" y2="170" stroke="currentColor" stroke-width="1.2" marker-end="url(#arrGRU)" opacity="0.5"/>
  <line x1="295" y1="290" x2="295" y2="260" stroke="currentColor" stroke-width="1.2" marker-end="url(#arrGRU)" opacity="0.5"/>
</svg>

## 3. 核心公式

```
z_t  = σ( W_z,h·h_(t-1) + W_z,x·x_t + b_z )                 ← 更新门
r_t  = σ( W_r,h·h_(t-1) + W_r,x·x_t + b_r )                 ← 重置门
h̃_t = tanh( W_h,x·x_t + W_h,h·(r_t ⊙ h_(t-1)) + b_h )       ← 候选隐藏状态

h_t  = (1 - z_t) ⊙ h_(t-1) + z_t ⊙ h̃_t                       ← 新隐藏状态（唯一的状态）
```

- **重置门 `r_t`**：算候选值 `h̃_t` 时，先决定「参考多少旧记忆」——`r_t` 接近 0 时，
  `h̃_t` 几乎完全基于当前输入 `x_t`，相当于「暂时忽略过去，重新看待这一步」
- **更新门 `z_t`**：在「保留旧记忆」和「采用新候选值」之间做**加权平均**——`z_t` 接近
  0 时 `h_t ≈ h_(t-1)`（几乎不变），`z_t` 接近 1 时 `h_t ≈ h̃_t`（几乎完全替换成新值）

## 4. 手把手：一步步展开计算

用和 `LSTM.md` 同样规模的最小例子（1 维输入、1 维隐藏状态）：

```
W_z,h=0.8  W_z,x=0.4  b_z=-0.1
W_r,h=0.6  W_r,x=0.3  b_r= 0.2
W_h,h=0.5  W_h,x=0.9  b_h= 0.0

h_0 = 0
输入序列 x = 0.8, -0.5, 0.6, …
```

**t = 1（x₁ = 0.8，h₀ = 0）**

```
z₁ = σ(0.8×0 + 0.4×0.8 - 0.1) = σ(0.220)  ≈ 0.5548
r₁ = σ(0.6×0 + 0.3×0.8 + 0.2) = σ(0.440)  ≈ 0.6083
h̃₁ = tanh(0.9×0.8 + 0.5×(r₁×0)) = tanh(0.720) ≈ 0.6169
h₁ = (1-0.5548)×0 + 0.5548×0.6169 ≈ 0.3422
```

**t = 2（x₂ = -0.5，h₁ = 0.3422）**

```
z₂ = σ(0.8×0.3422 + 0.4×(-0.5) - 0.1) ≈ σ(-0.0263) ≈ 0.4935
r₂ = σ(0.6×0.3422 + 0.3×(-0.5) + 0.2) ≈ σ(0.2553)  ≈ 0.5635
h̃₂ = tanh(0.9×(-0.5) + 0.5×(r₂×0.3422)) ≈ tanh(-0.3536) ≈ -0.3395
h₂ = (1-0.4935)×0.3422 + 0.4935×(-0.3395) ≈ 0.0058
```

**t = 3（x₃ = 0.6，h₂ = 0.0058）**

```
z₃ ≈ σ(0.8×0.0058 + 0.4×0.6 - 0.1) ≈ σ(0.1447) ≈ 0.5361
r₃ ≈ σ(0.6×0.0058 + 0.3×0.6 + 0.2) ≈ σ(0.3835) ≈ 0.5947
h̃₃ ≈ tanh(0.9×0.6 + 0.5×(r₃×0.0058)) ≈ tanh(0.5417) ≈ 0.4943
h₃ ≈ (1-0.5361)×0.0058 + 0.5361×0.4943 ≈ 0.2677
```

汇总成表：

| t | x_t | z_t | r_t | h̃_t | h_t |
|---|---|---|---|---|---|
| 1 | 0.8 | 0.5548 | 0.6083 | 0.6169 | 0.3422 |
| 2 | -0.5 | 0.4935 | 0.5635 | -0.3395 | 0.0058 |
| 3 | 0.6 | 0.5361 | 0.5947 | 0.4943 | 0.2677 |

对照 `h_t` 这一列的算法：**它永远是 `h_(t-1)` 和 `h̃_t` 的一个加权平均**（权重
`1-z_t` 和 `z_t` 加起来正好是 1），这比 LSTM 里 `c_t = f_t⊙c_(t-1) + i_t⊙g_t`（`f_t`、
`i_t` 各自独立、加起来可以是任意值）更「保守」——数值天然被约束在旧值和候选值之间，
不会因为两个门同时偏大而出现意外的数值膨胀。

## 5. 动态演示

▶ [点击查看动态演示](./GRU动态演示.html)：实时显示重置门、更新门的强度条，以及隐藏
状态 `h_t` 是如何在「旧值」和「候选值」之间被更新门拉扯、插值出来的。

## 6. GRU 和 LSTM 到底有什么区别

| 维度 | LSTM | GRU |
|---|---|---|
| 门的数量 | 3 个：遗忘门 `f_t`、输入门 `i_t`、输出门 `o_t` | 2 个：更新门 `z_t`、重置门 `r_t` |
| 记忆载体 | 两条线：细胞状态 `c_t` + 隐藏状态 `h_t` | 一条线：只有隐藏状态 `h_t`（合并了 `c_t` 和 `h_t`） |
| 「忘」和「写」的关系 | 独立控制：`f_t`、`i_t` 各自算，理论上可以同时接近 1 或同时接近 0 | 耦合成一个门：`(1-z_t)` 和 `z_t` 加起来恒等于 1，此消彼长 |
| 是否有输出门 | 有：`h_t = o_t ⊙ tanh(c_t)`，`c_t` 里的信息可以选择性地不完全暴露给 `h_t` | 没有：`h_t` 就是完整状态，直接对外暴露，没有额外的「读取」控制 |
| 参数量 | 4 组权重（`f`、`i`、`g`、`o`），更多 | 3 组权重（`z`、`r`、候选值），大约是 LSTM 的 3/4 |
| 计算/训练开销 | 更大（矩阵运算更多、需要维护两个状态） | 更小、更快，尤其在参数量和显存受限、数据量不算特别大的场景下更划算 |
| 解决梯度消失的机制 | `∂c_t/∂c_(t-1) = f_t`，`f_t` 学到接近 1 时形成「常量误差传送带」 | `∂h_t/∂h_(t-1)` 里主导项含 `(1-z_t)`，`z_t` 学到接近 0 时同样让梯度几乎不衰减地传过去，原理一致，只是载体从 `c_t` 换成了 `h_t` 本身 |
| 实践表现 | 在需要精细控制「记住什么、忘记什么、暴露什么」的任务上更灵活 | 参数更少、更不容易过拟合，很多任务上和 LSTM 效果相当甚至更好，是常见的「先试试」选项 |

**一句话总结**：GRU 可以看成是 LSTM 的一个「精简重组」——把独立的细胞状态并回隐藏
状态，把遗忘门和输入门强制耦合成一个和为 1 的更新门，去掉了输出门。参数更少、结构
更简单，但保留了 LSTM「用门控代替固定衰减因子」这个解决梯度消失问题的核心思路。

## 7. Java 实现示例

```java
public class SimpleGRU {

    // 每个门各自一套参数：Wx (对输入 x_t)、Wh (对隐藏状态 h_(t-1))、b (偏置)
    private final double[][] wzx, wzh; private final double[] bz; // 更新门
    private final double[][] wrx, wrh; private final double[] br; // 重置门
    private final double[][] whx, whh; private final double[] bh; // 候选隐藏状态

    public SimpleGRU(double[][] wzx, double[][] wzh, double[] bz,
                      double[][] wrx, double[][] wrh, double[] br,
                      double[][] whx, double[][] whh, double[] bh) {
        this.wzx = wzx; this.wzh = wzh; this.bz = bz;
        this.wrx = wrx; this.wrh = wrh; this.br = br;
        this.whx = whx; this.whh = whh; this.bh = bh;
    }

    private static double sigmoid(double x) { return 1.0 / (1.0 + Math.exp(-x)); }

    /** 对一个输入序列做一次完整的前向传播，返回每一步的隐藏状态 */
    public double[][] forward(double[][] inputs) {
        int hiddenSize = bz.length;
        double[] h = new double[hiddenSize]; // h_0 = 0（GRU 没有单独的 c_0）
        double[][] outputs = new double[inputs.length][];

        for (int t = 0; t < inputs.length; t++) {
            double[] xt = inputs[t];
            double[] z = new double[hiddenSize];
            double[] r = new double[hiddenSize];
            for (int k = 0; k < hiddenSize; k++) {
                z[k] = sigmoid(dot(wzx[k], xt) + dot(wzh[k], h) + bz[k]);
                r[k] = sigmoid(dot(wrx[k], xt) + dot(wrh[k], h) + br[k]);
            }

            double[] rh = new double[hiddenSize]; // r_t ⊙ h_(t-1)：重置门先作用在旧状态上
            for (int k = 0; k < hiddenSize; k++) rh[k] = r[k] * h[k];

            double[] hCandidate = new double[hiddenSize];
            for (int k = 0; k < hiddenSize; k++) {
                hCandidate[k] = Math.tanh(dot(whx[k], xt) + dot(whh[k], rh) + bh[k]);
            }

            double[] hNext = new double[hiddenSize];
            for (int k = 0; k < hiddenSize; k++) {
                // 新旧状态的加权平均：z_t 越大，越偏向候选值
                hNext[k] = (1 - z[k]) * h[k] + z[k] * hCandidate[k];
            }
            h = hNext;
            outputs[t] = h;
        }
        return outputs;
    }

    private static double dot(double[] w, double[] v) {
        double sum = 0;
        for (int j = 0; j < v.length; j++) sum += w[j] * v[j];
        return sum;
    }
}
```

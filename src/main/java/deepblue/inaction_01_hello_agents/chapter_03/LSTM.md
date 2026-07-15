# 长短期记忆网络（Long Short-Term Memory, LSTM）

## 1. 为什么需要 LSTM

`RNN.md` 第 6 节推导过：普通 RNN 的隐藏状态更新是 `h_t = tanh(W_hh·h_(t-1) + …)`，
反向传播时梯度要沿时间连乘 `tanh'(z_t)·W_hh`——这是一个**每一步都固定要执行、大小
不受内容控制的乘法**。只要这个乘积长期小于 1，梯度就会指数级衰减（梯度消失）；只要
长期大于 1，就会指数级增长（梯度爆炸）。网络没有办法「选择性地」决定某段记忆该不该
保留，衰减/放大是无差别地施加在每一步上的。

LSTM（1997 年提出）的核心想法是：**把「记忆该保留多少」这件事，从一个固定不变的
乘法因子，变成一个网络自己学出来、每一步都可能不同的「门」（gate）**。它引入了一条
额外的、几乎不经过非线性挤压的「细胞状态（cell state）」通道，让信息可以几乎原样地
在时间上传递很多步，同时用三个门来控制「读、写、忘」——这也是本文件名字里「长短期」
的含义：既能记住短期的新信息，也能有选择地把某些信息带到很长的时间之后。

## 2. 核心结构：三个门 + 一条「细胞状态高速公路」

<svg viewBox="0 0 640 340" width="100%" style="max-width:640px" role="img" aria-label="LSTM 单元内部结构：细胞状态沿顶部高速公路传递，遗忘门、输入门、候选值、输出门从下方的 h 与 x 计算得到">
  <defs>
    <marker id="arrLSTM" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
      <path d="M0,0 L6,3 L0,6 Z" fill="currentColor" opacity="0.75"/>
    </marker>
  </defs>

  <!-- 细胞状态高速公路 -->
  <text x="10" y="45" font-size="12" fill="currentColor" opacity="0.6">c_(t-1)</text>
  <line x1="55" y1="50" x2="160" y2="50" stroke="currentColor" stroke-width="3" marker-end="url(#arrLSTM)" opacity="0.85"/>
  <circle cx="175" cy="50" r="15" fill="none" stroke="currentColor" stroke-width="2"/>
  <text x="175" y="55" font-size="14" fill="currentColor" text-anchor="middle">×</text>
  <line x1="190" y1="50" x2="405" y2="50" stroke="currentColor" stroke-width="3" marker-end="url(#arrLSTM)" opacity="0.85"/>
  <circle cx="420" cy="50" r="15" fill="none" stroke="currentColor" stroke-width="2"/>
  <text x="420" y="55" font-size="14" fill="currentColor" text-anchor="middle">+</text>
  <line x1="435" y1="50" x2="600" y2="50" stroke="currentColor" stroke-width="3" marker-end="url(#arrLSTM)" opacity="0.85"/>
  <text x="606" y="45" font-size="12" fill="currentColor" opacity="0.6">c_t</text>
  <text x="300" y="30" font-size="11" fill="currentColor" opacity="0.55" text-anchor="middle">细胞状态：几乎只有「×」和「+」，不经过 tanh 压缩</text>

  <!-- 遗忘门支路 -->
  <line x1="175" y1="65" x2="175" y2="175" stroke="#3b82f6" stroke-width="2" marker-end="url(#arrLSTM)"/>
  <rect x="130" y="175" width="90" height="42" rx="9" fill="none" stroke="#3b82f6" stroke-width="2.5"/>
  <text x="175" y="199" font-size="13" fill="#3b82f6" text-anchor="middle" font-weight="600">f_t  (σ)</text>
  <text x="175" y="230" font-size="11" fill="#3b82f6" text-anchor="middle" opacity="0.85">遗忘门：决定 c_(t-1) 保留多少</text>

  <!-- 候选值 g_t -->
  <line x1="380" y1="130" x2="420" y2="65" stroke="#8b5cf6" stroke-width="2" marker-end="url(#arrLSTM)"/>
  <rect x="335" y="175" width="90" height="42" rx="9" fill="none" stroke="#8b5cf6" stroke-width="2.5"/>
  <text x="380" y="199" font-size="13" fill="#8b5cf6" text-anchor="middle" font-weight="600">g_t  (tanh)</text>
  <text x="380" y="230" font-size="11" fill="#8b5cf6" text-anchor="middle" opacity="0.85">候选新信息</text>

  <!-- 输入门 i_t（与 g_t 相乘后再并入 + 节点） -->
  <circle cx="380" cy="130" r="13" fill="none" stroke="currentColor" stroke-width="2"/>
  <text x="380" y="135" font-size="13" fill="currentColor" text-anchor="middle">×</text>
  <line x1="460" y1="175" x2="392" y2="138" stroke="#10b981" stroke-width="2" marker-end="url(#arrLSTM)"/>
  <rect x="460" y="175" width="90" height="42" rx="9" fill="none" stroke="#10b981" stroke-width="2.5"/>
  <text x="505" y="199" font-size="13" fill="#10b981" text-anchor="middle" font-weight="600">i_t  (σ)</text>
  <text x="505" y="230" font-size="11" fill="#10b981" text-anchor="middle" opacity="0.85">输入门：决定新信息写入多少</text>

  <!-- 输出门 + tanh(c_t) → h_t -->
  <line x1="500" y1="60" x2="500" y2="110" stroke="currentColor" stroke-width="2" marker-end="url(#arrLSTM)" opacity="0.7"/>
  <rect x="470" y="110" width="60" height="34" rx="8" fill="none" stroke="currentColor" stroke-width="2" opacity="0.8"/>
  <text x="500" y="132" font-size="12" fill="currentColor" text-anchor="middle" opacity="0.85">tanh(c_t)</text>
  <circle cx="500" cy="170" r="13" fill="none" stroke="currentColor" stroke-width="2"/>
  <text x="500" y="175" font-size="13" fill="currentColor" text-anchor="middle">×</text>
  <line x1="500" y1="144" x2="500" y2="158" stroke="currentColor" stroke-width="2" marker-end="url(#arrLSTM)" opacity="0.7"/>
  <line x1="560" y1="217" x2="510" y2="178" stroke="#f59e0b" stroke-width="2" marker-end="url(#arrLSTM)"/>
  <rect x="560" y="175" width="70" height="42" rx="9" fill="none" stroke="#f59e0b" stroke-width="2.5"/>
  <text x="595" y="199" font-size="13" fill="#f59e0b" text-anchor="middle" font-weight="600">o_t (σ)</text>
  <text x="595" y="230" font-size="11" fill="#f59e0b" text-anchor="middle" opacity="0.85">输出门</text>

  <line x1="500" y1="183" x2="500" y2="260" stroke="currentColor" stroke-width="2.5" marker-end="url(#arrLSTM)"/>
  <text x="520" y="255" font-size="13" fill="currentColor" font-weight="600">h_t</text>
  <text x="520" y="272" font-size="11" fill="currentColor" opacity="0.6">→ 输出，也送回下一步</text>

  <!-- 输入总线：h_(t-1) 与 x_t -->
  <line x1="90" y1="300" x2="600" y2="300" stroke="currentColor" stroke-width="1.6" opacity="0.5"/>
  <text x="40" y="305" font-size="12" fill="currentColor" opacity="0.65">h_(t-1) ⊕ x_t</text>
  <line x1="175" y1="300" x2="175" y2="217" stroke="currentColor" stroke-width="1.4" marker-end="url(#arrLSTM)" opacity="0.55"/>
  <line x1="380" y1="300" x2="380" y2="217" stroke="currentColor" stroke-width="1.4" marker-end="url(#arrLSTM)" opacity="0.55"/>
  <line x1="505" y1="300" x2="505" y2="217" stroke="currentColor" stroke-width="1.4" marker-end="url(#arrLSTM)" opacity="0.55"/>
  <line x1="595" y1="300" x2="595" y2="217" stroke="currentColor" stroke-width="1.4" marker-end="url(#arrLSTM)" opacity="0.55"/>
</svg>

看图的关键：**顶部那条细胞状态「高速公路」上只有「×」和「+」两种运算，没有一次
`tanh` 压缩**。四个门（`f_t`、`i_t`、`g_t`、`o_t`）全部由下方同一组输入
`[h_(t-1), x_t]` 算出，然后分别去「调节」这条高速公路：遗忘门决定旧记忆留多少，
输入门和候选值一起决定写入多少新信息，输出门决定这一步要「读出」多少记忆作为 `h_t`。

## 3. 核心公式

```
f_t = σ( W_f,h·h_(t-1) + W_f,x·x_t + b_f )     ← 遗忘门：c_(t-1) 保留多少
i_t = σ( W_i,h·h_(t-1) + W_i,x·x_t + b_i )     ← 输入门：新信息写入多少
g_t = tanh( W_g,h·h_(t-1) + W_g,x·x_t + b_g )  ← 候选值：这一步「新看到」的信息
o_t = σ( W_o,h·h_(t-1) + W_o,x·x_t + b_o )     ← 输出门：这一步读出多少记忆

c_t = f_t ⊙ c_(t-1) + i_t ⊙ g_t                ← 细胞状态更新（唯一的「记忆」通道）
h_t = o_t ⊙ tanh(c_t)                           ← 隐藏状态（这一步对外的输出）
```

- **σ（sigmoid）**：把值压到 `(0, 1)`，天然适合当「开关强度」——0 表示完全关闭，
  1 表示完全打开
- **⊙**：逐元素相乘（Hadamard 积），不是矩阵乘法
- 四个门的公式**长得和普通 RNN 的隐藏状态更新一模一样**（线性组合 + 一个非线性）——
  LSTM 真正的创新只有一步：**`c_t = f_t ⊙ c_(t-1) + i_t ⊙ g_t` 这个「用加法而不是
  完全替换」的更新方式**

## 4. 手把手：一步步展开计算

用一个 1 维输入、1 维隐藏/细胞状态的最小例子（和动态演示同一套参数）：

```
W_f,h=0.8  W_f,x=0.5  b_f= 0.1
W_i,h=0.6  W_i,x=0.4  b_i=-0.2
W_g,h=0.5  W_g,x=0.9  b_g= 0.0
W_o,h=0.7  W_o,x=0.3  b_o= 0.1

h_0 = 0, c_0 = 0
输入序列 x = 0.8, -0.5, 0.6, …
```

**t = 1（x₁ = 0.8，h₀ = 0，c₀ = 0）**

```
f₁ = σ(0.8×0 + 0.5×0.8 + 0.1)  = σ(0.500)  ≈ 0.6225
i₁ = σ(0.6×0 + 0.4×0.8 - 0.2)  = σ(0.120)  ≈ 0.5300
g₁ = tanh(0.5×0 + 0.9×0.8 + 0) = tanh(0.720) ≈ 0.6169
c₁ = f₁×c₀ + i₁×g₁ = 0.6225×0 + 0.5300×0.6169 ≈ 0.3269
o₁ = σ(0.7×0 + 0.3×0.8 + 0.1)  = σ(0.340)  ≈ 0.5842
h₁ = o₁ × tanh(c₁) = 0.5842 × tanh(0.3269) ≈ 0.1845
```

**t = 2（x₂ = -0.5，h₁ = 0.1845，c₁ = 0.3269）**

```
f₂ = σ(0.8×0.1845 + 0.5×(-0.5) + 0.1)  ≈ σ(-0.0024) ≈ 0.4994
i₂ = σ(0.6×0.1845 + 0.4×(-0.5) - 0.2)  ≈ σ(-0.2893) ≈ 0.4282
g₂ = tanh(0.5×0.1845 + 0.9×(-0.5))     ≈ tanh(-0.3578) ≈ -0.3432
c₂ = 0.4994×0.3269 + 0.4282×(-0.3432)  ≈ 0.0163
o₂ = σ(0.7×0.1845 + 0.3×(-0.5) + 0.1)  ≈ σ(0.0792)  ≈ 0.5198
h₂ = 0.5198 × tanh(0.0163) ≈ 0.0085
```

**t = 3（x₃ = 0.6，h₂ = 0.0085，c₂ = 0.0163）**

```
f₃ = σ(0.8×0.0085 + 0.5×0.6 + 0.1) ≈ σ(0.4068) ≈ 0.6003
i₃ = σ(0.6×0.0085 + 0.4×0.6 - 0.2) ≈ σ(0.0451) ≈ 0.5113
g₃ = tanh(0.5×0.0085 + 0.9×0.6)    ≈ tanh(0.5442) ≈ 0.4962
c₃ = 0.6003×0.0163 + 0.5113×0.4962 ≈ 0.2635
o₃ = σ(0.7×0.0085 + 0.3×0.6 + 0.1) ≈ σ(0.2859) ≈ 0.5710
h₃ = 0.5710 × tanh(0.2635) ≈ 0.1471
```

汇总成表：

| t | x_t | f_t | i_t | g_t | c_t | o_t | h_t |
|---|---|---|---|---|---|---|---|
| 1 | 0.8 | 0.6225 | 0.5300 | 0.6169 | 0.3269 | 0.5842 | 0.1845 |
| 2 | -0.5 | 0.4994 | 0.4282 | -0.3432 | 0.0163 | 0.5198 | 0.0085 |
| 3 | 0.6 | 0.6003 | 0.5113 | 0.4962 | 0.2635 | 0.5710 | 0.1471 |

注意 `c_t` 这一列：它由「旧的 `c_(t-1)` 打了一个 `f_t` 折扣」加上「新的 `i_t·g_t`」两
部分**相加**得到，而不是像普通 RNN 那样把新旧信息一起塞进同一个 `tanh` 里挤压——这正
是第 6 节要讲的「为什么梯度不容易消失」的关键。

## 5. 动态演示

下面的动态图把细胞状态 `c_t` 和隐藏状态 `h_t` 的更新过程「跑」了起来：四个门实时
计算并以不同颜色的柱状条显示强度（`0~1`），细胞状态沿着顶部「高速公路」被遗忘门和
输入门共同调节，可以直观看到它是「缩放 + 累加」而不是「重新压缩」。

▶ [点击查看动态演示](./LSTM动态演示.html)

## 6. LSTM 是如何解决梯度消失/爆炸的

回到 `RNN.md` 第 6.1 节的推导：普通 RNN 里 `∂h_T/∂h_k` 是一串
`tanh'(z_t)·W_hh` 的连乘，**每一步都强制乘一个和内容无关、通常小于 1 的固定
因子**，这是梯度消失的根源。

LSTM 的细胞状态更新是：

```
c_t = f_t ⊙ c_(t-1) + i_t ⊙ g_t
```

对 `c_(t-1)` 求偏导，主导项是：

```
∂c_t/∂c_(t-1) = f_t
```

这一步和普通 RNN 的关键区别有两个：

1. **这是加法更新，不是把新旧信息一起塞进同一个 `tanh` 里重新压缩**——只要
   `i_t ⊙ g_t` 这一项不产生梯度爆炸式的干扰，`c_(t-1)` 的梯度就能几乎原封不动地
   通过「×f_t」这一条路径传下去。
2. **`f_t` 不是固定值，而是网络在每个时间步、每个维度上自己学出来的一个 `(0,1)`
   之间的数**。如果某段序列里的某些信息需要被长期记住，网络完全可以把对应的
   `f_t` 学到接近 `1`（sigmoid 在输入较大时会饱和到接近 1）——这时 `c_t ≈ c_(t-1)`，
   梯度反传时几乎不衰减地传过这一步。这被称为「常量误差传送带」（Constant Error
   Carousel）。

把这个结论和 `RNN.md` 里 `(0.45)^n` 的衰减表放在一起对比就很直观：

| 间隔步数 n | 普通 RNN：(0.45)ⁿ（固定衰减） | LSTM：若学到 f_t≈0.95，(0.95)ⁿ |
|---|---|---|
| 5 | 0.0185 | 0.774 |
| 10 | 0.00034 | 0.599 |
| 20 | ≈0.00000012 | 0.358 |
| 50 | ≈0 | 0.077 |

普通 RNN 的衰减因子是**写死的**（由共享参数 `W_hh` 和 `tanh'` 决定，对所有内容一视
同仁）；LSTM 的 `f_t` 是**学出来的、按需调节的**——需要长期记住的信息，网络就学会
让对应的遗忘门接近 1，其余不重要的信息则可以让遗忘门接近 0 迅速清空。这就是 LSTM
能捕捉更长距离依赖的根本原因。

（补充一句：LSTM 并没有完全消灭梯度爆炸的可能——`i_t ⊙ g_t` 这条支路仍然可能在某些
情况下让数值变大，所以工程上训练 LSTM 时依然常常搭配梯度裁剪。但由于所有门都被
sigmoid/tanh 限制在有界区间内，比起普通 RNN 里 `W_hh` 可以有任意大的特征值，LSTM
的数值状态本身要稳定得多。）

## 7. Java 实现示例

```java
public class SimpleLSTM {

    // 每个门各自一套参数：Wx (对输入 x_t)、Wh (对隐藏状态 h_(t-1))、b (偏置)
    private final double[][] wfx, wfh; private final double[] bf; // 遗忘门
    private final double[][] wix, wih; private final double[] bi; // 输入门
    private final double[][] wgx, wgh; private final double[] bg; // 候选值
    private final double[][] wox, woh; private final double[] bo; // 输出门

    public SimpleLSTM(double[][] wfx, double[][] wfh, double[] bf,
                       double[][] wix, double[][] wih, double[] bi,
                       double[][] wgx, double[][] wgh, double[] bg,
                       double[][] wox, double[][] woh, double[] bo) {
        this.wfx = wfx; this.wfh = wfh; this.bf = bf;
        this.wix = wix; this.wih = wih; this.bi = bi;
        this.wgx = wgx; this.wgh = wgh; this.bg = bg;
        this.wox = wox; this.woh = woh; this.bo = bo;
    }

    private static double sigmoid(double x) { return 1.0 / (1.0 + Math.exp(-x)); }

    /** 对一个输入序列做一次完整的前向传播，返回每一步的隐藏状态 */
    public double[][] forward(double[][] inputs) {
        int hiddenSize = bf.length;
        double[] h = new double[hiddenSize]; // h_0 = 0
        double[] c = new double[hiddenSize]; // c_0 = 0：细胞状态初始为 0
        double[][] outputs = new double[inputs.length][];

        for (int t = 0; t < inputs.length; t++) {
            double[] xt = inputs[t];
            double[] f = new double[hiddenSize];
            double[] i = new double[hiddenSize];
            double[] g = new double[hiddenSize];
            double[] o = new double[hiddenSize];

            for (int k = 0; k < hiddenSize; k++) {
                f[k] = sigmoid(gateSum(wfx[k], wfh[k], xt, h, bf[k]));
                i[k] = sigmoid(gateSum(wix[k], wih[k], xt, h, bi[k]));
                g[k] = Math.tanh(gateSum(wgx[k], wgh[k], xt, h, bg[k]));
                o[k] = sigmoid(gateSum(wox[k], woh[k], xt, h, bo[k]));
            }

            double[] cNext = new double[hiddenSize];
            double[] hNext = new double[hiddenSize];
            for (int k = 0; k < hiddenSize; k++) {
                cNext[k] = f[k] * c[k] + i[k] * g[k]; // 细胞状态：遗忘旧的 + 写入新的
                hNext[k] = o[k] * Math.tanh(cNext[k]); // 隐藏状态：读出多少记忆
            }
            c = cNext;
            h = hNext;
            outputs[t] = h;
        }
        return outputs;
    }

    /** 计算 Wx·x_t + Wh·h_(t-1) + b，对应某一个门在某一维度上的原始值 */
    private static double gateSum(double[] wxRow, double[] whRow, double[] xt, double[] h, double b) {
        double sum = b;
        for (int j = 0; j < xt.length; j++) sum += wxRow[j] * xt[j];
        for (int j = 0; j < h.length; j++) sum += whRow[j] * h[j];
        return sum;
    }
}
```

### 7.1 运行结果示例

执行 `SimpleLSTM.java` 里的 `main` 方法（`javac SimpleLSTM.java && java
deepblue.inaction_01_hello_agents.chapter_03.SimpleLSTM`），复现 §4 手算例子，实
际输出如下：

```text
=== §4 手算例子对照（期望 h1≈0.1845, h2≈0.0085, h3≈0.1471）===
t=1  x_t= 0.80  f_t=0.6225  i_t=0.5300  g_t=0.6169  c_t=0.3269  o_t=0.5842  h_t=0.1845
t=2  x_t=-0.50  f_t=0.4994  i_t=0.4282  g_t=-0.3432  c_t=0.0163  o_t=0.5198  h_t=0.0085
t=3  x_t= 0.60  f_t=0.6003  i_t=0.5113  g_t=0.4962  c_t=0.2635  o_t=0.5710  h_t=0.1471
forward() 方法返回的 h 序列: [[0.1844688687745804], [0.008473870209834512], [0.14705629126344294]]
```

每一步打印的 `f_t、i_t、g_t、c_t、o_t、h_t` 和 §4 手算表格逐列对应、数字一致；最
后一行调用封装好的 `forward()` 方法，验证它和手动展开的门控计算结果完全吻合（只是
浮点数精度更高，没有手算时的四舍五入误差）。

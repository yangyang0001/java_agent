# Transformer

## 1. 为什么会有 Transformer：RNN / LSTM / GRU 走到头的地方

`RNN.md`、`LSTM.md`、`GRU.md` 都在解决同一件事：**怎么把历史信息压缩进一个固定大小
的隐藏状态 `h_t`，一步一步往后传**。门控机制（`LSTM` 的三个门、`GRU` 的两个门）让这
条传递链不那么容易梯度消失，但改不掉两个更根本的问题：

1. **必须串行计算**：算 `h_t` 前必须先算出 `h_(t-1)`，哪怕换上再快的 GPU，这条链也
   只能一步一步往前挪，序列一长，训练和推理都慢。
2. **长距离依赖的"路径长度"太长**：信息从第 1 个词传到第 100 个词，要经过 99 次
   "压缩再解压"（99 次矩阵变换）。门控只是让梯度不那么容易消失，并不能保证信息本身
   在传递 99 次之后还保持原样——本质上像一根传话筒，传得越远，失真的风险越大。

2017 年论文《Attention Is All You Need》提出 Transformer，核心想法只有一句话：
**放弃递归，让序列里任意两个位置直接"对话"**——不管两个词隔多远，建立联系只需要一
次注意力计算，路径长度恒为 `O(1)`；同时因为不再是"必须等上一步"的串行依赖，整条序
列可以一次性并行算完。

## 2. 核心组件总览

一个标准 Transformer（Encoder-Decoder 结构）由下面这些组件搭出来，后面逐个讲：

| 组件 | 一句话作用 |
|---|---|
| Token Embedding | 把词/字变成向量 |
| 位置编码（Positional Encoding） | 给向量注入"顺序"信息（Attention 本身不知道谁在前谁在后） |
| Scaled Dot-Product Attention | 让每个 token 直接"查阅"序列里所有 token，按相关度加权取值——整个架构的核心 |
| Multi-Head Attention | 把上面的注意力算 `h` 份，从不同角度分别关注，再拼起来 |
| Masked Self-Attention | Decoder 里用的自注意力，加了"只能看过去、不能看未来"的遮罩 |
| Cross-Attention | Decoder 用来"查阅" Encoder 输出的注意力，是 Encoder 和 Decoder 之间唯一的信息通道 |
| Feed-Forward Network（FFN） | 对每个位置单独做一次非线性变换，相当于"消化"一下刚查到的信息 |
| 残差连接 + LayerNorm（Add & Norm） | 让深层网络训练稳定，同时充当组件之间的"公共通信总线" |
| 输出层（Linear + Softmax） | 把最终向量映射成词表上的概率分布 |

## 3. 逐个组件讲解

### 3.1 输入表示：Token Embedding + 位置编码

Token Embedding 把离散的词/字 id 查表映射成一个 `d_model` 维向量，这一步和 RNN 里
一样，没什么特别的。

特别的是**位置编码**：Attention 是"任意两个位置直接算相关度"，天然不知道谁在前
谁在后（打乱输入顺序，算出来的注意力分数集合是一样的）。RNN 靠"一步步递归"天然带
着顺序信息，Transformer 必须**显式把顺序信息加进向量里**。原论文用的是正弦位置编
码：

```
PE(pos, 2i)   = sin( pos / 10000^(2i/d_model) )
PE(pos, 2i+1) = cos( pos / 10000^(2i/d_model) )

最终输入 = TokenEmbedding(x_pos) + PE(pos)
```

不同维度用不同频率的 sin/cos，越靠前的维度振荡越快（对邻近位置敏感），越靠后的维
度振荡越慢（对远距离位置敏感），这样每个位置都能拿到一个独一无二、且能通过简单线性
变换互相推算相对距离的编码。

### 3.2 Scaled Dot-Product Attention（最核心的组件）

把每个 token 的向量投影成三份：

```
Q = X·W_Q   （Query，"我想问什么"）
K = X·W_K   （Key，  "我能被问到什么"）
V = X·W_V   （Value，"如果被问到，我实际给出的内容"）

Attention(Q, K, V) = softmax( Q·Kᵀ / √d_k ) · V
```

可以类比成一次"检索"：拿自己的 `Query` 去跟序列里每个 token 的 `Key` 做点积，点积
越大代表"越相关"；用 `softmax` 把相关度归一化成一组和为 1 的权重；最后按这组权重
对所有 token 的 `Value` 做加权求和，得到"融合了全序列相关信息"的新向量。除以
`√d_k` 只是为了防止点积数值过大把 `softmax` 推进梯度接近 0 的饱和区。

<svg viewBox="0 0 660 240" width="100%" style="max-width:660px" role="img" aria-label="Scaled Dot-Product Attention 数据流：Q和K做矩阵乘法得到相关度分数，缩放后可选地加掩码，再做softmax归一化成权重，最后与V做矩阵乘法得到输出">
  <defs>
    <marker id="arrATT" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
      <path d="M0,0 L6,3 L0,6 Z" fill="currentColor" opacity="0.8"/>
    </marker>
  </defs>

  <rect x="15" y="20" width="70" height="34" rx="7" fill="none" stroke="#3b82f6" stroke-width="2.2"/>
  <text x="50" y="42" font-size="13" fill="#3b82f6" text-anchor="middle" font-weight="600">Q</text>
  <rect x="15" y="70" width="70" height="34" rx="7" fill="none" stroke="#8b5cf6" stroke-width="2.2"/>
  <text x="50" y="92" font-size="13" fill="#8b5cf6" text-anchor="middle" font-weight="600">K</text>
  <rect x="15" y="170" width="70" height="34" rx="7" fill="none" stroke="#10b981" stroke-width="2.2"/>
  <text x="50" y="192" font-size="13" fill="#10b981" text-anchor="middle" font-weight="600">V</text>

  <line x1="85" y1="37" x2="150" y2="60" stroke="#3b82f6" stroke-width="2" marker-end="url(#arrATT)"/>
  <line x1="85" y1="87" x2="150" y2="65" stroke="#8b5cf6" stroke-width="2" marker-end="url(#arrATT)"/>
  <rect x="150" y="45" width="110" height="40" rx="8" fill="none" stroke="currentColor" stroke-width="2"/>
  <text x="205" y="70" font-size="12" fill="currentColor" text-anchor="middle">MatMul Q·Kᵀ</text>

  <line x1="260" y1="65" x2="310" y2="65" stroke="currentColor" stroke-width="2" marker-end="url(#arrATT)"/>
  <rect x="310" y="45" width="90" height="40" rx="8" fill="none" stroke="currentColor" stroke-width="2"/>
  <text x="355" y="70" font-size="12" fill="currentColor" text-anchor="middle">÷ √d_k</text>

  <line x1="355" y1="85" x2="355" y2="105" stroke="#f59e0b" stroke-width="1.6" stroke-dasharray="3,3" marker-end="url(#arrATT)"/>
  <rect x="300" y="105" width="110" height="34" rx="7" fill="none" stroke="#f59e0b" stroke-width="1.8" stroke-dasharray="4,3"/>
  <text x="355" y="126" font-size="11" fill="#f59e0b" text-anchor="middle">Mask（可选，Decoder用）</text>

  <line x1="400" y1="65" x2="450" y2="65" stroke="currentColor" stroke-width="2" marker-end="url(#arrATT)"/>
  <rect x="450" y="45" width="90" height="40" rx="8" fill="none" stroke="#f59e0b" stroke-width="2.2"/>
  <text x="495" y="70" font-size="12" fill="#f59e0b" text-anchor="middle" font-weight="600">Softmax</text>
  <text x="495" y="30" font-size="10" fill="currentColor" opacity="0.6" text-anchor="middle">→ 归一化成权重</text>

  <line x1="495" y1="85" x2="495" y2="150" stroke="currentColor" stroke-width="1.6" opacity="0.6"/>
  <line x1="85" y1="187" x2="470" y2="170" stroke="#10b981" stroke-width="2" marker-end="url(#arrATT)"/>
  <line x1="495" y1="150" x2="495" y2="170" stroke="currentColor" stroke-width="2" marker-end="url(#arrATT)"/>
  <rect x="450" y="170" width="90" height="40" rx="8" fill="none" stroke="currentColor" stroke-width="2"/>
  <text x="495" y="195" font-size="12" fill="currentColor" text-anchor="middle">MatMul ·V</text>

  <line x1="540" y1="190" x2="600" y2="190" stroke="currentColor" stroke-width="2.4" marker-end="url(#arrATT)"/>
  <text x="608" y="195" font-size="12" fill="currentColor" opacity="0.75">输出</text>
</svg>

### 3.3 Multi-Head Attention

只做一次注意力，只能学到"一种"相关性度量方式（比如只学会了"语法上的主谓关系"）。
Multi-Head 的做法是把 `d_model` 维平均切成 `h` 份，每一份独立做一次
Scaled Dot-Product Attention（各自有自己的 `W_Q、W_K、W_V`），相当于**同时用 h 个
不同的"角度"去关注序列**（有的头学会关注语法结构，有的头学会关注指代关系……），最
后把 `h` 个输出拼接起来，过一个线性层 `W_O` 融合到一起：

```
head_i = Attention(Q·W_Qi, K·W_Ki, V·W_Vi)     i = 1..h
MultiHead(Q,K,V) = Concat(head_1, ..., head_h) · W_O
```

### 3.4 Masked Self-Attention（Decoder 的自注意力）

Decoder 在生成第 `t` 个词时，不能"偷看"第 `t+1` 个及之后还没生成的词，否则训练和推
理就不一致了。做法很简单：在 `softmax` 之前，把分数矩阵里"未来位置"对应的元素加上
`-∞`（实现上通常是加一个很大的负数），`softmax` 之后这些位置的权重会变成 0，等效于
"这一步完全看不到未来"。这就是上面那张图里那个虚线 `Mask` 框的作用——Encoder 的自
注意力不需要它（可以看到整句话），只有 Decoder 的自注意力需要。

### 3.5 Cross-Attention（Encoder-Decoder Attention）

这是 Encoder 和 Decoder 之间**唯一的信息通道**：`Query` 来自 Decoder 当前的表示，
`Key` 和 `Value` 来自 Encoder 的最终输出。效果是"Decoder 每生成一个词，都可以直接
查阅整个输入序列，看哪些输入词跟当前要生成的内容最相关"（比如翻译时，生成到某个目
标词，就去查原文里对应的那个词）。

### 3.6 Position-wise Feed-Forward Network

拿到 Attention 融合完全序列信息之后的向量，FFN 对**每个位置独立**做一次非线性变
换（不同位置之间不再交换信息，纯粹是"消化"一下刚查到的东西）：

```
FFN(x) = max(0, x·W1 + b1)·W2 + b2      （或用 GELU 代替 ReLU）
```

先升维（比如 `d_model=512` 升到 `2048`）再降回来，给模型足够的非线性表达能力。如果
说 Attention 负责"token 之间交换信息"，FFN 就负责"每个 token 自己把信息想清楚"。

### 3.7 残差连接 + LayerNorm（Add & Norm）

每个子层（Multi-Head Attention 或 FFN）都不是直接替换输入，而是：

```
x = LayerNorm( x + Sublayer(x) )
```

**加回原值（残差连接）**保证了信息不会被某一层意外抹掉——最差情况下，某层什么都没
学到，输出也至少还保留原来的 `x`；这也是 Transformer 能堆到几十甚至上百层而不崩溃
的关键。**LayerNorm** 把每个位置的向量重新归一化，稳定数值分布，避免层数一深梯度
就跑飞。

### 3.8 输出层

Decoder 最后一层的输出过一个线性层映射到词表大小，再做一次 `softmax`，得到"下一个
词是词表里每个词"的概率分布——这一步和 RNN/LSTM/GRU 做语言模型时的输出层是一样的。

## 4. 整体架构

<svg viewBox="0 0 720 460" width="100%" style="max-width:720px" role="img" aria-label="Transformer整体架构：左侧Encoder堆叠自注意力和前馈网络，右侧Decoder堆叠遮罩自注意力、交叉注意力（读取Encoder输出）和前馈网络，每个子层都带残差连接与LayerNorm，最后经线性层和softmax输出">
  <defs>
    <marker id="arrARCH" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
      <path d="M0,0 L6,3 L0,6 Z" fill="currentColor" opacity="0.8"/>
    </marker>
  </defs>

  <text x="150" y="15" font-size="12" fill="currentColor" opacity="0.7" text-anchor="middle" font-weight="600">Encoder</text>
  <text x="530" y="15" font-size="12" fill="currentColor" opacity="0.7" text-anchor="middle" font-weight="600">Decoder</text>

  <!-- Encoder column -->
  <rect x="30" y="25" width="240" height="34" rx="7" fill="none" stroke="currentColor" stroke-width="1.8" opacity="0.8"/>
  <text x="150" y="47" font-size="11.5" text-anchor="middle" fill="currentColor">输入 Embedding + 位置编码</text>

  <line x1="150" y1="59" x2="150" y2="75" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="30" y="75" width="240" height="34" rx="7" fill="none" stroke="#3b82f6" stroke-width="2.2"/>
  <text x="150" y="97" font-size="11.5" text-anchor="middle" fill="#3b82f6" font-weight="600">Multi-Head Self-Attention</text>

  <line x1="150" y1="109" x2="150" y2="123" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="30" y="123" width="240" height="26" rx="6" fill="none" stroke="currentColor" stroke-width="1.4" opacity="0.65"/>
  <text x="150" y="141" font-size="10.5" text-anchor="middle" fill="currentColor" opacity="0.8">Add &amp; Norm（残差+归一化）</text>

  <line x1="150" y1="149" x2="150" y2="165" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="30" y="165" width="240" height="34" rx="7" fill="none" stroke="#8b5cf6" stroke-width="2.2"/>
  <text x="150" y="187" font-size="11.5" text-anchor="middle" fill="#8b5cf6" font-weight="600">Feed-Forward Network</text>

  <line x1="150" y1="199" x2="150" y2="213" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="30" y="213" width="240" height="26" rx="6" fill="none" stroke="currentColor" stroke-width="1.4" opacity="0.65"/>
  <text x="150" y="231" font-size="10.5" text-anchor="middle" fill="currentColor" opacity="0.8">Add &amp; Norm（残差+归一化）</text>

  <text x="150" y="255" font-size="11" text-anchor="middle" fill="currentColor" opacity="0.55">↑ 上面这一整块重复 ×N 层</text>
  <line x1="150" y1="239" x2="150" y2="275" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="30" y="275" width="240" height="30" rx="7" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <text x="150" y="295" font-size="11.5" text-anchor="middle" fill="currentColor">Encoder 输出（每个位置一个向量）</text>

  <!-- Decoder column -->
  <rect x="410" y="25" width="240" height="34" rx="7" fill="none" stroke="currentColor" stroke-width="1.8" opacity="0.8"/>
  <text x="530" y="47" font-size="11.5" text-anchor="middle" fill="currentColor">输出 Embedding + 位置编码</text>

  <line x1="530" y1="59" x2="530" y2="75" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="410" y="75" width="240" height="34" rx="7" fill="none" stroke="#3b82f6" stroke-width="2.2"/>
  <text x="530" y="97" font-size="11.5" text-anchor="middle" fill="#3b82f6" font-weight="600">Masked Multi-Head Self-Attention</text>

  <line x1="530" y1="109" x2="530" y2="123" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="410" y="123" width="240" height="26" rx="6" fill="none" stroke="currentColor" stroke-width="1.4" opacity="0.65"/>
  <text x="530" y="141" font-size="10.5" text-anchor="middle" fill="currentColor" opacity="0.8">Add &amp; Norm</text>

  <line x1="530" y1="149" x2="530" y2="163" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="410" y="163" width="240" height="34" rx="7" fill="none" stroke="#f59e0b" stroke-width="2.4"/>
  <text x="530" y="185" font-size="11.5" text-anchor="middle" fill="#f59e0b" font-weight="600">Cross-Attention（Q←Decoder,K/V←Encoder）</text>

  <line x1="530" y1="197" x2="530" y2="211" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="410" y="211" width="240" height="26" rx="6" fill="none" stroke="currentColor" stroke-width="1.4" opacity="0.65"/>
  <text x="530" y="229" font-size="10.5" text-anchor="middle" fill="currentColor" opacity="0.8">Add &amp; Norm</text>

  <line x1="530" y1="237" x2="530" y2="251" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="410" y="251" width="240" height="34" rx="7" fill="none" stroke="#8b5cf6" stroke-width="2.2"/>
  <text x="530" y="273" font-size="11.5" text-anchor="middle" fill="#8b5cf6" font-weight="600">Feed-Forward Network</text>

  <line x1="530" y1="285" x2="530" y2="299" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="410" y="299" width="240" height="26" rx="6" fill="none" stroke="currentColor" stroke-width="1.4" opacity="0.65"/>
  <text x="530" y="317" font-size="10.5" text-anchor="middle" fill="currentColor" opacity="0.8">Add &amp; Norm</text>

  <text x="530" y="341" font-size="11" text-anchor="middle" fill="currentColor" opacity="0.55">↑ 上面这一整块重复 ×N 层</text>
  <line x1="530" y1="325" x2="530" y2="361" stroke="currentColor" stroke-width="1.6" marker-end="url(#arrARCH)"/>
  <rect x="410" y="361" width="240" height="30" rx="7" fill="none" stroke="currentColor" stroke-width="1.8"/>
  <text x="530" y="381" font-size="11.5" text-anchor="middle" fill="currentColor">Linear + Softmax → 输出概率分布</text>

  <!-- cross attention bridge -->
  <path d="M270,290 C 340,290 340,180 405,180" fill="none" stroke="#f59e0b" stroke-width="2" stroke-dasharray="4,3" marker-end="url(#arrARCH)"/>
  <text x="345" y="230" font-size="10.5" fill="#f59e0b" text-anchor="middle">K, V（Encoder→Decoder 的唯一通道）</text>
</svg>

**小提示**：现在最常见的 LLM（GPT 系列、Llama 等）其实是 **Decoder-Only** 架构——
去掉了整个 Encoder，也去掉了 Cross-Attention，只留下"Masked Self-Attention +
FFN"反复堆叠。因为语言模型的任务就是"看着前面的词，预测下一个词"，用不到一个独立
的双向编码器；BERT 则反过来是 **Encoder-Only**（不需要生成，只需要理解，也不需要
Mask）。理解了上面这张完整图，这两种简化版就是"去掉一半"而已。

## 5. 组件之间如何"消息传递"

这是 Transformer 和 RNN 系列最本质的区别，可以拆成两个方向看：

**横向（同一层内，token 与 token 之间）**——通过 Attention 的 Q/K/V 机制。每个
token 同时向所有其他 token"广播"自己的 `Key` 和 `Value`，再用自己的 `Query` 去"问"
该重点听谁的，注意力权重决定"听取"多少。这相当于**一个全连接图（fully-connected
graph）上的一轮消息传递**：任意两个节点之间一步直达，不需要像 RNN 那样沿着一条链
一步步接力。序列长度为 `n`，一次 Attention 就完成了 `n×n` 次两两通信。

**纵向（层与层之间）**——靠"残差流"（residual stream）。每一层的 Attention 子层
和 FFN 子层做的事情，本质都是"**读取当前的残差流 → 算出一个增量 → 加回残差流**"
（`x = x + Sublayer(x)`），而不是把 `x` 整个覆盖替换掉。可以把残差流想象成一条贯穿
全部层的"公共总线"：底层学到的浅层特征（比如词性），中层学到的语法结构，高层学到
的语义关系，都是**累加**在同一条向量上，而不是被后面的层覆盖冲掉。这也解释了为什么
Transformer 可以放心堆到几十甚至上百层——RNN 的 `h_t` 每一步都是"整体替换"，层数
（这里对应"时间步数"）一深，早期信息很容易被后来的更新盖掉；Transformer 的残差流
是"叠加"，信息理论上更容易被完整保留下来，需要时任何一层都能把它读出来用。

**跨模块（Encoder 与 Decoder 之间）**——靠 Cross-Attention。这是全图里唯一一条把
Encoder 那一侧信息带到 Decoder 那一侧的线，如上面架构图里那条虚线箭头所示。

## 6. Transformer 如何"记忆"

这里要先纠正一个直觉：Transformer **没有** RNN/LSTM/GRU 那种"一个固定大小的向量，
随时间被反复更新覆盖"的记忆状态。它的"记忆"方式完全是另一套逻辑：

1. **不压缩、直接摆出来**：RNN 系列必须把 1~t-1 步的全部历史压缩进一个固定维度的
   `h_(t-1)`（或 `c_(t-1)`），这是一种**有损压缩记忆**——维度是固定的，序列一长，
   早期信息必然被挤占、被遗忘。Transformer 完全不压缩：只要还在上下文窗口内，序列
   里每一个 token 的表示都原样保留在那里，任何一层、任何位置都可以通过 Attention
   直接去"查"，不需要谁替谁转述。可以理解成从"电话传话"变成了"所有人的发言记录都
   摆在桌上，随时可以翻查"。
2. **位置编码替代"顺序记忆"**：RNN 靠递归的先后顺序天然记住"谁在前谁在后"，
   Transformer 靠 §3.1 里加进向量的位置编码来记住顺序，这是一种"记忆"从**过程性
   （怎么算出来的）**变成**显式编码（写在数据里）**的转变。
3. **推理阶段的 KV Cache 是"外部记忆"**：自回归生成时，每生成一个新 token，之前
   所有 token 的 `K`、`V` 会被缓存下来，不用重新计算——这在效果上确实很像"记住已经
   说过的话"，但它缓存的是**完整、未压缩**的键值对集合，不是一个被反复覆盖的固定
   向量，缓存会随生成长度线性增长，直到触及上下文窗口上限。
4. **代价**：这种"无损但有限窗口"的记忆不是没有代价的——Attention 要让每个 token
   都能查到所有 token，计算量和显存占用是 `O(n²)`（`n` 是序列长度），而 RNN 每步只
   需要 `O(1)` 的状态更新。这也是为什么"超长上下文"一直是 Transformer 的一个专门
   研究方向（稀疏注意力、滑动窗口、外部检索/RAG 等，本质都是在想办法用更低成本模拟
   "更长的记忆"）。

一句话对比：**RNN/LSTM/GRU 的记忆是"随时间压缩、会遗忘"；Transformer 的记忆是"当
下全量可见、不压缩，但窗口外直接清零"。**

## 7. 手把手：一步步展开计算

用一个最小例子（3 个 token，每个词向量维度 `d=2`），并且为了手算方便，取
`W_Q=W_K=I`（单位矩阵，即 `Q=K=X`），`W_V` 是一个"交换两个维度"的矩阵：

```
x1 = [1, 0]   x2 = [0, 1]   x3 = [1, 1]

Q = K = X（因为 W_Q = W_K = 单位矩阵）
V = X·W_V，W_V 交换两维 → v1=[0,1]  v2=[1,0]  v3=[1,1]

d_k = 2，缩放因子 √d_k ≈ 1.414
```

**关键点：下面 3 个 token 的输出是同时、独立算出来的，互不依赖彼此的计算顺序**——
这正是和 RNN 必须等 `h_(t-1)` 算完才能算 `h_t` 的本质区别。

**token 1（Q1=[1,0]）**

```
score(1,·) = [Q1·K1, Q1·K2, Q1·K3] = [1, 0, 1]
scaled     = [0.7071, 0, 0.7071]
softmax    ≈ [0.4011, 0.1978, 0.4011]
output1 = 0.4011·v1 + 0.1978·v2 + 0.4011·v3 ≈ [0.599, 0.802]
```

**token 2（Q2=[0,1]）**

```
score(2,·) = [0, 1, 1]
scaled     = [0, 0.7071, 0.7071]
softmax    ≈ [0.1978, 0.4011, 0.4011]
output2 ≈ [0.802, 0.599]
```

**token 3（Q3=[1,1]）**

```
score(3,·) = [1, 1, 2]
scaled     = [0.7071, 0.7071, 1.4142]
softmax    ≈ [0.2482, 0.2482, 0.5036]
output3 ≈ [0.752, 0.752]
```

汇总成表：

| token | Query | 对 (t1,t2,t3) 的注意力权重 | 输出向量 |
|---|---|---|---|
| 1 | [1,0] | [0.4011, 0.1978, 0.4011] | [0.599, 0.802] |
| 2 | [0,1] | [0.1978, 0.4011, 0.4011] | [0.802, 0.599] |
| 3 | [1,1] | [0.2482, 0.2482, 0.5036] | [0.752, 0.752] |

可以看到 token 1 和 token 2 因为分别在两个维度上"更像"token 3（都和 `x3=[1,1]` 有
一维相同），所以都给了 token 3 最大的权重；而 token 3 自己因为和另外两个 token 的
相关度打平，权重也接近平均。整张表是**一次矩阵运算**（`softmax(QKᵀ/√d_k)·V`）批
量算出来的，不存在"先算 token 1 再算 token 2"的顺序依赖。

## 8. 和 RNN / LSTM / GRU 的对比

| 维度 | RNN | LSTM / GRU | Transformer |
|---|---|---|---|
| 序列处理方式 | 严格串行，`h_t` 依赖 `h_(t-1)` | 同样串行，只是状态更新方式更复杂 | 全序列并行，token 之间通过注意力矩阵直接建立联系 |
| 建立长距离依赖的"路径长度" | 与序列长度 `n` 成正比，`O(n)` | 门控缓解梯度消失，但路径长度仍是 `O(n)` | 任意两个 token 之间恒为 `O(1)`——一次注意力就直达 |
| 梯度消失/爆炸 | 严重 | 用门控明显缓解 | 基本不存在——注意力是直接的矩阵乘法+softmax，不需要沿时间反向传播 `n` 步 |
| GPU 并行利用率 | 差，必须按时间步顺序算 | 差，同 RNN | 好，`Q/K/V` 的矩阵乘法可以整批并行计算 |
| 记忆机制 | 固定大小隐藏状态，逐步覆盖、天然会遗忘早期信息 | 用门控 + 细胞状态缓解遗忘，但仍是固定大小的压缩记忆 | 无压缩，上下文窗口内全部 token 都可被任意层直接查阅（"无损但有限窗口"），推理时可用 KV Cache |
| 计算复杂度（相对序列长度 `n`） | 时间 `O(n)`，但必须串行执行 | 同 RNN，串行 | `O(n²)`，但可以并行，实际 wall-clock 时间通常远快于 RNN |
| 网络深度可扩展性 | 层数（时间步）一多容易梯度问题，难堆深 | 稍好，仍有限 | 残差连接+LayerNorm 让深层训练稳定，主流 LLM 能堆到数十甚至上百层 |
| 顺序信息来源 | 递归结构天然体现顺序 | 同 RNN | 没有天然顺序，需要显式加位置编码 |
| 主要代价 | 训练慢，长序列效果差 | 比 RNN 好，但仍慢，仍有上限 | 显存/算力随序列长度平方增长，需要更多数据/参数才能训到位 |

**一句话总结**：RNN/LSTM/GRU 用"越走越深的递归链 + 门控"去对抗遗忘和梯度消失，本
质上仍是一根必须按顺序传递的信息链；Transformer 用"注意力让任意两个 token 一步直
达 + 残差流累加信息"从根上绕开了这条链——代价是放弃了固定大小的压缩记忆，换成了
"整段上下文全量可见但窗口有限、且计算随长度平方增长"的新代价。这正是为什么
Transformer 能撑起现在动辄成千上万 token 上下文的大语言模型，而纯 RNN 系列做不到。

## 9. 动态演示

▶ [点击查看动态演示](./Transformer动态演示.html)：数据和 §7 手算例子完全一致
（`W_Q=W_K=I`，`W_V` 交换两维）。图里滑动的圆点是当前发起查询的 token，它到每个
token 的连线粗细/透明度就是 `softmax(Q·Kᵀ/√d_k)` 算出的注意力权重（对应 §3.2 的
数据流图）；下方散点图里三个彩色点是各 token 的 `V` 向量，黑色点是
`weights·V` 加权求和得到的输出，会随权重变化平滑移动。动画会轮流跑一遍
**Encoder**（无 mask，query 能看到全部 token）和一遍 **Decoder**（§3.4 的 causal
mask，query 只能看到自己和更早的 token）——对比两种模式下 token 1、2 的连线，可以
直观看到"看不到未来"具体是怎么把某些连线直接掐掉的；token 3 因为本来就是序列最
后一位，两种模式下完全一样。

## 10. Java 实现示例

下面是 `Transformer.java` 的完整实现——把 §2~§8 讲到的每个组件都串起来：Token
Embedding、位置编码、Multi-Head Attention、Masked Self-Attention、
Cross-Attention、FFN、残差连接+LayerNorm、输出层，对应 §4 架构图里 Encoder 和
Decoder 两条完整链路，能跑通一次 seq2seq 前向传播。

```java
package deepblue.inaction_01_hello_agents.chapter_03;

import java.util.Random;

public class Transformer {

    private final TokenEmbedding srcEmbedding;
    private final TokenEmbedding tgtEmbedding;
    private final TransformerEncoder encoder;
    private final TransformerDecoder decoder;
    private final double[][] wOut; // §3.8：d_model -> tgt 词表大小

    public Transformer(int srcVocabSize, int tgtVocabSize, int dModel, int numHeads,
                        int dFf, int numLayers, Random rng) {
        this.srcEmbedding = new TokenEmbedding(srcVocabSize, dModel, rng);
        this.tgtEmbedding = new TokenEmbedding(tgtVocabSize, dModel, rng);
        this.encoder = new TransformerEncoder(numLayers, dModel, numHeads, dFf, rng);
        this.decoder = new TransformerDecoder(numLayers, dModel, numHeads, dFf, rng);
        this.wOut = randomMatrix(dModel, tgtVocabSize, rng, 1.0 / Math.sqrt(dModel));
    }

    /** 一次完整的 seq2seq 前向传播，返回每个目标位置在词表上的概率分布。 */
    public double[][] forward(int[] srcIds, int[] tgtIds) {
        double[][] encoderOutput = encoder.forward(srcEmbedding.forward(srcIds));
        double[][] decoderOutput = decoder.forward(tgtEmbedding.forward(tgtIds), encoderOutput);
        double[][] logits = matmul(decoderOutput, wOut);
        return softmaxRows(logits);
    }

    // ================= Token Embedding =================

    static class TokenEmbedding {
        private final double[][] table;
        private final int dModel;

        TokenEmbedding(int vocabSize, int dModel, Random rng) {
            this.table = randomMatrix(vocabSize, dModel, rng, 1.0 / Math.sqrt(dModel));
            this.dModel = dModel;
        }

        double[][] forward(int[] tokenIds) {
            double scaleFactor = Math.sqrt(dModel); // 原论文里 embedding 额外乘 √d_model
            double[][] out = new double[tokenIds.length][dModel];
            for (int i = 0; i < tokenIds.length; i++) {
                for (int d = 0; d < dModel; d++) {
                    out[i][d] = table[tokenIds[i]][d] * scaleFactor;
                }
            }
            return out;
        }
    }

    // ================= Multi-Head Attention（§3.3） =================

    static class MultiHeadAttention {
        private final int dModel, numHeads, dK;
        private final double[][] wq, wk, wv, wo;

        MultiHeadAttention(int dModel, int numHeads, Random rng) {
            this.dModel = dModel;
            this.numHeads = numHeads;
            this.dK = dModel / numHeads;
            double s = 1.0 / Math.sqrt(dModel);
            this.wq = randomMatrix(dModel, dModel, rng, s);
            this.wk = randomMatrix(dModel, dModel, rng, s);
            this.wv = randomMatrix(dModel, dModel, rng, s);
            this.wo = randomMatrix(dModel, dModel, rng, s);
        }

        /**
         * xQ、xKV 分开传，是为了同一份代码服务三种场景：Encoder 自注意力
         * （xQ=xKV=encoder 序列）、Decoder 掩码自注意力（xQ=xKV=decoder 序列，
         * 另加 causal mask）、Cross-Attention（xQ=decoder 序列，xKV=encoder
         * 输出，mask=null，§3.5）。
         */
        double[][] forward(double[][] xQ, double[][] xKV, double[][] mask) {
            double[][] q = matmul(xQ, wq);
            double[][] k = matmul(xKV, wk);
            double[][] v = matmul(xKV, wv);

            double[][] concat = new double[xQ.length][dModel];
            for (int h = 0; h < numHeads; h++) {
                double[][] qh = sliceCols(q, h * dK, dK);
                double[][] kh = sliceCols(k, h * dK, dK);
                double[][] vh = sliceCols(v, h * dK, dK);
                double[][] headOut = scaledDotProductAttention(qh, kh, vh, mask);
                for (int i = 0; i < xQ.length; i++) {
                    System.arraycopy(headOut[i], 0, concat[i], h * dK, dK);
                }
            }
            return matmul(concat, wo);
        }
    }

    // ================= Feed-Forward Network（§3.6） =================

    static class FeedForward {
        private final double[][] w1, w2;
        private final double[] b1, b2;

        FeedForward(int dModel, int dFf, Random rng) {
            this.w1 = randomMatrix(dModel, dFf, rng, 1.0 / Math.sqrt(dModel));
            this.b1 = new double[dFf];
            this.w2 = randomMatrix(dFf, dModel, rng, 1.0 / Math.sqrt(dFf));
            this.b2 = new double[dModel];
        }

        double[][] forward(double[][] x) {
            double[][] hidden = relu(addBias(matmul(x, w1), b1));
            return addBias(matmul(hidden, w2), b2);
        }
    }

    // ================= Encoder / Decoder 层（§3.7 Add & Norm） =================

    static class EncoderLayer {
        private final MultiHeadAttention mha;
        private final FeedForward ffn;

        EncoderLayer(int dModel, int numHeads, int dFf, Random rng) {
            this.mha = new MultiHeadAttention(dModel, numHeads, rng);
            this.ffn = new FeedForward(dModel, dFf, rng);
        }

        double[][] forward(double[][] x) {
            x = layerNorm(add(x, mha.forward(x, x, null)));
            x = layerNorm(add(x, ffn.forward(x)));
            return x;
        }
    }

    static class DecoderLayer {
        private final MultiHeadAttention selfMha;
        private final MultiHeadAttention crossMha; // §3.5
        private final FeedForward ffn;

        DecoderLayer(int dModel, int numHeads, int dFf, Random rng) {
            this.selfMha = new MultiHeadAttention(dModel, numHeads, rng);
            this.crossMha = new MultiHeadAttention(dModel, numHeads, rng);
            this.ffn = new FeedForward(dModel, dFf, rng);
        }

        double[][] forward(double[][] x, double[][] encoderOutput, double[][] causalMask) {
            x = layerNorm(add(x, selfMha.forward(x, x, causalMask)));
            x = layerNorm(add(x, crossMha.forward(x, encoderOutput, null))); // Q←decoder, K/V←encoder
            x = layerNorm(add(x, ffn.forward(x)));
            return x;
        }
    }

    static class TransformerEncoder {
        private final int dModel;
        private final EncoderLayer[] layers;

        TransformerEncoder(int numLayers, int dModel, int numHeads, int dFf, Random rng) {
            this.dModel = dModel;
            this.layers = new EncoderLayer[numLayers];
            for (int i = 0; i < numLayers; i++) {
                layers[i] = new EncoderLayer(dModel, numHeads, dFf, rng);
            }
        }

        double[][] forward(double[][] tokenEmbeddings) {
            double[][] x = add(tokenEmbeddings, positionalEncoding(tokenEmbeddings.length, dModel));
            for (EncoderLayer layer : layers) {
                x = layer.forward(x);
            }
            return x;
        }
    }

    static class TransformerDecoder {
        private final int dModel;
        private final DecoderLayer[] layers;

        TransformerDecoder(int numLayers, int dModel, int numHeads, int dFf, Random rng) {
            this.dModel = dModel;
            this.layers = new DecoderLayer[numLayers];
            for (int i = 0; i < numLayers; i++) {
                layers[i] = new DecoderLayer(dModel, numHeads, dFf, rng);
            }
        }

        double[][] forward(double[][] tokenEmbeddings, double[][] encoderOutput) {
            int seqLen = tokenEmbeddings.length;
            double[][] x = add(tokenEmbeddings, positionalEncoding(seqLen, dModel));
            double[][] mask = causalMask(seqLen);
            for (DecoderLayer layer : layers) {
                x = layer.forward(x, encoderOutput, mask);
            }
            return x;
        }
    }

    // ================= 基础数学工具 =================

    /** §3.1：正弦/余弦位置编码。 */
    static double[][] positionalEncoding(int seqLen, int dModel) {
        double[][] pe = new double[seqLen][dModel];
        for (int pos = 0; pos < seqLen; pos++) {
            for (int i = 0; i < dModel; i += 2) {
                double angle = pos / Math.pow(10000, (2.0 * (i / 2)) / dModel);
                pe[pos][i] = Math.sin(angle);
                if (i + 1 < dModel) {
                    pe[pos][i + 1] = Math.cos(angle);
                }
            }
        }
        return pe;
    }

    /** §3.4：下三角矩阵，1=可见、0=遮蔽，用来让 Decoder 看不到未来位置。 */
    static double[][] causalMask(int seqLen) {
        double[][] mask = new double[seqLen][seqLen];
        for (int i = 0; i < seqLen; i++) {
            for (int j = 0; j <= i; j++) {
                mask[i][j] = 1.0;
            }
        }
        return mask;
    }

    /** §3.2：Attention(Q,K,V) = softmax(Q·Kᵀ / √d_k)·V。 */
    static double[][] scaledDotProductAttention(double[][] q, double[][] k, double[][] v, double[][] mask) {
        int dK = q[0].length;
        double[][] scores = matmul(q, transpose(k));
        double scaleFactor = Math.sqrt(dK);
        for (int i = 0; i < scores.length; i++) {
            for (int j = 0; j < scores[0].length; j++) {
                scores[i][j] /= scaleFactor;
                if (mask != null && mask[i][j] == 0) {
                    scores[i][j] = -1e9; // 屏蔽的位置 softmax 后权重趋于 0
                }
            }
        }
        return matmul(softmaxRows(scores), v);
    }

    // matmul / transpose / add / addBias / relu / layerNorm / softmaxRows /
    // sliceCols / randomMatrix 等基础矩阵工具方法省略，完整实现见 Transformer.java
}
```

对照 §7 的手算过程：`scaledDotProductAttention` 对每个 token 的计算**没有任何数据
依赖**——第 `i` 行输出只用到 `q[i]` 和全部的 `k、v`，不依赖其它行的输出，这正是它
能被整批矩阵乘法并行算完（而不是像 `SimpleRNN`/`SimpleLSTM`/`SimpleGRU` 那样必须
按 `t=0,1,2...` 顺序跑 for 循环）的原因。`DecoderLayer` 里的 `causalMask` 则是
Decoder 和 Encoder 自注意力唯一的区别——遮住未来位置，逼着模型只能看"已经生成的
部分"；`crossMha` 的 `xQ` 来自 Decoder、`xKV` 来自 `encoderOutput`，就是 §3.5 里
"Encoder 和 Decoder 之间唯一的信息通道"在代码里的样子。完整可运行版本（含
`main` 里三个演示：复现 §7 手算数字、可视化 causal mask 效果、跑一次完整
Encoder-Decoder 前向传播）见同目录下的 `Transformer.java`；对应的 Python/numpy
实现见 `Transformer.py`。

### 10.1 运行结果示例

执行 `Transformer.java` 里的 `main` 方法（`javac Transformer.java && java
deepblue.inaction_01_hello_agents.chapter_03.Transformer`），实际输出如下：

```text
=== §7 手算例子对照（期望 output1≈[0.599,0.802]，output2≈[0.802,0.599]，output3≈[0.752,0.752]）===
token 1 输出: [0.599, 0.802]
token 2 输出: [0.802, 0.599]
token 3 输出: [0.752, 0.752]

=== §3.4 Masked Self-Attention：加 mask 前后对比 ===
不加 mask（Encoder 用）：
[0.868, 0.047, 0.071, 0.015]
[0.031, 0.244, 0.393, 0.332]
[0.022, 0.185, 0.637, 0.155]
[0.006, 0.201, 0.200, 0.593]
加 causal mask 后（Decoder 用，右上角应全为 0）：
[1.000, 0.000, 0.000, 0.000]
[0.114, 0.886, 0.000, 0.000]
[0.026, 0.220, 0.754, 0.000]
[0.006, 0.201, 0.200, 0.593]

=== 完整 Encoder-Decoder 前向传播 ===
源序列长度: 5 -> 目标序列长度: 4
输出形状: [4, 60]（每个目标位置一个长度为 60 的概率分布）
该位置概率和: 1.000000
该位置概率和: 1.000000
该位置概率和: 1.000000
该位置概率和: 1.000000
贪心解码得到的下一个 token id: 31 12 27 50
```

三段输出对应三个验证点：

1. **§7 手算对照**：单头 Attention 在人工设定的权重（`W_Q=W_K=I`，`W_V` 交换两
   维）下算出的三个输出向量，和 §7 手推的数字完全一致，验证 `scaledDotProduct
   Attention` 实现无误。
2. **causal mask 效果**：加 mask 前，第 1 个位置也能看到第 2、3、4 个位置（一整
   行权重都非零）；加 mask 后，矩阵变成下三角——第 1 行只能看自己（权重全落在
   对角线），第 4 行能看全部 4 个位置且和加 mask 前完全一样（本来就看得到全部历
   史，不受影响），右上角强制为 0，直观验证了"Decoder 看不到未来"。
3. **完整前向传播**：5 个 token 的源序列、4 个 token 的目标序列跑完 Embedding →
   Encoder → Decoder（含 Masked Self-Attention 和 Cross-Attention）→ 输出层，得
   到 `[4, 60]` 的概率矩阵，每一行概率和都是 `1.0`（softmax 归一化正确），最后
   贪心解码（对每行取 `argmax`）得到 4 个 token id。因为模型参数是随机初始化、
   未经训练的，这里的具体 token id 没有语义意义，它验证的是**形状和数值在整条
   Encoder-Decoder 链路里能正确流转**——从 embedding 到最终概率分布，每一步的
   矩阵维度都对得上。

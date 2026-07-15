"""
Transformer.md 的可运行版本：用 numpy 从零实现完整的 Encoder-Decoder Transformer
（Token Embedding + 位置编码 + Multi-Head Self-Attention + Masked Self-Attention +
Cross-Attention + FFN + 残差/LayerNorm + 输出层），对应 §2~§8 的每一个组件。

这个文件把 §4 架构图里 Encoder 和 Decoder 两条完整链路都串起来，能跑通一次
seq2seq 前向传播，并复现 §7 手算例子的数字，供对照验证。
"""

import numpy as np


def positional_encoding(seq_len: int, d_model: int) -> np.ndarray:
    """§3.1：正弦/余弦位置编码，形状 (seq_len, d_model)。"""
    pos = np.arange(seq_len)[:, None]
    i = np.arange(d_model)[None, :]
    angle_rates = 1.0 / np.power(10000, (2 * (i // 2)) / np.float64(d_model))
    angles = pos * angle_rates
    pe = np.zeros((seq_len, d_model))
    pe[:, 0::2] = np.sin(angles[:, 0::2])
    pe[:, 1::2] = np.cos(angles[:, 1::2])
    return pe


def causal_mask(seq_len: int) -> np.ndarray:
    """§3.4：下三角矩阵，1=可见、0=遮蔽，用来让 Decoder 看不到未来位置。"""
    return np.tril(np.ones((seq_len, seq_len)))


def softmax(x: np.ndarray, axis: int = -1) -> np.ndarray:
    x = x - np.max(x, axis=axis, keepdims=True)  # 防止指数溢出
    e = np.exp(x)
    return e / np.sum(e, axis=axis, keepdims=True)


def scaled_dot_product_attention(q: np.ndarray, k: np.ndarray, v: np.ndarray,
                                  mask: np.ndarray = None) -> np.ndarray:
    """§3.2：Attention(Q,K,V) = softmax(Q·Kᵀ / √d_k)·V，q/k/v 形状 (..., seq_len, d_k)。"""
    d_k = q.shape[-1]
    scores = q @ np.swapaxes(k, -1, -2) / np.sqrt(d_k)
    if mask is not None:
        scores = np.where(mask == 0, -1e9, scores)  # §3.4：屏蔽的位置 softmax 后权重趋于 0
    weights = softmax(scores, axis=-1)
    return weights @ v


class TokenEmbedding:
    """把 token id 查表映射成向量；原论文里额外乘 √d_model 让 embedding 和位置编码量级匹配。"""

    def __init__(self, vocab_size: int, d_model: int, rng: np.random.Generator):
        self.table = rng.normal(0, 1.0 / np.sqrt(d_model), (vocab_size, d_model))
        self.d_model = d_model

    def forward(self, token_ids: np.ndarray) -> np.ndarray:
        return self.table[token_ids] * np.sqrt(self.d_model)


class MultiHeadAttention:
    """§3.3：把 d_model 平均切成 num_heads 份，各自做一次 Attention 再拼接。

    forward 的 (x_q, x_kv) 分开传是为了同一份代码能同时服务三种场景：
    Encoder 自注意力（x_q=x_kv=encoder 序列）、Decoder 掩码自注意力
    （x_q=x_kv=decoder 序列，另加 causal mask）、Cross-Attention
    （x_q=decoder 序列，x_kv=encoder 输出，§3.5）。
    """

    def __init__(self, d_model: int, num_heads: int, rng: np.random.Generator):
        assert d_model % num_heads == 0
        self.d_model = d_model
        self.num_heads = num_heads
        self.d_k = d_model // num_heads
        scale = 1.0 / np.sqrt(d_model)
        self.w_q = rng.normal(0, scale, (d_model, d_model))
        self.w_k = rng.normal(0, scale, (d_model, d_model))
        self.w_v = rng.normal(0, scale, (d_model, d_model))
        self.w_o = rng.normal(0, scale, (d_model, d_model))

    def _split_heads(self, x: np.ndarray) -> np.ndarray:
        seq_len = x.shape[0]
        x = x.reshape(seq_len, self.num_heads, self.d_k)
        return x.transpose(1, 0, 2)  # (num_heads, seq_len, d_k)

    def forward(self, x_q: np.ndarray, x_kv: np.ndarray, mask: np.ndarray = None) -> np.ndarray:
        q = self._split_heads(x_q @ self.w_q)
        k = self._split_heads(x_kv @ self.w_k)
        v = self._split_heads(x_kv @ self.w_v)
        heads_out = scaled_dot_product_attention(q, k, v, mask)  # (num_heads, seq_len, d_k)
        concat = heads_out.transpose(1, 0, 2).reshape(x_q.shape[0], self.d_model)
        return concat @ self.w_o


class FeedForward:
    """§3.6：对每个位置独立做 max(0, x·W1+b1)·W2+b2。"""

    def __init__(self, d_model: int, d_ff: int, rng: np.random.Generator):
        self.w1 = rng.normal(0, 1.0 / np.sqrt(d_model), (d_model, d_ff))
        self.b1 = np.zeros(d_ff)
        self.w2 = rng.normal(0, 1.0 / np.sqrt(d_ff), (d_ff, d_model))
        self.b2 = np.zeros(d_model)

    def forward(self, x: np.ndarray) -> np.ndarray:
        hidden = np.maximum(0, x @ self.w1 + self.b1)  # ReLU
        return hidden @ self.w2 + self.b2


def layer_norm(x: np.ndarray, eps: float = 1e-6) -> np.ndarray:
    """§3.7：按最后一维做归一化，这里省略可学习的 gamma/beta，只保留核心效果。"""
    mean = x.mean(axis=-1, keepdims=True)
    var = x.var(axis=-1, keepdims=True)
    return (x - mean) / np.sqrt(var + eps)


class EncoderLayer:
    """§3.7 的 x = LayerNorm(x + Sublayer(x)) 套两次：先 Self-Attention，再 FFN。"""

    def __init__(self, d_model: int, num_heads: int, d_ff: int, rng: np.random.Generator):
        self.mha = MultiHeadAttention(d_model, num_heads, rng)
        self.ffn = FeedForward(d_model, d_ff, rng)

    def forward(self, x: np.ndarray) -> np.ndarray:
        x = layer_norm(x + self.mha.forward(x, x))
        x = layer_norm(x + self.ffn.forward(x))
        return x


class DecoderLayer:
    """Decoder 每层三个子层：Masked Self-Attention → Cross-Attention → FFN，均带 Add&Norm。"""

    def __init__(self, d_model: int, num_heads: int, d_ff: int, rng: np.random.Generator):
        self.self_mha = MultiHeadAttention(d_model, num_heads, rng)
        self.cross_mha = MultiHeadAttention(d_model, num_heads, rng)  # §3.5
        self.ffn = FeedForward(d_model, d_ff, rng)

    def forward(self, x: np.ndarray, encoder_output: np.ndarray, mask: np.ndarray) -> np.ndarray:
        x = layer_norm(x + self.self_mha.forward(x, x, mask=mask))
        x = layer_norm(x + self.cross_mha.forward(x, encoder_output))  # Q←decoder, K/V←encoder
        x = layer_norm(x + self.ffn.forward(x))
        return x


class TransformerEncoder:
    """堆叠 N 层 EncoderLayer，对应 §4 架构图 Encoder 一侧、重复 ×N 的部分。"""

    def __init__(self, num_layers: int, d_model: int, num_heads: int, d_ff: int, seed: int = 0):
        rng = np.random.default_rng(seed)
        self.d_model = d_model
        self.layers = [EncoderLayer(d_model, num_heads, d_ff, rng) for _ in range(num_layers)]

    def forward(self, token_embeddings: np.ndarray) -> np.ndarray:
        seq_len = token_embeddings.shape[0]
        x = token_embeddings + positional_encoding(seq_len, self.d_model)
        for layer in self.layers:
            x = layer.forward(x)
        return x


class TransformerDecoder:
    """堆叠 N 层 DecoderLayer，对应 §4 架构图 Decoder 一侧、重复 ×N 的部分。"""

    def __init__(self, num_layers: int, d_model: int, num_heads: int, d_ff: int, seed: int = 0):
        rng = np.random.default_rng(seed)
        self.d_model = d_model
        self.layers = [DecoderLayer(d_model, num_heads, d_ff, rng) for _ in range(num_layers)]

    def forward(self, token_embeddings: np.ndarray, encoder_output: np.ndarray) -> np.ndarray:
        seq_len = token_embeddings.shape[0]
        x = token_embeddings + positional_encoding(seq_len, self.d_model)
        mask = causal_mask(seq_len)
        for layer in self.layers:
            x = layer.forward(x, encoder_output, mask)
        return x


class Transformer:
    """§4 完整 Encoder-Decoder 架构：Embedding → Encoder → Decoder → 输出层（§3.8）。"""

    def __init__(self, src_vocab_size: int, tgt_vocab_size: int, d_model: int = 64,
                 num_heads: int = 4, d_ff: int = 256, num_layers: int = 2, seed: int = 0):
        rng = np.random.default_rng(seed)
        self.d_model = d_model
        self.src_embedding = TokenEmbedding(src_vocab_size, d_model, rng)
        self.tgt_embedding = TokenEmbedding(tgt_vocab_size, d_model, rng)
        self.encoder = TransformerEncoder(num_layers, d_model, num_heads, d_ff, seed=seed)
        self.decoder = TransformerDecoder(num_layers, d_model, num_heads, d_ff, seed=seed + 1)
        self.w_out = rng.normal(0, 1.0 / np.sqrt(d_model), (d_model, tgt_vocab_size))

    def forward(self, src_ids: np.ndarray, tgt_ids: np.ndarray) -> np.ndarray:
        encoder_output = self.encoder.forward(self.src_embedding.forward(src_ids))
        decoder_output = self.decoder.forward(self.tgt_embedding.forward(tgt_ids), encoder_output)
        logits = decoder_output @ self.w_out
        return softmax(logits, axis=-1)  # 每一行是词表上的概率分布


def _demo_single_head_matches_markdown():
    """复现 Transformer.md §7 的手算例子：W_Q=W_K=I，W_V 交换两个维度。"""
    x = np.array([[1.0, 0.0], [0.0, 1.0], [1.0, 1.0]])
    w_qk = np.eye(2)
    w_v = np.array([[0.0, 1.0], [1.0, 0.0]])  # 交换两维

    q = x @ w_qk
    k = x @ w_qk
    v = x @ w_v
    out = scaled_dot_product_attention(q, k, v)

    print("=== §7 手算例子对照（期望 output1≈[0.599,0.802]，output2≈[0.802,0.599]，output3≈[0.752,0.752]）===")
    for i, row in enumerate(out, start=1):
        print(f"token {i} 输出: {np.round(row, 3)}")


def _demo_causal_mask():
    """验证 §3.4：加了 causal mask 之后，注意力权重矩阵是下三角——看不到未来位置。"""
    seq_len, d_k = 4, 2
    rng = np.random.default_rng(1)
    q = k = rng.normal(0, 1, (seq_len, d_k))
    scores = q @ k.T / np.sqrt(d_k)
    weights_no_mask = softmax(scores)
    weights_masked = softmax(np.where(causal_mask(seq_len) == 0, -1e9, scores))

    print("\n=== §3.4 Masked Self-Attention：加 mask 前后对比 ===")
    print("不加 mask（Encoder 用）：\n", np.round(weights_no_mask, 3))
    print("加 causal mask 后（Decoder 用，右上角应全为 0）：\n", np.round(weights_masked, 3))


def _demo_full_transformer():
    """跑一次完整的 Encoder-Decoder 前向传播，模拟一次翻译任务的形状和输出。"""
    src_vocab_size, tgt_vocab_size = 50, 60
    d_model, num_heads, d_ff, num_layers = 32, 4, 64, 2
    src_ids = np.array([5, 12, 7, 9, 2])       # 源语言 token id 序列（长度 5）
    tgt_ids = np.array([1, 8, 3, 15])          # 已生成的目标语言 token id 序列（长度 4）

    model = Transformer(src_vocab_size, tgt_vocab_size, d_model, num_heads, d_ff, num_layers, seed=42)
    probs = model.forward(src_ids, tgt_ids)

    print("\n=== 完整 Encoder-Decoder 前向传播 ===")
    print(f"源序列长度: {len(src_ids)} -> 目标序列长度: {len(tgt_ids)}")
    print(f"输出形状: {probs.shape}（每个目标位置一个长度为 {tgt_vocab_size} 的概率分布）")
    print(f"每一行概率和应为 1: {np.round(probs.sum(axis=-1), 6)}")
    next_token_ids = np.argmax(probs, axis=-1)
    print(f"贪心解码得到的下一个 token id: {next_token_ids}")


if __name__ == "__main__":
    _demo_single_head_matches_markdown()
    _demo_causal_mask()
    _demo_full_transformer()

package deepblue.inaction_01_hello_agents.chapter_03;

import java.util.Random;

/**
 * Transformer.md 的完整可运行版本：Encoder-Decoder 架构，对应 §2~§8 的每一个组件——
 * Token Embedding、位置编码、Multi-Head Self-Attention、Masked Self-Attention、
 * Cross-Attention、FFN、残差连接+LayerNorm、输出层。
 *
 * 这个文件把 §4 架构图里 Encoder 和 Decoder 两条完整链路都串起来，能跑通一次
 * seq2seq 前向传播，并复现 §7 手算例子的数字，供对照验证。
 */
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

    static double[][] matmul(double[][] a, double[][] b) {
        double[][] out = new double[a.length][b[0].length];
        for (int i = 0; i < a.length; i++) {
            for (int k = 0; k < b.length; k++) {
                double aik = a[i][k];
                if (aik == 0) continue;
                for (int j = 0; j < b[0].length; j++) {
                    out[i][j] += aik * b[k][j];
                }
            }
        }
        return out;
    }

    static double[][] transpose(double[][] a) {
        double[][] out = new double[a[0].length][a.length];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                out[j][i] = a[i][j];
            }
        }
        return out;
    }

    static double[][] add(double[][] a, double[][] b) {
        double[][] out = new double[a.length][a[0].length];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < a[0].length; j++) {
                out[i][j] = a[i][j] + b[i][j];
            }
        }
        return out;
    }

    static double[][] addBias(double[][] x, double[] bias) {
        double[][] out = new double[x.length][x[0].length];
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[0].length; j++) {
                out[i][j] = x[i][j] + bias[j];
            }
        }
        return out;
    }

    static double[][] relu(double[][] x) {
        double[][] out = new double[x.length][x[0].length];
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[0].length; j++) {
                out[i][j] = Math.max(0, x[i][j]);
            }
        }
        return out;
    }

    /** §3.7：按最后一维做归一化，这里省略可学习的 gamma/beta，只保留核心效果。 */
    static double[][] layerNorm(double[][] x) {
        double eps = 1e-6;
        double[][] out = new double[x.length][x[0].length];
        for (int i = 0; i < x.length; i++) {
            double mean = 0;
            for (double v : x[i]) mean += v;
            mean /= x[i].length;
            double variance = 0;
            for (double v : x[i]) variance += (v - mean) * (v - mean);
            variance /= x[i].length;
            double denom = Math.sqrt(variance + eps);
            for (int j = 0; j < x[i].length; j++) {
                out[i][j] = (x[i][j] - mean) / denom;
            }
        }
        return out;
    }

    /** 按行做 softmax。 */
    static double[][] softmaxRows(double[][] x) {
        double[][] out = new double[x.length][x[0].length];
        for (int i = 0; i < x.length; i++) {
            double max = Double.NEGATIVE_INFINITY;
            for (double v : x[i]) max = Math.max(max, v);
            double sum = 0;
            for (int j = 0; j < x[i].length; j++) {
                out[i][j] = Math.exp(x[i][j] - max); // 减去最大值防止数值溢出
                sum += out[i][j];
            }
            for (int j = 0; j < x[i].length; j++) out[i][j] /= sum;
        }
        return out;
    }

    static double[][] sliceCols(double[][] x, int start, int width) {
        double[][] out = new double[x.length][width];
        for (int i = 0; i < x.length; i++) {
            System.arraycopy(x[i], start, out[i], 0, width);
        }
        return out;
    }

    static double[][] randomMatrix(int rows, int cols, Random rng, double scale) {
        double[][] out = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                out[i][j] = rng.nextGaussian() * scale;
            }
        }
        return out;
    }

    // ================= main：三个演示，对应 Transformer.py 的三个 demo =================

    public static void main(String[] args) {
        demoSingleHeadMatchesMarkdown();
        demoCausalMask();
        demoFullTransformer();
    }

    /** 复现 Transformer.md §7 的手算例子：W_Q=W_K=I，W_V 交换两个维度。 */
    private static void demoSingleHeadMatchesMarkdown() {
        double[][] x = {{1, 0}, {0, 1}, {1, 1}};
        double[][] wQK = {{1, 0}, {0, 1}};
        double[][] wV = {{0, 1}, {1, 0}}; // 交换两维

        double[][] q = matmul(x, wQK);
        double[][] k = matmul(x, wQK);
        double[][] v = matmul(x, wV);
        double[][] out = scaledDotProductAttention(q, k, v, null);

        System.out.println("=== §7 手算例子对照（期望 output1≈[0.599,0.802]，output2≈[0.802,0.599]，output3≈[0.752,0.752]）===");
        for (int i = 0; i < out.length; i++) {
            System.out.printf("token %d 输出: [%.3f, %.3f]%n", i + 1, out[i][0], out[i][1]);
        }
    }

    /** 验证 §3.4：加了 causal mask 之后，注意力权重矩阵是下三角——看不到未来位置。 */
    private static void demoCausalMask() {
        int seqLen = 4, dK = 2;
        Random rng = new Random(1);
        double[][] q = randomMatrix(seqLen, dK, rng, 1.0);
        double[][] scores = scale(matmul(q, transpose(q)), 1.0 / Math.sqrt(dK));

        System.out.println("\n=== §3.4 Masked Self-Attention：加 mask 前后对比 ===");
        System.out.println("不加 mask（Encoder 用）：");
        printMatrix(softmaxRows(scores));

        double[][] mask = causalMask(seqLen);
        double[][] masked = new double[seqLen][seqLen];
        for (int i = 0; i < seqLen; i++) {
            for (int j = 0; j < seqLen; j++) {
                masked[i][j] = mask[i][j] == 0 ? -1e9 : scores[i][j];
            }
        }
        System.out.println("加 causal mask 后（Decoder 用，右上角应全为 0）：");
        printMatrix(softmaxRows(masked));
    }

    /** 跑一次完整的 Encoder-Decoder 前向传播，模拟一次翻译任务的形状和输出。 */
    private static void demoFullTransformer() {
        int srcVocabSize = 50, tgtVocabSize = 60;
        int dModel = 32, numHeads = 4, dFf = 64, numLayers = 2;
        int[] srcIds = {5, 12, 7, 9, 2};  // 源语言 token id 序列（长度 5）
        int[] tgtIds = {1, 8, 3, 15};     // 已生成的目标语言 token id 序列（长度 4）

        Transformer model = new Transformer(srcVocabSize, tgtVocabSize, dModel, numHeads,
                dFf, numLayers, new Random(42));
        double[][] probs = model.forward(srcIds, tgtIds);

        System.out.println("\n=== 完整 Encoder-Decoder 前向传播 ===");
        System.out.printf("源序列长度: %d -> 目标序列长度: %d%n", srcIds.length, tgtIds.length);
        System.out.printf("输出形状: [%d, %d]（每个目标位置一个长度为 %d 的概率分布）%n",
                probs.length, probs[0].length, tgtVocabSize);
        for (double[] row : probs) {
            double sum = 0;
            for (double p : row) sum += p;
            System.out.printf("该位置概率和: %.6f%n", sum);
        }
        System.out.print("贪心解码得到的下一个 token id: ");
        for (double[] row : probs) {
            int argmax = 0;
            for (int j = 1; j < row.length; j++) {
                if (row[j] > row[argmax]) argmax = j;
            }
            System.out.print(argmax + " ");
        }
        System.out.println();
    }

    private static double[][] scale(double[][] x, double s) {
        double[][] out = new double[x.length][x[0].length];
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x[0].length; j++) {
                out[i][j] = x[i][j] * s;
            }
        }
        return out;
    }

    private static void printMatrix(double[][] m) {
        for (double[] row : m) {
            StringBuilder sb = new StringBuilder("[");
            for (int j = 0; j < row.length; j++) {
                sb.append(String.format("%.3f", row[j]));
                if (j < row.length - 1) sb.append(", ");
            }
            sb.append("]");
            System.out.println(sb);
        }
    }
}

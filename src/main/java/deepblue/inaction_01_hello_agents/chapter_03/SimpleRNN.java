package deepblue.inaction_01_hello_agents.chapter_03;

import java.util.Arrays;

/**
 * 一个只有 1 维输入、2 维隐藏状态的最小 RNN 前向传播，对应 RNN.md §3/§7 的公式：
 * h_t = tanh(W_xh·x_t + W_hh·h_(t-1) + b_h)
 * y_t =        W_hy·h_t + b_y
 */
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

    /** 复现 RNN.md §4 的手把手手算例子，逐步打印每一步的隐藏状态和输出，供对照验证。 */
    public static void main(String[] args) {
        double[][] wxh = {{0.6}, {-0.4}};                 // hiddenSize=2, inputSize=1
        double[][] whh = {{0.5, -0.3}, {0.2, 0.4}};        // hiddenSize=2 x hiddenSize=2
        double[] bh = {0.1, -0.1};
        double[][] why = {{0.7, -0.6}};                    // outputSize=1 x hiddenSize=2
        double[] by = {0.05};
        double[][] inputs = {{0.9}, {-0.6}, {0.4}};

        System.out.println("=== §4 手算例子对照（期望 h1≈[0.565,-0.430] y1≈0.703，"
                + "h2≈[0.150,0.081] y2≈0.106，h3≈[0.372,-0.195] y3≈0.427）===");
        double[] h = {0, 0};
        for (int t = 0; t < inputs.length; t++) {
            double[] xt = inputs[t];
            double[] hNext = new double[h.length];
            for (int i = 0; i < h.length; i++) {
                double sum = bh[i];
                for (int j = 0; j < xt.length; j++) sum += wxh[i][j] * xt[j];
                for (int j = 0; j < h.length; j++) sum += whh[i][j] * h[j];
                hNext[i] = Math.tanh(sum);
            }
            h = hNext;
            double y = by[0];
            for (int j = 0; j < h.length; j++) y += why[0][j] * h[j];
            System.out.printf("t=%d  x_t=%5.2f  h_t=[%.3f, %.3f]  y_t=%.3f%n",
                    t + 1, xt[0], h[0], h[1], y);
        }

        SimpleRNN rnn = new SimpleRNN(wxh, whh, bh, why, by);
        double[][] outputs = rnn.forward(inputs);
        System.out.println("forward() 方法返回的 y 序列: " + Arrays.deepToString(outputs));
    }
}

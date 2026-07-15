package deepblue.inaction_01_hello_agents.chapter_03;

import java.util.Arrays;

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

    /** 复现 LSTM.md §4 的手把手手算例子，逐步打印每个门的值，供对照验证。 */
    public static void main(String[] args) {
        double[][] wfx = {{0.5}}, wfh = {{0.8}}; double[] bf = {0.1};
        double[][] wix = {{0.4}}, wih = {{0.6}}; double[] bi = {-0.2};
        double[][] wgx = {{0.9}}, wgh = {{0.5}}; double[] bg = {0.0};
        double[][] wox = {{0.3}}, woh = {{0.7}}; double[] bo = {0.1};
        double[][] inputs = {{0.8}, {-0.5}, {0.6}};

        System.out.println("=== §4 手算例子对照（期望 h1≈0.1845, h2≈0.0085, h3≈0.1471）===");
        double[] h = {0}, c = {0};
        for (int t = 0; t < inputs.length; t++) {
            double[] xt = inputs[t];
            double f = sigmoid(gateSum(wfx[0], wfh[0], xt, h, bf[0]));
            double i = sigmoid(gateSum(wix[0], wih[0], xt, h, bi[0]));
            double g = Math.tanh(gateSum(wgx[0], wgh[0], xt, h, bg[0]));
            double o = sigmoid(gateSum(wox[0], woh[0], xt, h, bo[0]));
            double cNext = f * c[0] + i * g;
            double hNext = o * Math.tanh(cNext);
            System.out.printf("t=%d  x_t=%5.2f  f_t=%.4f  i_t=%.4f  g_t=%.4f  c_t=%.4f  o_t=%.4f  h_t=%.4f%n",
                    t + 1, xt[0], f, i, g, cNext, o, hNext);
            c[0] = cNext;
            h[0] = hNext;
        }

        SimpleLSTM lstm = new SimpleLSTM(wfx, wfh, bf, wix, wih, bi, wgx, wgh, bg, wox, woh, bo);
        double[][] outputs = lstm.forward(inputs);
        System.out.println("forward() 方法返回的 h 序列: " + Arrays.deepToString(outputs));
    }
}

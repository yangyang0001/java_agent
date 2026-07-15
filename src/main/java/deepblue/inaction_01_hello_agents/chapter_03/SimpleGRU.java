package deepblue.inaction_01_hello_agents.chapter_03;

import java.util.Arrays;

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

    /** 复现 GRU.md §4 的手把手手算例子，逐步打印每个门的值，供对照验证。 */
    public static void main(String[] args) {
        double[][] wzx = {{0.4}}, wzh = {{0.8}}; double[] bz = {-0.1};
        double[][] wrx = {{0.3}}, wrh = {{0.6}}; double[] br = {0.2};
        double[][] whx = {{0.9}}, whh = {{0.5}}; double[] bh = {0.0};
        double[][] inputs = {{0.8}, {-0.5}, {0.6}};

        System.out.println("=== §4 手算例子对照（期望 h1≈0.3422, h2≈0.0058, h3≈0.2677）===");
        double[] h = {0};
        for (int t = 0; t < inputs.length; t++) {
            double[] xt = inputs[t];
            double z = sigmoid(dot(wzx[0], xt) + dot(wzh[0], h) + bz[0]);
            double r = sigmoid(dot(wrx[0], xt) + dot(wrh[0], h) + br[0]);
            double[] rh = {r * h[0]};
            double hCandidate = Math.tanh(dot(whx[0], xt) + dot(whh[0], rh) + bh[0]);
            double hNext = (1 - z) * h[0] + z * hCandidate;
            System.out.printf("t=%d  x_t=%5.2f  z_t=%.4f  r_t=%.4f  h~_t=%.4f  h_t=%.4f%n",
                    t + 1, xt[0], z, r, hCandidate, hNext);
            h[0] = hNext;
        }

        SimpleGRU gru = new SimpleGRU(wzx, wzh, bz, wrx, wrh, br, whx, whh, bh);
        double[][] outputs = gru.forward(inputs);
        System.out.println("forward() 方法返回的 h 序列: " + Arrays.deepToString(outputs));
    }
}

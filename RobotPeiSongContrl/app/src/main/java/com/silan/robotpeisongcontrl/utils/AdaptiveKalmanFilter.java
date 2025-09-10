package com.silan.robotpeisongcontrl.utils;

public class AdaptiveKalmanFilter {
    private double x; // 估计值
    private double p = 1; // 估计误差协方差
    private final double q; // 过程噪声协方差
    private final double rNear; // 近距离测量噪声
    private final double rFar; // 远距离测量噪声
    private boolean isFirst = true;

    public AdaptiveKalmanFilter(double processNoise, double nearNoise, double farNoise) {
        this.q = processNoise;
        this.rNear = nearNoise;
        this.rFar = farNoise;
    }

    // 根据距离动态选择测量噪声
    public double filter(double measurement, double distance) {
        if (isFirst) {
            x = measurement;
            isFirst = false;
            return x;
        }

        // 预测
        double xPred = x;
        double pPred = p + q;

        // 动态调整测量噪声（距离越近，噪声越大）
        double r = distance < 0.5 ? rNear : rFar;

        // 更新
        double k = pPred / (pPred + r); // 卡尔曼增益
        x = xPred + k * (measurement - xPred);
        p = (1 - k) * pPred;

        return x;
    }
}

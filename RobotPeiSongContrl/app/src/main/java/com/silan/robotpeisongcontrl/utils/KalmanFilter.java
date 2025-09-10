package com.silan.robotpeisongcontrl.utils;

public class KalmanFilter {
    private double processNoise;
    private double measurementNoise;
    private double maxChange;
    private double estimate;
    private double errorCovariance = 1.0;

    public KalmanFilter(double processNoise, double measurementNoise, double maxChange) {
        this.processNoise = processNoise;
        this.measurementNoise = measurementNoise;
        this.maxChange = maxChange;
    }

    public double filter(double measurement) {
        // 预测阶段
        double predictedEstimate = estimate;
        double predictedErrorCovariance = errorCovariance + processNoise;

        // 更新阶段
        double kalmanGain = predictedErrorCovariance / (predictedErrorCovariance + measurementNoise);
        estimate = predictedEstimate + kalmanGain * (measurement - predictedEstimate);
        errorCovariance = (1 - kalmanGain) * predictedErrorCovariance;

        // 限制数据跳变（符合文档2实时性要求）
        if (Math.abs(estimate - predictedEstimate) > maxChange) {
            estimate = predictedEstimate + (estimate > predictedEstimate ? maxChange : -maxChange);
        }
        return estimate;
    }

    public void reset() {
        estimate = 0;
        errorCovariance = 1.0;
    }
}

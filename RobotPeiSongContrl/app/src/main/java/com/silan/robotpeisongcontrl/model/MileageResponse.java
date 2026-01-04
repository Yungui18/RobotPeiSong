package com.silan.robotpeisongcontrl.model;

/**
 * 里程响应模型
 */
public class MileageResponse {
    private double totalMileage; // 总里程（米）
    private long timestamp;      // 时间戳

    // Getter & Setter
    public double getTotalMileage() { return totalMileage; }
    public void setTotalMileage(double totalMileage) { this.totalMileage = totalMileage; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

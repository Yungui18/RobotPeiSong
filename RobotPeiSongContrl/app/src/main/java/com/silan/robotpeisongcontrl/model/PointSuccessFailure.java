package com.silan.robotpeisongcontrl.model;

/**
 * 点位成败统计模型
 */
public class PointSuccessFailure {
    private String pointName; // 点位名称
    private int successCount; // 成功数
    private int failureCount; // 失败数

    // 构造函数
    public PointSuccessFailure(String pointName, int successCount, int failureCount) {
        this.pointName = pointName;
        this.successCount = successCount;
        this.failureCount = failureCount;
    }

    // Getter & Setter
    public String getPointName() { return pointName; }
    public int getSuccessCount() { return successCount; }
    public int getFailureCount() { return failureCount; }
}

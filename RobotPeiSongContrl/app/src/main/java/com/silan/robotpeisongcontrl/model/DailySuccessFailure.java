package com.silan.robotpeisongcontrl.model;

/**
 * 日期成败统计模型
 */
public class DailySuccessFailure {
    private String date;       // 日期（yyyy-MM-dd）
    private int successCount;  // 成功数
    private int failureCount;  // 失败数

    // 构造函数
    public DailySuccessFailure(String date, int successCount, int failureCount) {
        this.date = date;
        this.successCount = successCount;
        this.failureCount = failureCount;
    }

    // Getter & Setter
    public String getDate() { return date; }
    public int getSuccessCount() { return successCount; }
    public int getFailureCount() { return failureCount; }
}

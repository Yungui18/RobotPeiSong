package com.silan.robotpeisongcontrl.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 每日里程模型
 */
public class DailyMileage {
    private String date;    // 日期（yyyy-MM-dd）
    private double mileage; // 当日里程（米）

    public DailyMileage(long timestamp, double mileage) {
        this.date = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(timestamp));
        this.mileage = mileage;
    }

    // Getter & Setter
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public double getMileage() { return mileage; }
    public void setMileage(double mileage) { this.mileage = mileage; }
}

package com.silan.robotpeisongcontrl.model;

/**
 * 机器人状态信息实体类
 * 用于存储和管理机器人的各项实时状态参数，如电池电量、运行状态等
 * 提供参数的Getter和Setter方法，支持状态数据的读写操作
 */
public class RobotStatus {
    private double batteryPercentage;
    private String dockingStatus;
    private boolean isCharging;
    private boolean isDCConnected;
    private String powerStage;
    private String sleepMode;

    // 构造方法
    public RobotStatus() {}

    // Getter和Setter
    /**
     * 获取机器人电池百分比
     * @return 电池百分比（double类型）
     */
    public double getBatteryPercentage() {
        return batteryPercentage;
    }

    /**
     * 设置机器人电池百分比
     * @param batteryPercentage 电池百分比（double类型）
     */
    public void setBatteryPercentage(double batteryPercentage) {
        this.batteryPercentage = batteryPercentage;
    }

    public String getDockingStatus() {
        return dockingStatus;
    }

    public void setDockingStatus(String dockingStatus) {
        this.dockingStatus = dockingStatus;
    }

    public boolean isCharging() {
        return isCharging;
    }

    public void setCharging(boolean charging) {
        isCharging = charging;
    }

    public boolean isDCConnected() {
        return isDCConnected;
    }

    public void setDCConnected(boolean DCConnected) {
        isDCConnected = DCConnected;
    }

    public String getPowerStage() {
        return powerStage;
    }

    public void setPowerStage(String powerStage) {
        this.powerStage = powerStage;
    }

    public String getSleepMode() {
        return sleepMode;
    }

    public void setSleepMode(String sleepMode) {
        this.sleepMode = sleepMode;
    }
}

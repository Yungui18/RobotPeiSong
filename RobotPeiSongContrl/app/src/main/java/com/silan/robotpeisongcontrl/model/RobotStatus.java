package com.silan.robotpeisongcontrl.model;

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
    public double getBatteryPercentage() {
        return batteryPercentage;
    }

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

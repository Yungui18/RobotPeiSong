package com.silan.robotpeisongcontrl.model;

/**
 * 点位（POI）实体类
 * 用于定义配送或巡逻任务中的关键点位信息，如站点、仓门、目标位置等
 * 存储点位的唯一标识、坐标、显示名称等核心属性
 */
public class Poi {
    private String id;
    private String displayName;
    private double x;
    private double y;
    private double yaw;

    // 构造方法
    public Poi() {}

    // Getter和Setter
    /**
     * 获取POI的唯一标识ID
     * @return POI的ID字符串
     */
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取POI的显示名称
     * @return 显示名称字符串（如："仓门1"）
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 设置POI的显示名称
     * @param displayName 显示名称字符串（如："仓门1"）
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取POI的X坐标
     * @return X坐标值（double类型）
     */
    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getYaw() {
        return yaw;
    }

    public void setYaw(double yaw) {
        this.yaw = yaw;
    }
}

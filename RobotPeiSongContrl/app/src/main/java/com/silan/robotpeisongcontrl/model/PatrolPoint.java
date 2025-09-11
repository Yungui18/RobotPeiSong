package com.silan.robotpeisongcontrl.model;

/**
 * 巡逻点实体类
 * 用于定义机器人巡逻任务中的单个点位信息，关联基础POI信息和任务属性
 * 支持判断点位是否关联任务，并提供字符串描述格式化功能
 */
public class PatrolPoint {
    private Poi poi;
    private int task; // 0:无任务, 1-4:对应仓门

    public PatrolPoint(Poi poi, int task) {
        this.poi = poi;
        this.task = task;
    }

    public Poi getPoi() {
        return poi;
    }

    public int getTask() {
        return task;
    }

    public boolean hasTask() {
        return task > 0;
    }

    public void setPoi(Poi poi) {
        this.poi = poi;
    }

    public void setTask(int task) {
        this.task = task;
    }

    @Override
    public String toString() {
        return poi.getDisplayName() + (hasTask() ? "(仓门" + task + ")" : "");
    }
}
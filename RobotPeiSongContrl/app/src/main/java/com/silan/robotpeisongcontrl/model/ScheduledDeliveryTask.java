package com.silan.robotpeisongcontrl.model;

import java.util.Arrays;
import java.util.UUID;

/**
 * 定时配送任务实体类
 * 用于管理需要按计划执行的配送任务，包含任务ID、执行时间、目标点位等信息
 * 支持任务的创建、状态跟踪和参数管理
 */
public class ScheduledDeliveryTask {
    public static final int TYPE_POINT = 0;
    public static final int TYPE_ROUTE = 1;

    private String id;
    private int taskType;
    private Poi poi; // 点位配送使用
    private int schemeId; // 路线配送使用
    private boolean[] selectedDoors = new boolean[4];
    private int hour;
    private int minute;
    private boolean enabled;
    private long lastModified;
    private long triggerTime; // 任务触发的时间戳
    // 任务触发的时间戳
    /**
     * 构造方法：初始化定时配送任务
     * 确保每个任务实例都有唯一的UUID作为ID
     */
    public ScheduledDeliveryTask() {
        // 确保每个任务都有ID
        this.id = UUID.randomUUID().toString();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public int getTaskType() { return taskType; }
    public void setTaskType(int taskType) { this.taskType = taskType; }

    public Poi getPoi() { return poi; }
    public void setPoi(Poi poi) { this.poi = poi; }

    public int getSchemeId() { return schemeId; }
    public void setSchemeId(int schemeId) { this.schemeId = schemeId; }

    public boolean[] getSelectedDoors() { return selectedDoors; }
    public void setSelectedDoors(boolean[] selectedDoors) {
        this.selectedDoors = Arrays.copyOf(selectedDoors, selectedDoors.length);
    }

    public int getHour() { return hour; }
    public void setHour(int hour) { this.hour = hour; }

    public int getMinute() { return minute; }
    public void setMinute(int minute) { this.minute = minute; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(long triggerTime) {
        this.triggerTime = triggerTime;
    }
    public boolean isExpired() {
        return System.currentTimeMillis() > triggerTime + 2 * 60 * 60 * 1000; // 2小时过期
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    // 在保存任务时更新修改时间
    public void updateLastModified() {
        this.lastModified = System.currentTimeMillis();
    }
}

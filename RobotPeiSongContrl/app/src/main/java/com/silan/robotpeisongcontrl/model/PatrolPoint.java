package com.silan.robotpeisongcontrl.model;

public class PatrolPoint {
    private Poi poi;
    private int task; // 0:无任务, 1-6:对应仓门

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
package com.silan.robotpeisongcontrl.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ScheduledTask implements Serializable {
    private String id;
    private String name;
    private int type; // 0:点位配送, 1:路线配送
    private String poiId; // 仅点位配送使用
    private int schemeId; // 仅路线配送使用
    private List<Integer> doors = new ArrayList<>(); // 负责配送的仓门
    private Calendar triggerTime;
    private int priority; // 0:优先级A, 1:优先级B

    public ScheduledTask() {}

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }
    public String getPoiId() { return poiId; }
    public void setPoiId(String poiId) { this.poiId = poiId; }
    public int getSchemeId() { return schemeId; }
    public void setSchemeId(int schemeId) { this.schemeId = schemeId; }
    public List<Integer> getDoors() { return doors; }
    public void setDoors(List<Integer> doors) { this.doors = doors; }
    public Calendar getTriggerTime() { return triggerTime; }
    public void setTriggerTime(Calendar triggerTime) { this.triggerTime = triggerTime; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    @Override
    public String toString() {
        return name + " - " + getDoorInfo() + " - " + getTimeString();
    }

    private String getDoorInfo() {
        StringBuilder sb = new StringBuilder();
        for (int door : doors) {
            sb.append("仓门").append(door).append(" ");
        }
        return sb.toString().trim();
    }

    private String getTimeString() {
        return String.format("%02d:%02d", triggerTime.get(Calendar.HOUR_OF_DAY),
                triggerTime.get(Calendar.MINUTE));
    }
}
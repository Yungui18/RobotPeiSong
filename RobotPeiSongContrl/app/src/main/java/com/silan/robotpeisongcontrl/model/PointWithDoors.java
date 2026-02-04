package com.silan.robotpeisongcontrl.model;

import java.util.List;

/**
 * 点位与仓门的绑定实体（路线配送专用）
 * 每个点位绑定专属的仓门列表，支持多仓门
 */
public class PointWithDoors {
    private String pointId;       // 点位ID（与Poi的id一致）
    private String pointName;     // 点位名称（展示用）
    private List<Integer> doorIds;// 该点位需要开启的仓门硬件ID列表
    private List<String> doorNames;// 仓门名称（展示用，如"行1-1号（电机）"）

    // 空构造（Gson序列化需要）
    public PointWithDoors() {}

    // 全参构造
    public PointWithDoors(String pointId, String pointName, List<Integer> doorIds, List<String> doorNames) {
        this.pointId = pointId;
        this.pointName = pointName;
        this.doorIds = doorIds;
        this.doorNames = doorNames;
    }

    // Getter & Setter
    public String getPointId() { return pointId; }
    public void setPointId(String pointId) { this.pointId = pointId; }
    public String getPointName() { return pointName; }
    public void setPointName(String pointName) { this.pointName = pointName; }
    public List<Integer> getDoorIds() { return doorIds; }
    public void setDoorIds(List<Integer> doorIds) { this.doorIds = doorIds; }
    public List<String> getDoorNames() { return doorNames; }
    public void setDoorNames(List<String> doorNames) { this.doorNames = doorNames; }
}

package com.silan.robotpeisongcontrl.model;

import java.util.List;
import java.util.UUID;

/**
 * 路线配送方案（独立于巡游模块，路线配送专用）
 */
public class DeliveryRoutePlan {
    private String planId;          // 方案唯一ID（UUID生成）
    private String planName;        // 方案名称（用户自定义）
    private List<PointWithDoors> pointList; // 点位+仓门绑定列表（按配送顺序排序）
    private boolean isEnabled = true; // 方案启用状态

    // 空构造（Gson序列化需要）
    public DeliveryRoutePlan() {
        this.planId = UUID.randomUUID().toString(); // 自动生成唯一ID
    }

    // 带名称的构造
    public DeliveryRoutePlan(String planName, List<PointWithDoors> pointList) {
        this.planId = UUID.randomUUID().toString();
        this.planName = planName;
        this.pointList = pointList;
    }

    // ========== 仅新增这1个核心方法 ==========
    // 返回planId的hashCode，用于和task的schemeId匹配（唯一且不变）
    public int getSchemeId() {
        return planId.hashCode();
    }

    // 原有Getter & Setter（完全保留，无需修改）
    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public List<PointWithDoors> getPointList() { return pointList; }
    public void setPointList(List<PointWithDoors> pointList) { this.pointList = pointList; }
    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean enabled) { isEnabled = enabled; }
}

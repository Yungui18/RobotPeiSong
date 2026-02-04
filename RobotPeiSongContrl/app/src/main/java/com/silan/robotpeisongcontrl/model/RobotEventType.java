package com.silan.robotpeisongcontrl.model;

import java.util.HashSet;
import java.util.Set;

/**
 * 机器人事件类型枚举
 */
public enum RobotEventType {
    PATH_OCCUPIED("PATH OCCUPIED"),                // 行进路径被阻挡
    CURRENT_POSE_OCCUPIED("CURRENT_POSE_OCCUPIED"),// 当前机器人位姿被占据
    ON_DOCK("ON_DOCK"),                            // 机器人上桩
    OFF_DOCK("OFF DOCK"),                          // 机器人下桩
    PASS_THE_NARROW_CORRIDOR("PASS_THE_NARROW_CORRIDOR"),// 通过窄走廊
    POWER_OFF("POWER_OFF"),                        // 正在关机
    MOVE_TO_LANDING_POINT_FAILED("MOVE_TO LANDING_POINT FAILED"),// 前往充电桩失败
    SEARCH_DOCK_FAILED("SEARCH_DOCK_FAILED"),      // 找桩失败
    BRAKE_RELEASED("BRAKE_RELEASED"),              // 刹车释放按钮被按下
    BUMPER_TRIGGERED("BUMPER TRIGGERED");          // 碰撞传感器触发

    private final String type;

    RobotEventType(String type) {
        this.type = type;
    }

    // 获取接口返回的原始type字符串
    public String getType() {
        return type;
    }

    // 根据接口type字符串匹配枚举（兼容空格/大小写）
    public static RobotEventType fromType(String type) {
        if (type == null || type.isEmpty()) return null;
        String trimType = type.trim().replaceAll("\\s+", " ");
        for (RobotEventType event : values()) {
            if (event.getType().equals(trimType)) {
                return event;
            }
        }
        return null;
    }

    public static Set<String> getAllEventTypeCodes() {
        Set<String> typeSet = new HashSet<>();
        for (RobotEventType type : RobotEventType.values()) {
            typeSet.add(type.getType());
        }
        return typeSet;
    }
}
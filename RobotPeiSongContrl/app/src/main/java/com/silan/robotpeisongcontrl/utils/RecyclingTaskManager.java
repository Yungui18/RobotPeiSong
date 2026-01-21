package com.silan.robotpeisongcontrl.utils;

import com.silan.robotpeisongcontrl.model.Poi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 全局回收任务管理器，维护回收任务记录和回收点位配置（遵循项目单例规范）
 */
public class RecyclingTaskManager {
    private static RecyclingTaskManager instance;
    // 已执行的回收任务记录（点位+仓门）
    private List<RecyclingTaskRecord> executedTaskRecords;
    // 回收点位对象（任务结束后返回该点位）
    private Poi recyclePoi;

    // 回收任务记录实体类（内部类，贴合项目代码结构）
    public static class RecyclingTaskRecord {
        private String pointName; // 点位名称
        private List<Integer> doorIds; // 关联仓门ID列表

        public RecyclingTaskRecord(String pointName, List<Integer> doorIds) {
            this.pointName = pointName;
            this.doorIds = new ArrayList<>(doorIds);
        }

        // 格式化输出「点位+仓门」，用于页面展示
        public String getFormattedRecord() {
            StringBuilder doorStr = new StringBuilder();
            for (int i = 0; i < doorIds.size(); i++) {
                doorStr.append(doorIds.get(i));
                if (i < doorIds.size() - 1) {
                    doorStr.append(",");
                }
            }
            return pointName + "+" + doorStr + "号仓门";
        }

        // getter 方法
        public String getPointName() {
            return pointName;
        }

        public List<Integer> getDoorIds() {
            return doorIds;
        }
    }

    // 私有构造，防止外部实例化
    private RecyclingTaskManager() {
        executedTaskRecords = new ArrayList<>();
    }

    // 同步锁单例，与项目其他工具类保持一致
    public static synchronized RecyclingTaskManager getInstance() {
        if (instance == null) {
            instance = new RecyclingTaskManager();
        }
        return instance;
    }

    // ******************** 核心对外方法 ********************
    /**
     * 添加一条回收任务记录
     */
    public void addTaskRecord(String pointName, List<Integer> doorIds) {
        if (pointName == null || doorIds == null || doorIds.isEmpty()) {
            return;
        }
        executedTaskRecords.add(new RecyclingTaskRecord(pointName, doorIds));
    }

    /**
     * 获取所有格式化后的任务记录（用于文本框展示）
     */
    public List<String> getFormattedAllTaskRecords() {
        List<String> formattedList = new ArrayList<>();
        for (RecyclingTaskRecord record : executedTaskRecords) {
            formattedList.add(record.getFormattedRecord());
        }
        return formattedList;
    }

    /**
     * 获取所有执行过任务的仓门ID（去重，用于一键开门）
     */
    public Set<Integer> getExecutedDoorNumbers() {
        Set<Integer> doorSet = new HashSet<>();
        for (RecyclingTaskRecord record : executedTaskRecords) {
            doorSet.addAll(record.getDoorIds());
        }
        return doorSet;
    }

    /**
     * 清空任务记录（回桩完成后重置）
     */
    public void clearAllTaskRecords() {
        executedTaskRecords.clear();
        recyclePoi = null;
    }

    // 回收点位相关 getter/setter
    public Poi getRecyclePoi() {
        return recyclePoi;
    }

    public void setRecyclePoi(Poi recyclePoi) {
        this.recyclePoi = recyclePoi;
    }
}
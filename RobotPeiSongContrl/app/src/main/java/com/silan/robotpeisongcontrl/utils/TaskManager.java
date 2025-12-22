package com.silan.robotpeisongcontrl.utils;

import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class TaskManager {
    private static TaskManager instance;
    private Poi currentPoi;
    private final Queue<Poi> taskQueue = new LinkedList<>();
    private final Set<String> assignedPointNames = new HashSet<>();
    private final Map<String, List<Integer>> pointToDoorsMap = new HashMap<>();
    private static final Queue<ScheduledDeliveryTask> pendingScheduledTasks = new LinkedList<>();

    private TaskManager() {}

    // 添加点位与多个仓门的关联
    public void addPointWithDoors(Poi poi, List<Integer> doorIds) {
        String pointName = poi.getDisplayName();
        if (!assignedPointNames.contains(pointName)) {
            taskQueue.add(poi);
            assignedPointNames.add(pointName);
            pointToDoorsMap.put(pointName, new ArrayList<>(doorIds));
        }
    }

    // 根据任务获取关联的所有仓门ID
    public List<Integer> getDoorIdsForPoint(String pointName) {
        return pointToDoorsMap.getOrDefault(pointName, new ArrayList<>());
    }

    // 新增：获取当前所有任务列表（供跳转时传递）
    public List<Poi> getTasks() {
        return new ArrayList<>(taskQueue);
    }

    // 新增：获取点位-仓门映射（供跳转时传递）
    public Map<String, List<Integer>> getPointDoorMapping() {
        return new HashMap<>(pointToDoorsMap);
    }

    public static void addPendingScheduledTask(ScheduledDeliveryTask task) {
        pendingScheduledTasks.add(task);
    }

    public static boolean hasPendingScheduledTask() {
        return !pendingScheduledTasks.isEmpty();
    }

    public static ScheduledDeliveryTask getNextPendingScheduledTask() {
        return pendingScheduledTasks.poll();
    }

    public static synchronized TaskManager getInstance() {
        if (instance == null) {
            instance = new TaskManager();
        }
        return instance;
    }

    public boolean isPointAssigned(String pointName) {
        return assignedPointNames.contains(pointName);
    }

    public void addTask(Poi poi) {
        if (!assignedPointNames.contains(poi.getDisplayName())) {
            taskQueue.add(poi);
            assignedPointNames.add(poi.getDisplayName());
        }
    }

    public void removeTask(Poi poi) {
        taskQueue.remove(poi);
        String pointName = poi.getDisplayName();
        assignedPointNames.remove(pointName);
        pointToDoorsMap.remove(pointName);
    }

    public Poi getNextTask() {
        currentPoi = taskQueue.poll();
        return currentPoi;
    }

    public Poi getCurrentPoi() {
        return currentPoi;
    }

    public void clearTasks() {
        taskQueue.clear();
        assignedPointNames.clear();
        pointToDoorsMap.clear();
    }

    public boolean hasTasks() {
        return !taskQueue.isEmpty();
    }

    public int taskCount() {
        return taskQueue.size();
    }
}

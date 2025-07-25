package com.silan.robotpeisongcontrl.utils;

import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class TaskManager {
    private static TaskManager instance;
    private Poi currentPoi;
    private final Queue<Poi> taskQueue = new LinkedList<>(); // 现在存储Poi对象
    private final Set<String> assignedPointNames = new HashSet<>();
    private static final Queue<ScheduledDeliveryTask> pendingScheduledTasks = new LinkedList<>();


    private TaskManager() {}

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
        assignedPointNames.remove(poi.getDisplayName());
    }

    public Poi getNextTask() {
        currentPoi = taskQueue.poll(); // 设置当前点位
        return currentPoi;
    }
    public Poi getCurrentPoi() {
        return currentPoi;
    }


    public void clearTasks() {
        taskQueue.clear();
        assignedPointNames.clear();
    }

    public boolean hasTasks() {
        return !taskQueue.isEmpty();
    }

    public int taskCount() {
        return taskQueue.size();
    }
}

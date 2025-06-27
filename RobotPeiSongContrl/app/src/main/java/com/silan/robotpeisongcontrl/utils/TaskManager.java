package com.silan.robotpeisongcontrl.utils;

import com.silan.robotpeisongcontrl.model.Poi;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class TaskManager {
    private static TaskManager instance;
    private final Queue<Poi> taskQueue = new LinkedList<>(); // 现在存储Poi对象
    private final Set<String> assignedPointNames = new HashSet<>();


    private TaskManager() {}

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
        taskQueue.add(poi);
        assignedPointNames.add(poi.getDisplayName());
    }
    public void removeTask(Poi poi) {
        taskQueue.remove(poi);
        assignedPointNames.remove(poi.getDisplayName());
    }

    public Poi getNextTask() {
        return taskQueue.poll();
    }

    public void clearTasks() {
        taskQueue.clear();
    }

    public boolean hasTasks() {
        return !taskQueue.isEmpty();
    }

    public int taskCount() {
        return taskQueue.size();
    }
}

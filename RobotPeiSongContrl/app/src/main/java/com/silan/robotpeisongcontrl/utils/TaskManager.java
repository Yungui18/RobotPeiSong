package com.silan.robotpeisongcontrl.utils;

import java.util.LinkedList;
import java.util.Queue;

public class TaskManager {
    private static TaskManager instance;
    private final Queue<String> taskQueue = new LinkedList<>();

    private TaskManager() {}

    public static synchronized TaskManager getInstance() {
        if (instance == null) {
            instance = new TaskManager();
        }
        return instance;
    }

    public void addTask(String poiName) {
        taskQueue.add(poiName);
    }

    public String getNextTask() {
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

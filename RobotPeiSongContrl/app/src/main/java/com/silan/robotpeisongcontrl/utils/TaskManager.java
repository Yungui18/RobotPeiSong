package com.silan.robotpeisongcontrl.utils;

import com.silan.robotpeisongcontrl.model.Poi;

import java.util.LinkedList;
import java.util.Queue;

public class TaskManager {
    private static TaskManager instance;
    private final Queue<Poi> taskQueue = new LinkedList<>(); // 现在存储Poi对象

    private TaskManager() {}

    public static synchronized TaskManager getInstance() {
        if (instance == null) {
            instance = new TaskManager();
        }
        return instance;
    }

    public void addTask(Poi poi) {
        taskQueue.add(poi);
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

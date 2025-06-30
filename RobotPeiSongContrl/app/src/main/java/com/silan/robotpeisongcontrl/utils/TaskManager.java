package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.silan.robotpeisongcontrl.model.PatrolPoint;
import com.silan.robotpeisongcontrl.model.PatrolScheme;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.ScheduledTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class TaskManager {
    private static TaskManager instance;
    private final Queue<Poi> taskQueue = new LinkedList<>(); // 现在存储Poi对象
    private Context context;
    private final Set<String> assignedPointNames = new HashSet<>();


    private TaskManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized TaskManager getInstance(Context context) {
        if (instance == null) {
            instance = new TaskManager(context);
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
        return taskQueue.poll();
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
    public void executeScheduledTask(ScheduledTask task) {
        // 检查当前是否有任务在执行
        boolean hasCurrentTask = hasTasks();

        // 优先级A：中断当前任务，执行定时任务
        if (task.getPriority() == 0 && hasCurrentTask) {
            // 保存当前任务状态
            List<Poi> currentTasks = new ArrayList<>(taskQueue);
            taskQueue.clear();

            // 添加定时任务
            addScheduledTask(task);

            // 执行完成后恢复原任务
            for (Poi poi : currentTasks) {
                taskQueue.add(poi);
            }
        }
        // 优先级B：等待当前任务完成
        else if (task.getPriority() == 1 && hasCurrentTask) {
            // 等待当前任务完成后执行
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                addScheduledTask(task);
            }, 5000); // 每5秒检查一次任务是否完成
        }
        // 无任务在执行，直接执行
        else {
            addScheduledTask(task);
        }
    }

    private void addScheduledTask(ScheduledTask task) {
        if (task.getType() == 0) { // 点位配送
            Poi poi = new Poi();
            poi.setId(task.getPoiId());
            poi.setDisplayName(task.getName());
            addTask(poi);
        } else { // 路线配送
            // 使用成员变量context
            PatrolScheme scheme = PatrolSchemeManager.loadScheme(context, task.getSchemeId());
            if (scheme != null) {
                for (PatrolPoint point : scheme.getPoints()) {
                    addTask(point.getPoi());
                }
            }
        }
    }
}

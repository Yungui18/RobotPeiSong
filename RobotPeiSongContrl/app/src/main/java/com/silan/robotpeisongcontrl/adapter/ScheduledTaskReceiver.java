package com.silan.robotpeisongcontrl.adapter;

import static android.content.Context.MODE_PRIVATE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.silan.robotpeisongcontrl.model.ScheduledTask;
import com.silan.robotpeisongcontrl.utils.ScheduledTaskManager;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.util.List;

public class ScheduledTaskReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String taskId = intent.getStringExtra("task_id");
        List<ScheduledTask> tasks = ScheduledTaskManager.loadTasks(context);
        // 检查定时配送是否启用
        SharedPreferences prefs = context.getSharedPreferences("ScheduledDeliveryPrefs", MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("enabled", false);
        if (!enabled) {
            return; // 开关关闭，不执行任务
        }
        for (ScheduledTask task : tasks) {
            if (task.getId().equals(taskId)) {
                // 使用带Context参数的getInstance
                TaskManager taskManager = TaskManager.getInstance(context);
                taskManager.executeScheduledTask(task);
                break;
            }
        }
    }
}
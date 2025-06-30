package com.silan.robotpeisongcontrl.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.silan.robotpeisongcontrl.adapter.ScheduledTaskReceiver;
import com.silan.robotpeisongcontrl.model.ScheduledTask;
import com.silan.robotpeisongcontrl.utils.ScheduledTaskManager;

import java.util.List;

public class ScheduledTaskService extends Service {
    private AlarmManager alarmManager;

    @Override
    public void onCreate() {
        super.onCreate();
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 只在开关开启时调度任务
        if (isScheduledDeliveryEnabled()) {
            scheduleAllTasks();
        }
        return START_STICKY;
    }

    private boolean isScheduledDeliveryEnabled() {
        SharedPreferences prefs = getSharedPreferences("ScheduledDeliveryPrefs", MODE_PRIVATE);
        return prefs.getBoolean("enabled", false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 服务停止时取消所有定时
        cancelAllScheduledTasks();
    }

    private void scheduleAllTasks() {
        List<ScheduledTask> tasks = ScheduledTaskManager.loadTasks(this);
        for (ScheduledTask task : tasks) {
            scheduleTask(task);
        }
    }

    private void scheduleTask(ScheduledTask task) {
        Intent intent = new Intent(this, ScheduledTaskReceiver.class);
        intent.putExtra("task_id", task.getId());

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        } else {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, task.getId().hashCode(), intent, flags);

        // 设置定时触发
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                task.getTriggerTime().getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, // 每天重复
                pendingIntent);
    }

    private void cancelAllScheduledTasks() {
        List<ScheduledTask> tasks = ScheduledTaskManager.loadTasks(this);
        for (ScheduledTask task : tasks) {
            cancelScheduledTask(task);
        }
    }

    private void cancelScheduledTask(ScheduledTask task) {
        Intent intent = new Intent(this, ScheduledTaskReceiver.class);

        // 修复：添加 FLAG_IMMUTABLE 或 FLAG_MUTABLE
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        } else {
            flags |= PendingIntent.FLAG_MUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, task.getId().hashCode(), intent, flags);

        alarmManager.cancel(pendingIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
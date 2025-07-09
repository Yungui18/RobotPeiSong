package com.silan.robotpeisongcontrl.utils;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.receiver.ScheduledDeliveryReceiver;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ScheduledDeliveryManager {
    private static final String TAG = "ScheduledDeliveryManager";
    private static final String PREFS_NAME = "scheduled_tasks";
    public static final String ACTION_DELIVERY_ALARM = "com.silan.robotpeisongcontrl.action.DELIVERY_ALARM";
    private static final Gson gson = new Gson();
    private static final Type TASK_LIST_TYPE = new TypeToken<List<ScheduledDeliveryTask>>(){}.getType();

    public static void saveTask(Context context, ScheduledDeliveryTask task) {
        List<ScheduledDeliveryTask> tasks = loadAllTasks(context);

        // 添加或更新任务
        boolean found = false;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equals(task.getId())) {
                tasks.set(i, task);
                found = true;
                break;
            }
        }

        if (!found) {
            tasks.add(task);
        }

        // 保存整个任务列表
        saveTaskList(context, tasks);
    }

    public static void deleteTask(Context context, String taskId) {
        List<ScheduledDeliveryTask> tasks = loadAllTasks(context);
        Iterator<ScheduledDeliveryTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getId().equals(taskId)) {
                iterator.remove();
                break;
            }
        }
        saveTaskList(context, tasks);
        cancelTask(context, taskId);
    }

    private static void saveTaskList(Context context, List<ScheduledDeliveryTask> tasks) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        String json = gson.toJson(tasks);
        editor.putString("tasks", json);
        editor.apply();
    }

    public static List<ScheduledDeliveryTask> loadAllTasks(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString("tasks", "[]");

        try {
            return gson.fromJson(json, TASK_LIST_TYPE);
        } catch (JsonSyntaxException e) {
            Log.e("ScheduledDelivery", "Failed to parse tasks", e);
            // 返回空列表而不是null
            return new ArrayList<>();
        }
    }

    public static ScheduledDeliveryTask loadTask(Context context, String taskId) {
        List<ScheduledDeliveryTask> tasks = loadAllTasks(context);
        for (ScheduledDeliveryTask task : tasks) {
            if (task.getId().equals(taskId)) {
                return task;
            }
        }
        return null;
    }

    public static void scheduleTask(Context context, ScheduledDeliveryTask task) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !ExactAlarmPermissionHelper.canScheduleExactAlarms(context)) {
            throw new SecurityException("Missing SCHEDULE_EXACT_ALARM permission");
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ScheduledDeliveryReceiver.class);
        intent.setAction(ACTION_DELIVERY_ALARM); // 设置明确的 action
        intent.putExtra("task_id", task.getId());

        int requestCode = task.getId().hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 设置触发时间
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, task.getHour());
        calendar.set(Calendar.MINUTE, task.getMinute());
        calendar.set(Calendar.SECOND, 0);
//        calendar.set(Calendar.MILLISECOND, 0);

        // 如果时间已过，设置为明天
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        // 使用更可靠的 API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        } else {
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }

        Log.d("ScheduledDelivery", "Task scheduled for " + task.getHour() + ":" + task.getMinute());
    }

    public static void cancelTask(Context context, String taskId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, ScheduledDeliveryReceiver.class);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.cancel(pendingIntent);
    }
    public static boolean tryScheduleTask(Context context, ScheduledDeliveryTask task) {
        try {
            scheduleTask(context, task);
            return true;
        } catch (SecurityException e) {
            Log.e("ScheduledDelivery", "Permission denied for exact alarms", e);
            return false;
        }
    }
}

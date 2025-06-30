package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.ScheduledTask;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ScheduledTaskManager {
    private static final String PREFS_NAME = "ScheduledTasks";
    private static final String KEY_TASKS = "tasks";

    public static void saveTask(Context context, ScheduledTask task) {
        List<ScheduledTask> tasks = loadTasks(context);
        tasks.add(task);
        saveAllTasks(context, tasks);
    }

    public static void deleteTask(Context context, String taskId) {
        List<ScheduledTask> tasks = loadTasks(context);
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equals(taskId)) {
                tasks.remove(i);
                break;
            }
        }
        saveAllTasks(context, tasks);
    }

    public static List<ScheduledTask> loadTasks(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_TASKS, null);
        if (json == null) {
            return new ArrayList<>();
        }

        Gson gson = new Gson();
        Type type = new TypeToken<List<ScheduledTask>>(){}.getType();
        List<ScheduledTask> tasks = gson.fromJson(json, type);
        return tasks != null ? tasks : new ArrayList<>();
    }

    private static void saveAllTasks(Context context, List<ScheduledTask> tasks) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String json = gson.toJson(tasks);
        editor.putString(KEY_TASKS, json);
        editor.apply();
    }
}

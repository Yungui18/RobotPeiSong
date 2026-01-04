package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

// 任务成功记录（和DeliveryFailureManager对应）
public class TaskSuccessManager {
    private static final String PREFS_NAME = "task_success";
    private static final String KEY_SUCCESS_LIST = "success_list";
    private static final Gson gson = new Gson();

    // 成功任务模型（内部类）
    public static class TaskSuccess {
        private String pointName; // 点位名称
        private long timestamp;   // 完成时间戳
        private String date;      // 日期（yyyy-MM-dd）

        public TaskSuccess(String pointName, long timestamp) {
            this.pointName = pointName;
            this.timestamp = timestamp;
            this.date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                    .format(new java.util.Date(timestamp));
        }

        // Getter
        public String getPointName() { return pointName; }
        public String getDate() { return date; }
    }

    // 添加成功任务记录
    public static void addSuccess(Context context, String pointName) {
        List<TaskSuccess> list = loadAllSuccess(context);
        list.add(new TaskSuccess(pointName, System.currentTimeMillis()));
        saveAllSuccess(context, list);
    }

    // 加载所有成功记录
    public static List<TaskSuccess> loadAllSuccess(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_SUCCESS_LIST, "[]");
        Type type = new TypeToken<List<TaskSuccess>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // 保存所有成功记录
    private static void saveAllSuccess(Context context, List<TaskSuccess> list) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SUCCESS_LIST, gson.toJson(list)).apply();
    }

    // 清空成功记录
    public static void clearSuccess(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}

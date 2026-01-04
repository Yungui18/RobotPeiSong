package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.DailyMileage;
import com.silan.robotpeisongcontrl.model.MileageResponse;

import java.lang.reflect.Type;
import java.util.List;

public class MileageManager {
    private static final String PREFS_NAME = "mileage_data";
    private static final String KEY_DAILY_MILEAGE = "daily_mileage";
    private static final String KEY_LAST_TOTAL = "last_total_mileage";
    private static final Gson gson = new Gson();

    // 保存每日里程数据
    public static void saveDailyMileage(Context context, DailyMileage dailyMileage) {
        List<DailyMileage> list = loadDailyMileage(context);
        // 检查是否已有当日数据，有则更新，无则添加
        boolean updated = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getDate().equals(dailyMileage.getDate())) {
                list.set(i, dailyMileage);
                updated = true;
                break;
            }
        }
        if (!updated) {
            list.add(dailyMileage);
        }
        // 保存到SP
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_DAILY_MILEAGE, gson.toJson(list)).apply();
    }

    // 加载所有每日里程数据
    public static List<DailyMileage> loadDailyMileage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_DAILY_MILEAGE, "[]");
        Type type = new TypeToken<List<DailyMileage>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // 处理里程API数据，计算当日增量
    public static void processMileageResponse(Context context, MileageResponse response) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        double lastTotal = prefs.getFloat(KEY_LAST_TOTAL, 0f);
        double currentTotal = response.getTotalMileage();
        double dailyIncrement = currentTotal - lastTotal;

        // 保存当日增量
        if (dailyIncrement > 0) {
            DailyMileage dailyMileage = new DailyMileage(response.getTimestamp(), dailyIncrement);
            saveDailyMileage(context, dailyMileage);
        }

        // 更新最后一次总里程
        prefs.edit().putFloat(KEY_LAST_TOTAL, (float) currentTotal).apply();
    }

    // 清空里程数据
    public static void clearMileage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}

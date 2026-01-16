package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.DailyMileage;
import com.silan.robotpeisongcontrl.model.MileageResponse;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MileageManager {
    private static final String PREFS_NAME = "mileage_data";
    private static final String KEY_DAILY_MILEAGE = "daily_mileage";
    private static final String KEY_LAST_TOTAL = "last_total_mileage";
    private static final String KEY_FIRST_INIT = "is_first_mileage_init"; // 首次初始化标记
    private static final Gson gson = new Gson();

    // 保存每日里程数据（新增Context非空校验）
    public static void saveDailyMileage(Context context, DailyMileage dailyMileage) {
        if (context == null) {
            return; // Context为空直接返回，避免崩溃
        }
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

    // 加载所有每日里程数据（核心修复：Context非空校验）
    public static List<DailyMileage> loadDailyMileage(Context context) {
        // 关键修复：Context为空时返回空列表，而非直接调用getSharedPreferences
        if (context == null) {
            return new ArrayList<>();
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_DAILY_MILEAGE, "[]");
        Type type = new TypeToken<List<DailyMileage>>() {}.getType();
        return gson.fromJson(json, type);
    }

    // 处理里程API数据（新增Context非空校验）
    public static void processMileageResponse(Context context, MileageResponse response) {
        if (context == null || response == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isFirstInit = prefs.getBoolean(KEY_FIRST_INIT, false);
        double currentTotal = response.getTotalMileage();
        double lastTotal;

        if (!isFirstInit) {
            // 首次初始化：将当前总里程设为初始值，当日增量=0
            lastTotal = currentTotal;
            prefs.edit()
                    .putBoolean(KEY_FIRST_INIT, true)
                    .putFloat(KEY_LAST_TOTAL, (float) currentTotal)
                    .apply();
        } else {
            // 非首次：读取上次总里程
            lastTotal = prefs.getFloat(KEY_LAST_TOTAL, 0f);
        }

        // 计算当日增量（首次为0，后续正常计算）
        double dailyIncrement = currentTotal - lastTotal;

        // 保存当日增量（增量>0时保存）
        if (dailyIncrement > 0) {
            DailyMileage dailyMileage = new DailyMileage(response.getTimestamp(), dailyIncrement);
            saveDailyMileage(context, dailyMileage);
        }

        // 非首次时更新最后一次总里程
        if (isFirstInit) {
            prefs.edit().putFloat(KEY_LAST_TOTAL, (float) currentTotal).apply();
        }
    }

    // 清空里程数据（新增Context非空校验）
    public static void clearMileage(Context context) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .clear()
                .remove(KEY_FIRST_INIT) // 重置首次初始化标记
                .apply();
    }
}

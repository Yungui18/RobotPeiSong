package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.DeliveryFailure;

import java.lang.reflect.Type;
import java.util.List;

public class DeliveryFailureManager {
    private static final String PREFS_NAME = "delivery_failures";
    private static final Gson gson = new Gson();

    public static void addFailure(Context context, DeliveryFailure failure) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        List<DeliveryFailure> failures = loadAllFailures(context);

        // 添加到列表
        failures.add(failure);

        // 保存更新
        saveAllFailures(prefs, failures);
    }

    public static List<DeliveryFailure> loadAllFailures(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString("failures", "[]");

        Type type = new TypeToken<List<DeliveryFailure>>(){}.getType();
        return gson.fromJson(json, type);
    }

    public static void clearFailures(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("failures", "[]").apply();
    }

    private static void saveAllFailures(SharedPreferences prefs, List<DeliveryFailure> failures) {
        String json = gson.toJson(failures);
        prefs.edit().putString("failures", json).apply();
    }
}

package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.DeliveryRoutePlan;

import java.util.List;

public class DeliveryRoutePlanManager {
    private static final String PREFS_NAME = "DeliveryRoutePlans";
    private static final String PLANS_KEY = "route_plans";

    // 保存单个方案
    public static void savePlan(Context context, DeliveryRoutePlan plan) {
        List<DeliveryRoutePlan> planList = loadAllPlans(context);
        planList.removeIf(p -> p.getPlanId().equals(plan.getPlanId()));
        planList.add(plan);
        saveAllPlans(context, planList);
    }

    // 加载单个方案（根据planId）
    public static DeliveryRoutePlan loadPlan(Context context, String planId) {
        List<DeliveryRoutePlan> planList = loadAllPlans(context);
        for (DeliveryRoutePlan plan : planList) {
            if (plan.getPlanId().equals(planId)) {
                return plan;
            }
        }
        return null;
    }

    // 加载所有方案
    public static List<DeliveryRoutePlan> loadAllPlans(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(PLANS_KEY, "[]");
        return new Gson().fromJson(json, new TypeToken<List<DeliveryRoutePlan>>() {}.getType());
    }

    // 保存所有方案
    private static void saveAllPlans(Context context, List<DeliveryRoutePlan> planList) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = new Gson().toJson(planList);
        prefs.edit().putString(PLANS_KEY, json).apply();
    }
}

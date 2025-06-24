package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.PatrolPoint;
import com.silan.robotpeisongcontrl.model.PatrolScheme;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatrolSchemeManager {
    private static final String PREFS_NAME = "PatrolSchemes";

    public static void saveScheme(Context context, PatrolScheme scheme) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();

        // 获取所有方案
        Map<Integer, PatrolScheme> schemes = loadSchemes(context);

        // 添加或更新当前方案
        schemes.put(scheme.getSchemeId(), scheme);

        // 保存整个方案集合
        String json = gson.toJson(schemes);
        editor.putString("schemes", json);
        editor.apply();
    }

    public static PatrolScheme loadScheme(Context context, int schemeId) {
        Map<Integer, PatrolScheme> schemes = loadSchemes(context);
        return schemes.get(schemeId);
    }

    public static void deleteScheme(Context context, int schemeId) {
        Map<Integer, PatrolScheme> schemes = loadSchemes(context);
        schemes.remove(schemeId);

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();
        String json = gson.toJson(schemes);
        editor.putString("schemes", json);
        editor.apply();
    }

    public static Map<Integer, PatrolScheme> loadSchemes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString("schemes", null);
        if (json == null) {
            return new HashMap<>();
        }

        Gson gson = new Gson();
        Type type = new TypeToken<Map<Integer, PatrolScheme>>(){}.getType();
        Map<Integer, PatrolScheme> schemes = gson.fromJson(json, type);

        // 确保返回有效的Map
        return schemes != null ? schemes : new HashMap<>();
    }
}
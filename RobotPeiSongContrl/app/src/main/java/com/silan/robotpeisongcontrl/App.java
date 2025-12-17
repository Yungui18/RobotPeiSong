package com.silan.robotpeisongcontrl;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.silan.robotpeisongcontrl.utils.NavBarManager;

import java.util.Locale;

public class App extends Application {
    // 新增：全局Application实例（核心，供NavBarManager获取上下文）
    private static App sInstance;

    private Handler standbyHandler = new Handler();
    private Runnable standbyRunnable;
    private long lastInteractionTime = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化全局实例
        sInstance = this;

        // 应用启动时设置语言（原有逻辑）
        updateLanguage();
        // 清理无效的定时任务数据（原有逻辑）
        clearInvalidScheduledData();

        // 初始化导航栏管理器（默认隐藏导航栏）
        NavBarManager.getInstance(this);
    }

    // 新增：提供全局上下文获取方法
    public static App getInstance() {
        return sInstance;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        // 新增：应用退出时释放导航栏管理器资源
        if (sInstance != null) {
            NavBarManager.getInstance(sInstance).release(sInstance);
        }
    }

    // 原有逻辑（无修改）
    private void clearInvalidScheduledData() {
        SharedPreferences prefs = getSharedPreferences("scheduled_tasks", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        // 删除旧的错误格式数据
        for (String key : prefs.getAll().keySet()) {
            if (!key.equals("tasks")) {
                editor.remove(key);
            }
        }

        // 尝试修复任务列表
        String tasksJson = prefs.getString("tasks", "[]");
        if (!tasksJson.startsWith("[")) {
            editor.putString("tasks", "[]");
        }

        editor.apply();
    }

    // 原有逻辑（无修改）
    private void updateLanguage() {
        SharedPreferences prefs = getSharedPreferences("personalization_prefs", MODE_PRIVATE);
        String langCode = prefs.getString("selected_language", "zh");
        setLocale(this, langCode);
    }

    // 原有逻辑（无修改）
    private void setLocale(Context context, String langCode) {
        Locale locale = getLocaleFromCode(langCode);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }

        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    // 原有逻辑（无修改）
    private Locale getLocaleFromCode(String langCode) {
        switch (langCode) {
            case "zh": return Locale.SIMPLIFIED_CHINESE;
            case "zh_rTW": return Locale.TRADITIONAL_CHINESE;
            case "en": return Locale.ENGLISH;
            case "ko": return Locale.KOREAN;
            case "ja": return Locale.JAPANESE;
            default: return Locale.getDefault();
        }
    }
}

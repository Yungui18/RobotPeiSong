package com.silan.robotpeisongcontrl;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import com.silan.robotpeisongcontrl.service.ScheduledTaskService;

import java.util.Locale;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // 应用启动时设置语言
        updateLanguage();
        // 只在开关开启时启动服务
        if (isScheduledDeliveryEnabled()) {
            startService(new Intent(this, ScheduledTaskService.class));
        }
    }

    private boolean isScheduledDeliveryEnabled() {
        SharedPreferences prefs = getSharedPreferences("ScheduledDeliveryPrefs", MODE_PRIVATE);
        return prefs.getBoolean("enabled", false);
    }

    private void updateLanguage() {
        SharedPreferences prefs = getSharedPreferences("personalization_prefs", MODE_PRIVATE);
        String langCode = prefs.getString("selected_language", "zh");
        setLocale(this, langCode);
    }

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

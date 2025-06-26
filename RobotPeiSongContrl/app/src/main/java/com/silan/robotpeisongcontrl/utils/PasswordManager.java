package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PasswordManager {
    private static final String PREFS_NAME = "password_prefs";
    private static final String KEY_SETTINGS_PASSWORD = "settings_password";
    public static final String DEFAULT_PASSWORD = "1234";

    public static void saveSettingsPassword(Context context, String password) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SETTINGS_PASSWORD, password).apply();
    }

    public static String getSettingsPassword(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SETTINGS_PASSWORD, DEFAULT_PASSWORD);
    }
}

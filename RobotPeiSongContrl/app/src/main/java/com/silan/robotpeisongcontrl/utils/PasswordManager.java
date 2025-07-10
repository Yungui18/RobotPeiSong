package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PasswordManager {
    private static final String PREFS_NAME = "password_prefs";
    private static final String KEY_SETTINGS_PASSWORD = "settings_password";
    private static final String KEY_SUPER_ADMIN_PASSWORD = "super_admin_password";

    public static final String DEFAULT_PASSWORD = "1234";
    public static final String DEFAULT_SUPER_ADMIN_PASSWORD = "123456";
    public static final String PASSWORD_TYPE_SETTINGS = "settings_password";
    public static final String PASSWORD_TYPE_SUPER_ADMIN = "super_admin_password";

    public static void saveSettingsPassword(Context context, String password) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SETTINGS_PASSWORD, password).apply();
    }

    public static String getSettingsPassword(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SETTINGS_PASSWORD, DEFAULT_PASSWORD);
    }

    public static void saveSuperAdminPassword(Context context, String password) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SUPER_ADMIN_PASSWORD, password).apply();
    }

    public static String getSuperAdminPassword(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_SUPER_ADMIN_PASSWORD, DEFAULT_SUPER_ADMIN_PASSWORD);
    }
}

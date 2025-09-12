package com.silan.robotpeisongcontrl.model;

import android.util.Log;

public class DebugLogger {
    public static void v(String tag, String message) {
        if (Constants.DEBUG_MODE && Constants.CURRENT_LOG_LEVEL <= Constants.LOG_LEVEL_VERBOSE) {
            Log.v(tag, message);
        }
    }

    public static void d(String tag, String message) {
        if (Constants.DEBUG_MODE && Constants.CURRENT_LOG_LEVEL <= Constants.LOG_LEVEL_DEBUG) {
            Log.d(tag, message);
        }
    }

    public static void i(String tag, String message) {
        if (Constants.DEBUG_MODE && Constants.CURRENT_LOG_LEVEL <= Constants.LOG_LEVEL_INFO) {
            Log.i(tag, message);
        }
    }

    public static void e(String tag, String message) {
        Log.e(tag, message); // 错误日志总是显示
    }

}

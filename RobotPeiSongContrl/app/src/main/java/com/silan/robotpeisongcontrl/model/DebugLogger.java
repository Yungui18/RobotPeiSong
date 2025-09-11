package com.silan.robotpeisongcontrl.model;

import android.util.Log;

/**
 * 调试日志工具类
 * 提供分级别的日志打印功能（VERBOSE/DEBUG/INFO/ERROR），受全局调试模式控制
 * 用于开发阶段的调试信息输出，生产环境可通过配置关闭非必要日志
 */
public class DebugLogger {
    /**
     * 打印VERBOSE级别的日志
     * 仅在调试模式开启且当前日志级别允许时打印
     * @param tag 日志标签
     * @param message 日志内容
     */
    public static void v(String tag, String message) {
        if (Constants.DEBUG_MODE && Constants.CURRENT_LOG_LEVEL <= Constants.LOG_LEVEL_VERBOSE) {
            Log.v(tag, message);
        }
    }

    /**
     * 打印DEBUG级别的日志
     * 仅在调试模式开启且当前日志级别允许时打印
     * @param tag 日志标签
     * @param message 日志内容
     */
    public static void d(String tag, String message) {
        if (Constants.DEBUG_MODE && Constants.CURRENT_LOG_LEVEL <= Constants.LOG_LEVEL_DEBUG) {
            Log.d(tag, message);
        }
    }

    /**
     * 打印INFO级别的日志
     * 仅在调试模式开启且当前日志级别允许时打印
     * @param tag 日志标签
     * @param message 日志内容
     */
    public static void i(String tag, String message) {
        if (Constants.DEBUG_MODE && Constants.CURRENT_LOG_LEVEL <= Constants.LOG_LEVEL_INFO) {
            Log.i(tag, message);
        }
    }

    /**
     * 打印ERROR级别的日志
     * 错误日志始终显示，不受调试模式和日志级别限制
     * @param tag 日志标签
     * @param message 日志内容
     */
    public static void e(String tag, String message) {
        Log.e(tag, message); // 错误日志总是显示
    }

}

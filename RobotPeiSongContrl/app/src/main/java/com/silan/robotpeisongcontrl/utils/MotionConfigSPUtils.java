package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 机器人运动配置SP工具类
 */
public class MotionConfigSPUtils {
    private static final String SP_NAME = "robot_motion_config";
    private static final String KEY_NAV_MODE = "nav_mode"; // 导航模式
    private static final String KEY_ACCEPTABLE_PRECISION = "acceptable_precision"; // 到点精度

    // 默认值
    public static final int DEFAULT_NAV_MODE = 0;
    public static final float DEFAULT_PRECISION = 0.1f;
    public static final float MAX_PRECISION = 2.0f; // 精度最大值

    private static SharedPreferences getSP(Context context) {
        return context.getApplicationContext().getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 保存导航模式
     */
    public static void saveNavMode(Context context, int mode) {
        getSP(context).edit().putInt(KEY_NAV_MODE, mode).apply();
    }

    /**
     * 读取导航模式（默认0：自由导航）
     */
    public static int getNavMode(Context context) {
        return getSP(context).getInt(KEY_NAV_MODE, DEFAULT_NAV_MODE);
    }

    /**
     * 保存到点精度（自动限制0~2之间）
     */
    public static void saveAcceptablePrecision(Context context, float precision) {
        // 限制精度范围：最小0，最大MAX_PRECISION(2)
        float finalPrecision = Math.max(0, Math.min(precision, MAX_PRECISION));
        getSP(context).edit().putFloat(KEY_ACCEPTABLE_PRECISION, finalPrecision).apply();
    }

    /**
     * 读取到点精度（默认0.1，最大2）
     */
    public static float getAcceptablePrecision(Context context) {
        return getSP(context).getFloat(KEY_ACCEPTABLE_PRECISION, DEFAULT_PRECISION);
    }
}

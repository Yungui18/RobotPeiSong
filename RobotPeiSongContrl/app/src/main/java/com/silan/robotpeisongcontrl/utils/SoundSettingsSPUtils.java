package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SoundSettingsSPUtils {
    private static final String SP_NAME = "sound_settings";

    // 存储key
    private static final String KEY_MANUAL_VOLUME = "manual_volume";          // 手动音量（0-100）
    private static final String KEY_AUTO_VOLUME_ENABLE = "auto_volume_enable";// 自动音量开关
    private static final String PREFIX_BIND_SOUND = "bind_sound_";            // 素材绑定前缀

    /**
     * 获取SharedPreferences实例
     */
    private static SharedPreferences getSP(Context context) {
        return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    // ===================== 音量相关 =====================
    /**
     * 保存手动音量设置
     */
    public static void saveManualVolume(Context context, int volume) {
        // 边界保护：0-100
        volume = Math.max(0, Math.min(volume, 100));
        getSP(context).edit().putInt(KEY_MANUAL_VOLUME, volume).apply();
    }

    /**
     * 获取手动音量（默认100）
     */
    public static int getManualVolume(Context context) {
        return getSP(context).getInt(KEY_MANUAL_VOLUME, 100);
    }

    /**
     * 保存自动音量开关
     */
    public static void saveAutoVolumeEnable(Context context, boolean enable) {
        getSP(context).edit().putBoolean(KEY_AUTO_VOLUME_ENABLE, enable).apply();
    }

    /**
     * 获取自动音量开关状态（默认关闭）
     */
    public static boolean isAutoVolumeEnable(Context context) {
        return getSP(context).getBoolean(KEY_AUTO_VOLUME_ENABLE, false);
    }

    // ===================== 素材绑定相关 =====================
    /**
     * 保存业务节点与语音素材的绑定关系
     */
    public static void saveBindSoundPath(Context context, String key, String soundPath) {
        getSP(context).edit().putString(PREFIX_BIND_SOUND + key, soundPath).apply();
    }

    /**
     * 获取业务节点绑定的语音素材路径
     */
    public static String getBindSoundPath(Context context, String key) {
        return getSP(context).getString(PREFIX_BIND_SOUND + key, "");
    }
}

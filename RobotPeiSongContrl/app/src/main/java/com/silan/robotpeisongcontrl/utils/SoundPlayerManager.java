package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

public class SoundPlayerManager {
    private static final String TAG = "SoundPlayerManager";
    private static SoundPlayerManager instance;
    private final Context mContext;
    private final AudioManager mAudioManager;
    private MediaPlayer mMediaPlayer;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private boolean isPlaying = false;
    private OnPlayCompleteListener mPlayCompleteListener;

    // 所有业务节点的语音绑定KEY
    public static final String KEY_DOOR_OPEN = "door_open";          // 仓门开启
    public static final String KEY_CLICK_START = "click_start";      // 点击出发按钮
    public static final String KEY_AFTER_START = "after_start";      // 出发后
    public static final String KEY_COMPLETE_TAKE = "complete_take";  // 取物后点击完成
    public static final String KEY_TIME_OUT = "time_out";            // 倒计时结束未取物
    public static final String KEY_TASK_ARRIVE = "task_arrive";      // 任务到达
    public static final String KEY_PATH_OCCUPIED = "path_occupied";                // 行进路径被阻挡
    public static final String KEY_CURRENT_POSE_OCCUPIED = "current_pose_occupied";// 当前位姿被占据
    public static final String KEY_ON_DOCK = "on_dock";                            // 机器人上桩
    public static final String KEY_OFF_DOCK = "off_dock";                          // 机器人下桩
    public static final String KEY_PASS_NARROW_CORRIDOR = "pass_narrow_corridor";  // 通过窄走廊
    public static final String KEY_POWER_OFF = "power_off";                        // 正在关机
    public static final String KEY_MOVE_DOCK_FAILED = "move_dock_failed";          // 前往充电桩失败
    public static final String KEY_SEARCH_DOCK_FAILED = "search_dock_failed";      // 找桩失败
    public static final String KEY_BRAKE_RELEASED = "brake_released";              // 刹车释放
    public static final String KEY_BUMPER_TRIGGERED = "bumper_triggered";          // 碰撞传感器触发

    // 新增：播放完成回调接口
    public interface OnPlayCompleteListener {
        void onPlayComplete();
    }

    // 单例模式
    public static SoundPlayerManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SoundPlayerManager.class) {
                if (instance == null) {
                    instance = new SoundPlayerManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private SoundPlayerManager(Context context) {
        mContext = context;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    /**
     * 扫描指定路径下的所有MP3文件
     * @param filePath 素材文件路径（如U盘路径：/storage/usb0/voice/）
     * @return MP3文件列表
     */
    public File[] scanMp3Files(String filePath) {
        File dir = new File(filePath);
        if (!dir.exists() || !dir.isDirectory()) {
            Log.e(TAG, "素材目录不存在：" + filePath);
            return new File[0];
        }
        // 过滤出MP3文件（忽略大小写）
        return dir.listFiles((file, name) -> name.toLowerCase().endsWith(".mp3"));
    }

    /**
     * 播放指定业务节点绑定的语音
     * @param key 业务节点key（如KEY_DOOR_OPEN）
     */
    public void playSound(String key) {
        // 从SharedPreferences获取该节点绑定的素材路径
        String soundPath = SoundSettingsSPUtils.getBindSoundPath(mContext, key);
        if (TextUtils.isEmpty(soundPath)) {
            Log.w(TAG, "未绑定语音素材：" + key);
            return;
        }
        playSoundByPath(soundPath);
    }

    /**
     * 根据文件路径播放MP3
     * @param soundPath MP3文件绝对路径
     */
    public void playSoundByPath(String soundPath) {
        if (TextUtils.isEmpty(soundPath)) {
            Log.e(TAG, "语音路径为空，无法播放");
            return;
        }

        File soundFile = new File(soundPath);
        if (!soundFile.exists()) {
            Log.e(TAG, "语音文件不存在：" + soundPath);
            return;
        }

        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC); // 确保是媒体音
            mMediaPlayer.setDataSource(soundPath);
            mMediaPlayer.prepareAsync();

            mMediaPlayer.setOnPreparedListener(mp -> {
                adjustVolume();
                mp.start();
                isPlaying = true;
                Log.d(TAG, "MediaPlayer：开始播放，路径=" + soundPath);
            });

            mMediaPlayer.setOnCompletionListener(mp -> {
                Log.d(TAG, "MediaPlayer：播放完成，路径=" + soundPath);
                releaseMediaPlayer();
                isPlaying = false;
                if (mPlayCompleteListener != null) {
                    mHandler.post(() -> mPlayCompleteListener.onPlayComplete());
                }
            });

            mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer：播放错误，what=" + what + ", extra=" + extra + ", 路径=" + soundPath);
                releaseMediaPlayer();
                isPlaying = false;
                if (mPlayCompleteListener != null) {
                    mHandler.post(() -> mPlayCompleteListener.onPlayComplete());
                }
                return true;
            });
        } catch (IOException e) {
            Log.e(TAG, "MediaPlayer：初始化失败，路径=" + soundPath, e);
            releaseMediaPlayer();
            isPlaying = false;
        }
    }

    /**
     * 调节音量（手动设置 + 自动时间规则）
     */
    private void adjustVolume() {
        if (mMediaPlayer == null || mAudioManager == null) return;

        int manualVolume = SoundSettingsSPUtils.getManualVolume(mContext);
        float timeVolumeRatio = getTimeVolumeRatio();
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        // 修复：避免因浮点数计算导致音量为0
        int finalVolume = Math.round(maxVolume * (manualVolume / 100f) * timeVolumeRatio);
        finalVolume = Math.max(1, Math.min(finalVolume, maxVolume)); // 强制最小音量为1，避免静音

        float playerVolume = finalVolume / (float) maxVolume;
        mMediaPlayer.setVolume(playerVolume, playerVolume);
        Log.d(TAG, "音量调节：手动音量=" + manualVolume + ", 时间系数=" + timeVolumeRatio + ", 最终音量=" + finalVolume);
    }

    /**
     * 新增：设置预览音量（实时生效，不保存到SP）
     * @param volume 0-100的音量值
     */
    public void setPreviewVolume(int volume) {
        if (mMediaPlayer == null || !isPlaying || mAudioManager == null) return;

        // 边界保护
        volume = Math.max(0, Math.min(volume, 100));
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float playerVolume = volume / 100f;
        mMediaPlayer.setVolume(playerVolume, playerVolume);
    }

    /**
     * 根据时间获取音量调节系数
     * 示例规则：8:00-22:00 系数1.0，其余时间系数0.5（夜间静音/降音）
     */
    private float getTimeVolumeRatio() {
        // 从SP获取自动调节的开关状态
        boolean autoVolumeEnable = SoundSettingsSPUtils.isAutoVolumeEnable(mContext);
        if (!autoVolumeEnable) return 1.0f;

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        // 8点到22点之间音量正常，其余时间降音
        return (hour >= 8 && hour < 22) ? 1.0f : 0.5f;
    }

    /**
     * 停止播放语音
     */
    public void stopSound() {
        mHandler.removeCallbacksAndMessages(null);
        if (mMediaPlayer != null) {
            try {
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
                mMediaPlayer.reset(); // 强制重置状态，避免残留
            } catch (IllegalStateException e) {
                Log.e(TAG, "停止播放时状态异常（忽略）", e);
            }
            releaseMediaPlayer();
        }
        isPlaying = false;
    }

    /**
     * 释放MediaPlayer资源
     */
    private void releaseMediaPlayer() {
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.setOnPreparedListener(null);
                mMediaPlayer.setOnCompletionListener(null);
                mMediaPlayer.setOnErrorListener(null);
                mMediaPlayer.release();
            } catch (IllegalStateException e) {
                Log.e(TAG, "释放MediaPlayer时状态异常（忽略）", e);
            }
            mMediaPlayer = null;
        }
    }

    /**
     * 新增：设置播放完成回调
     */
    public void setOnPlayCompleteListener(OnPlayCompleteListener listener) {
        this.mPlayCompleteListener = listener;
    }

    /**
     * 新增：获取当前播放状态
     */
    public boolean isPlaying() {
        return isPlaying;
    }

    /**
     * 销毁单例资源
     */
    public void destroy() {
        stopSound();
        mHandler.removeCallbacksAndMessages(null);
        mPlayCompleteListener = null;
        instance = null;
    }
}

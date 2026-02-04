package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.silan.robotpeisongcontrl.model.RobotEventType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 机器人事件语音触发管理类（单例）
 * 负责事件轮询、事件去重、语音绑定触发
 */
public class RobotEventSoundManager {
    private static final String TAG = "RobotEventSoundManager";
    private static final long POLLING_INTERVAL = 2000; // 事件轮询间隔（2秒）
    private static RobotEventSoundManager instance;

    private final Context mContext;
    private final SoundPlayerManager mSoundPlayerManager;
    private final Handler mMainHandler;
    private final Set<String> mTriggeredEventSet; // 已触发的事件集合（去重）
    private Runnable mPollingRunnable; // 轮询Runnable（用于停止）
    private boolean isPolling; // 是否正在轮询

    // 事件类型与语音KEY的绑定映射
    private final Map<RobotEventType, String> EVENT_SOUND_KEY_MAP = new HashMap<>() {{
        put(RobotEventType.PATH_OCCUPIED, SoundPlayerManager.KEY_PATH_OCCUPIED);
        put(RobotEventType.CURRENT_POSE_OCCUPIED, SoundPlayerManager.KEY_CURRENT_POSE_OCCUPIED);
        put(RobotEventType.ON_DOCK, SoundPlayerManager.KEY_ON_DOCK);
        put(RobotEventType.OFF_DOCK, SoundPlayerManager.KEY_OFF_DOCK);
        put(RobotEventType.PASS_THE_NARROW_CORRIDOR, SoundPlayerManager.KEY_PASS_NARROW_CORRIDOR);
        put(RobotEventType.POWER_OFF, SoundPlayerManager.KEY_POWER_OFF);
        put(RobotEventType.MOVE_TO_LANDING_POINT_FAILED, SoundPlayerManager.KEY_MOVE_DOCK_FAILED);
        put(RobotEventType.SEARCH_DOCK_FAILED, SoundPlayerManager.KEY_SEARCH_DOCK_FAILED);
        put(RobotEventType.BRAKE_RELEASED, SoundPlayerManager.KEY_BRAKE_RELEASED);
        put(RobotEventType.BUMPER_TRIGGERED, SoundPlayerManager.KEY_BUMPER_TRIGGERED);
    }};

    // 单例初始化
    public static RobotEventSoundManager getInstance(Context context) {
        if (instance == null) {
            synchronized (RobotEventSoundManager.class) {
                if (instance == null) {
                    instance = new RobotEventSoundManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private RobotEventSoundManager(Context context) {
        mContext = context;
        mSoundPlayerManager = SoundPlayerManager.getInstance(context);
        mMainHandler = new Handler(Looper.getMainLooper());
        mTriggeredEventSet = new HashSet<>();
        isPolling = false;
    }

    /**
     * 启动机器人事件轮询并触发语音
     * 同一事件仅触发一次，直到调用clearTriggeredEvents()清空
     */
    public void startEventPolling() {
        if (isPolling) {
            Log.w(TAG, "事件轮询已在运行，无需重复启动");
            return;
        }
        Log.d(TAG, "启动机器人事件轮询，间隔：" + POLLING_INTERVAL + "ms");
        // 明确指定：RobotController内部的RobotEventCallback
        mPollingRunnable = RobotController.startRobotEventPolling(POLLING_INTERVAL,
                new RobotController.RobotEventCallback() {
                    @Override
                    public void onEventReceived(List<RobotController.RobotEvent> events) {
                        if (events == null || events.isEmpty()) {
                            return;
                        }
                        handleRobotEvents(events);
                    }

                    @Override
                    public void onEventFailure(Exception e) {
                        Log.e(TAG, "事件轮询失败", e);
                    }
                });
        isPolling = true;
    }

    /**
     * 停止机器人事件轮询
     */
    public void stopEventPolling() {
        if (!isPolling || mPollingRunnable == null) {
            return;
        }
        Log.d(TAG, "停止机器人事件轮询");
        mMainHandler.removeCallbacks(mPollingRunnable);
        isPolling = false;
        mTriggeredEventSet.clear(); // 清空已触发事件
    }

    /**
     * 处理机器人事件，触发对应语音（去重）
     */
    private void handleRobotEvents(List<RobotController.RobotEvent> events) {
        Set<String> currentEventTypes = new HashSet<>();
        for (RobotController.RobotEvent event : events) {
            RobotEventType type = RobotEventType.fromType(event.getType());
            if (type != null) {
                currentEventTypes.add(type.getType());
            }
        }
        Set<String> triggeredCopy = new HashSet<>(mTriggeredEventSet);
        for (String triggeredType : triggeredCopy) {
            if (!currentEventTypes.contains(triggeredType)) {
                mTriggeredEventSet.remove(triggeredType);
                Log.d(TAG, "事件已消失，移除触发标记：" + triggeredType);
            }
        }
        for (RobotController.RobotEvent event : events) {
            String eventTypeStr = event.getType();
            RobotEventType eventType = RobotEventType.fromType(eventTypeStr);
            if (eventType == null) {
                Log.w(TAG, "未知机器人事件类型：" + eventTypeStr);
                continue;
            }

            // 同一事件仅触发一次
            if (mTriggeredEventSet.contains(eventType.getType())) {
                continue;
            }

            // 获取绑定的语音KEY
            String soundKey = EVENT_SOUND_KEY_MAP.get(eventType);
            if (soundKey == null || soundKey.isEmpty()) {
                Log.w(TAG, "未绑定语音的事件类型：" + eventType.getType());
                continue;
            }

            // 触发语音播放
            Log.d(TAG, "触发机器人事件语音：" + eventType.getType() + " -> " + soundKey);
            mSoundPlayerManager.playSound(soundKey);
            mTriggeredEventSet.add(eventType.getType()); // 标记为已触发
        }
    }

    /**
     * 清空已触发的事件集合（允许同一事件再次触发）
     */
    public void clearTriggeredEvents() {
        mTriggeredEventSet.clear();
        Log.d(TAG, "清空已触发机器人事件集合");
    }

    /**
     * 判断是否正在轮询
     */
    public boolean isPolling() {
        return isPolling;
    }
}

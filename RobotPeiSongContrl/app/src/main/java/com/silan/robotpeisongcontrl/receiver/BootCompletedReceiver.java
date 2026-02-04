package com.silan.robotpeisongcontrl.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.utils.ScheduledDeliveryManager;

import java.util.List;

/**
 * 开机完成广播接收器：设备重启后自动重新调度定时任务
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = "BootCompletedReceiver";
    private static final String PREFS_SCHEDULED = "scheduled_delivery_prefs";
    private static final String KEY_ENABLED = "scheduled_delivery_enabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        // 仅处理开机完成广播
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "设备开机完成，开始检查并重新调度定时任务");
            // 1. 获取定时功能的启用状态（与EnableScheduledDeliveryFragment一致的SharedPreferences）
            SharedPreferences prefs = context.getSharedPreferences(PREFS_SCHEDULED, Context.MODE_PRIVATE);
            boolean isScheduledEnabled = prefs.getBoolean(KEY_ENABLED, false);

            if (isScheduledEnabled) {
                // 2. 若定时功能已启用，重新调度所有启用的定时任务
                try {
                    List<ScheduledDeliveryTask> tasks = ScheduledDeliveryManager.loadAllTasks(context);
                    int scheduledCount = 0;
                    for (ScheduledDeliveryTask task : tasks) {
                        if (task.isEnabled()) {
                            // 重新注册闹钟
                            ScheduledDeliveryManager.scheduleTask(context, task);
                            scheduledCount++;
                        }
                    }
                    Log.d(TAG, "开机后重新调度完成，共调度 " + scheduledCount + " 个启用的定时任务");
                    // 可选：吐司提示（调试用，正式版可删除）
                    // Toast.makeText(context, "开机成功，已自动恢复 " + scheduledCount + " 个定时任务", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "开机后重新调度定时任务失败", e);
                    Toast.makeText(context, "定时任务恢复失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "定时功能未启用，无需调度任务");
            }
        }
    }
}

package com.silan.robotpeisongcontrl.receiver;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.silan.robotpeisongcontrl.ScheduledDeliveryExecutionActivity;
import com.silan.robotpeisongcontrl.model.RobotStatus;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.services.ScheduledRetryService;
import com.silan.robotpeisongcontrl.utils.ExactAlarmPermissionHelper;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.ScheduledDeliveryManager;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.nio.charset.StandardCharsets;

import okio.ByteString;

public class ScheduledDeliveryReceiver extends BroadcastReceiver {
    private static final String TAG = "ScheduledDeliveryRcvr";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "收到定时配送广播： " + intent.getAction());

        String taskId = intent.getStringExtra("task_id");
        if (taskId == null) {
            Log.w(TAG, "任务ID为空，忽略广播");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!ExactAlarmPermissionHelper.canScheduleExactAlarms(context)) {
                Log.w(TAG, "缺少 SCHEDULE_EXACT_ALARM 权限，无法执行任务");
                return;
            }
        }

        // 检查全局启用状态
        SharedPreferences prefs = context.getSharedPreferences(
                "scheduled_delivery_prefs", Context.MODE_PRIVATE);
        boolean isEnabled = prefs.getBoolean("scheduled_delivery_enabled", false);

        if (!isEnabled) {
            Log.d(TAG, "定时配送功能已禁用，忽略任务");
            return;
        }

        // 加载任务
        ScheduledDeliveryTask task = ScheduledDeliveryManager.loadTask(context, taskId);
        if (task == null) {
            Log.w(TAG, "Task not found: " + taskId);
            return;
        }

        if (!task.isEnabled()) {
            Log.d(TAG, "Task disabled: " + taskId);
            return;
        }

        Log.i(TAG, "Executing scheduled task: " + taskId);
//        task.setTriggerTime(System.currentTimeMillis());

        // 检查机器人状态
        checkRobotStatusAndExecute(context, task);
    }

    private void checkRobotStatusAndExecute(Context context, ScheduledDeliveryTask task) {
        Log.d(TAG, "Checking robot status for task: " + task.getId());

        RobotController.getRobotStatus(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String json = responseData.string(StandardCharsets.UTF_8);
                    RobotStatus status = RobotController.parseRobotStatus(json);
                    Log.d(TAG, "Robot status: battery=" + status.getBatteryPercentage());

                    if (isRobotBusy(status)) {
                        // 机器人忙，将任务加入等待队列
//                        if (task.isExpired()) {
//                            Log.w("ScheduledDelivery", "任务已过期");
//                        } else {
                            TaskManager.addPendingScheduledTask(task);
                            Log.i("ScheduledDelivery", "添加等待任务进任务队列");
//                        }
                    } else {
                        startDeliveryTask(context, task);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "获取机器人状态出错", e);
                    scheduleRetry(context, task);
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to get robot status", e);
                scheduleRetry(context, task);
            }
        });
    }

    private boolean isRobotBusy(RobotStatus status) {
        Log.d(TAG, "Checking robot busy state:");
        Log.d(TAG, " - Docking status: " + status.getDockingStatus());
        Log.d(TAG, " - Charging: " + status.isCharging());
        Log.d(TAG, " - Power stage: " + status.getPowerStage());
        Log.d(TAG, " - Sleep mode: " + status.getSleepMode());
        Log.d(TAG, " - DC connected: " + status.isDCConnected());

        // 更智能的忙碌状态检测
        boolean isBusy = false;

        // 如果机器人处于唤醒状态但不在充电桩上
        if (status.getSleepMode().equals("awake") &&
                status.getDockingStatus().equals("not_on_dock")) {
            isBusy = true;
        }

        // 如果机器人正在执行配送任务（通过任务管理器判断）
        if (TaskManager.getInstance().hasTasks()) {
            isBusy = true;
        }

        Log.d(TAG, "Robot busy: " + isBusy);
        return isBusy;
    }

    private void scheduleRetry(Context context, ScheduledDeliveryTask task) {
        Log.d(TAG, "Scheduling retry for task: " + task.getId());

        // 使用JobScheduler进行更可靠的重试
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ComponentName serviceComponent = new ComponentName(context, ScheduledRetryService.class);
            JobInfo.Builder builder = new JobInfo.Builder(task.getId().hashCode(), serviceComponent);
            builder.setMinimumLatency(5 * 60 * 1000); // 5分钟后重试
            builder.setOverrideDeadline(10 * 60 * 1000); // 最晚10分钟后执行
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);

            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            jobScheduler.schedule(builder.build());
        } else {
            // 旧版本使用AlarmManager
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent retryIntent = new Intent(context, ScheduledDeliveryReceiver.class);
            retryIntent.putExtra("task_id", task.getId());

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    task.getId().hashCode() + 1, // 不同的requestCode
                    retryIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            long triggerAtMillis = System.currentTimeMillis() + 5 * 60 * 1000;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        }
    }

    private void startDeliveryTask(Context context, ScheduledDeliveryTask task) {
        Log.i(TAG, "Starting delivery for task: " + task.getId());

        Intent intent = new Intent(context, ScheduledDeliveryExecutionActivity.class);
        intent.putExtra("task_id", task.getId());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}

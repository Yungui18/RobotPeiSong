package com.silan.robotpeisongcontrl.services;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.receiver.ScheduledDeliveryReceiver;
import com.silan.robotpeisongcontrl.utils.ScheduledDeliveryManager;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScheduledRetryService extends JobService {
    private static final String TAG = "ScheduledRetryService";

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "Job started for retry");

        String taskId = null;
        if (params != null && params.getExtras() != null) {
            // 从JobParameters中获取任务ID
            taskId = params.getExtras().getString("task_id");
        }

        if (taskId == null) {
            Log.w(TAG, "No task ID in job parameters");
            return false;
        }

        // 重新检查机器人状态
        ScheduledDeliveryTask task = ScheduledDeliveryManager.loadTask(this, taskId);
        if (task == null) {
            Log.w(TAG, "Task not found: " + taskId);
            return false;
        }

        // 委托给广播接收器处理
        Intent intent = new Intent(this, ScheduledDeliveryReceiver.class);
        intent.putExtra("task_id", taskId);
        sendBroadcast(intent);

        return true; // 返回true表示工作还在进行
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Job stopped");
        return true; // 返回true表示需要重新调度
    }
}
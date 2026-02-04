package com.silan.robotpeisongcontrl.fragments;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.utils.ScheduledDeliveryManager;

import java.util.List;

public class EnableScheduledDeliveryFragment extends Fragment {
    private static final int REQUEST_CODE_ALARM_PERMISSION = 1001;
    private Switch switchEnable;
    private SharedPreferences prefs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_enable_scheduled_delivery, container, false);

        prefs = requireActivity().getSharedPreferences("scheduled_delivery_prefs", Context.MODE_PRIVATE);

        switchEnable = view.findViewById(R.id.switch_enable);
        boolean isEnabled = prefs.getBoolean("scheduled_delivery_enabled", false);
        switchEnable.setChecked(isEnabled);

        // 应用启动时自动检查，若启用则调度任务
        if (isEnabled) {
            // 检查精确闹钟权限（Android 12+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                if (alarmManager.canScheduleExactAlarms()) {
                    enableAllTasks(); // 有权限，直接调度
                } else {
                    switchEnable.setChecked(false); // 无权限，重置开关为关闭
                    prefs.edit().putBoolean("scheduled_delivery_enabled", false).apply();
                    Toast.makeText(getContext(), "缺少精确闹钟权限，定时功能已自动禁用", Toast.LENGTH_LONG).show();
                }
            } else {
                enableAllTasks(); // 低版本无需权限，直接调度
            }
        }

        switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 检查并请求权限
                AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                if (!alarmManager.canScheduleExactAlarms()) {
                    requestAlarmPermission();
                    switchEnable.setChecked(false); // 暂时保持关闭状态
                    return;
                }
            }

            prefs.edit().putBoolean("scheduled_delivery_enabled", isChecked).apply();
            Toast.makeText(getContext(), isChecked ? "定时配送已启用" : "定时配送已禁用",
                    Toast.LENGTH_SHORT).show();

            if (isChecked) {
                enableAllTasks();
            } else {
                // 关闭功能时取消所有已注册的闹钟
                cancelAllScheduledTasks();
            }
        });

        return view;
    }

    private void cancelAllScheduledTasks() {
        try {
            List<ScheduledDeliveryTask> tasks = ScheduledDeliveryManager.loadAllTasks(requireContext());
            for (ScheduledDeliveryTask task : tasks) {
                ScheduledDeliveryManager.cancelTask(requireContext(), task.getId());
            }
            Log.d("ScheduledDelivery", "所有定时任务闹钟已取消");
        } catch (Exception e) {
            Log.e("ScheduledDelivery", "取消任务闹钟失败", e);
        }
    }

    @TargetApi(Build.VERSION_CODES.S)
    private void requestAlarmPermission() {
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        startActivityForResult(intent, REQUEST_CODE_ALARM_PERMISSION);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ALARM_PERMISSION) {
            // 重新尝试启用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(Context.ALARM_SERVICE);
                if (alarmManager.canScheduleExactAlarms()) {
                    switchEnable.setChecked(true);
                    prefs.edit().putBoolean("scheduled_delivery_enabled", true).apply();
                    enableAllTasks();
                }
            }
        }
    }

    private void enableAllTasks() {
        try {
            List<ScheduledDeliveryTask> tasks = ScheduledDeliveryManager.loadAllTasks(requireContext());
            for (ScheduledDeliveryTask task : tasks) {
                if (task.isEnabled()) {
                    ScheduledDeliveryManager.scheduleTask(requireContext(), task);
                }
            }
        } catch (Exception e) {
            Log.e("ScheduledDelivery", "Failed to enable tasks", e);
            Toast.makeText(getContext(), "启用任务失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
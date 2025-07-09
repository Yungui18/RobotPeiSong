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
            }
        });

        return view;
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
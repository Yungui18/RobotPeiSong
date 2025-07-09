package com.silan.robotpeisongcontrl.fragments;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.utils.ScheduledDeliveryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ViewTasksFragment extends Fragment {

    private ListView listView;
    private ScheduledTaskAdapter adapter;
    private List<ScheduledDeliveryTask> taskList = new ArrayList<>();
    private boolean isDeliveryEnabled;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_view_tasks, container, false);

        listView = view.findViewById(R.id.list_tasks);

        // 加载全局启用状态
        SharedPreferences prefs = requireActivity().getSharedPreferences(
                "scheduled_delivery_prefs", Context.MODE_PRIVATE);
        isDeliveryEnabled = prefs.getBoolean("scheduled_delivery_enabled", false);

        // 加载任务列表
        try {
            taskList = ScheduledDeliveryManager.loadAllTasks(requireContext());
        } catch (Exception e) {
            Log.e("ViewTasks", "Failed to load tasks", e);
            Toast.makeText(getContext(), "加载任务失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        adapter = new ScheduledTaskAdapter(requireContext(), taskList, isDeliveryEnabled);
        listView.setAdapter(adapter);

        return view;
    }

    private class ScheduledTaskAdapter extends ArrayAdapter<ScheduledDeliveryTask> {

        private final boolean isGlobalEnabled;

        public ScheduledTaskAdapter(Context context, List<ScheduledDeliveryTask> tasks,
                                    boolean isGlobalEnabled) {
            super(context, 0, tasks);
            this.isGlobalEnabled = isGlobalEnabled;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ScheduledDeliveryTask task = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.item_scheduled_task, parent, false);
            }

            TextView tvInfo = convertView.findViewById(R.id.tv_task_info);
            Switch switchEnable = convertView.findViewById(R.id.switch_enable);
            Button btnEdit = convertView.findViewById(R.id.btn_edit);
            Button btnDelete = convertView.findViewById(R.id.btn_delete);

            // 设置任务信息
            String info = String.format(Locale.getDefault(),
                    "%02d:%02d | %s : %s | 仓门: %s",
                    task.getHour(), task.getMinute(),
                    task.getTaskType() == ScheduledDeliveryTask.TYPE_POINT ?
                            "点位配送" : "路线配送",
                    task.getPoi().getDisplayName(),
                    getSelectedDoorsString(task.getSelectedDoors()));

            tvInfo.setText(info);

            // 设置启用开关
            switchEnable.setChecked(task.isEnabled());
            switchEnable.setEnabled(isGlobalEnabled);
            switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                task.setEnabled(isChecked);
                ScheduledDeliveryManager.saveTask(getContext(), task);

                if (isChecked) {
                    ScheduledDeliveryManager.scheduleTask(getContext(), task);
                } else {
                    ScheduledDeliveryManager.cancelTask(getContext(), task.getId());
                }
            });

            // 编辑按钮
            btnEdit.setOnClickListener(v -> {
                // 实现编辑功能
                editTask(task);
            });

            // 删除按钮
            btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext())
                        .setTitle("删除任务")
                        .setMessage("确定要删除此定时配送任务吗？")
                        .setPositiveButton("删除", (dialog, which) -> {
                            ScheduledDeliveryManager.deleteTask(getContext(), task.getId());
                            taskList.remove(position);
                            notifyDataSetChanged();
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });

            return convertView;
        }

        private String getSelectedDoorsString(boolean[] selectedDoors) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < selectedDoors.length; i++) {
                if (selectedDoors[i]) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(i + 1);
                }
            }
            return sb.toString();
        }
    }

    private void editTask(ScheduledDeliveryTask task) {
        boolean wasEnabled = task.isEnabled();
        // 打开编辑界面
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                requireContext(),
                (view, hourOfDay, minute) -> {
                    String originalId = task.getId();
                    task.setHour(hourOfDay);
                    task.setMinute(minute);
                    ScheduledDeliveryManager.saveTask(requireContext(), task);
                    if (wasEnabled) {
                        ScheduledDeliveryManager.cancelTask(requireContext(), task.getId());
                        ScheduledDeliveryManager.scheduleTask(requireContext(), task);
                    }
                    adapter.notifyDataSetChanged();
                },
                task.getHour(),
                task.getMinute(),
                true
        );
        timePickerDialog.show();
    }
}
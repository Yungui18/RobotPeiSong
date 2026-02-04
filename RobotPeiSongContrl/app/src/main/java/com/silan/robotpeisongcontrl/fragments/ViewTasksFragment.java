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

    private static final String TAG = "ViewTasksFragment";
    private ListView listView;
    private ScheduledTaskAdapter adapter;
    private List<ScheduledDeliveryTask> taskList = new ArrayList<>();
    private boolean isDeliveryEnabled;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_view_tasks, container, false);

        listView = view.findViewById(R.id.list_tasks);

        if (getActivity() == null) {
            return view;
        }

        // 加载全局启用状态
        SharedPreferences prefs = getActivity().getSharedPreferences(
                "scheduled_delivery_prefs", Context.MODE_PRIVATE);
        isDeliveryEnabled = prefs.getBoolean("scheduled_delivery_enabled", false);

        // 加载任务列表 - 增加完整的异常捕获
        try {
            taskList = ScheduledDeliveryManager.loadAllTasks(getActivity());
            if (taskList == null) {
                taskList = new ArrayList<>();
                Toast.makeText(getActivity(), "暂无定时任务", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load tasks", e);
            taskList = new ArrayList<>(); // 确保列表不为null
            Toast.makeText(getActivity(), "加载任务失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        adapter = new ScheduledTaskAdapter(getActivity(), taskList, isDeliveryEnabled);
        listView.setAdapter(adapter);

        return view;
    }

    private class ScheduledTaskAdapter extends ArrayAdapter<ScheduledDeliveryTask> {

        private final boolean isGlobalEnabled;
        private final Context mContext;

        public ScheduledTaskAdapter(Context context, List<ScheduledDeliveryTask> tasks,
                                    boolean isGlobalEnabled) {
            super(context, 0, tasks);
            this.mContext = context;
            this.isGlobalEnabled = isGlobalEnabled;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (position < 0 || position >= getCount()) {
                return convertView == null ? new View(mContext) : convertView;
            }

            ScheduledDeliveryTask task = getItem(position);
            if (task == null) {
                return convertView == null ? new View(mContext) : convertView;
            }

            if (convertView == null) {
                convertView = LayoutInflater.from(mContext)
                        .inflate(R.layout.item_scheduled_task, parent, false);
            }

            TextView tvInfo = convertView.findViewById(R.id.tv_task_info);
            Switch switchEnable = convertView.findViewById(R.id.switch_enable);
            Button btnEdit = convertView.findViewById(R.id.btn_edit);
            Button btnDelete = convertView.findViewById(R.id.btn_delete);

            String poiName = "未知点位";
            if (task.getPoi() != null && task.getPoi().getDisplayName() != null) {
                poiName = task.getPoi().getDisplayName();
            }

            // 设置任务信息
            String info = String.format(Locale.getDefault(),
                    "%02d:%02d | %s : %s | 仓门: %s",
                    task.getHour(), task.getMinute(),
                    task.getTaskType() == ScheduledDeliveryTask.TYPE_POINT ?
                            "点位配送" : "路线配送",
                    poiName,
                    getSelectedDoorsString(task.getSelectedDoors()));

            tvInfo.setText(info);

            // 设置启用开关
            switchEnable.setChecked(task.isEnabled());
            switchEnable.setEnabled(isGlobalEnabled);
            switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (mContext == null) return;

                task.setEnabled(isChecked);
                try {
                    ScheduledDeliveryManager.saveTask(mContext, task);

                    if (isChecked) {
                        ScheduledDeliveryManager.scheduleTask(mContext, task);
                    } else {
                        ScheduledDeliveryManager.cancelTask(mContext, task.getId());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to update task status", e);
                    Toast.makeText(mContext, "更新任务状态失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

            // 编辑按钮
            btnEdit.setOnClickListener(v -> {
                // 实现编辑功能
                editTask(task);
            });

            // 删除按钮
            btnDelete.setOnClickListener(v -> {
                if (mContext == null) return;

                new AlertDialog.Builder(mContext)
                        .setTitle("删除任务")
                        .setMessage("确定要删除此定时配送任务吗？")
                        .setPositiveButton("删除", (dialog, which) -> {
                            try {
                                ScheduledDeliveryManager.deleteTask(mContext, task.getId());
                                taskList.remove(position);
                                notifyDataSetChanged();
                            } catch (Exception e) {
                                Log.e(TAG, "Failed to delete task", e);
                                Toast.makeText(mContext, "删除任务失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            });

            return convertView;
        }

        private String getSelectedDoorsString(boolean[] selectedDoors) {
            if (selectedDoors == null) {
                return "";
            }

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
        if (task == null || getActivity() == null) {
            return;
        }

        boolean wasEnabled = task.isEnabled();
        // 打开编辑界面
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                getActivity(),
                (view, hourOfDay, minute) -> {
                    if (isDetached() || getActivity() == null) {
                        return;
                    }

                    String originalId = task.getId();
                    task.setHour(hourOfDay);
                    task.setMinute(minute);
                    try {
                        ScheduledDeliveryManager.saveTask(getActivity(), task);
                        if (wasEnabled) {
                            ScheduledDeliveryManager.cancelTask(getActivity(), task.getId());
                            ScheduledDeliveryManager.scheduleTask(getActivity(), task);
                        }
                        adapter.notifyDataSetChanged();
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to edit task", e);
                        Toast.makeText(getActivity(), "编辑任务失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                },
                task.getHour(),
                task.getMinute(),
                true
        );
        timePickerDialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // 清空列表引用，防止内存泄漏
        if (taskList != null) {
            taskList.clear();
        }
        listView = null;
        adapter = null;
    }
}
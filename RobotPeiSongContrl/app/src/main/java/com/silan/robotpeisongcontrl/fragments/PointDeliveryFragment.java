package com.silan.robotpeisongcontrl.fragments;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.utils.ExactAlarmPermissionHelper;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.ScheduledDeliveryManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okio.ByteString;

public class PointDeliveryFragment extends Fragment {

    private Spinner spinnerPoints;
    private Button[] doorButtons;
    private TimePicker timePicker;
    private Button btnConfirm;
    private List<Poi> poiList = new ArrayList<>();
    private boolean[] selectedDoors;
    private ActivityResultLauncher<Intent> alarmPermissionLauncher;
    private int doorCount;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // 获取仓门数量
        doorCount = WarehouseDoorSettingsFragment.getDoorCount(requireContext());
        selectedDoors = new boolean[doorCount];

        View view = inflater.inflate(R.layout.fragment_point_delivery, container, false);
        alarmPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> ExactAlarmPermissionHelper.handlePermissionResult(requireContext())
        );

        // 初始化UI
        spinnerPoints = view.findViewById(R.id.spinner_points);
        timePicker = view.findViewById(R.id.time_picker);
        btnConfirm = view.findViewById(R.id.btn_confirm);

        // 加载任务按钮布局
        LinearLayout buttonsContainer = view.findViewById(R.id.door_buttons_container);
        loadDoorButtonsLayout(buttonsContainer);

        // 加载POI列表
        loadPoiList();

        // 确认按钮监听
        btnConfirm.setOnClickListener(v -> saveDeliveryTask());

        return view;
    }

    private void loadDoorButtonsLayout(LinearLayout container) {
        // 清空容器
        container.removeAllViews();
        List<Integer> doorNumbers = WarehouseDoorSettingsFragment.getDoorNumbers(requireContext());
        doorButtons = new Button[doorNumbers.size()]; // 仓门编号最大为9，数组容量设为10

        LayoutInflater inflater = LayoutInflater.from(getContext());
        int doorCount = doorNumbers.size();
        switch (doorCount) {
            case 3:
                inflater.inflate(R.layout.task_three_buttons_layout, container);
                break;
            case 4:
                inflater.inflate(R.layout.task_four_buttons_layout, container);
                break;
            case 6:
                inflater.inflate(R.layout.task_six_buttons_layout, container);
                break;
        }

        // 初始化按钮引用并设置点击事件
        for (int i = 0; i < doorNumbers.size(); i++) {
            int doorId = doorNumbers.get(i);
            final int index = i;
            doorButtons[i] = container.findViewById(getResources().getIdentifier(
                    "btn_task" + doorId, "id", requireContext().getPackageName()));

            if (doorButtons[i] != null) {
                doorButtons[i].setOnClickListener(v -> {
                    selectedDoors[index] = !selectedDoors[index];
                    updateDoorButtonState(index, doorId); // 传入doorId用于更新按钮状态
                });
            }
        }
    }

    // 根据doorId更新按钮状态
    private void updateDoorButtonState(int index, int doorId) {
        if (selectedDoors[index]) {
            doorButtons[doorId].setBackgroundResource(R.drawable.button_red_rect);
        } else {
            doorButtons[doorId].setBackgroundResource(R.drawable.button_blue_rect);
        }
    }

    private void loadPoiList() {
        RobotController.getPoiList(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                poiList = RobotController.parsePoiList(json);

                requireActivity().runOnUiThread(() -> {
                    List<String> pointNames = new ArrayList<>();
                    for (Poi poi : poiList) {
                        pointNames.add(poi.getDisplayName());
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            requireContext(),
                            android.R.layout.simple_spinner_item,
                            pointNames
                    );
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerPoints.setAdapter(adapter);
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("PointDelivery", "Failed to load POIs", e);
            }
        });
    }

    private void updateDoorButtonState(int index) {
        if (selectedDoors[index]) {
            doorButtons[index].setBackgroundResource(R.drawable.button_red_rect);
        } else {
            doorButtons[index].setBackgroundResource(R.drawable.button_blue_rect);
        }
    }

    private void saveDeliveryTask() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !ExactAlarmPermissionHelper.canScheduleExactAlarms(requireContext())) {

            // 请求权限
            ExactAlarmPermissionHelper.requestExactAlarmPermission(
                    (AppCompatActivity) requireActivity(),
                    alarmPermissionLauncher
            );
            return;
        }


        int selectedPosition = spinnerPoints.getSelectedItemPosition();
        if (selectedPosition < 0 || selectedPosition >= poiList.size()) {
            Toast.makeText(getContext(), "请选择配送点位", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean atLeastOneDoorSelected = false;
        for (boolean selected : selectedDoors) {
            if (selected) {
                atLeastOneDoorSelected = true;
                break;
            }
        }

        if (!atLeastOneDoorSelected) {
            Toast.makeText(getContext(), "请至少选择一个仓门", Toast.LENGTH_SHORT).show();
            return;
        }

        Poi selectedPoi = poiList.get(selectedPosition);
        int hour = timePicker.getHour();
        int minute = timePicker.getMinute();

        ScheduledDeliveryTask task = new ScheduledDeliveryTask();
        task.setTaskType(ScheduledDeliveryTask.TYPE_POINT);
        task.setPoi(selectedPoi);
        task.setSelectedDoors(Arrays.copyOf(selectedDoors, selectedDoors.length));
        task.setHour(hour);
        task.setMinute(minute);
        task.setEnabled(true);

        ScheduledDeliveryManager.saveTask(requireContext(), task);
        ScheduledDeliveryManager.scheduleTask(requireContext(), task);
        Toast.makeText(getContext(), "定时任务已保存", Toast.LENGTH_SHORT).show();

        // 设置Alarm
        ScheduledDeliveryManager.scheduleTask(requireContext(), task);

        ScheduledDeliveryManager.saveTask(requireContext(), task);
        try {
            ScheduledDeliveryManager.scheduleTask(requireContext(), task);
        } catch (SecurityException e) {
            Log.e("PointDelivery", "Failed to schedule exact alarm", e);
            Toast.makeText(requireContext(), "无法设置精确闹钟: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        Toast.makeText(requireContext(), "定时任务已保存", Toast.LENGTH_SHORT).show();
    }
}
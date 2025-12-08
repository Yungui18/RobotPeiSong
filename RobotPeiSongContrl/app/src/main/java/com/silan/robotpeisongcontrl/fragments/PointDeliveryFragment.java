package com.silan.robotpeisongcontrl.fragments;

import android.content.Intent;
import android.graphics.Color;
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
        List<BasicSettingsFragment.DoorInfo> enabledDoors = BasicSettingsFragment.getEnabledDoors(requireContext());
        doorCount = enabledDoors.size();
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
        loadDoorButtonsLayout(buttonsContainer, enabledDoors); // 传入动态仓门列表

        // 加载POI列表
        loadPoiList();

        // 确认按钮监听
        btnConfirm.setOnClickListener(v -> saveDeliveryTask());

        return view;
    }

    private void loadDoorButtonsLayout(LinearLayout container, List<BasicSettingsFragment.DoorInfo> enabledDoors) {
        // 清空容器
        container.removeAllViews();
        doorButtons = new Button[enabledDoors.size()];
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < enabledDoors.size(); i++) {
            BasicSettingsFragment.DoorInfo doorInfo = enabledDoors.get(i);
            int doorHardwareId  = doorInfo.getHardwareId();

            // 创建按钮
            Button button = new Button(getContext());
            button.setId(View.generateViewId());
            String doorType = "";
            switch (doorInfo.getType()) {
                case 0: doorType = "电机"; break;
                case 1: doorType = "电磁锁"; break;
                case 2: doorType = "推杆"; break;
            }
            button.setText(String.format("仓门%d", doorHardwareId, doorType));
            button.setBackgroundResource(R.drawable.button_sky_blue_rect);
            button.setTextColor(Color.WHITE);
            button.setTextSize(16);

            // 设置布局参数
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
            );
            params.setMargins(8, 8, 8, 8);
            button.setLayoutParams(params);

            // 存储按钮引用
            doorButtons[i] = button;

            // 点击事件
            final int index = i;
            button.setOnClickListener(v -> {
                selectedDoors[index] = !selectedDoors[index];
                updateDoorButtonState(index); // 只传索引
            });

            // 添加到容器
            container.addView(button);
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
            doorButtons[index].setBackgroundResource(R.drawable.button_sky_blue_rect);
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
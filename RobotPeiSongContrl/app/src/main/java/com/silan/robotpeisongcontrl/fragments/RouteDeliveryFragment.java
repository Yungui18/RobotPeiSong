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
import android.widget.Spinner;
import android.widget.TimePicker;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.model.PatrolScheme;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.utils.ExactAlarmPermissionHelper;
import com.silan.robotpeisongcontrl.utils.PatrolSchemeManager;
import com.silan.robotpeisongcontrl.utils.ScheduledDeliveryManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class RouteDeliveryFragment extends Fragment {
    private Spinner spinnerRoutes;
    private Button[] doorButtons = new Button[4];
    private TimePicker timePicker;
    private Button btnConfirm;
    private List<PatrolScheme> schemeList = new ArrayList<>();
    private boolean[] selectedDoors = new boolean[4];
    private ActivityResultLauncher<Intent> alarmPermissionLauncher;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_route_delivery, container, false);
        alarmPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> ExactAlarmPermissionHelper.handlePermissionResult(requireContext())
        );
        // 初始化UI
        spinnerRoutes = view.findViewById(R.id.spinner_routes);
        doorButtons[0] = view.findViewById(R.id.btn_door1);
        doorButtons[1] = view.findViewById(R.id.btn_door2);
        doorButtons[2] = view.findViewById(R.id.btn_door3);
        doorButtons[3] = view.findViewById(R.id.btn_door4);
        timePicker = view.findViewById(R.id.time_picker);
        btnConfirm = view.findViewById(R.id.btn_confirm);

        // 加载路线方案
        loadPatrolSchemes();

        // 设置仓门按钮监听
        for (int i = 0; i < doorButtons.length; i++) {
            final int index = i;
            doorButtons[i].setOnClickListener(v -> {
                selectedDoors[index] = !selectedDoors[index];
                updateDoorButtonState(index);
            });
        }

        // 确认按钮监听
        btnConfirm.setOnClickListener(v -> saveDeliveryTask());

        return view;
    }

    private void loadPatrolSchemes() {
        Map<Integer, PatrolScheme> schemes = PatrolSchemeManager.loadSchemes(requireContext());
        schemeList = new ArrayList<>(schemes.values());

        List<String> schemeNames = new ArrayList<>();
        for (PatrolScheme scheme : schemeList) {
            schemeNames.add("方案 " + scheme.getSchemeId());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                schemeNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRoutes.setAdapter(adapter);
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

        int selectedPosition = spinnerRoutes.getSelectedItemPosition();
        if (selectedPosition < 0 || selectedPosition >= schemeList.size()) {
            Toast.makeText(getContext(), "请选择配送路线", Toast.LENGTH_SHORT).show();
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

        PatrolScheme selectedScheme = schemeList.get(selectedPosition);
        int hour = timePicker.getHour();
        int minute = timePicker.getMinute();

        ScheduledDeliveryTask task = new ScheduledDeliveryTask();
        task.setTaskType(ScheduledDeliveryTask.TYPE_ROUTE);
        task.setSchemeId(selectedScheme.getSchemeId());
        task.setSelectedDoors(Arrays.copyOf(selectedDoors, selectedDoors.length));
        task.setHour(hour);
        task.setMinute(minute);
        task.setEnabled(true);

        ScheduledDeliveryManager.saveTask(requireContext(), task);
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
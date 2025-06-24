package com.silan.robotpeisongcontrl.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.model.PatrolPoint;
import com.silan.robotpeisongcontrl.model.PatrolScheme;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.RobotStatus;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.PatrolSchemeManager;
import com.silan.robotpeisongcontrl.utils.RobotController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okio.ByteString;

public class PatrolSettingsFragment extends Fragment {

    private List<Poi> poiList = new ArrayList<>();
    private List<PatrolPoint> selectedPoints = new ArrayList<>(); // 改为 PatrolPoint 列表
    private int currentSchemeId = 1;
    private Button btnTask1, btnTask2, btnTask3, btnTask4;
    private int currentSelectedTask = 0;
    private Poi currentSelectedPoi = null;
    private LinearLayout container;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_patrol_settings, container, false);
        this.container = view.findViewById(R.id.poi_buttons_container);

        // 初始化任务按钮
        btnTask1 = view.findViewById(R.id.btn_task1);
        btnTask2 = view.findViewById(R.id.btn_task2);
        btnTask3 = view.findViewById(R.id.btn_task3);
        btnTask4 = view.findViewById(R.id.btn_task4);

        // 设置任务按钮点击事件
        btnTask1.setOnClickListener(v -> handleTaskButtonClick(1));
        btnTask2.setOnClickListener(v -> handleTaskButtonClick(2));
        btnTask3.setOnClickListener(v -> handleTaskButtonClick(3));
        btnTask4.setOnClickListener(v -> handleTaskButtonClick(4));

        // 加载POI列表
        loadPoiList();

        // 初始化下拉菜单
        Spinner schemeSpinner = view.findViewById(R.id.scheme_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.scheme_array, android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        schemeSpinner.setAdapter(adapter);
        schemeSpinner.setOnItemSelectedListener(new SchemeSelectionListener());

        // 创建路线方案按钮
        view.findViewById(R.id.btn_create_scheme).setOnClickListener(v -> createPatrolScheme());

        // 删除方案按钮
        view.findViewById(R.id.btn_delete_scheme).setOnClickListener(v -> deletePatrolScheme());

        // 显示电量信息
        displayBatteryInfo(view);

        return view;
    }

    private void handleTaskButtonClick(int taskId) {
        if (currentSelectedPoi != null) {
            currentSelectedTask = taskId;
            updateTaskButtonsUI();

            // 添加点到方案
            addPointToScheme(currentSelectedPoi, currentSelectedTask);
            resetSelection();
        } else {
            Toast.makeText(getContext(), "请先选择点位", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPoiList() {
        // 从API获取POI列表
        RobotController.getPoiList(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                poiList = RobotController.parsePoiList(json);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> setupPoiButtons());
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getActivity(), "获取POI失败", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private void setupPoiButtons() {
        if (getView() == null) return;

        container.removeAllViews();

        for (Poi poi : poiList) {
            Button btn = new Button(getContext());
            btn.setText(poi.getDisplayName());
            btn.setTag(poi);
            btn.setBackgroundResource(R.drawable.button_blue_rect);
            btn.setOnClickListener(v -> {
                // 重置之前选中的POI按钮
                resetPoiButtonsUI();

                // 设置当前选中的POI
                currentSelectedPoi = (Poi) v.getTag();
                currentSelectedTask = 0;

                // 高亮显示选中的POI
                btn.setBackgroundResource(R.drawable.button_red_rect);

                // 更新任务按钮状态
                updateTaskButtonsUI();
            });
            container.addView(btn);
        }
    }

    private void addPointToScheme(Poi poi, int task) {
        PatrolPoint point = new PatrolPoint(poi, task);
        selectedPoints.add(point);
        updateSelectedPointsDisplay();
    }

    private void updateSelectedPointsDisplay() {
        TextView selectedView = getView().findViewById(R.id.tv_selected_pois);
        StringBuilder builder = new StringBuilder("已选点位: ");
        for (PatrolPoint point : selectedPoints) {
            builder.append(point.toString()).append(" → ");
        }
        if (selectedPoints.size() > 0) {
            builder.setLength(builder.length() - 3); // 移除最后的箭头
        }
        selectedView.setText(builder.toString());
    }

    private void updateTaskButtonsUI() {
        // 重置所有按钮
        btnTask1.setBackgroundResource(R.drawable.button_blue_rect);
        btnTask2.setBackgroundResource(R.drawable.button_blue_rect);
        btnTask3.setBackgroundResource(R.drawable.button_blue_rect);
        btnTask4.setBackgroundResource(R.drawable.button_blue_rect);

        // 高亮当前选中的任务
        switch (currentSelectedTask) {
            case 1:
                btnTask1.setBackgroundResource(R.drawable.button_red_rect);
                break;
            case 2:
                btnTask2.setBackgroundResource(R.drawable.button_red_rect);
                break;
            case 3:
                btnTask3.setBackgroundResource(R.drawable.button_red_rect);
                break;
            case 4:
                btnTask4.setBackgroundResource(R.drawable.button_red_rect);
                break;
        }
    }

    private void resetPoiButtonsUI() {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof Button) {
                child.setBackgroundResource(R.drawable.button_blue_rect);
            }
        }
    }

    private void resetSelection() {
        currentSelectedPoi = null;
        currentSelectedTask = 0;
        resetPoiButtonsUI();
        updateTaskButtonsUI();
    }

    private void createPatrolScheme() {
        if (selectedPoints.isEmpty()) {
            Toast.makeText(getContext(), "请至少选择一个点位", Toast.LENGTH_SHORT).show();
            return;
        }

        // 修复：使用 PatrolPoint 列表创建 PatrolScheme
        PatrolScheme scheme = new PatrolScheme(currentSchemeId, selectedPoints);
        PatrolSchemeManager.saveScheme(getContext(), scheme);

        Toast.makeText(getContext(), "方案 " + currentSchemeId + " 创建成功", Toast.LENGTH_SHORT).show();
        selectedPoints.clear();
        updateSelectedPointsDisplay();
    }

    private void deletePatrolScheme() {
        PatrolSchemeManager.deleteScheme(getContext(), currentSchemeId);
        Toast.makeText(getContext(), "方案 " + currentSchemeId + " 已删除", Toast.LENGTH_SHORT).show();
    }

    private void displayBatteryInfo(View view) {
        TextView batteryView = view.findViewById(R.id.tv_battery_info);

        RobotController.getRobotStatus(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                RobotStatus status = RobotController.parseRobotStatus(json);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            batteryView.setText(String.format("电量: %.0f%%", status.getBatteryPercentage()))
                    );
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getActivity(), "获取电量失败", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private class SchemeSelectionListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            currentSchemeId = position + 1; // 方案1~9
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {}
    }
}
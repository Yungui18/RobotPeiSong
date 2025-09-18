package com.silan.robotpeisongcontrl;


import static android.app.PendingIntent.getActivity;
import static java.security.AccessController.getContext;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.silan.robotpeisongcontrl.fragments.WarehouseDoorSettingsFragment;
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

public class PatrolSettingsActivity extends BaseActivity {

    private List<Poi> poiList = new ArrayList<>();
    private List<PatrolPoint> selectedPoints = new ArrayList<>();
    private int currentSchemeId = 1;
    private Button[] taskButtons; // 动态按钮数组（替代原有的btnTask1~4）
    private int currentSelectedTask = 0;
    private Poi currentSelectedPoi = null;
    private LinearLayout container;
    private LinearLayout taskButtonsContainer; // 动态按钮容器
    private int doorCount; // 仓门数量

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patrol_settings);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 初始化视图
        initViews();

        // 加载POI列表
        loadPoiList();

        // 显示电量信息
        displayBatteryInfo();
    }

    private void initViews() {
        container = findViewById(R.id.poi_buttons_container);
        taskButtonsContainer = findViewById(R.id.task_buttons_container); // 获取动态容器

        // 获取仓门数量（与其他页面保持一致）
        doorCount = WarehouseDoorSettingsFragment.getDoorCount(this);
        // 初始化任务按钮（根据仓门数量）
        loadTaskButtonsLayout();

        // 初始化方案下拉框（保持不变）
        Spinner schemeSpinner = findViewById(R.id.scheme_spinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.scheme_array, android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        schemeSpinner.setAdapter(adapter);
        schemeSpinner.setOnItemSelectedListener(new SchemeSelectionListener());

        // 其他按钮逻辑（保持不变）
        findViewById(R.id.btn_create_scheme).setOnClickListener(v -> createPatrolScheme());
        findViewById(R.id.btn_delete_scheme).setOnClickListener(v -> deletePatrolScheme());
    }

    // 动态加载任务按钮布局（参考PointDeliveryFragment）
    private void loadTaskButtonsLayout() {
        // 清空容器
        taskButtonsContainer.removeAllViews();

        // 根据仓门数量初始化按钮数组
        taskButtons = new Button[doorCount];

        // 加载对应数量的布局文件
        LayoutInflater inflater = LayoutInflater.from(this);
        switch (doorCount) {
            case 3:
                inflater.inflate(R.layout.task_three_buttons_layout, taskButtonsContainer);
                break;
            case 4:
                inflater.inflate(R.layout.task_four_buttons_layout, taskButtonsContainer);
                break;
            case 6:
                inflater.inflate(R.layout.task_six_buttons_layout, taskButtonsContainer);
                break;
        }

        // 绑定按钮并设置点击事件
        for (int i = 0; i < doorCount; i++) {
            final int taskId = i + 1; // 任务ID从1开始
            taskButtons[i] = taskButtonsContainer.findViewById(
                    getResources().getIdentifier("btn_task" + taskId, "id", getPackageName())
            );

            if (taskButtons[i] != null) {
                taskButtons[i].setOnClickListener(v -> handleTaskButtonClick(taskId));
            }
        }
    }

    // 任务按钮点击逻辑
    private void handleTaskButtonClick(int taskId) {
        if (currentSelectedPoi != null) {
            currentSelectedTask = taskId;
            updateTaskButtonsUI();

            // 添加点到方案
            addPointToScheme(currentSelectedPoi, currentSelectedTask);
            resetSelection();
        } else {
            Toast.makeText(this, "请先选择点位", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPoiList() {
        // 从API获取POI列表
        RobotController.getPoiList(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                poiList = RobotController.parsePoiList(json);
                runOnUiThread(() -> setupPoiButtons());
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(PatrolSettingsActivity.this, "获取POI失败", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void setupPoiButtons() {
        if (container == null) return;

        container.removeAllViews();

        for (Poi poi : poiList) {
            Button btn = new Button(this);
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
        TextView selectedView = findViewById(R.id.tv_selected_pois);
        StringBuilder builder = new StringBuilder("已选点位: ");
        for (PatrolPoint point : selectedPoints) {
            builder.append(point.toString()).append(" → ");
        }
        if (selectedPoints.size() > 0) {
            builder.setLength(builder.length() - 3); // 移除最后的箭头
        }
        selectedView.setText(builder.toString());
    }

    // 更新任务按钮UI
    private void updateTaskButtonsUI() {
        // 重置所有按钮
        for (int i = 0; i < doorCount; i++) {
            taskButtons[i].setBackgroundResource(R.drawable.button_blue_rect);
        }

        // 高亮当前选中的任务（任务ID从1开始，数组索引从0开始）
        if (currentSelectedTask > 0 && currentSelectedTask <= doorCount) {
            taskButtons[currentSelectedTask - 1].setBackgroundResource(R.drawable.button_red_rect);
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
            Toast.makeText(this, "请至少选择一个点位", Toast.LENGTH_SHORT).show();
            return;
        }

        // 使用 PatrolPoint 列表创建 PatrolScheme
        PatrolScheme scheme = new PatrolScheme(currentSchemeId, selectedPoints);
        PatrolSchemeManager.saveScheme(this, scheme);

        Toast.makeText(this, "方案 " + currentSchemeId + " 创建成功", Toast.LENGTH_SHORT).show();
        selectedPoints.clear();
        updateSelectedPointsDisplay();
    }

    private void deletePatrolScheme() {
        PatrolSchemeManager.deleteScheme(this, currentSchemeId);
        Toast.makeText(this, "方案 " + currentSchemeId + " 已删除", Toast.LENGTH_SHORT).show();
    }

    private void displayBatteryInfo() {
        TextView batteryView = findViewById(R.id.tv_battery_info);

        RobotController.getRobotStatus(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                RobotStatus status = RobotController.parseRobotStatus(json);
                runOnUiThread(() ->
                        batteryView.setText(String.format("电量: %.0f%%", status.getBatteryPercentage()))
                );
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(PatrolSettingsActivity.this, "获取电量失败", Toast.LENGTH_SHORT).show()
                );
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
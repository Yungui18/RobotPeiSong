package com.silan.robotpeisongcontrl;


import static android.app.PendingIntent.getActivity;
import static java.security.AccessController.getContext;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.silan.robotpeisongcontrl.fragments.BasicSettingsFragment;
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
    private Button[] taskButtons; // 动态按钮数组
    private int currentSelectedTask = 0;
    private Poi currentSelectedPoi = null;
    private LinearLayout container;
    private LinearLayout taskButtonsContainer; // 动态按钮容器
    private List<BasicSettingsFragment.DoorInfo> enabledDoors;

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

        enabledDoors = BasicSettingsFragment.getEnabledDoors(this);
        int doorCount = enabledDoors.size(); // 从新配置获取仓门数量
        // 初始化任务按钮（根据仓门数量动态创建）
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

        if (enabledDoors == null || enabledDoors.isEmpty()) {
            Toast.makeText(this, "未检测到启用的仓门", Toast.LENGTH_SHORT).show();
            return;
        }

        // 根据仓门数量初始化按钮数组
        taskButtons = new Button[enabledDoors.size()];

        // 加载对应数量的布局文件
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < enabledDoors.size(); i++) {
            BasicSettingsFragment.DoorInfo doorInfo = enabledDoors.get(i);
            int hardwareDoorId = doorInfo.getHardwareId(); // 获取硬件仓门ID

            // 动态创建按钮
            Button button = new Button(this);
            button.setId(View.generateViewId());
            button.setText(String.format("仓门%d", hardwareDoorId)); // 显示硬件ID
            button.setBackgroundResource(R.drawable.button_sky_blue_rect);
            button.setTextColor(getResources().getColor(android.R.color.white));

            // 设置布局参数（均匀分布）
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1.0f
            );
            params.setMargins(8, 8, 8, 8);
            button.setLayoutParams(params);

            // 存储按钮引用（数组索引对应仓门列表索引）
            taskButtons[i] = button;

            // 设置按钮点击事件（传递硬件仓门ID作为taskId）
            final int currentHardwareId = hardwareDoorId;
            button.setOnClickListener(v -> handleTaskButtonClick(currentHardwareId));

            // 添加按钮到容器
            taskButtonsContainer.addView(button);
        }
    }

    // 任务按钮点击逻辑
    private void handleTaskButtonClick(int taskId) {
        if (currentSelectedPoi != null) {
            currentSelectedTask = taskId;// 存储硬件仓门ID
            updateTaskButtonsUI(); // 高亮选中的按钮

            // 添加点到方案（传递硬件仓门ID作为任务ID）
            addPointToScheme(currentSelectedPoi, taskId);
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
                currentSelectedTask = 0;// 重置任务选择

                // 高亮显示选中的POI
                btn.setBackgroundResource(R.drawable.button_red_rect);

                // 更新任务按钮状态
                updateTaskButtonsUI();
            });
            container.addView(btn);
        }
    }

    private void addPointToScheme(Poi poi, int taskId) {
        PatrolPoint point = new PatrolPoint(poi, taskId);// taskId为硬件仓门ID
        selectedPoints.add(point);
        updateSelectedPointsDisplay();
    }

    private void updateSelectedPointsDisplay() {
        TextView selectedView = findViewById(R.id.tv_selected_pois);
        StringBuilder builder = new StringBuilder("已选点位: ");
        for (PatrolPoint point : selectedPoints) {
            builder.append(point.getPoi().getDisplayName())
                    .append("(仓门").append(point.getTask()).append(") → ");
        }
        if (selectedPoints.size() > 0) {
            builder.setLength(builder.length() - 3); // 移除最后的箭头
        }
        selectedView.setText(builder.toString());
    }

    // 更新任务按钮UI
    private void updateTaskButtonsUI() {
        if (taskButtons == null) return;

        // 重置所有按钮为默认状态
        for (Button button : taskButtons) {
            if (button != null) {
                button.setBackgroundResource(R.drawable.button_blue_rect);
            }
        }

        // 高亮当前选中的任务按钮（根据硬件仓门ID匹配）
        if (currentSelectedTask > 0) {
            for (Button button : taskButtons) {
                if (button != null) {
                    // 从按钮文本中提取硬件仓门ID（格式：仓门X）
                    String buttonText = button.getText().toString();
                    int hardwareId = Integer.parseInt(buttonText.replace("仓门", ""));
                    if (hardwareId == currentSelectedTask) {
                        button.setBackgroundResource(R.drawable.button_red_rect);
                        break;
                    }
                }
            }
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
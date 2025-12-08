package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.fragments.BasicSettingsFragment;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.utils.DoorStateManager;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiDeliveryTaskSelectionActivity extends BaseActivity {
//在TaskSelectionActivity和MultiDeliveryTaskSelectionActivity页面，仓门按钮都只显示仓门1，并且点击任意按钮，都只打开一个12舱门，为什么
    private TextView countdownText;
    private CountDownTimer timer;
    private final TaskManager taskManager = TaskManager.getInstance();
    private List<Poi> poiList = new ArrayList<>();
    private Button[] taskButtons;
    private Set<Integer> selectedButtonIndices = new HashSet<>();
    private LinearLayout taskDetailsContainer;
    private LinearLayout taskButtonsContainer;
    private DoorStateManager doorStateManager; // 仓门状态管理器
    private List<BasicSettingsFragment.DoorInfo> enabledDoors;
    // 任务计数
    private int taskCount = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_delivery_task_selection);

        // 初始化仓门状态管理器
        doorStateManager = DoorStateManager.getInstance(this);

        // 获取仓门配置
        enabledDoors = BasicSettingsFragment.getEnabledDoors(this);
        int doorCount = enabledDoors.size(); // 从新配置获取仓门数量

        // 初始化视图
        taskButtonsContainer = findViewById(R.id.task_buttons_container);
        if (taskButtonsContainer == null) {
            Log.e("MultiDelivery", "未找到task_buttons_container容器");
            Toast.makeText(this, "布局加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 初始化"关闭所有仓门"按钮
        Button btnCloseAllDoors = findViewById(R.id.btn_close_all_doors);
        btnCloseAllDoors.setOnClickListener(v -> {
            // 关闭所有已打开的仓门
            doorStateManager.closeAllOpenedDoors();
            Toast.makeText(MultiDeliveryTaskSelectionActivity.this, "已关闭所有仓门", Toast.LENGTH_SHORT).show();
        });

        // 开始任务按钮
        Button btnStart = findViewById(R.id.btn_start_multi_delivery);
        btnStart.setOnClickListener(v -> startMultiDelivery());

        // 加载动态按钮布局
        loadTaskButtonsLayout(doorCount);

        // 数字按钮
        setupNumberButtons();

        // 倒计时150秒
        startCountdown();

        // 获取从MainActivity传递过来的POI列表
        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>() {
            }.getType();
            poiList = gson.fromJson(poiListJson, type);
        }

        countdownText = findViewById(R.id.tv_countdown);
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            // 清空任务
            taskManager.clearTasks();
            clearTaskButtons();
            if (taskDetailsContainer != null) {
                taskDetailsContainer.removeAllViews();
            }
            finish();
        });

        // 任务细节显示容器
        taskDetailsContainer = findViewById(R.id.task_details_container);
    }

    private void loadTaskButtonsLayout(int doorCount) {
        // 清空容器
        taskButtonsContainer.removeAllViews();

        // 无启用仓门时显示提示
        if (enabledDoors == null || enabledDoors.isEmpty()) {
            TextView tipView = new TextView(this);
            tipView.setText("暂无启用的仓门，请先在基础设置中配置");
            tipView.setTextColor(Color.RED);
            tipView.setTextSize(16);
            taskButtonsContainer.addView(tipView);
            Log.w("MultiDelivery", "没有启用的仓门");
            return;
        }

        // 动态初始化按钮数组
        taskButtons = new Button[enabledDoors.size()];

        // 根据仓门数量加载不同的布局
        for (int i = 0; i < enabledDoors.size(); i++) {
            BasicSettingsFragment.DoorInfo doorInfo = enabledDoors.get(i);
            int hardwareDoorId = doorInfo.getHardwareId();

            // 动态创建按钮
            Button button = new Button(this);
            button.setId(View.generateViewId());
            button.setText(String.format("仓门%d", hardwareDoorId));
            button.setBackgroundResource(R.drawable.button_blue_rect);
            button.setTextColor(getResources().getColor(android.R.color.white));
            button.setTextSize(18);

            // 设置布局参数，使其均匀分布
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, // 宽度填满
                    ViewGroup.LayoutParams.WRAP_CONTENT,   // 高度自适应
                    0 // weight=0
            );
            params.setMargins(0, 10, 0, 10); // 上下边距
            button.setLayoutParams(params);

            // 存储按钮引用
            taskButtons[i] = button;

            // 设置点击事件
            final int currentIndex = i;
            final int currentHardwareId = hardwareDoorId;
            button.setOnClickListener(v -> {
                Log.d("TaskClick", "点击了仓门 " + currentHardwareId);
                doorStateManager.openDoor(currentHardwareId); // 打开对应的硬件仓门

                if (selectedButtonIndices.contains(currentIndex)) {
                    selectedButtonIndices.remove(currentIndex);
                    button.setBackgroundResource(R.drawable.button_blue_rect);
                } else {
                    selectedButtonIndices.add(currentIndex);
                    button.setBackgroundResource(R.drawable.button_red_rect);
                }
            });

            // 添加按钮到容器
            taskButtonsContainer.addView(button);
        }
    }

    private void startCountdown() {
        timer = new CountDownTimer(150000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdownText.setText(String.format("%ds", millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                taskManager.clearTasks();
                clearTaskButtons();
                finish();
            }
        }.start();
    }

    private void setupNumberButtons() {
        int[] numberButtonIds = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3,
                R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7,
                R.id.btn_8, R.id.btn_9, R.id.btn_clear, R.id.btn_done
        };

        TextView display = findViewById(R.id.tv_display);

        for (int id : numberButtonIds) {
            Button btn = findViewById(id);
            btn.setOnClickListener(v -> {
                if (id == R.id.btn_clear) {
                    display.setText("");
                } else if (id == R.id.btn_done) {
                    validatePoint(display.getText().toString());
                    display.setText("");
                } else {
                    display.append(((Button) v).getText());
                }
            });
        }
    }

    private void validatePoint(String pointName) {
        if (selectedButtonIndices.isEmpty()) {
            Toast.makeText(this, "请先选择至少一个任务按钮", Toast.LENGTH_SHORT).show();
            return;
        }

        // 查找对应的POI
        Poi poi = RobotController.findPoiByName(pointName, poiList);

        if (poi != null) {
            // 检查该点位是否已分配任务
            if (taskManager.isPointAssigned(poi.getDisplayName())) {
                Toast.makeText(this, "该点位已分配任务，不可重复分配", Toast.LENGTH_SHORT).show();
                return;
            }

            // 将选中的按钮索引转换为硬件仓门ID列表
            List<Integer> selectedDoorHardwareIds = new ArrayList<>();
            for (int index : selectedButtonIndices) {
                if (index >= 0 && index < enabledDoors.size()) {
                    selectedDoorHardwareIds.add(enabledDoors.get(index).getHardwareId());
                }
            }
            // 关联点位与多个仓门ID
            taskManager.addPointWithDoors(poi, selectedDoorHardwareIds);

            // 显示任务细节（保持不变）
            showTaskDetails(poi, selectedDoorHardwareIds);
            // 恢复按钮状态
            clearTaskButtons();
        } else {
            Toast.makeText(this, "点位不存在", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearTaskButtons() {
        if (taskButtons == null) return;
        for (int i = 0; i < taskButtons.length; i++) {
            if (taskButtons[i] != null) {
                taskButtons[i].setBackgroundResource(R.drawable.button_blue_rect);
            }
        }
        selectedButtonIndices.clear();
    }

    // 开始多任务配送
    private void startMultiDelivery() {
        int taskCount = taskDetailsContainer.getChildCount();
        if (taskCount == 0) {
            Toast.makeText(this, "请至少选择一个任务", Toast.LENGTH_SHORT).show();
            return;
        }

        // 选择完毕后关闭所有已打开的仓门
        doorStateManager.closeAllOpenedDoors();
        // 显示提示：等待仓门关闭
        Toast.makeText(this, "等待仓门关闭...", Toast.LENGTH_SHORT).show();

        // 跳转至配送执行页面
        timer.cancel(); // 取消倒计时
        // 延迟5秒后再跳转（关键修改）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
            startActivity(intent);
            finish();
        }, 10000); // 5000毫秒 = 5秒
    }

    private void showTaskDetails(Poi poi, List<Integer> selectedDoorHardwareIds) {
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout taskItem = (LinearLayout) inflater.inflate(
                R.layout.item_task_detail, taskDetailsContainer, false);

        TextView taskNumText = taskItem.findViewById(R.id.tv_task_num);
        TextView taskDoorText = taskItem.findViewById(R.id.tv_task_door);
        taskNumText.setText("任务号: " + (taskDetailsContainer.getChildCount() + 1));
        taskDoorText.setText("点位: " + poi.getDisplayName() + "，仓门: " + getDoorNames(selectedDoorHardwareIds));

        taskItem.setTag(poi);

        ImageButton btnDelete = taskItem.findViewById(R.id.btn_delete_task);
        btnDelete.setOnClickListener(v -> {
            taskDetailsContainer.removeView(taskItem);
            taskManager.removeTask((Poi) taskItem.getTag());
            updateTaskNumbers();
        });

        taskDetailsContainer.addView(taskItem);
    }

    // 辅助方法：获取仓门名称字符串
    private String getDoorNames(List<Integer> selectedDoorHardwareIds) {
        StringBuilder doorInfo = new StringBuilder();
        for (int hardwareId : selectedDoorHardwareIds) {
            doorInfo.append("仓门").append(hardwareId).append(" ");
        }
        return doorInfo.toString().trim();
    }

    // 更新任务编号显示
    private void updateTaskNumbers() {
        if (taskDetailsContainer == null) return;
        int childCount = taskDetailsContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = taskDetailsContainer.getChildAt(i);
            TextView taskNumText = child.findViewById(R.id.tv_task_num);
            if (taskNumText != null) {
                taskNumText.setText("任务号: " + (i + 1));
            }
        }
    }

    // 重写系统返回键事件
    @Override
    public void onBackPressed() {
        // 清空任务
        taskManager.clearTasks();
        clearTaskButtons();
        if (taskDetailsContainer != null) {
            taskDetailsContainer.removeAllViews();
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }

        // 页面销毁时关闭所有仓门
        if (doorStateManager != null) {
            doorStateManager.closeAllOpenedDoors();
        }
    }
}
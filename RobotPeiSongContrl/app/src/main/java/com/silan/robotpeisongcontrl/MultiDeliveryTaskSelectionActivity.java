package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.fragments.WarehouseDoorSettingsFragment;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiDeliveryTaskSelectionActivity extends BaseActivity {

    private TextView countdownText;
    private CountDownTimer timer;
    private final TaskManager taskManager = TaskManager.getInstance();
    private List<Poi> poiList = new ArrayList<>();
    private final Button[] taskButtons = new Button[6];
    private Set<Integer> selectedButtonIndices = new HashSet<>();
    private LinearLayout taskDetailsContainer;
    private LinearLayout taskButtonsContainer;
    // 任务计数
    private int taskCount = 0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_delivery_task_selection);

        // 初始化视图
        taskButtonsContainer = findViewById(R.id.task_buttons_container);

        // 开始任务按钮
        Button btnStart = findViewById(R.id.btn_start_multi_delivery);
        btnStart.setOnClickListener(v -> startMultiDelivery());

        // 根据仓门数量加载对应的按钮布局
        int doorCount = WarehouseDoorSettingsFragment.getDoorCount(this);
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
            taskDetailsContainer.removeAllViews(); // 清空任务详情
            taskCount = 0; // 重置任务计数
            finish();
        });

        // 任务细节显示容器
        taskDetailsContainer = findViewById(R.id.task_details_container);
    }

    private void loadTaskButtonsLayout(int doorCount) {
        // 清空容器
        taskButtonsContainer.removeAllViews();

        // 根据仓门数量加载不同的布局
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

        // 为按钮设置点击事件
        setupTaskButtonsClickListener(doorCount);
    }

    private void setupTaskButtonsClickListener(int doorCount) {
        List<Integer> doorNumbers = WarehouseDoorSettingsFragment.getDoorNumbers(this);
        for (int doorId : doorNumbers) {
            int buttonId = getResources().getIdentifier(
                    "btn_task" + doorId, "id", getPackageName());
            Button button = taskButtonsContainer.findViewById(buttonId);

            if (button == null) {
                Log.e("TaskClick", "未找到按钮: btn_task" + doorId);
                continue;
            }

            // 存储按钮引用（使用仓门编号作为索引）
            taskButtons[doorId] = button;

            final int currentDoorId = doorId;
            button.setOnClickListener(v -> {
                Log.d("TaskClick", "点击了仓门" + currentDoorId);
                if (selectedButtonIndices.contains(currentDoorId)) {
                    selectedButtonIndices.remove(currentDoorId);
                    button.setBackgroundResource(R.drawable.button_blue_rect);
                } else {
                    selectedButtonIndices.add(currentDoorId);
                    button.setBackgroundResource(R.drawable.button_red_rect);
                }
            });
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
            // 添加POI对象到任务队列
            if (taskManager.isPointAssigned(poi.getDisplayName())) {
                Toast.makeText(this, "该点位已分配任务，不可重复分配", Toast.LENGTH_SHORT).show();
                return;
            }
            for (int index : selectedButtonIndices) {
                taskManager.addTask(poi);
            }
            // 显示任务细节
            showTaskDetails(poi, selectedButtonIndices);
            // 恢复按钮状态
            clearTaskButtons();
        } else {
            Toast.makeText(this, "点位不存在", Toast.LENGTH_SHORT).show();
        }
    }

    private void clearTaskButtons() {
        int doorCount = WarehouseDoorSettingsFragment.getDoorCount(this);
        for (int i = 0; i < doorCount; i++) { // 仅循环实际仓门数量
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

        // 跳转至配送执行页面
        timer.cancel(); // 取消倒计时
        Intent intent = new Intent(this, MovingActivity.class);
        intent.putExtra("poi_list", new Gson().toJson(poiList)); // 传递POI列表
        startActivity(intent);
        finish();
    }

    private void showTaskDetails(Poi poi, Set<Integer> selectedButtons) {
        // 加载统一的任务详情布局
        LayoutInflater inflater = LayoutInflater.from(this);
        LinearLayout taskItem = (LinearLayout) inflater.inflate(
                R.layout.item_task_detail, taskDetailsContainer, false);

        // 设置任务信息（任务号使用当前子项数量+1）
        TextView taskNumText = taskItem.findViewById(R.id.tv_task_num);
        TextView taskDoorText = taskItem.findViewById(R.id.tv_task_door);
        taskNumText.setText("任务号: " + (taskDetailsContainer.getChildCount() + 1));
        taskDoorText.setText("点位: " + poi.getDisplayName() + "，仓门: " + getDoorNames(selectedButtons));

        // 保存POI信息到视图标签，用于删除时从任务管理器移除
        taskItem.setTag(poi);

        // 删除按钮点击事件
        ImageButton btnDelete = taskItem.findViewById(R.id.btn_delete_task);
        btnDelete.setOnClickListener(v -> {
            taskDetailsContainer.removeView(taskItem);
            taskManager.removeTask((Poi) taskItem.getTag()); // 从任务管理器移除
            updateTaskNumbers(); // 更新任务编号
        });

        taskDetailsContainer.addView(taskItem);
    }

    // 辅助方法：获取仓门名称字符串
    private String getDoorNames(Set<Integer> selectedButtons) {
        StringBuilder doorInfo = new StringBuilder();
        for (int doorId : selectedButtons) { // doorId是1基（仓门1、2、3...）
            doorInfo.append("仓门").append(doorId).append(" ");
        }
        return doorInfo.toString();
    }

    // 更新任务编号显示
    private void updateTaskNumbers() {
        int childCount = taskDetailsContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = taskDetailsContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout taskLayout = (LinearLayout) child;
                TextView taskNumText = taskLayout.findViewById(R.id.tv_task_num);
                if (taskNumText != null) { // 确保视图存在
                    taskNumText.setText("任务号: " + (i + 1));
                }
            }
        }
    }

    // 重写系统返回键事件
    @Override
    public void onBackPressed() {
        // 清空任务
        taskManager.clearTasks();
        clearTaskButtons();
        taskDetailsContainer.removeAllViews(); // 清空任务详情
        taskCount = 0; // 重置任务计数
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
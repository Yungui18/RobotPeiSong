package com.silan.robotpeisongcontrl;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 多配送任务选择界面Activity
 * 负责展示和处理多个配送任务的选择、编辑和提交逻辑
 * 提供任务列表展示、任务编号更新、任务详情编辑等UI交互功能
 */
public class MultiDeliveryTaskSelectionActivity extends BaseActivity {

    private TextView countdownText;
    private CountDownTimer timer;
    private final TaskManager taskManager = TaskManager.getInstance();
    private List<Poi> poiList = new ArrayList<>();
    private final Button[] taskButtons = new Button[4];
    private Set<Integer> selectedButtonIndices = new HashSet<>();
    private LinearLayout taskDetailsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_delivery_task_selection);

        // 初始化任务按钮
        taskButtons[0] = findViewById(R.id.btn_task1);
        taskButtons[1] = findViewById(R.id.btn_task2);
        taskButtons[2] = findViewById(R.id.btn_task3);
        taskButtons[3] = findViewById(R.id.btn_task4);

        // 任务按钮点击事件
        for (int i = 0; i < taskButtons.length; i++) {
            int index = i;
            taskButtons[i].setOnClickListener(v -> selectTask(index));
        }

        // 开始任务按钮
        Button btnStart = findViewById(R.id.btn_start_multi_delivery);
        btnStart.setOnClickListener(v -> startTasks());

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
        btnBack.setOnClickListener(v -> finish());

        // 任务细节显示容器
        taskDetailsContainer = findViewById(R.id.task_details_container);
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

    private void selectTask(int index) {
        if (selectedButtonIndices.contains(index)) {
            // 如果已经选中，取消选中
            selectedButtonIndices.remove(index);
            taskButtons[index].setBackgroundResource(R.drawable.button_blue_rect);
        } else {
            // 如果未选中，选中该按钮
            selectedButtonIndices.add(index);
            taskButtons[index].setBackgroundResource(R.drawable.button_red_rect);
        }
    }

    private void clearTaskButtons() {
        for (int i = 0; i < taskButtons.length; i++) {
            taskButtons[i].setBackgroundResource(R.drawable.button_blue_rect);
        }
        selectedButtonIndices.clear();
    }

    private void startTasks() {
        if (taskManager.hasTasks()) {
            timer.cancel();
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
            startActivity(intent);
            for (Button button : taskButtons) {
                button.setBackgroundResource(R.drawable.button_green_rect);
            }
            finish();
        } else {
            Toast.makeText(this, "请先创建任务", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTaskDetails(Poi poi, Set<Integer> selectedButtons) {
        LinearLayout taskLayout = new LinearLayout(this);
        taskLayout.setOrientation(LinearLayout.HORIZONTAL);
        taskLayout.setPadding(0, 10, 0, 10);

        TextView taskDetail = new TextView(this);
        taskDetail.setText("点位: " + poi.getDisplayName() +
                ", 仓门: " + getDoorNames(selectedButtons));
        taskDetail.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ));

        Button deleteBtn = new Button(this);
        deleteBtn.setText("删除");
        deleteBtn.setOnClickListener(v -> {
            taskManager.removeTask(poi);
            taskDetailsContainer.removeView(taskLayout);
            updateTaskNumbers();
            Toast.makeText(this, "任务已删除", Toast.LENGTH_SHORT).show();
        });

        taskLayout.addView(taskDetail);
        taskLayout.addView(deleteBtn);
        taskDetailsContainer.addView(taskLayout);
    }

    // 辅助方法：获取仓门名称字符串
    private String getDoorNames(Set<Integer> selectedButtons) {
        StringBuilder doorInfo = new StringBuilder();
        for (int index : selectedButtons) {
            doorInfo.append("仓门").append(index + 1).append(" ");
        }
        return doorInfo.toString();
    }

    /**
     * 更新任务编号显示
     * 遍历任务详情容器中的子视图，修改每个任务的编号文本（从1开始）
     */
    private void updateTaskNumbers() {
        int childCount = taskDetailsContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = taskDetailsContainer.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout taskLayout = (LinearLayout) child;
                TextView taskDetail = (TextView) taskLayout.getChildAt(0);
                String originalText = taskDetail.getText().toString();
                int firstCommaIndex = originalText.indexOf(",");
                if (firstCommaIndex != -1) {
                    String newText = "任务号: " + (i + 1) + originalText.substring(firstCommaIndex);
                    taskDetail.setText(newText);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
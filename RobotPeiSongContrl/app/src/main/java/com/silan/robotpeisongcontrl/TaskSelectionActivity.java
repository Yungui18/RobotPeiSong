package com.silan.robotpeisongcontrl;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okio.ByteString;

public class TaskSelectionActivity extends BaseActivity {
    private TextView countdownText;
    private CountDownTimer timer;
    private final TaskManager taskManager = TaskManager.getInstance();
    private int currentSelectedButtonIndex = -1;
    private List<Poi> poiList = new ArrayList<>();
    private int poiCount = 0;
    private Button[] taskButtons;
    private boolean[] taskAssigned;
    private int doorCount;
    private LinearLayout taskButtonsContainer;
    private DoorStateManager doorStateManager;
    private List<Integer> doorNumbers;
    private List<BasicSettingsFragment.DoorInfo> enabledDoors;
    // 关闭仓门弹窗
    private ProgressDialog closeDoorDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_selection);

        // 初始化关闭仓门弹窗
        closeDoorDialog = new ProgressDialog(this);
        closeDoorDialog.setMessage("正在关闭仓门，请稍候...");
        closeDoorDialog.setCancelable(false);

        enabledDoors = BasicSettingsFragment.getEnabledDoors(this);
        doorCount = enabledDoors.size();
        doorNumbers = new ArrayList<>();
        for (BasicSettingsFragment.DoorInfo door : enabledDoors) {
            doorNumbers.add(door.getHardwareId());
        }
        taskAssigned = new boolean[doorCount];

        doorStateManager = DoorStateManager.getInstance(this);

        taskButtonsContainer = findViewById(R.id.task_buttons_container);
        if (taskButtonsContainer == null) {
            Log.e("TaskSelection", "未找到task_buttons_container容器");
            Toast.makeText(this, "布局加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadTaskButtonsLayout();

        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>() {
            }.getType();
            poiList = gson.fromJson(poiListJson, type);
            poiCount = poiList.size();
        } else {
            loadPoiList();
        }

        countdownText = findViewById(R.id.tv_countdown);
        Button btnStart = findViewById(R.id.btn_start);
        btnStart.setOnClickListener(v -> startTasks());

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            // 退回按钮逻辑 - 弹窗+关闭仓门+清空任务
            closeDoorDialog.show();
            new Thread(() -> {
                // 关闭所有仓门
                doorStateManager.closeAllOpenedDoors();
                // 清空任务
                taskManager.clearTasks();
                clearTaskButtons();
                // 延迟确保仓门关闭
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 主线程关闭弹窗并退出
                runOnUiThread(() -> {
                    closeDoorDialog.dismiss();
                    finish();
                });
            }).start();
        });

        // 数字按钮逻辑
        setupNumberButtons();

        startCountdown();
    }

    private void loadTaskButtonsLayout() {
        taskButtonsContainer.removeAllViews();

        if (doorCount == 0) {
            TextView tipView = new TextView(this);
            tipView.setText("暂无启用的仓门，请先在基础设置中配置");
            tipView.setTextColor(Color.RED);
            tipView.setTextSize(16);
            taskButtonsContainer.addView(tipView);
            return;
        }

        taskButtons = new Button[doorCount];

        for (int i = 0; i < doorCount; i++) {
            BasicSettingsFragment.DoorInfo doorInfo = enabledDoors.get(i);
            Button button = new Button(this);
            button.setId(View.generateViewId());

            String typeStr = getDoorTypeText(doorInfo.getType());
            String buttonText = String.format("行%d-%d号（%s）ID:%d",
                    doorInfo.getRow(),
                    doorInfo.getPosition(),
                    typeStr,
                    doorInfo.getHardwareId());

            button.setText(buttonText);
            button.setBackgroundResource(R.drawable.button_blue_rect);
            button.setTextColor(Color.WHITE);
            button.setTextSize(16);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0
            );
            params.setMargins(0, 10, 0, 10);
            button.setLayoutParams(params);

            final int index = i;
            final int currentHardwareId = doorInfo.getHardwareId();
            button.setOnClickListener(v -> {
                if (taskAssigned[index]) {
                    Toast.makeText(TaskSelectionActivity.this,
                            "该任务已分配，无法操作仓门",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // 单点仓门互斥校验
                boolean openSuccess = doorStateManager.openSingleDoor(currentHardwareId);
                if (!openSuccess) {
                    Integer openedDoorId = doorStateManager.getSingleOpenedDoorId();
                    Toast.makeText(this, "请先关闭当前打开的仓门：ID=" + openedDoorId, Toast.LENGTH_SHORT).show();
                    return;
                }

                // 更新按钮样式
                if (doorStateManager.isDoorOpened(currentHardwareId)) {
                    taskButtons[index].setBackgroundResource(R.drawable.button_red_rect);
                    currentSelectedButtonIndex = index;
                    Toast.makeText(TaskSelectionActivity.this,
                            "仓门" + currentHardwareId + "已打开",
                            Toast.LENGTH_SHORT).show();
                } else {
                    taskButtons[index].setBackgroundResource(R.drawable.button_blue_rect);
                    currentSelectedButtonIndex = -1;
                    Toast.makeText(TaskSelectionActivity.this,
                            "仓门" + currentHardwareId + "已关闭",
                            Toast.LENGTH_SHORT).show();
                }
                Log.d("TaskSelection", "点击仓门：" + buttonText + "，硬件ID：" + currentHardwareId);
            });

            taskButtonsContainer.addView(button);
            taskButtons[i] = button;
        }
    }

    private String getDoorTypeText(int type) {
        switch (type) {
            case 0:
                return "电机";
            case 1:
                return "电磁锁";
            case 2:
                return "推杆";
            default:
                return "未知";
        }
    }

    private void loadPoiList() {
        RobotController.getPoiList(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                poiList = RobotController.parsePoiList(json);
                runOnUiThread(() -> {
                    poiCount = poiList.size();
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("TaskSelection", "Failed to load POIs", e);
                runOnUiThread(() -> {
                    Toast.makeText(TaskSelectionActivity.this, "点位加载失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void startCountdown() {
        timer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdownText.setText(String.format("%ds", millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                taskManager.clearTasks();
                clearTaskButtons();
                doorStateManager.closeAllOpenedDoors();
                finish();
            }
        }.start();
    }

    // 数字按钮
    private void setupNumberButtons() {
        int[] numberButtonIds = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3,
                R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7,
                R.id.btn_8, R.id.btn_9, R.id.btn_clear
        };

        TextView display = findViewById(R.id.tv_display);
        // 退格按钮
        Button btnBackspace = findViewById(R.id.btn_done);
        // 完成并关闭仓门按钮
        Button btnCompleteCloseDoor = findViewById(R.id.btn_complete_close_door);

        // 数字按钮逻辑
        for (int id : numberButtonIds) {
            Button btn = findViewById(id);
            btn.setOnClickListener(v -> {
                if (id == R.id.btn_clear) {
                    display.setText("");
                } else {
                    display.append(((Button) v).getText());
                }
            });
        }

        // 退格功能
        btnBackspace.setOnClickListener(v -> {
            String currentText = display.getText().toString().trim();
            if (!currentText.isEmpty()) {
                // 删除最后一个字符
                display.setText(currentText.substring(0, currentText.length() - 1));
            }
        });

        // 完成并关闭仓门功能
        btnCompleteCloseDoor.setOnClickListener(v -> {
            if (currentSelectedButtonIndex == -1) {
                Toast.makeText(this, "请先选择仓门", Toast.LENGTH_SHORT).show();
                return;
            }
            // 关闭选中的仓门
            int doorId = doorNumbers.get(currentSelectedButtonIndex);
            doorStateManager.closeDoor(doorId);
            // 清空输入框
            display.setText("");
            // 更新按钮样式
            taskButtons[currentSelectedButtonIndex].setBackgroundResource(R.drawable.button_blue_rect);
            currentSelectedButtonIndex = -1;
            Toast.makeText(this, "仓门" + doorId + "已关闭", Toast.LENGTH_SHORT).show();
        });
    }

    private void validatePoint(String pointName) {
        if (currentSelectedButtonIndex == -1) {
            Toast.makeText(this, "请先选择一个任务按钮", Toast.LENGTH_SHORT).show();
            return;
        }

        if (poiCount <= 0) {
            Toast.makeText(this, "点位数据未加载完成，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }

        if (taskManager.taskCount() >= poiCount) {
            Toast.makeText(this, "任务数量已达上限（最多" + poiCount + "个）", Toast.LENGTH_SHORT).show();
            return;
        }

        Poi poi = RobotController.findPoiByName(pointName, poiList);

        if (poi != null) {
            int selectedDoorId = doorNumbers.get(currentSelectedButtonIndex);

            if (taskManager.isPointAssigned(poi.getDisplayName())) {
                Toast.makeText(this, "该点位已分配任务，不可重复分配", Toast.LENGTH_SHORT).show();
                return;
            }

            List<Integer> doorIds = new ArrayList<>();
            doorIds.add(selectedDoorId);

            taskManager.addPointWithDoors(poi, doorIds);
            Log.d("TaskSelection", "单点任务添加成功，点位：" + pointName + "，关联仓门ID列表：" + doorIds);

            taskManager.addTask(poi);

            taskButtons[currentSelectedButtonIndex].setText(poi.getDisplayName());
            taskButtons[currentSelectedButtonIndex].setBackgroundResource(R.drawable.button_green_rect);
            taskAssigned[currentSelectedButtonIndex] = true;
            currentSelectedButtonIndex = -1;
        } else {
            Toast.makeText(this, "点位不存在", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectTask(int index) {
        for (int i = 0; i < doorCount; i++) {
            if (!taskAssigned[i]) {
                taskButtons[i].setBackgroundResource(R.drawable.button_blue_rect);
            }
        }

        if (taskAssigned[index]) {
            Toast.makeText(this, "该任务已分配，不能修改", Toast.LENGTH_SHORT).show();
            return;
        }

        taskButtons[index].setBackgroundResource(R.drawable.button_red_rect);
        currentSelectedButtonIndex = index;
    }

    private void clearTaskButtons() {
        for (int i = 0; i < doorCount; i++) {
            BasicSettingsFragment.DoorInfo doorInfo = enabledDoors.get(i);
            String typeStr = getDoorTypeText(doorInfo.getType());
            String resetText = String.format("行%d-%d号（%s）ID:%d",
                    doorInfo.getRow(),
                    doorInfo.getPosition(),
                    typeStr,
                    doorInfo.getHardwareId());

            taskButtons[i].setText(resetText);
            taskButtons[i].setBackgroundResource(R.drawable.button_blue_rect);
            taskAssigned[i] = false;
        }
        currentSelectedButtonIndex = -1;
    }

    private void startTasks() {
        if (taskManager.hasTasks()) {
            timer.cancel();
            doorStateManager.closeAllOpenedDoors();
            Toast.makeText(this, "等待仓门关闭...", Toast.LENGTH_SHORT).show();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent intent = new Intent(this, MovingActivity.class);
                intent.putExtra("poi_list", new Gson().toJson(poiList));
                startActivity(intent);
                for (Button button : taskButtons) {
                    button.setBackgroundResource(R.drawable.button_green_rect);
                }
                finish();
            }, 10000);
        } else {
            Toast.makeText(this, "请先创建任务", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        // 复用退回按钮逻辑
        closeDoorDialog.show();
        new Thread(() -> {
            doorStateManager.closeAllOpenedDoors();
            taskManager.clearTasks();
            clearTaskButtons();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            runOnUiThread(() -> {
                closeDoorDialog.dismiss();
                super.onBackPressed();
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
        if (closeDoorDialog != null && closeDoorDialog.isShowing()) {
            closeDoorDialog.dismiss();
        }
    }
}
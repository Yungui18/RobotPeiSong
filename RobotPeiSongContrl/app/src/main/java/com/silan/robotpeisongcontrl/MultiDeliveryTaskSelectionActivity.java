package com.silan.robotpeisongcontrl;

import android.app.ProgressDialog;
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
import com.silan.robotpeisongcontrl.utils.LoadingDialogUtil;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MultiDeliveryTaskSelectionActivity extends BaseActivity implements MainActivity.OnMainInitCompleteListener {
    private TextView countdownText;
    private CountDownTimer timer;
    private final TaskManager taskManager = TaskManager.getInstance();
    private List<Poi> poiList = new ArrayList<>();
    private Button[] taskButtons;
    private Set<Integer> selectedButtonIndices = new HashSet<>();
    private LinearLayout taskDetailsContainer;
    private LinearLayout taskButtonsContainer;
    private DoorStateManager doorStateManager;
    private List<BasicSettingsFragment.DoorInfo> enabledDoors;
    private int taskCount = 0;
    // 关闭仓门弹窗
    private ProgressDialog closeDoorDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_delivery_task_selection);

        // 初始化关闭仓门弹窗
        closeDoorDialog = new ProgressDialog(this);
        closeDoorDialog.setMessage("正在关闭仓门，请稍候...");
        closeDoorDialog.setCancelable(false);

        doorStateManager = DoorStateManager.getInstance(this);

        enabledDoors = BasicSettingsFragment.getEnabledDoors(this);
        int doorCount = enabledDoors.size();

        taskButtonsContainer = findViewById(R.id.task_buttons_container);
        if (taskButtonsContainer == null) {
            Log.e("MultiDelivery", "未找到task_buttons_container容器");
            Toast.makeText(this, "布局加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 移除关闭所有仓门按钮的代码

        Button btnStart = findViewById(R.id.btn_start_multi_delivery);
        btnStart.setOnClickListener(v -> startMultiDelivery());

        loadTaskButtonsLayout(doorCount);

        setupNumberButtons();

        startCountdown();

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
            // 显示加载弹窗
            LoadingDialogUtil.showLoadingDialog(this, "主界面初始化中，请稍候...");
            // 添加MainActivity初始化监听
            MainActivity.addMainInitListener(this);

            // 退回按钮逻辑
            closeDoorDialog.show();
            new Thread(() -> {
                // 关闭所有仓门
                doorStateManager.closeAllOpenedDoors();
                // 清空任务
                taskManager.clearTasks();
                clearTaskButtons();
                if (taskDetailsContainer != null) {
                    taskDetailsContainer.removeAllViews();
                }
                // 延迟确保关闭
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                // 主线程关闭弹窗并退出
                runOnUiThread(() -> {
                    closeDoorDialog.dismiss();
                });
            }).start();
        });

        taskDetailsContainer = findViewById(R.id.task_details_container);
    }

    private void loadTaskButtonsLayout(int doorCount) {
        taskButtonsContainer.removeAllViews();

        if (enabledDoors == null || enabledDoors.isEmpty()) {
            TextView tipView = new TextView(this);
            tipView.setText("暂无启用的仓门，请先在基础设置中配置");
            tipView.setTextColor(Color.RED);
            tipView.setTextSize(16);
            taskButtonsContainer.addView(tipView);
            Log.w("MultiDelivery", "没有启用的仓门");
            return;
        }

        taskButtons = new Button[enabledDoors.size()];

        for (int i = 0; i < enabledDoors.size(); i++) {
            BasicSettingsFragment.DoorInfo doorInfo = enabledDoors.get(i);
            int hardwareDoorId = doorInfo.getHardwareId();

            Button button = new Button(this);
            button.setId(View.generateViewId());
            button.setText(BasicSettingsFragment.getStandardDoorButtonText(doorInfo));
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

            taskButtons[i] = button;

            final int currentIndex = i;
            final int currentHardwareId = hardwareDoorId;
            button.setOnClickListener(v -> {
                Log.d("TaskClick", "点击了仓门 " + currentHardwareId);

                if (doorStateManager.isDoorOpened(currentHardwareId)) {
                    doorStateManager.closeDoor(currentHardwareId);
                    selectedButtonIndices.remove(currentIndex);
                    button.setBackgroundResource(R.drawable.button_sky_blue_rect);
                    Toast.makeText(MultiDeliveryTaskSelectionActivity.this,
                            "仓门" + currentHardwareId + "已关闭",
                            Toast.LENGTH_SHORT).show();
                } else {
                    doorStateManager.openDoor(currentHardwareId);
                    selectedButtonIndices.add(currentIndex);
                    button.setBackgroundResource(R.drawable.button_red_rect);
                    Toast.makeText(MultiDeliveryTaskSelectionActivity.this,
                            "仓门" + currentHardwareId + "已打开",
                            Toast.LENGTH_SHORT).show();
                }
            });

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
                display.setText(currentText.substring(0, currentText.length() - 1));
            }
        });

        // 完成并关闭仓门功能（多点：关闭所有选中仓门）
        btnCompleteCloseDoor.setOnClickListener(v -> {
            if (selectedButtonIndices.isEmpty()) {
                Toast.makeText(this, "请先选择至少一个仓门", Toast.LENGTH_SHORT).show();
                return;
            }
            Set<Integer> tempSelectedIndices = new HashSet<>(selectedButtonIndices);
            // 获取输入框点位名称并判空
            String pointName = display.getText().toString().trim();
            if (pointName.isEmpty()) {
                Toast.makeText(this, "请输入点位名称", Toast.LENGTH_SHORT).show();
                return;
            }
            // 调用validatePoint创建任务
            validatePoint(pointName);
            // 任务创建成功后再执行关闭仓门逻辑
            if (taskManager.isPointAssigned(pointName)) {
                for (int index : tempSelectedIndices) {
                    if (index >= 0 && index < enabledDoors.size()) {
                        int doorId = enabledDoors.get(index).getHardwareId();
                        doorStateManager.closeDoor(doorId);
                        if (index < taskButtons.length) {
                            taskButtons[index].setBackgroundResource(R.drawable.button_blue_rect);
                        }
                    }
                }
                display.setText("");
                selectedButtonIndices.clear();
                Toast.makeText(this, "已关闭所有选中仓门，任务创建成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "任务创建失败，请检查点位名称", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void validatePoint(String pointName) {
        if (selectedButtonIndices.isEmpty()) {
            Toast.makeText(this, "请先选择至少一个任务按钮", Toast.LENGTH_SHORT).show();
            return;
        }

        Poi poi = RobotController.findPoiByName(pointName, poiList);

        if (poi != null) {
            if (taskManager.isPointAssigned(poi.getDisplayName())) {
                Toast.makeText(this, "该点位已分配任务，不可重复分配", Toast.LENGTH_SHORT).show();
                return;
            }

            List<Integer> selectedDoorHardwareIds = new ArrayList<>();
            for (int index : selectedButtonIndices) {
                if (index >= 0 && index < enabledDoors.size()) {
                    selectedDoorHardwareIds.add(enabledDoors.get(index).getHardwareId());
                }
            }
            taskManager.addPointWithDoors(poi, selectedDoorHardwareIds);

            showTaskDetails(poi, selectedDoorHardwareIds);
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

    private void startMultiDelivery() {
        int taskCount = taskDetailsContainer.getChildCount();
        if (taskCount == 0) {
            Toast.makeText(this, "请至少选择一个任务", Toast.LENGTH_SHORT).show();
            return;
        }

        doorStateManager.closeAllOpenedDoors();
        Toast.makeText(this, "等待仓门关闭...", Toast.LENGTH_SHORT).show();

        timer.cancel();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
            startActivity(intent);
            finish();
        }, 10000);
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

    private String getDoorNames(List<Integer> selectedDoorHardwareIds) {
        StringBuilder doorInfo = new StringBuilder();
        for (int hardwareId : selectedDoorHardwareIds) {
            doorInfo.append("仓门").append(hardwareId).append(" ");
        }
        return doorInfo.toString().trim();
    }

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

    @Override
    public void onBackPressed() {
        // 显示加载弹窗并添加监听
        LoadingDialogUtil.showLoadingDialog(this, "主界面初始化中，请稍候...");
        MainActivity.addMainInitListener(this);

        // 复用退回按钮逻辑
        closeDoorDialog.show();
        new Thread(() -> {
            doorStateManager.closeAllOpenedDoors();
            taskManager.clearTasks();
            clearTaskButtons();
            if (taskDetailsContainer != null) {
                taskDetailsContainer.removeAllViews();
            }
            try {
                Thread.sleep(2000);
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
        // 移除监听器+关闭弹窗
        MainActivity.removeMainInitListener(this);
        LoadingDialogUtil.dismissLoadingDialog();
        if (timer != null) {
            timer.cancel();
        }
        if (closeDoorDialog != null && closeDoorDialog.isShowing()) {
            closeDoorDialog.dismiss();
        }
        if (doorStateManager != null) {
            doorStateManager.closeAllOpenedDoors();
        }
    }

    @Override
    public void onInitComplete() {
        //  关闭加载弹窗
        LoadingDialogUtil.dismissLoadingDialog();
        //  返回主界面
        finish();
    }

    @Override
    public void onInitFailed() {
        LoadingDialogUtil.dismissLoadingDialog();
        Log.i("SettingMainActivity", "onInitFailed: 主界面初始化失败，请重试");
    }
}
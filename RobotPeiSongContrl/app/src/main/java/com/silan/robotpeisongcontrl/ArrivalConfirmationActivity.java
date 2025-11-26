package com.silan.robotpeisongcontrl;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.fragments.BasicSettingsFragment;
import com.silan.robotpeisongcontrl.model.DeliveryFailure;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.utils.DeliveryFailureManager;
import com.silan.robotpeisongcontrl.utils.DoorStateManager;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 到达确认界面
 * 主要功能：
 * 1. 显示倒计时
 * 2. 提供取物和完成按钮
 * 3. 根据设置决定是否进行密码验证
 * 4. 处理配送流程中的密码验证逻辑
 */
public class ArrivalConfirmationActivity extends BaseActivity {

    private CountDownTimer timer;
    private List<Poi> poiList;
    private String enteredPickupPassword = "";
    private LinearLayout dotsContainer;
    private Button[] numberButtons = new Button[10];
    private Button btnDelete;
    private AlertDialog passwordDialog;
    private boolean isScheduledTask;
    private boolean[] selectedDoors;
    private boolean isDeliveryFailed = false;
    private int currentDoorTask = 0;
    private DoorStateManager doorStateManager; // 仓门状态管理器实例
    private List<Integer> currentTaskDoorIds = new ArrayList<>();
    private static final int DOOR_OPEN_DELAY = 300;
    private Handler doorHandler = new Handler(Looper.getMainLooper());
    private List<BasicSettingsFragment.DoorInfo> enabledDoors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arrival_confirmation);

        enabledDoors = BasicSettingsFragment.getEnabledDoors(this);
        // 计算实际启用的仓门数量（避免空指针）
        int dynamicDoorCount = (enabledDoors != null) ? enabledDoors.size() : 0;
        // 获取POI列表
        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>() {
            }.getType();
            poiList = gson.fromJson(poiListJson, type);
        }

        TextView countdownText = findViewById(R.id.tv_countdown);
        Button btnPickup = findViewById(R.id.btn_pickup);
        Button btnComplete = findViewById(R.id.btn_complete);

        // 初始化仓门状态管理器
        doorStateManager = DoorStateManager.getInstance(this);

        // 初始化60秒倒计时
        timer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdownText.setText(String.format("%ds", millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                // 记录配送失败
                isDeliveryFailed = true;
                recordDeliveryFailure();

                // 关闭所有已打开的仓门
                doorStateManager.closeAllOpenedDoors();

                proceedToNextTask();
            }
        }.start();
        isScheduledTask = getIntent().getBooleanExtra("scheduled_task", false);
        selectedDoors = getIntent().getBooleanArrayExtra("selected_doors");
        if (selectedDoors == null || selectedDoors.length != dynamicDoorCount) {
            selectedDoors = new boolean[dynamicDoorCount];
        }

        if (isScheduledTask) {
            performPickupAction();
        }

        // 关键修改：获取当前任务关联的仓门ID
        Poi currentPoi = TaskManager.getInstance().getCurrentPoi();
        if (currentPoi != null) {
            currentTaskDoorIds = TaskManager.getInstance().getDoorIdsForPoint(currentPoi.getDisplayName());
            Log.d("ArrivalConfirmation", "当前任务关联的所有仓门ID：" + currentTaskDoorIds);
        }

        // 取物按钮点击事件
        btnPickup.setOnClickListener(v -> handlePickupAction());

        // 完成按钮点击事件
        btnComplete.setOnClickListener(v -> handleCompleteAction());
        currentDoorTask = getIntent().getIntExtra("current_door_task", 0);
    }

    /**
     * 记录配送失败
     */
    private void recordDeliveryFailure() {
        // 获取当前点位信息
        Poi currentPoi = TaskManager.getInstance().getCurrentPoi();

        if (currentPoi == null) {
            Log.e("ArrivalConfirmation", "当前点位为空，无法记录失败");
            return;
        }

        // 创建失败任务
        DeliveryFailure failure = new DeliveryFailure(
                currentPoi.getDisplayName(),
                getDoorsToOpen(),
                System.currentTimeMillis()
        );

        // 保存到失败任务管理器
        DeliveryFailureManager.addFailure(getApplicationContext(), failure);
    }

    /**
     * 计算需要打开的仓门（基于动态仓门配置）
     *
     * @return 仓门打开状态数组（索引对应启用仓门列表的顺序）
     */
    private boolean[] getDoorsToOpen() {
        List<Integer> enabledHardwareIds = new ArrayList<>();
        if (enabledDoors != null) {
            for (BasicSettingsFragment.DoorInfo doorInfo : enabledDoors) {
                enabledHardwareIds.add(doorInfo.getHardwareId());
            }
        }
        int doorCount = enabledHardwareIds.size();
        boolean[] doorsToOpen = new boolean[doorCount];
        // 优先使用当前任务关联的仓门ID
        if (!currentTaskDoorIds.isEmpty()) {
            for (int hardwareId : currentTaskDoorIds) {
                int doorIndex = enabledHardwareIds.indexOf(hardwareId);
                if (doorIndex != -1) {
                    doorsToOpen[doorIndex] = true;
                    Log.d("ArrivalConfirmation", "任务关联仓门：索引=" + doorIndex + "，硬件ID=" + hardwareId);
                } else {
                    Log.e("ArrivalConfirmation", "任务关联的仓门ID=" + hardwareId + "未在启用列表中");
                }
            }
        } else if (currentDoorTask > 0) {
            int doorIndex = enabledHardwareIds.indexOf(currentDoorTask);
            if (doorIndex != -1) {
                doorsToOpen[doorIndex] = true;
                Log.d("ArrivalConfirmation", "巡游任务仓门：索引=" + doorIndex + "，硬件ID=" + currentDoorTask);
            } else {
                Log.e("ArrivalConfirmation", "巡游任务仓门ID=" + currentDoorTask + "未在启用列表中");
            }
        } else if (selectedDoors != null) {
            for (int i = 0; i < Math.min(selectedDoors.length, doorCount); i++) {
                doorsToOpen[i] = selectedDoors[i];
            }
            Log.d("ArrivalConfirmation", "定时任务选中仓门：" + java.util.Arrays.toString(doorsToOpen));
        } else if (doorCount > 0) {
            doorsToOpen[0] = true;
            Log.d("ArrivalConfirmation", "默认打开第一个仓门：索引=0，硬件ID=" + enabledHardwareIds.get(0));
        }


        return doorsToOpen;
    }

    /**
     * 处理取物操作
     * 根据设置决定是否显示密码验证对话框
     */
    private void handlePickupAction() {
        // 检查是否启用了配送验证
        SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
        boolean verificationEnabled = prefs.getBoolean("verification_enabled", false);

        if (verificationEnabled) {
            showPickupPasswordDialog(); // 显示取物密码验证对话框
        } else {
            performPickupAction(); // 直接执行取物操作
        }
    }

    /**
     * 处理完成操作 - 不再需要验证送物密码
     */
    private void handleCompleteAction() {
        // 关闭所有已打开的仓门
        doorStateManager.closeAllOpenedDoors();
        // 显示提示：等待仓门关闭
        Toast.makeText(this, "等待仓门关闭...", Toast.LENGTH_SHORT).show();
        // 仅移除当前正在处理的点位任务（保留其他未处理任务）
        TaskManager taskManager = TaskManager.getInstance();
        Poi currentPoi = taskManager.getCurrentPoi(); // 获取当前正在处理的点位
        if (currentPoi != null) {
            taskManager.removeTask(currentPoi); // 只删除当前点位的任务
        }
        // 延迟5秒后再继续下一个任务（关键修改）
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            proceedToNextTask();
        }, 10000); // 5000毫秒 = 5秒
    }

    /**
     * 显示取物密码验证对话框
     */
    private void showPickupPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_password_auth, null);
        builder.setView(dialogView);

        // 设置标题
        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText("取物验证");

        // 初始化视图
        dotsContainer = dialogView.findViewById(R.id.dots_container);
        numberButtons[0] = dialogView.findViewById(R.id.btn_0);
        numberButtons[1] = dialogView.findViewById(R.id.btn_1);
        numberButtons[2] = dialogView.findViewById(R.id.btn_2);
        numberButtons[3] = dialogView.findViewById(R.id.btn_3);
        numberButtons[4] = dialogView.findViewById(R.id.btn_4);
        numberButtons[5] = dialogView.findViewById(R.id.btn_5);
        numberButtons[6] = dialogView.findViewById(R.id.btn_6);
        numberButtons[7] = dialogView.findViewById(R.id.btn_7);
        numberButtons[8] = dialogView.findViewById(R.id.btn_8);
        numberButtons[9] = dialogView.findViewById(R.id.btn_9);
        btnDelete = dialogView.findViewById(R.id.btn_delete);

        // 初始化密码圆点
        createPasswordDots();

        // 设置数字按钮点击事件
        for (int i = 0; i < numberButtons.length; i++) {
            final int digit = i;
            numberButtons[i].setOnClickListener(v -> addDigit(String.valueOf(digit)));
        }

        // 设置删除按钮点击事件
        btnDelete.setOnClickListener(v -> removeDigit());

        // 创建对话框
        passwordDialog = builder.create();
        passwordDialog.setCanceledOnTouchOutside(false);
        passwordDialog.show();

        // 重置输入状态
        enteredPickupPassword = "";
        updateDotsDisplay();
    }

    /**
     * 创建密码圆点指示器
     */
    private void createPasswordDots() {
        dotsContainer.removeAllViews();

        int dotSize = getResources().getDimensionPixelSize(R.dimen.password_dot_size);
        int margin = getResources().getDimensionPixelSize(R.dimen.password_dot_margin);

        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
            params.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(params);

            // 创建圆形背景
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(getResources().getDimensionPixelSize(R.dimen.password_dot_stroke), Color.GRAY);
            dot.setBackground(bg);

            dotsContainer.addView(dot);
        }
    }

    /**
     * 添加数字到密码
     */
    private void addDigit(String digit) {
        if (enteredPickupPassword.length() < 4) {
            enteredPickupPassword += digit;
            updateDotsDisplay();

            // 自动检查密码
            if (enteredPickupPassword.length() == 4) {
                if (validatePickupPassword(enteredPickupPassword)) {
                    // 验证通过，执行取物操作
                    performPickupAction();

                    // 关闭对话框
                    if (passwordDialog != null && passwordDialog.isShowing()) {
                        passwordDialog.dismiss();
                    }
                } else {
                    Toast.makeText(ArrivalConfirmationActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
                    enteredPickupPassword = "";
                    updateDotsDisplay();
                }
            }
        }
    }

    /**
     * 删除最后一个数字
     */
    private void removeDigit() {
        if (enteredPickupPassword.length() > 0) {
            enteredPickupPassword = enteredPickupPassword.substring(0, enteredPickupPassword.length() - 1);
            updateDotsDisplay();
        }
    }

    /**
     * 更新圆点显示状态
     */
    private void updateDotsDisplay() {
        for (int i = 0; i < dotsContainer.getChildCount(); i++) {
            View dot = dotsContainer.getChildAt(i);
            if (dot != null) {
                GradientDrawable bg = (GradientDrawable) dot.getBackground();
                if (i < enteredPickupPassword.length()) {
                    // 填充的圆点
                    bg.setColor(Color.BLACK);
                    bg.setStroke(0, Color.TRANSPARENT);
                } else {
                    // 空心的圆点
                    bg.setColor(Color.TRANSPARENT);
                    bg.setStroke(getResources().getDimensionPixelSize(R.dimen.password_dot_stroke), Color.GRAY);
                }
            }
        }
    }

    /**
     * 验证取物密码是否正确
     */
    private boolean validatePickupPassword(String enteredPassword) {
        SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
        String correctPassword = prefs.getString("pickup_password", "");
        return enteredPassword.equals(correctPassword);
    }

    /**
     * 执行取物操作
     */
    private void performPickupAction() {
        List<Integer> enabledHardwareIds = new ArrayList<>();
        if (enabledDoors != null) {
            for (BasicSettingsFragment.DoorInfo doorInfo : enabledDoors) {
                enabledHardwareIds.add(doorInfo.getHardwareId());
            }
        }
        // 获取需要打开的仓门索引数组
        boolean[] doorsToOpen = getDoorsToOpen();

        // 收集需要打开的仓门ID（过滤无效项）
        List<Integer> doorsToOpenList = new ArrayList<>();
        for (int i = 0; i < doorsToOpen.length; i++) {
            if (doorsToOpen[i] && i < enabledHardwareIds.size()) {
                int hardwareId = enabledHardwareIds.get(i);
                doorsToOpenList.add(hardwareId);
                Log.d("ArrivalConfirmation", "待打开仓门：硬件ID=" + hardwareId);
            }
        }

        // 按间隔发送每个仓门的打开指令
        for (int i = 0; i < doorsToOpenList.size(); i++) {
            final int hardwareId = doorsToOpenList.get(i);
            doorHandler.postDelayed(() -> {
                // 调用仓门管理器打开指定硬件ID的仓门
                doorStateManager.openDoor(hardwareId);
                Log.d("ArrivalConfirmation", "已发送打开指令：硬件ID=" + hardwareId);
            }, i * DOOR_OPEN_DELAY);
        }

        Toast.makeText(this, "请取走物品", Toast.LENGTH_SHORT).show();
    }

    /**
     * 继续下一个任务或回桩
     */
    private void proceedToNextTask() {
        timer.cancel(); // 停止倒计时

        if (TaskManager.getInstance().hasTasks()) {
            // 还有任务，继续下一个
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
            startActivity(intent);
        } else {
            // 所有任务完成，回桩
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
            startActivity(intent);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
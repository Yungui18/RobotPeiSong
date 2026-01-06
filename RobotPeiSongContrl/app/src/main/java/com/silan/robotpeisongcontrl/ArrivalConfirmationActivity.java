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
import com.silan.robotpeisongcontrl.utils.TaskSuccessManager;

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
    private DoorStateManager doorStateManager;
    private List<Integer> currentTaskDoorIds = new ArrayList<>();
    private static final int DOOR_OPEN_DELAY = 300;
    private Handler doorHandler = new Handler(Looper.getMainLooper());
    private List<BasicSettingsFragment.DoorInfo> enabledDoors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arrival_confirmation);

        enabledDoors = BasicSettingsFragment.getEnabledDoors(this);
        int dynamicDoorCount = (enabledDoors != null) ? enabledDoors.size() : 0;
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

        doorStateManager = DoorStateManager.getInstance(this);

        // 原有倒计时逻辑
        timer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdownText.setText(String.format("%ds", millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                isDeliveryFailed = true;
                recordDeliveryFailure();
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
            // ===== 修改：定时任务执行取物时加灰化 =====
            lockAllButtons();
            performPickupAction();
            unlockAllButtons();
        }

        Poi currentPoi = TaskManager.getInstance().getCurrentPoi();
        if (currentPoi != null) {
            currentTaskDoorIds = TaskManager.getInstance().getDoorIdsForPoint(currentPoi.getDisplayName());
            Log.d("ArrivalConfirmation", "当前任务关联的所有仓门ID：" + currentTaskDoorIds);
        }

        // ===== 核心修改1：取物按钮点击加灰化 =====
        btnPickup.setOnClickListener(v -> {
            lockAllButtons(); // 锁定所有按钮，防止连续点击
            handlePickupAction();
        });

        // ===== 核心修改2：完成按钮点击加灰化 =====
        btnComplete.setOnClickListener(v -> {
            lockAllButtons(); // 锁定所有按钮，防止连续点击
            handleCompleteAction();
        });
        currentDoorTask = getIntent().getIntExtra("current_door_task", 0);
    }

    private void recordDeliveryFailure() {
        Poi currentPoi = TaskManager.getInstance().getCurrentPoi();
        if (currentPoi == null) {
            Log.e("ArrivalConfirmation", "当前点位为空，无法记录失败");
            return;
        }
        DeliveryFailure failure = new DeliveryFailure(
                currentPoi.getDisplayName(),
                getDoorsToOpen(),
                System.currentTimeMillis()
        );
        DeliveryFailureManager.addFailure(getApplicationContext(), failure);
    }

    private boolean[] getDoorsToOpen() {
        List<Integer> enabledHardwareIds = new ArrayList<>();
        if (enabledDoors != null) {
            for (BasicSettingsFragment.DoorInfo doorInfo : enabledDoors) {
                enabledHardwareIds.add(doorInfo.getHardwareId());
            }
        }
        int doorCount = enabledHardwareIds.size();
        boolean[] doorsToOpen = new boolean[doorCount];
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

    private void handlePickupAction() {
        SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
        boolean verificationEnabled = prefs.getBoolean("verification_enabled", false);

        if (verificationEnabled) {
            unlockAllButtons(); // 密码弹窗需要解锁按钮输入
            showPickupPasswordDialog();
        } else {
            performPickupAction();
            unlockAllButtons(); // 执行完取物逻辑后解锁
        }
    }

    private void handleCompleteAction() {
        try {
            doorStateManager.closeAllOpenedDoors();
            Toast.makeText(this, "等待仓门关闭...", Toast.LENGTH_SHORT).show();
            TaskManager taskManager = TaskManager.getInstance();
            Poi currentPoi = taskManager.getCurrentPoi();
            if (currentPoi != null) {
                taskManager.removeTask(currentPoi);
                TaskSuccessManager.addSuccess(getApplicationContext(), currentPoi.getDisplayName());
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                proceedToNextTask();
                unlockAllButtons(); // 延迟执行后解锁
            }, 10000);
        } catch (Exception e) {
            unlockAllButtons(); // 异常时也解锁
            Toast.makeText(this, "完成操作失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showPickupPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_password_auth, null);
        builder.setView(dialogView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText("取物验证");

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

        createPasswordDots();

        for (int i = 0; i < numberButtons.length; i++) {
            final int digit = i;
            numberButtons[i].setOnClickListener(v -> addDigit(String.valueOf(digit)));
        }

        btnDelete.setOnClickListener(v -> removeDigit());

        passwordDialog = builder.create();
        passwordDialog.setCanceledOnTouchOutside(false);
        passwordDialog.show();

        enteredPickupPassword = "";
        updateDotsDisplay();
    }

    private void createPasswordDots() {
        dotsContainer.removeAllViews();

        int dotSize = getResources().getDimensionPixelSize(R.dimen.password_dot_size);
        int margin = getResources().getDimensionPixelSize(R.dimen.password_dot_margin);

        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
            params.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(params);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(getResources().getDimensionPixelSize(R.dimen.password_dot_stroke), Color.GRAY);
            dot.setBackground(bg);

            dotsContainer.addView(dot);
        }
    }

    private void addDigit(String digit) {
        if (enteredPickupPassword.length() < 4) {
            enteredPickupPassword += digit;
            updateDotsDisplay();

            if (enteredPickupPassword.length() == 4) {
                if (validatePickupPassword(enteredPickupPassword)) {
                    lockAllButtons(); // 验证通过后锁定按钮执行取物
                    performPickupAction();
                    if (passwordDialog != null && passwordDialog.isShowing()) {
                        passwordDialog.dismiss();
                    }
                    unlockAllButtons(); // 取物完成后解锁
                } else {
                    Toast.makeText(ArrivalConfirmationActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
                    enteredPickupPassword = "";
                    updateDotsDisplay();
                }
            }
        }
    }

    private void removeDigit() {
        if (enteredPickupPassword.length() > 0) {
            enteredPickupPassword = enteredPickupPassword.substring(0, enteredPickupPassword.length() - 1);
            updateDotsDisplay();
        }
    }

    private void updateDotsDisplay() {
        for (int i = 0; i < dotsContainer.getChildCount(); i++) {
            View dot = dotsContainer.getChildAt(i);
            if (dot != null) {
                GradientDrawable bg = (GradientDrawable) dot.getBackground();
                if (i < enteredPickupPassword.length()) {
                    bg.setColor(Color.BLACK);
                    bg.setStroke(0, Color.TRANSPARENT);
                } else {
                    bg.setColor(Color.TRANSPARENT);
                    bg.setStroke(getResources().getDimensionPixelSize(R.dimen.password_dot_stroke), Color.GRAY);
                }
            }
        }
    }

    private boolean validatePickupPassword(String enteredPassword) {
        SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
        String correctPassword = prefs.getString("pickup_password", "");
        return enteredPassword.equals(correctPassword);
    }

    private void performPickupAction() {
        List<Integer> enabledHardwareIds = new ArrayList<>();
        if (enabledDoors != null) {
            for (BasicSettingsFragment.DoorInfo doorInfo : enabledDoors) {
                enabledHardwareIds.add(doorInfo.getHardwareId());
            }
        }
        boolean[] doorsToOpen = getDoorsToOpen();

        List<Integer> doorsToOpenList = new ArrayList<>();
        for (int i = 0; i < doorsToOpen.length; i++) {
            if (doorsToOpen[i] && i < enabledHardwareIds.size()) {
                int hardwareId = enabledHardwareIds.get(i);
                doorsToOpenList.add(hardwareId);
                Log.d("ArrivalConfirmation", "待打开仓门：硬件ID=" + hardwareId);
            }
        }

        for (int i = 0; i < doorsToOpenList.size(); i++) {
            final int hardwareId = doorsToOpenList.get(i);
            doorHandler.postDelayed(() -> {
                doorStateManager.openDoor(hardwareId);
                Log.d("ArrivalConfirmation", "已发送打开指令：硬件ID=" + hardwareId);
            }, i * DOOR_OPEN_DELAY);
        }

        Toast.makeText(this, "请取走物品", Toast.LENGTH_SHORT).show();
    }

    private void proceedToNextTask() {
        timer.cancel();

        if (TaskManager.getInstance().hasTasks()) {
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
            startActivity(intent);
        } else {
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
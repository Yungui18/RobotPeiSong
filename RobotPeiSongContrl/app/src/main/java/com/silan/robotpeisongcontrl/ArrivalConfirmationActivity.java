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
import com.silan.robotpeisongcontrl.utils.RecyclingTaskManager;
import com.silan.robotpeisongcontrl.utils.SoundPlayerManager;
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
    private Button btnComplete;
    private Button btnPickup;
    private boolean isRecycleTask = false;
    private SoundPlayerManager soundPlayerManager;
    private boolean isRouteDelivery = false;
    private CountDownTimer completeBtnCountDown;
    private boolean isCompleteBtnAble = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arrival_confirmation);

        soundPlayerManager = SoundPlayerManager.getInstance(this);
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

        // 接收关键标记
        isRecycleTask = intent.getBooleanExtra("is_recycle", false);
        isRouteDelivery = intent.getBooleanExtra("is_route_delivery", false); // 路线配送
        Log.d("ArrivalConfirmation", "是否为回收任务：" + isRecycleTask + "  是否路线配送：" + isRouteDelivery);

        TextView countdownText = findViewById(R.id.tv_countdown);
        btnPickup = findViewById(R.id.btn_pickup);
        btnComplete = findViewById(R.id.btn_complete);

        // ========== 修复：强制初始状态只显示取物，隐藏完成 ==========
        btnPickup.setVisibility(View.VISIBLE);
        btnComplete.setVisibility(View.GONE);

        doorStateManager = DoorStateManager.getInstance(this);

        // 倒计时
        timer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdownText.setText(String.format("%ds", millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                isDeliveryFailed = true;
                if (soundPlayerManager != null) {
                    soundPlayerManager.playSound(SoundPlayerManager.KEY_TIME_OUT);
                }
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    recordDeliveryFailure();
                    doorStateManager.closeAllOpenedDoors();
                    proceedToNextTask();
                }, 4000);
            }
        }.start();

        isScheduledTask = getIntent().getBooleanExtra("scheduled_task", false);
        selectedDoors = getIntent().getBooleanArrayExtra("selected_doors");
        if (selectedDoors == null || selectedDoors.length != dynamicDoorCount) {
            selectedDoors = new boolean[dynamicDoorCount];
        }

        // 获取当前点位与仓门
        Poi currentPoi = TaskManager.getInstance().getCurrentPoi();
        if (currentPoi != null) {
            List<Integer> temp = TaskManager.getInstance().getDoorIdsForPoint(currentPoi.getDisplayName());
            currentTaskDoorIds = temp == null ? new ArrayList<>() : temp;
            Log.d("ArrivalConfirmation", "当前任务关联的所有仓门ID：" + currentTaskDoorIds);
        } else {
            currentTaskDoorIds = new ArrayList<>();
            Log.e("ArrivalConfirmation", "当前任务点位为空，无仓门ID");
        }

            if (isScheduledTask && !isRouteDelivery && !isRecycleTask) {
            lockAllButtons();
            performPickupAction();
            btnComplete.setVisibility(View.VISIBLE);
            btnPickup.setVisibility(View.GONE);
            initCompleteBtnCountDown();
            startCompleteBtnCountDown();
            unlockAllButtons();
        }

        // 取物按钮（手动点击才开门）
        btnPickup.setOnClickListener(v -> {
            lockAllButtons();
            handlePickupAction();
        });

        // 完成按钮
        btnComplete.setOnClickListener(v -> {
            lockAllButtons();
            handleCompleteAction();
        });

        currentDoorTask = getIntent().getIntExtra("current_door_task", 0);

        TextView tvPointName = findViewById(R.id.tv_point_name);
        TextView tvDoorInfo = findViewById(R.id.tv_door_info);
        TextView tvCurrentDoor = findViewById(R.id.tv_current_door);

        // 点位信息
        if (currentPoi != null) {
            tvPointName.setText("当前点位：" + currentPoi.getDisplayName());

            List<Integer> doorIds = TaskManager.getInstance().getDoorIdsForPoint(currentPoi.getDisplayName());
            List<String> doorNames = new ArrayList<>();
            if (doorIds != null && !doorIds.isEmpty()) {
                for (int id : doorIds) {
                    for (BasicSettingsFragment.DoorInfo info : enabledDoors) {
                        if (info.getHardwareId() == id) {
                            doorNames.add(BasicSettingsFragment.getStandardDoorButtonText(info));
                            break;
                        }
                    }
                }
            }

            if (doorNames.isEmpty()) {
                tvDoorInfo.setText("绑定仓门：无");
                tvCurrentDoor.setText("当前操作：无可用仓门");
            } else {
                tvDoorInfo.setText("绑定仓门：" + String.join("、", doorNames));
                tvCurrentDoor.setText("当前操作：" + doorNames.get(0));
            }
        } else {
            tvPointName.setText("当前点位：未知");
            tvDoorInfo.setText("绑定仓门：无");
            tvCurrentDoor.setText("当前操作：无");
        }
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
            unlockAllButtons();
            showPickupPasswordDialog();
        } else {
            performPickupAction();
            btnPickup.setVisibility(View.GONE);
            btnComplete.setVisibility(View.VISIBLE);
            initCompleteBtnCountDown();
            startCompleteBtnCountDown();
            unlockAllButtons();
        }
    }

    private void handleCompleteAction() {
        try {
            soundPlayerManager.playSound(SoundPlayerManager.KEY_COMPLETE_TAKE);
            doorStateManager.closeAllOpenedDoors();
            Toast.makeText(this, "等待仓门关闭...", Toast.LENGTH_SHORT).show();
            TaskManager taskManager = TaskManager.getInstance();
            Poi currentPoi = taskManager.getCurrentPoi();
            // 移除：提前执行的removeTask()，避免删除仓门映射

            saveRecyclingTaskRecord();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                // 新增：延迟执行removeTask()，确保关门指令完成
                if (currentPoi != null) {
                    taskManager.removeTask(currentPoi);
                    TaskSuccessManager.addSuccess(getApplicationContext(), currentPoi.getDisplayName());
                }
                proceedToNextTask();
                unlockAllButtons();
            }, 10000);
        } catch (Exception e) {
            unlockAllButtons();
            Toast.makeText(this, "完成操作失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            Log.e("ArrivalConfirmation", "完成操作异常", e);
        }
    }

    /**
     * 初始化完成按钮4秒倒计时器（设置倒计时逻辑：更新文字+控制可用状态）
     */
    private void initCompleteBtnCountDown() {
        // 先取消原有倒计时，防止重复创建
        if (completeBtnCountDown != null) {
            completeBtnCountDown.cancel();
        }
        // 初始化4秒倒计时（总时长4000ms，间隔1000ms刷新一次）
        completeBtnCountDown = new CountDownTimer(4000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // 倒计时中：禁用按钮，更新文字显示剩余秒数
                isCompleteBtnAble = false;
                btnComplete.setEnabled(false);
                btnComplete.setText("完成（" + millisUntilFinished / 1000 + "s）");
                // 可选：修改禁用状态下的按钮颜色，提升视觉区分
                btnComplete.setBackgroundResource(R.drawable.button_gray_rect); // 灰色背景（需新增资源）
            }

            @Override
            public void onFinish() {
                // 倒计时结束：启用按钮，恢复原有文字和样式
                isCompleteBtnAble = true;
                btnComplete.setEnabled(true);
                btnComplete.setText(R.string.complete);
                btnComplete.setBackgroundResource(R.drawable.button_white_rect); // 恢复原有白色背景
            }
        };
    }

    /**
     * 启动完成按钮倒计时
     */
    private void startCompleteBtnCountDown() {
        if (completeBtnCountDown != null) {
            completeBtnCountDown.start();
        }
    }

    /**
     * 取消完成按钮倒计时（生命周期销毁时调用，防止内存泄漏）
     */
    private void cancelCompleteBtnCountDown() {
        if (completeBtnCountDown != null) {
            completeBtnCountDown.cancel();
            completeBtnCountDown = null;
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
                    lockAllButtons();
                    performPickupAction();
                    if (passwordDialog != null && passwordDialog.isShowing()) {
                        passwordDialog.dismiss();
                    }
                    // ========== 原有逻辑：切换按钮 ==========
                    btnPickup.setVisibility(View.GONE);
                    btnComplete.setVisibility(View.VISIBLE);
                    // ========== 新增核心：禁用完成按钮+启动4秒倒计时 ==========
                    initCompleteBtnCountDown();
                    startCompleteBtnCountDown();
                    unlockAllButtons();
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

    /**
     * 保存回收任务记录到全局管理器（核心补充）
     */
    private void saveRecyclingTaskRecord() {
        // 1. 非回收任务直接返回，不执行存储
        if (!isRecycleTask) {
            return;
        }

        // 2. 获取当前任务的点位和关联仓门（复用原有 TaskManager 逻辑）
        TaskManager taskManager = TaskManager.getInstance();
        Poi currentPoi = taskManager.getCurrentPoi();
        if (currentPoi == null) {
            Log.e("ArrivalConfirmation", "回收任务记录失败：当前点位为空");
            Toast.makeText(this, "回收任务记录失败：点位数据缺失", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. 获取点位名称和关联仓门ID列表（复用原有任务数据）
        String pointName = currentPoi.getDisplayName();
        List<Integer> doorIds = taskManager.getDoorIdsForPoint(pointName);
        if (doorIds == null || doorIds.isEmpty()) {
            Log.w("ArrivalConfirmation", "回收任务记录提醒：当前点位无关联仓门");
            return;
        }

        // 4. 存入全局回收任务管理器（核心步骤）
        RecyclingTaskManager.getInstance().addTaskRecord(pointName, doorIds);
        Log.d("ArrivalConfirmation", "回收任务记录成功：点位=" + pointName + "，仓门=" + doorIds);
        Toast.makeText(this, "回收任务记录成功", Toast.LENGTH_SHORT).show();
    }

    private void performPickupAction() {
        soundPlayerManager.playSound(SoundPlayerManager.KEY_AFTER_START);
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
                Log.d("ArrivalConfirmation", "待下发开门指令的仓门：硬件ID=" + hardwareId);
            }
        }

        // 新增：空列表保护，避免无指令下发
        if (doorsToOpenList.isEmpty()) {
            Log.e("ArrivalConfirmation", "执行取物失败：无任何可用仓门可打开");
            Toast.makeText(this, "取物失败：未找到可用仓门", Toast.LENGTH_SHORT).show();
            unlockAllButtons();
            return;
        }

        // 延迟下发开门指令（保留原有逻辑，增加日志）
        for (int i = 0; i < doorsToOpenList.size(); i++) {
            final int hardwareId = doorsToOpenList.get(i);
            doorHandler.postDelayed(() -> {
                Log.d("ArrivalConfirmation", "开始下发开门指令 → 硬件ID=" + hardwareId);
                doorStateManager.openDoor(hardwareId);
                Log.d("ArrivalConfirmation", "开门指令下发完成 → 硬件ID=" + hardwareId);
            }, i * DOOR_OPEN_DELAY);
        }

        Toast.makeText(this, "请取走物品", Toast.LENGTH_SHORT).show();
    }

    private void proceedToNextTask() {
        timer.cancel();
        TaskManager taskManager = TaskManager.getInstance();
        Intent intent = new Intent(this, MovingActivity.class);

        // 透传基础参数（回收任务+POI列表+路线配送标记）
        intent.putExtra("is_recycle", isRecycleTask);
        intent.putExtra("recycle_point_id", getIntent().getStringExtra("recycle_point_id"));
        intent.putExtra("poi_list", getIntent().getStringExtra("poi_list"));
        intent.putExtra("is_route_delivery", isRouteDelivery); // 透传路线配送标记
        intent.putExtra("scheduled_task", isScheduledTask);
        intent.putExtra("selected_doors", selectedDoors);

        if (taskManager.hasTasks()) {
            // 有剩余任务：路线配送/普通任务都跳转MovingActivity执行下一个
            intent.putExtra("poi_list", new Gson().toJson(taskManager.getTasks()));
            Log.d("ArrivalConfirmation", isRouteDelivery ? "路线配送有剩余点位，跳转至下一个" : "有剩余配送任务，跳转至下一个点位");
            startActivity(intent);
            finish();
        } else {
            if (isRouteDelivery) {
                // ================ 路线配送专属：无剩余点位，直接回桩 ================
                Log.d("ArrivalConfirmation", "路线配送所有点位完成，执行回桩逻辑");
                intent.putExtra("is_back_dock", true);
                startActivity(intent);
                finish();
                // ===================================================================
            } else {
                // 普通任务：无剩余任务标记回桩
                intent.putExtra("is_back_dock", true);
                Log.d("ArrivalConfirmation", "无剩余配送任务，执行回桩逻辑");
                startActivity(intent);
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
        cancelCompleteBtnCountDown();
        if (soundPlayerManager != null && !isDeliveryFailed) {
            soundPlayerManager.stopSound();
        }
    }
}
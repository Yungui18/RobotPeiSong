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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.fragments.BasicSettingsFragment;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.utils.DoorStateManager;
import com.silan.robotpeisongcontrl.utils.LoadingDialogUtil;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okio.ByteString;

public class TaskSelectionActivity extends BaseActivity implements MainActivity.OnMainInitCompleteListener  {
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
    private ProgressDialog closeDoorDialog;

    // 新增：任务点位选择下拉菜单（显示全名）
    private Spinner spTaskPoi;
    private TextView tvDisplay;
    private boolean isDropdownMode = true;
    private Button btnSwitchInputMode;
    private LinearLayout llDropdownMode;
    private LinearLayout llKeyboardMode;

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
        // ===== 新增1：设置仓门状态监听（单点配送）=====
        setDoorStateListener();

        taskButtonsContainer = findViewById(R.id.task_buttons_container);
        if (taskButtonsContainer == null) {
            Log.e("TaskSelection", "未找到task_buttons_container容器");
            Toast.makeText(this, "布局加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 新增：绑定输入显示框和下拉菜单
        tvDisplay = findViewById(R.id.tv_display);
        spTaskPoi = findViewById(R.id.sp_task_poi);
        btnSwitchInputMode = findViewById(R.id.btn_switch_input_mode);
        llDropdownMode = findViewById(R.id.ll_dropdown_mode);
        llKeyboardMode = findViewById(R.id.ll_keyboard_mode);
        llDropdownMode.setVisibility(View.VISIBLE);
        llKeyboardMode.setVisibility(View.GONE);

        loadTaskButtonsLayout();

        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>() {
            }.getType();
            poiList = gson.fromJson(poiListJson, type);
            poiCount = poiList.size();
            // 新增：初始化任务点位下拉菜单
            initTaskPoiSpinner();
        } else {
            loadPoiList();
        }

        countdownText = findViewById(R.id.tv_countdown);
        Button btnStart = findViewById(R.id.btn_start);
        btnStart.setOnClickListener(v -> {
            lockAllButtons(); // 新增：锁定按钮
            startTasks();
        });

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            lockAllButtons(); // 新增：锁定按钮
            // 1. 显示加载弹窗
            LoadingDialogUtil.showLoadingDialog(this, "主界面初始化中，请稍候...");
            // 2. 添加MainActivity初始化监听
            MainActivity.addMainInitListener(this);

            // 原有关闭仓门、清空任务逻辑不变（放到子线程中）
            closeDoorDialog.show();
            new Thread(() -> {
                doorStateManager.closeAllOpenedDoors();
                taskManager.clearTasks();
                runOnUiThread(() -> {
                    clearTaskButtons(); // 安全执行UI操作
                });
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                runOnUiThread(() -> {
                    closeDoorDialog.dismiss();
                    unlockAllButtons(); // 执行完解锁
                });
            }).start();
        });

        bindSwitchInputModeListener();

        // 数字按钮逻辑
        setupNumberButtons();

        startCountdown();
    }

    // ===== 新增2：设置仓门状态监听（单点配送）=====
    private void setDoorStateListener() {
        doorStateManager.setOnDoorStateChangeListener(new DoorStateManager.OnDoorStateChangeListener() {
            @Override
            public void onDoorOpened(int hardwareId) {
                // 仓门打开 → 按钮变红
                updateDoorButtonColor(hardwareId, R.drawable.button_red_rect);
            }

            @Override
            public void onDoorClosed(int hardwareId) {
                // 单点配送：仓门关闭 → 按钮变绿（未分配任务）/ 保持绿色（已分配任务）
                updateDoorButtonColor(hardwareId, R.drawable.button_sea_blue_rect);
            }
        });
    }

    // ===== 新增3：根据硬件ID更新按钮颜色 =====
    private void updateDoorButtonColor(int hardwareId, int backgroundResId) {
        if (enabledDoors == null || taskButtons == null) return;
        runOnUiThread(() -> {
            for (int i = 0; i < enabledDoors.size(); i++) {
                if (enabledDoors.get(i).getHardwareId() == hardwareId && i < taskButtons.length && !taskAssigned[i]) {
                    taskButtons[i].setBackgroundResource(backgroundResId);
                    break;
                }
            }
        });
    }

    /**
     * 绑定输入模式切换按钮点击事件
     */
    private void bindSwitchInputModeListener() {
        if (btnSwitchInputMode == null) return;

        btnSwitchInputMode.setOnClickListener(v -> {
            // 1. 切换模式标记
            isDropdownMode = !isDropdownMode;

            // 2. 根据标记控制两个模式容器的显隐
            if (isDropdownMode) {
                // 切换为：下拉列表模式（显示下拉，隐藏数字键盘）
                llDropdownMode.setVisibility(View.VISIBLE);
                llKeyboardMode.setVisibility(View.GONE);
                // 更新按钮文字
                btnSwitchInputMode.setText("切换为：数字键入模式");
            } else {
                // 切换为：数字键入模式（隐藏下拉，显示数字键盘）
                llDropdownMode.setVisibility(View.GONE);
                llKeyboardMode.setVisibility(View.VISIBLE);
                // 更新按钮文字
                btnSwitchInputMode.setText("切换为：点位列表模式");
                // 可选：清空数字键入框，避免残留
                if (tvDisplay != null) {
                    tvDisplay.setText("");
                }
            }
        });
    }

    // 初始化任务点位下拉菜单（显示点位全名）
    private void initTaskPoiSpinner() {
        if (poiList.isEmpty()) {
            Toast.makeText(this, "暂无可用点位", Toast.LENGTH_SHORT).show();
            return;
        }

        // 提取点位全名作为下拉选项
        List<String> poiFullNames = new ArrayList<>();
        for (Poi poi : poiList) {
            poiFullNames.add(poi.getDisplayName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, poiFullNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTaskPoi.setAdapter(adapter);

        // 下拉菜单选择监听：选择后填充数字部分到输入框
        spTaskPoi.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                Poi selectedPoi = poiList.get(position);
                String fullName = selectedPoi.getDisplayName();
                // 拆分数字部分（按"_"分割）
                String numberPart = getNumberPartFromFullName(fullName);
                tvDisplay.setText(numberPart);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                tvDisplay.setText("");
            }
        });
    }

    // 新增：从完整点位名称中提取数字部分
    private String getNumberPartFromFullName(String fullName) {
        if (fullName == null || !fullName.contains("_")) {
            return fullName; // 兼容原有格式，无下划线直接返回
        }
        return fullName.split("_")[0].trim();
    }

    // 新增：根据数字部分查找点位
    private Poi getPoiByNumberPart(String numberPart) {
        if (numberPart == null || numberPart.isEmpty() || poiList.isEmpty()) {
            return null;
        }

        for (Poi poi : poiList) {
            String fullName = poi.getDisplayName();
            String poiNumberPart = getNumberPartFromFullName(fullName);
            if (poiNumberPart.equals(numberPart.trim())) {
                return poi;
            }
        }
        return null;
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
            // ===== 单点默认绿色（button_green_rect），未分配任务时的初始状态 =====
            button.setBackgroundResource(R.drawable.button_sea_blue_rect);
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

                currentSelectedButtonIndex = index;
                Log.d("TaskSelection", "点击仓门：" + buttonText + "，硬件ID：" + currentHardwareId + "，选中索引：" + currentSelectedButtonIndex);
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
                    // 新增：初始化任务点位下拉菜单
                    initTaskPoiSpinner();
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

        // 退格按钮
        Button btnBackspace = findViewById(R.id.btn_done);
        // 完成并关闭仓门按钮
        Button btnCompleteCloseDoor = findViewById(R.id.btn_complete_close_door);

        // 数字按钮逻辑
        for (int id : numberButtonIds) {
            Button btn = findViewById(id);
            btn.setOnClickListener(v -> {
                if (id == R.id.btn_clear) {
                    tvDisplay.setText("");
                } else {
                    tvDisplay.append(((Button) v).getText());
                }
            });
        }

        // 退格功能
        btnBackspace.setOnClickListener(v -> {
            String currentText = tvDisplay.getText().toString().trim();
            if (!currentText.isEmpty()) {
                // 删除最后一个字符
                tvDisplay.setText(currentText.substring(0, currentText.length() - 1));
            }
        });

        // 完成并关闭仓门功能
        btnCompleteCloseDoor.setOnClickListener(v -> {
            lockAllButtons(); // 新增：锁定按钮
            int tempSelectedIndex = currentSelectedButtonIndex;
            if (tempSelectedIndex == -1) {
                unlockAllButtons(); // 异常时解锁
                Toast.makeText(this, "请先选择仓门", Toast.LENGTH_SHORT).show();
                return;
            }
            int doorId = doorNumbers.get(tempSelectedIndex);
            // 获取输入框数字部分并判空
            String numberPart = tvDisplay.getText().toString().trim();
            if (numberPart.isEmpty()) {
                unlockAllButtons(); // 异常时解锁
                Toast.makeText(this, "请输入点位数字部分或从下拉菜单选择", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean taskCreated = validatePointAndCreateTask(numberPart, tempSelectedIndex, doorId);
            if (taskCreated) {
                // 4. 任务创建成功后，关闭仓门并延迟清空选中状态
                doorStateManager.closeDoor(doorId);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    tvDisplay.setText("");
                    currentSelectedButtonIndex = -1; // 延迟清空，避免误判
                }, 300);
                Toast.makeText(this, "仓门" + doorId + "已关闭，任务创建成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "任务创建失败，请检查点位数字部分", Toast.LENGTH_SHORT).show();
            }
            unlockAllButtons(); // 执行完解锁
        });
    }

    // 新增：独立的任务创建校验方法，返回是否创建成功
    private boolean validatePointAndCreateTask(String numberPart, int selectedIndex, int doorId) {
        if (selectedIndex == -1) {
            return false;
        }
        if (poiCount <= 0) {
            Toast.makeText(this, "点位数据未加载完成，请稍后再试", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (taskManager.taskCount() >= poiCount) {
            Toast.makeText(this, "任务数量已达上限（最多" + poiCount + "个）", Toast.LENGTH_SHORT).show();
            return false;
        }

        // 根据数字部分查找匹配的点位
        Poi poi = getPoiByNumberPart(numberPart);
        if (poi == null) {
            return false;
        }
        if (taskManager.isPointAssigned(poi.getDisplayName())) {
            Toast.makeText(this, "该点位已分配任务，不可重复分配", Toast.LENGTH_SHORT).show();
            return false;
        }

        // 绑定任务与仓门
        List<Integer> doorIds = new ArrayList<>();
        doorIds.add(doorId);
        taskManager.addPointWithDoors(poi, doorIds);
        Log.d("TaskSelection", "单点任务添加成功，点位：" + poi.getDisplayName() + "，关联仓门ID列表：" + doorIds);

        taskManager.addTask(poi);
        taskButtons[selectedIndex].setText(poi.getDisplayName());
        taskButtons[selectedIndex].setBackgroundResource(R.drawable.button_green_rect); // 保持绿色
        taskAssigned[selectedIndex] = true;
        return true;
    }

    // 改造：支持通过数字部分验证点位
    private void validatePoint(String numberPart) {
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

        // 根据数字部分查找匹配的点位
        Poi poi = getPoiByNumberPart(numberPart);

        if (poi != null) {
            int selectedDoorId = doorNumbers.get(currentSelectedButtonIndex);

            if (taskManager.isPointAssigned(poi.getDisplayName())) {
                Toast.makeText(this, "该点位已分配任务，不可重复分配", Toast.LENGTH_SHORT).show();
                return;
            }

            List<Integer> doorIds = new ArrayList<>();
            doorIds.add(selectedDoorId);

            taskManager.addPointWithDoors(poi, doorIds);
            Log.d("TaskSelection", "单点任务添加成功，点位：" + poi.getDisplayName() + "，关联仓门ID列表：" + doorIds);

            taskManager.addTask(poi);

            taskButtons[currentSelectedButtonIndex].setText(poi.getDisplayName());
            taskButtons[currentSelectedButtonIndex].setBackgroundResource(R.drawable.button_green_rect); // 保持绿色
            taskAssigned[currentSelectedButtonIndex] = true;
            currentSelectedButtonIndex = -1;
        } else {
            Toast.makeText(this, "点位不存在（数字部分不匹配）", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectTask(int index) {
        for (int i = 0; i < doorCount; i++) {
            if (!taskAssigned[i]) {
                taskButtons[i].setBackgroundResource(R.drawable.button_green_rect); // 恢复默认绿色
            }
        }

        if (taskAssigned[index]) {
            Toast.makeText(this, "该任务已分配，不能修改", Toast.LENGTH_SHORT).show();
            return;
        }

        taskButtons[index].setBackgroundResource(R.drawable.button_red_rect); // 选中变红
        currentSelectedButtonIndex = index;
    }

    // ===== 修改2：清空按钮恢复默认绿色（单点）=====
    private void clearTaskButtons() {
        if (taskButtons == null || enabledDoors == null || taskButtons.length != enabledDoors.size()) {
            Log.e("TaskSelection", "任务按钮或仓门数据异常，无法清空按钮");
            return;
        }
        for (int i = 0; i < doorCount; i++) {
            BasicSettingsFragment.DoorInfo doorInfo = enabledDoors.get(i);
            String typeStr = getDoorTypeText(doorInfo.getType());
            String resetText = String.format("行%d-%d号（%s）ID:%d",
                    doorInfo.getRow(),
                    doorInfo.getPosition(),
                    typeStr,
                    doorInfo.getHardwareId());

            taskButtons[i].setText(resetText);
            taskButtons[i].setBackgroundResource(R.drawable.button_green_rect); // 恢复默认绿色
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
                if (taskButtons != null) {
                    for (Button button : taskButtons) {
                        button.setBackgroundResource(R.drawable.button_green_rect);
                    }
                }
                unlockAllButtons(); // 延迟执行后解锁
                finish();
            }, 10000);
        } else {
            unlockAllButtons(); // 无任务时解锁
            Toast.makeText(this, "请先创建任务", Toast.LENGTH_SHORT).show();
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
            runOnUiThread(() -> {
                clearTaskButtons();
            });
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
        // 移除监听器+关闭弹窗
        MainActivity.removeMainInitListener(this);
        LoadingDialogUtil.dismissLoadingDialog();
        if (timer != null) {
            timer.cancel();
        }
        if (closeDoorDialog != null && closeDoorDialog.isShowing()) {
            closeDoorDialog.dismiss();
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
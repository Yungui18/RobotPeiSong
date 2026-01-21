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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
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

public class MultiRecycleTaskSelectionActivity extends BaseActivity implements MainActivity.OnMainInitCompleteListener {
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
    private ProgressDialog closeDoorDialog;

    // 回收点位相关
    private Spinner spRecyclePoint;
    private String selectedRecyclePointId;
    private String currentDepartPointId;

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
        setContentView(R.layout.activity_multi_recycle_task_selection);

        // 初始化关闭仓门弹窗
        closeDoorDialog = new ProgressDialog(this);
        closeDoorDialog.setMessage("正在关闭仓门，请稍候...");
        closeDoorDialog.setCancelable(false);

        doorStateManager = DoorStateManager.getInstance(this);

        enabledDoors = BasicSettingsFragment.getEnabledDoors(this);
        int doorCount = enabledDoors.size();

        taskButtonsContainer = findViewById(R.id.task_buttons_container);
        if (taskButtonsContainer == null) {
            Log.e("MultiRecycle", "未找到task_buttons_container容器");
            Toast.makeText(this, "布局加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 初始化回收点位Spinner
        spRecyclePoint = findViewById(R.id.sp_recycle_point);
        // 绑定输入显示框和任务点位下拉菜单
        tvDisplay = findViewById(R.id.tv_display);
        spTaskPoi = findViewById(R.id.sp_task_poi);

        btnSwitchInputMode = findViewById(R.id.btn_switch_input_mode);
        llDropdownMode = findViewById(R.id.ll_dropdown_mode);
        llKeyboardMode = findViewById(R.id.ll_keyboard_mode);
        llDropdownMode.setVisibility(View.VISIBLE);
        llKeyboardMode.setVisibility(View.GONE);

        Button btnStart = findViewById(R.id.btn_start_multi_delivery);
        btnStart.setOnClickListener(v -> {
            lockAllButtons(); // 新增：锁定按钮
            startMultiRecycle();
        });

        loadTaskButtonsLayout(doorCount);

        bindSwitchInputModeListener();

        setupNumberButtons();

        startCountdown();

        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>() {
            }.getType();
            poiList = gson.fromJson(poiListJson, type);
            // 初始化回收点位选择器
            initRecyclePointSpinner();
            // 新增：初始化任务点位下拉菜单
            initTaskPoiSpinner();
            // 获取当前出发点位
            getCurrentDepartPoint();
        }

        countdownText = findViewById(R.id.tv_countdown);
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            //  显示加载弹窗
            LoadingDialogUtil.showLoadingDialog(this, "主界面初始化中，请稍候...");
            //  添加MainActivity初始化监听
            MainActivity.addMainInitListener(this);
            // 退回按钮逻辑
            closeDoorDialog.show();
            new Thread(() -> {
                // 关闭所有仓门
                doorStateManager.closeAllOpenedDoors();
                // 清空任务
                taskManager.clearTasks();
                runOnUiThread(() -> {
                    clearTaskButtons(); // 安全执行UI操作
                    if (taskDetailsContainer != null) {
                        taskDetailsContainer.removeAllViews();
                    }
                });

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

    // 新增：初始化任务点位下拉菜单（显示点位全名）
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

    /**
     * 绑定输入模式切换按钮点击事件
     */
    private void bindSwitchInputModeListener() {
        if (btnSwitchInputMode == null) return;

        btnSwitchInputMode.setOnClickListener(v -> {
            //  切换模式标记
            isDropdownMode = !isDropdownMode;

            // 根据标记控制两个模式容器的显隐
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
                // 清空数字键入框，避免残留
                if (tvDisplay != null) {
                    tvDisplay.setText("");
                }
            }
        });
    }

    // 从完整点位名称中提取数字部分
    private String getNumberPartFromFullName(String fullName) {
        if (fullName == null || !fullName.contains("_")) {
            return fullName; // 兼容原有格式，无下划线直接返回
        }
        return fullName.split("_")[0].trim();
    }

    // 根据数字部分查找点位
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

    // 初始化回收点位选择器
    private void initRecyclePointSpinner() {
        // 加载所有点位名称
        List<String> pointNames = new ArrayList<>();
        for (Poi poi : poiList) {
            pointNames.add(poi.getDisplayName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, pointNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spRecyclePoint.setAdapter(adapter);

        // 监听选择事件
        spRecyclePoint.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedRecyclePointId = poiList.get(position).getId();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                if (currentDepartPointId != null) {
                    selectedRecyclePointId = currentDepartPointId;
                } else if (!poiList.isEmpty()) {
                    selectedRecyclePointId = poiList.get(0).getId();
                }
            }
        });
    }

    // 获取当前出发点位
    private void getCurrentDepartPoint() {
        RobotController.getRobotPose(new RobotController.RobotPoseCallback() {
            @Override
            public void onSuccess(RobotController.RobotPose pose) {
                // 根据当前坐标匹配最近的点位
                currentDepartPointId = getNearestPointId(pose.x, pose.y);
                selectedRecyclePointId = currentDepartPointId;

                // 选中默认点位
                runOnUiThread(() -> {
                    for (int i = 0; i < poiList.size(); i++) {
                        if (poiList.get(i).getId().equals(currentDepartPointId)) {
                            spRecyclePoint.setSelection(i);
                            break;
                        }
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("MultiRecycle", "获取当前点位失败", e);
                runOnUiThread(() -> {
                    if (!poiList.isEmpty()) {
                        currentDepartPointId = poiList.get(0).getId();
                        selectedRecyclePointId = currentDepartPointId;
                        spRecyclePoint.setSelection(0);
                    }
                });
            }
        });
    }

    // 根据坐标匹配最近点位
    private String getNearestPointId(double x, double y) {
        if (poiList.isEmpty()) return "";

        String nearestId = poiList.get(0).getId();
        double minDistance = Double.MAX_VALUE;

        for (Poi poi : poiList) {
            double distance = Math.sqrt(Math.pow(poi.getX() - x, 2) + Math.pow(poi.getY() - y, 2));
            if (distance < minDistance) {
                minDistance = distance;
                nearestId = poi.getId();
            }
        }
        return nearestId;
    }

    private void loadTaskButtonsLayout(int doorCount) {
        taskButtonsContainer.removeAllViews();

        if (enabledDoors == null || enabledDoors.isEmpty()) {
            TextView tipView = new TextView(this);
            tipView.setText("暂无启用的仓门，请先在基础设置中配置");
            tipView.setTextColor(Color.RED);
            tipView.setTextSize(16);
            taskButtonsContainer.addView(tipView);
            Log.w("MultiRecycle", "没有启用的仓门");
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

                // 回收逻辑：仅绑定仓门，不实际开关仓门
                if (selectedButtonIndices.contains(currentIndex)) {
                    selectedButtonIndices.remove(currentIndex);
                    button.setBackgroundResource(R.drawable.button_sky_blue_rect);
                    Toast.makeText(MultiRecycleTaskSelectionActivity.this,
                            "已取消仓门" + currentHardwareId + "绑定",
                            Toast.LENGTH_SHORT).show();
                } else {
                    selectedButtonIndices.add(currentIndex);
                    button.setBackgroundResource(R.drawable.button_red_rect);
                    Toast.makeText(MultiRecycleTaskSelectionActivity.this,
                            "已绑定仓门" + currentHardwareId + "到回收任务",
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
                tvDisplay.setText(currentText.substring(0, currentText.length() - 1));
            }
        });

        // 完成并取消仓门绑定（多点：取消所有选中仓门绑定）
        btnCompleteCloseDoor.setOnClickListener(v -> {
            lockAllButtons(); // 新增：锁定按钮
            if (selectedButtonIndices.isEmpty()) {
                unlockAllButtons(); // 异常解锁
                Toast.makeText(this, "请先选择至少一个仓门", Toast.LENGTH_SHORT).show();
                return;
            }
            Set<Integer> tempSelectedIndices = new HashSet<>(selectedButtonIndices);

            // 获取输入框数字部分并判空
            String numberPart = tvDisplay.getText().toString().trim();
            if (numberPart.isEmpty()) {
                unlockAllButtons(); // 异常解锁
                Toast.makeText(this, "请输入点位数字部分或从下拉菜单选择", Toast.LENGTH_SHORT).show();
                return;
            }
            // 调用validatePoint创建回收任务（传入数字部分）
            validatePoint(numberPart);
            // 任务创建成功后再执行取消绑定逻辑
            Poi matchedPoi = getPoiByNumberPart(numberPart);
            if (matchedPoi != null && taskManager.isPointAssigned(matchedPoi.getDisplayName())) {
                for (int index : selectedButtonIndices) {
                    if (index >= 0 && index < taskButtons.length) {
                        taskButtons[index].setBackgroundResource(R.drawable.button_blue_rect);
                    }
                }
                tvDisplay.setText("");
                selectedButtonIndices.clear();
                Toast.makeText(this, "已取消所有仓门绑定，回收任务创建成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "任务创建失败，请检查点位数字部分", Toast.LENGTH_SHORT).show();
            }
            unlockAllButtons(); // 执行完解锁
        });
    }

    // 改造：支持通过数字部分验证点位
    private void validatePoint(String numberPart) {
        if (selectedButtonIndices.isEmpty()) {
            Toast.makeText(this, "请先选择至少一个任务按钮", Toast.LENGTH_SHORT).show();
            return;
        }

        // 根据数字部分查找匹配的点位
        Poi poi = getPoiByNumberPart(numberPart);

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
            Toast.makeText(this, "点位不存在（数字部分不匹配）", Toast.LENGTH_SHORT).show();
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

    private void startMultiRecycle() {
        int taskCount = taskDetailsContainer.getChildCount();
        if (taskCount == 0) {
            unlockAllButtons(); // 无任务解锁
            Toast.makeText(this, "请至少选择一个任务", Toast.LENGTH_SHORT).show();
            return;
        }

        // 回收逻辑：出发时不关闭仓门
        Toast.makeText(this, "开始多点回收任务...", Toast.LENGTH_SHORT).show();

        timer.cancel();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
            intent.putExtra("is_recycle", true); // 标记为回收任务
            intent.putExtra("recycle_point_id", selectedRecyclePointId); // 回收点位ID
            startActivity(intent);
            unlockAllButtons(); // 延迟执行后解锁
            finish();
        }, 1000);
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
            runOnUiThread(() -> {
                clearTaskButtons();
                if (taskDetailsContainer != null) { // 仅Multi系列Activity需要，单个任务Activity可省略
                    taskDetailsContainer.removeAllViews();
                }
            });
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
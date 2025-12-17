package com.silan.robotpeisongcontrl;

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
    private Button[] taskButtons; // 动态按钮数组（替代原有的固定数组）
    private boolean[] taskAssigned; // 动态任务状态数组
    private int doorCount; // 仓门数量
    private LinearLayout taskButtonsContainer; // 动态容器
    private DoorStateManager doorStateManager; // 仓门状态管理器
    private List<Integer> doorNumbers; // 实际仓门编号列表
    private List<BasicSettingsFragment.DoorInfo> enabledDoors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_selection);

        //获取仓门
        enabledDoors = BasicSettingsFragment.getEnabledDoors(this);
        doorCount = enabledDoors.size(); // 动态仓门数量
        doorNumbers = new ArrayList<>();
        for (BasicSettingsFragment.DoorInfo door : enabledDoors) {
            doorNumbers.add(door.getHardwareId()); // 动态获取硬件ID
        }
        // 初始化任务状态数组（长度为仓门数量）
        taskAssigned = new boolean[doorCount];

        // 初始化仓门状态管理器
        doorStateManager = DoorStateManager.getInstance(this);


        // 初始化动态容器
        taskButtonsContainer = findViewById(R.id.task_buttons_container);
        if (taskButtonsContainer == null) {
            Log.e("TaskSelection", "未找到task_buttons_container容器");
            Toast.makeText(this, "布局加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 动态加载任务按钮
        loadTaskButtonsLayout();

        // 获取从MainActivity传递过来的POI列表
        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>() {
            }.getType();
            poiList = gson.fromJson(poiListJson, type);
            poiCount = poiList.size();
        }else {
            loadPoiList();
        }

        // 其他初始化
        countdownText = findViewById(R.id.tv_countdown);
        Button btnStart = findViewById(R.id.btn_start);
        btnStart.setOnClickListener(v -> startTasks());

        // 初始化"关闭所有仓门"按钮
        Button btnCloseAllDoors = findViewById(R.id.btn_close_all_doors);
        btnCloseAllDoors.setOnClickListener(v -> {
            // 调用管理器关闭所有已打开的仓门
            doorStateManager.closeAllOpenedDoors();
            Toast.makeText(TaskSelectionActivity.this, "已关闭所有仓门", Toast.LENGTH_SHORT).show();
        });

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            // 清空任务
            taskManager.clearTasks();
            clearTaskButtons();
            finish();
        });

        // 数字按钮逻辑（保持不变）
        setupNumberButtons();

        // 倒计时
        startCountdown();
    }

    // 动态加载任务按钮布局
    private void loadTaskButtonsLayout() {
        // 清空容器
        taskButtonsContainer.removeAllViews();

        // 无启用仓门时显示提示
        if (doorCount == 0) {
            TextView tipView = new TextView(this);
            tipView.setText("暂无启用的仓门，请先在基础设置中配置");
            tipView.setTextColor(Color.RED);
            tipView.setTextSize(16);
            taskButtonsContainer.addView(tipView);
            return;
        }

        // 初始化按钮数组
        taskButtons = new Button[doorCount];

        // 动态创建按钮（遍历完整的仓门信息，而非仅硬件ID）
        for (int i = 0; i < doorCount; i++) {
            BasicSettingsFragment.DoorInfo doorInfo = enabledDoors.get(i);
            Button button = new Button(this);
            button.setId(View.generateViewId());

            // 生成详细的按钮文本：行X-Y号（类型）-ID:硬件ID
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

            // 垂直布局参数（保持原有样式）
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, // 宽度填满父容器
                    ViewGroup.LayoutParams.WRAP_CONTENT,   // 高度自适应
                    0 // 垂直布局中weight=0，不占用额外高度
            );
            params.setMargins(0, 10, 0, 10); // 上下边距10dp，增加间距
            button.setLayoutParams(params);

            // 设置点击事件（关联当前仓门的硬件ID）
            final int index = i;
            final int currentHardwareId = doorInfo.getHardwareId();
            button.setOnClickListener(v -> {
                // 1. 已分配任务的按钮（绿色）不可操作仓门
                if (taskAssigned[index]) {
                    Toast.makeText(TaskSelectionActivity.this,
                            "该任务已分配，无法操作仓门",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // 2. 判断仓门当前状态
                if (doorStateManager.isDoorOpened(currentHardwareId)) {
                    // 已打开：关闭仓门 + 按钮切蓝色 + 取消选中
                    doorStateManager.closeDoor(currentHardwareId);
                    taskButtons[index].setBackgroundResource(R.drawable.button_blue_rect);
                    currentSelectedButtonIndex = -1; // 重置选中索引
                    Toast.makeText(TaskSelectionActivity.this,
                            "仓门" + currentHardwareId + "已关闭",
                            Toast.LENGTH_SHORT).show();
                } else {
                    // 未打开：选中按钮（变红） + 打开仓门
                    selectTask(index); // 原有方法：重置其他按钮+当前按钮变红
                    doorStateManager.openDoor(currentHardwareId);
                    Toast.makeText(TaskSelectionActivity.this,
                            "仓门" + currentHardwareId + "已打开",
                            Toast.LENGTH_SHORT).show();
                }
                Log.d("TaskSelection", "点击仓门：" + buttonText + "，硬件ID：" + currentHardwareId);
            });

            taskButtonsContainer.addView(button);
            taskButtons[i] = button;
        }
    }

    // 新增：获取仓门类型文本（辅助方法）
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

    // 主动加载POI列表（确保能获取点位总数）
    private void loadPoiList() {
        RobotController.getPoiList(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                poiList = RobotController.parsePoiList(json);
                runOnUiThread(() -> {
                    poiCount = poiList.size(); // 更新点位总数
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
                // 关闭所有仓门
                doorStateManager.closeAllOpenedDoors();
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
        // 检查是否选择了任务按钮
        if (currentSelectedButtonIndex == -1) {
            Toast.makeText(this, "请先选择一个任务按钮", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查点位总数是否为0（未加载成功）
        if (poiCount <= 0) {
            Toast.makeText(this, "点位数据未加载完成，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查当前任务数是否已达到点位总数上限
        if (taskManager.taskCount() >= poiCount) {
            Toast.makeText(this, "任务数量已达上限（最多" + poiCount + "个）", Toast.LENGTH_SHORT).show();
            return;
        }

        // 查找对应的POI
        Poi poi = RobotController.findPoiByName(pointName, poiList);

        if (poi != null) {
            // 检查该点位是否已被分配（确保一个点位只能有一个任务）
            int selectedDoorId = doorNumbers.get(currentSelectedButtonIndex); // 获取实际仓门ID（如5、6、9）

            if (taskManager.isPointAssigned(poi.getDisplayName())) {
                Toast.makeText(this, "该点位已分配任务，不可重复分配", Toast.LENGTH_SHORT).show();
                return;
            }

            // 将单个仓门ID包装为List
            List<Integer> doorIds = new ArrayList<>();
            doorIds.add(selectedDoorId);

            // 调用TaskManager的新方法，关联点位与仓门ID列表
            taskManager.addPointWithDoors(poi, doorIds);
            Log.d("TaskSelection", "单点任务添加成功，点位：" + pointName + "，关联仓门ID列表：" + doorIds);

            // 添加POI对象到任务队列
            taskManager.addTask(poi);

            // 在按钮上显示POI的显示名称
            taskButtons[currentSelectedButtonIndex].setText(poi.getDisplayName());
            taskButtons[currentSelectedButtonIndex].setBackgroundResource(R.drawable.button_green_rect);
            taskAssigned[currentSelectedButtonIndex] = true;
            currentSelectedButtonIndex = -1;
        } else {
            Toast.makeText(this, "点位不存在", Toast.LENGTH_SHORT).show();
        }
    }

    // 选择任务按钮
    private void selectTask(int index) {
        // 重置未分配任务的按钮
        for (int i = 0; i < doorCount; i++) {
            if (!taskAssigned[i]) {
                taskButtons[i].setBackgroundResource(R.drawable.button_blue_rect);
            }
        }

        if (taskAssigned[index]) {
            Toast.makeText(this, "该任务已分配，不能修改", Toast.LENGTH_SHORT).show();
            return;
        }

        // 高亮选中按钮
        taskButtons[index].setBackgroundResource(R.drawable.button_red_rect);
        currentSelectedButtonIndex = index;
    }

    // 清空任务按钮状态
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
            // 关闭所有已打开的仓门
            doorStateManager.closeAllOpenedDoors();
            // 显示提示：等待仓门关闭
            Toast.makeText(this, "等待仓门关闭...", Toast.LENGTH_SHORT).show();

            // 延迟5秒后再跳转（关键修改）
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent intent = new Intent(this, MovingActivity.class);
                intent.putExtra("poi_list", new Gson().toJson(poiList));
                startActivity(intent);
                for (Button button : taskButtons) {
                    button.setBackgroundResource(R.drawable.button_green_rect);
                }
                finish();
            }, 10000); // 5000毫秒 = 5秒
        } else {
            Toast.makeText(this, "请先创建任务", Toast.LENGTH_SHORT).show();
        }
    }

    // 重写系统返回键事件
    @Override
    public void onBackPressed() {
        // 清空任务
        taskManager.clearTasks();
        clearTaskButtons();
        // 关闭所有仓门
        doorStateManager.closeAllOpenedDoors();
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
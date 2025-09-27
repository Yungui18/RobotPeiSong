package com.silan.robotpeisongcontrl;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.silan.robotpeisongcontrl.fragments.WarehouseDoorSettingsFragment;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private Set<String> occupiedPoints = new HashSet<>();// 定义已占用点位集合（全局）
    private static final int TOTAL_POINTS = 4;// 总点位数量

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_selection);

        // 获取仓门数量
        doorCount = WarehouseDoorSettingsFragment.getDoorCount(this);
        // 初始化任务状态数组（长度为仓门数量）
        taskAssigned = new boolean[doorCount];

        // 初始化动态容器
        taskButtonsContainer = findViewById(R.id.task_buttons_container);
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
            // 新增：如果Intent中没有POI列表，主动加载（参考PointDeliveryFragment）
            loadPoiList();
        }

        // 其他初始化（保持不变）
        countdownText = findViewById(R.id.tv_countdown);
        Button btnStart = findViewById(R.id.btn_start);
        btnStart.setOnClickListener(v -> startTasks());

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

        // 初始化按钮数组
        taskButtons = new Button[doorCount];

        // 加载对应布局
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

        // 绑定按钮并设置点击事件
        for (int i = 0; i < doorCount; i++) {
            final int index = i;
            taskButtons[i] = taskButtonsContainer.findViewById(
                    getResources().getIdentifier("btn_task" + (i + 1), "id", getPackageName())
            );

            if (taskButtons[i] != null) {
                taskButtons[i].setOnClickListener(v -> selectTask(index));
            }
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


    /**
     * 显示送物密码验证对话框
     */
    private void showDeliveryPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("送物验证");

        // 创建密码输入框
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("请输入4位密码");
        builder.setView(input);

        // 确定按钮
        builder.setPositiveButton("确定", (dialog, which) -> {
            String enteredPassword = input.getText().toString();
            if (validateDeliveryPassword(enteredPassword)) {
                startTasks(); // 验证通过，开始任务
            } else {
                Toast.makeText(TaskSelectionActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
            }
        });

        // 取消按钮
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /**
     * 验证送物密码是否正确
     */
    private boolean validateDeliveryPassword(String enteredPassword) {
        SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
        String correctPassword = prefs.getString("delivery_password", "");
        return enteredPassword.equals(correctPassword);
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
            if (taskManager.isPointAssigned(poi.getDisplayName())) {
                Toast.makeText(this, "该点位已分配任务，不可重复分配", Toast.LENGTH_SHORT).show();
                return;
            }
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
            taskButtons[i].setText("");
            taskButtons[i].setBackgroundResource(R.drawable.button_blue_rect);
            taskAssigned[i] = false;
        }
        currentSelectedButtonIndex = -1;
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

    // 重写系统返回键事件
    @Override
    public void onBackPressed() {
        // 清空任务
        taskManager.clearTasks();
        clearTaskButtons();
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
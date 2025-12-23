package com.silan.robotpeisongcontrl;

import static com.silan.robotpeisongcontrl.fragments.BasicSettingsFragment.getDoorTypeText;

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
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okio.ByteString;

public class SingleRecycleTaskSelectionActivity extends AppCompatActivity {
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

    // 回收点位相关
    private Spinner spRecyclePoint;
    private String selectedRecyclePointId;
    private String currentDepartPointId; // 默认回收点位（当前出发点）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_recycle_task_selection);

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
            Log.e("SingleRecycle", "未找到task_buttons_container容器");
            Toast.makeText(this, "布局加载失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 初始化回收点位Spinner
        spRecyclePoint = findViewById(R.id.sp_recycle_point);

        loadTaskButtonsLayout();

        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>() {
            }.getType();
            poiList = gson.fromJson(poiListJson, type);
            poiCount = poiList.size();
            // 初始化回收点位选择器
            initRecyclePointSpinner();
            // 获取当前出发点位（默认回收点位）
            getCurrentDepartPoint();
        } else {
            loadPoiList();
        }

        countdownText = findViewById(R.id.tv_countdown);
        Button btnStart = findViewById(R.id.btn_start);
        btnStart.setOnClickListener(v -> startRecycleTasks());

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            // 退回按钮逻辑
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
                Log.e("SingleRecycle", "获取当前点位失败", e);
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
                    Toast.makeText(SingleRecycleTaskSelectionActivity.this,
                            "该任务已分配，无法操作仓门",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // 回收逻辑：仅绑定点位和仓门，出发时不打开仓门
                taskButtons[index].setBackgroundResource(R.drawable.button_red_rect);
                currentSelectedButtonIndex = index;
                Toast.makeText(SingleRecycleTaskSelectionActivity.this,
                        "已绑定仓门" + currentHardwareId + "到回收任务",
                        Toast.LENGTH_SHORT).show();
                Log.d("SingleRecycle", "点击仓门：" + buttonText + "，硬件ID：" + currentHardwareId);
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
                    initRecyclePointSpinner();
                    getCurrentDepartPoint();
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("SingleRecycle", "Failed to load POIs", e);
                runOnUiThread(() -> {
                    Toast.makeText(SingleRecycleTaskSelectionActivity.this, "点位加载失败", Toast.LENGTH_SHORT).show();
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

    // 数字按钮逻辑
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

        // 完成并关闭仓门功能（回收逻辑：仅清空绑定状态）
        btnCompleteCloseDoor.setOnClickListener(v -> {
            if (currentSelectedButtonIndex == -1) {
                Toast.makeText(this, "请先选择仓门", Toast.LENGTH_SHORT).show();
                return;
            }
            // 清空绑定状态
            taskButtons[currentSelectedButtonIndex].setBackgroundResource(R.drawable.button_blue_rect);
            currentSelectedButtonIndex = -1;
            display.setText("");
            Toast.makeText(this, "已取消仓门绑定", Toast.LENGTH_SHORT).show();
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
            Log.d("SingleRecycle", "单点回收任务添加成功，点位：" + pointName + "，关联仓门ID列表：" + doorIds);

            taskManager.addTask(poi);

            taskButtons[currentSelectedButtonIndex].setText(poi.getDisplayName());
            taskButtons[currentSelectedButtonIndex].setBackgroundResource(R.drawable.button_green_rect);
            taskAssigned[currentSelectedButtonIndex] = true;
            currentSelectedButtonIndex = -1;
        } else {
            Toast.makeText(this, "点位不存在", Toast.LENGTH_SHORT).show();
        }
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

    private void startRecycleTasks() {
        if (taskManager.hasTasks()) {
            timer.cancel();
            // 回收逻辑：出发时不关闭仓门（仅到点位开仓门）
            Toast.makeText(this, "开始单点回收任务...", Toast.LENGTH_SHORT).show();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Intent intent = new Intent(this, MovingActivity.class);
                intent.putExtra("poi_list", new Gson().toJson(poiList));
                intent.putExtra("is_recycle", true); // 标记为回收任务
                intent.putExtra("recycle_point_id", selectedRecyclePointId); // 回收点位ID
                startActivity(intent);
                finish();
            }, 1000);
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
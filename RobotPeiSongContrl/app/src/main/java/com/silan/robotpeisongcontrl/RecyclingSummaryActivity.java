package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.utils.DoorStateManager;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RecyclingTaskManager;
import com.silan.robotpeisongcontrl.utils.RobotController;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import okio.ByteString;

public class RecyclingSummaryActivity extends BaseActivity  {

    private static final String TAG = "RecyclingSummaryActivity";
    private TextView tvRecycleRecords;
    private Button btnOpenExecutedDoors;
    private Button btnCloseAllDoors;
    private Button btnReturnToCharging;

    private RecyclingTaskManager recyclingTaskManager;
    private DoorStateManager doorStateManager;
    private List<Poi> poiList;
    private String currentActionId;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycling_summary);

        // 初始化控件
        initViews();
        // 初始化数据（获取POI列表、任务记录）
        initData();
        // 绑定按钮点击事件
        bindButtonEvents();
    }

    /**
     * 初始化控件
     */
    private void initViews() {
        tvRecycleRecords = findViewById(R.id.tv_recycle_records);
        btnOpenExecutedDoors = findViewById(R.id.btn_open_executed_doors);
        btnCloseAllDoors = findViewById(R.id.btn_close_all_doors);
        btnReturnToCharging = findViewById(R.id.btn_return_to_charging);

        // 初始化全局管理器（复用项目单例）
        recyclingTaskManager = RecyclingTaskManager.getInstance();
        doorStateManager = DoorStateManager.getInstance(this);
    }

    /**
     * 初始化数据（展示回收任务记录、获取POI列表）
     */
    private void initData() {
        // 1. 获取POI列表
        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>() {}.getType();
            poiList = gson.fromJson(poiListJson, type);
        }

        // 2. 展示格式化后的回收任务记录
        List<String> formattedRecords = recyclingTaskManager.getFormattedAllTaskRecords();
        if (formattedRecords.isEmpty()) {
            tvRecycleRecords.setText("暂无执行过的回收任务");
            return;
        }

        StringBuilder recordStr = new StringBuilder();
        for (int i = 0; i < formattedRecords.size(); i++) {
            recordStr.append(i + 1).append(". ").append(formattedRecords.get(i)).append("\n");
        }
        tvRecycleRecords.setText(recordStr.toString().trim());
    }

    /**
     * 绑定所有按钮点击事件
     */
    private void bindButtonEvents() {
        // 1. 一键开门：打开所有执行过回收任务的仓门
        btnOpenExecutedDoors.setOnClickListener(v -> openExecutedDoors());

        // 2. 一键关门：关闭所有仓门（复用 DoorStateManager 原有逻辑）
        btnCloseAllDoors.setOnClickListener(v -> closeAllDoors());

        // 3. 回桩：返回充电桩，先跳转回桩进度页，完成后跳主界面
        btnReturnToCharging.setOnClickListener(v -> startReturnToChargingProcess());
    }

    /**
     * 一键开门：打开所有执行过回收任务的仓门（复用 DoorStateManager）
     */
    private void openExecutedDoors() {
        Set<Integer> executedDoors = recyclingTaskManager.getExecutedDoorNumbers();
        if (executedDoors.isEmpty()) {
            Toast.makeText(this, "暂无执行过任务的仓门", Toast.LENGTH_SHORT).show();
            return;
        }

        // 复用项目原有 DoorStateManager 开门逻辑，批量打开指定仓门
        for (int doorId : executedDoors) {
            doorStateManager.openDoor(doorId);
        }

        Toast.makeText(this, "正在打开执行过任务的仓门", Toast.LENGTH_SHORT).show();
    }

    /**
     * 一键关门：关闭所有仓门（复用 DoorStateManager 全局关门逻辑）
     */
    private void closeAllDoors() {
        doorStateManager.closeAllOpenedDoors();
        Toast.makeText(this, "正在关闭所有仓门", Toast.LENGTH_SHORT).show();
    }

    /**
     * 启动回桩流程：先跳转回桩进度页，再执行回桩任务
     */
    private void startReturnToChargingProcess() {
        // 1. 跳转至回桩进度页面（展示回桩状态）
        Intent progressIntent = new Intent(this, ChargingReturnProgressActivity.class);
        startActivity(progressIntent);

        // 2. 执行回桩核心任务（复用 RobotController 原有回桩逻辑）
        executeReturnToChargingTask();
    }

    /**
     * 执行回桩核心任务（复用项目 RobotController.createReturnHomeAction）
     */
    private void executeReturnToChargingTask() {
        RobotController.createReturnHomeAction(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String json = responseData.string(StandardCharsets.UTF_8);
                    com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                    if (jsonObject.has("action_id")) {
                        currentActionId = jsonObject.get("action_id").getAsString();
                        Log.d(TAG, "回桩任务创建成功，action_id: " + currentActionId);

                        // 轮询回桩状态（复用 MovingActivity 轮询逻辑）
                        startChargingPolling();
                    } else {
                        handleChargingFailure("无法获取回桩任务ID");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析回桩响应失败: " + e.getMessage());
                    handleChargingFailure("解析响应失败");
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "回桩任务创建失败: " + e.getMessage());
                handleChargingFailure("回桩任务创建失败");
            }
        });
    }

    /**
     * 轮询回桩状态（复用 MovingActivity 轮询逻辑）
     */
    private void startChargingPolling() {
        handler.postDelayed(chargingPollingRunnable, 2000);
    }

    private final Runnable chargingPollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentActionId == null) {
                Log.e(TAG, "currentActionId为空，无法轮询回桩状态");
                return;
            }

            RobotController.pollActionStatus(currentActionId, new OkHttpUtils.ResponseCallback() {
                @Override
                public void onSuccess(ByteString responseData) {
                    try {
                        String json = responseData.string(StandardCharsets.UTF_8);
                        com.google.gson.JsonObject jsonObject = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

                        if (jsonObject.has("state")) {
                            com.google.gson.JsonObject state = jsonObject.getAsJsonObject("state");
                            int status = state.get("status").getAsInt();
                            int result = state.get("result").getAsInt();

                            // 回桩完成（状态4=已结束，结果0=成功）
                            if (status == 4 && result == 0) {
                                onChargingComplete();
                            } else {
                                // 核心修改1：替换 this 为 chargingPollingRunnable（已实现 Runnable）
                                handler.postDelayed(chargingPollingRunnable, 2000);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析回桩状态失败", e);
                        // 核心修改2：替换 this 为 chargingPollingRunnable（已实现 Runnable）
                        handler.postDelayed(chargingPollingRunnable, 2000);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "获取回桩状态失败", e);
                    // 核心修改3：替换 this 为 chargingPollingRunnable（已实现 Runnable）
                    handler.postDelayed(chargingPollingRunnable, 2000);
                }
            });
        }
    };

    /**
     * 回桩完成：清空任务记录，通知进度页跳转主界面
     */
    private void onChargingComplete() {
        // 1. 清空全局回收任务记录
        recyclingTaskManager.clearAllTaskRecords();

        // 2. 发送广播通知进度页跳转主界面
        Intent broadcastIntent = new Intent("ACTION_CHARGING_RETURN_COMPLETED");
        sendBroadcast(broadcastIntent);

        // 3. 结束当前页面
        finish();
    }

    /**
     * 回桩失败：通知进度页展示错误信息
     */
    private void handleChargingFailure(String errorMsg) {
        Intent broadcastIntent = new Intent("ACTION_CHARGING_RETURN_FAILED");
        broadcastIntent.putExtra("ERROR_MSG", errorMsg);
        sendBroadcast(broadcastIntent);
        finish();
    }
}
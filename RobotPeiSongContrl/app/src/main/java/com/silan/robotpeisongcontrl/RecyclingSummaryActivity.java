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
    private static final long BUTTON_LOCK_DURATION = 1500; // 按钮灰化防连击时长
    private TextView tvRecycleRecords;
    private Button btnDoorSwitch;    // 开门/关门 合一按钮
    private Button btnReturnToCharging;

    private RecyclingTaskManager recyclingTaskManager;
    private DoorStateManager doorStateManager;
    private List<Poi> poiList;
    private String currentActionId;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // 核心状态：仓门是否已打开（打开后必须关才能回桩）
    private boolean isDoorsOpened = false;
    private Set<Integer> executedDoors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycling_summary);

        initViews();
        initData();
        bindButtonEvents();
        // 初始化回桩按钮状态
        updateReturnButtonStatus();
    }

    private void initViews() {
        tvRecycleRecords = findViewById(R.id.tv_recycle_records);
        btnDoorSwitch = findViewById(R.id.btn_door_switch);
        btnReturnToCharging = findViewById(R.id.btn_return_to_charging);

        recyclingTaskManager = RecyclingTaskManager.getInstance();
        doorStateManager = DoorStateManager.getInstance(this);

        // 初始状态：关门，显示一键开门
        btnDoorSwitch.setText("一键开门");
        isDoorsOpened = false;
    }

    private void initData() {
        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>() {}.getType();
            poiList = gson.fromJson(poiListJson, type);
        }

        executedDoors = recyclingTaskManager.getExecutedDoorNumbers();

        List<String> formattedRecords = recyclingTaskManager.getFormattedAllTaskRecords();
        if (formattedRecords.isEmpty()) {
            tvRecycleRecords.setText("暂无执行过的回收任务");
            btnDoorSwitch.setClickable(false);
            btnDoorSwitch.setAlpha(0.5f);
            return;
        }

        StringBuilder recordStr = new StringBuilder();
        for (int i = 0; i < formattedRecords.size(); i++) {
            recordStr.append(i + 1).append(". ").append(formattedRecords.get(i)).append("\n");
        }
        tvRecycleRecords.setText(recordStr.toString().trim());

        btnDoorSwitch.setClickable(true);
        btnDoorSwitch.setAlpha(1.0f);
    }

    private void bindButtonEvents() {
        // 开关门按钮（互斥+防连击）
        btnDoorSwitch.setOnClickListener(v -> {
            lockAllButtons();
            if (!isDoorsOpened) {
                openExecutedDoors();
            } else {
                closeAllDoors();
            }
            handler.postDelayed(() -> {
                unlockAllButtons();
                // 每次开关门操作后，刷新回桩按钮状态
                updateReturnButtonStatus();
            }, BUTTON_LOCK_DURATION);
        });

        // 回桩按钮（带防连击 + 开门状态拦截）
        btnReturnToCharging.setOnClickListener(v -> {
            if (isDoorsOpened) {
                Toast.makeText(this, "请先关闭仓门，再执行回桩", Toast.LENGTH_SHORT).show();
                return;
            }
            lockAllButtons();
            startReturnToChargingProcess();
            handler.postDelayed(this::unlockAllButtons, BUTTON_LOCK_DURATION);
        });
    }

    /**
     * 核心：根据仓门状态，更新回桩按钮是否可用
     *  true  = 仓门开 → 回桩禁用、灰化
     *  false = 仓门关 → 回桩可用
     */
    private void updateReturnButtonStatus() {
        if (isDoorsOpened) {
            btnReturnToCharging.setClickable(false);
            btnReturnToCharging.setAlpha(0.5f);
        } else {
            btnReturnToCharging.setClickable(true);
            btnReturnToCharging.setAlpha(1.0f);
        }
    }

    /**
     * 开门逻辑
     */
    private void openExecutedDoors() {
        if (executedDoors.isEmpty()) {
            Toast.makeText(this, "暂无执行过任务的仓门", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder openDoorsStr = new StringBuilder();
        for (int doorId : executedDoors) {
            openDoorsStr.append(doorId).append("、");
            doorStateManager.openDoor(doorId);
        }
        // 优化提示：告知用户指令已发送，无动作请检查硬件
        String tip = "开门指令已发送，正在打开：" + openDoorsStr.substring(0, openDoorsStr.length()-1) + "号仓门";
        Toast.makeText(this, tip, Toast.LENGTH_LONG).show();
        // 关键日志：标记指令发送完成，便于排查
        Log.d(TAG, "【开门指令发送完成】仓门" + openDoorsStr + "，Modbus指令已发送，等待硬件执行");
        isDoorsOpened = true;
        btnDoorSwitch.setText("一键关门");
    }

    /**
     * 关门逻辑
     */
    private void closeAllDoors() {
        doorStateManager.closeAllOpenedDoors();
        Toast.makeText(this, "正在关闭所有仓门", Toast.LENGTH_SHORT).show();
        isDoorsOpened = false;
        btnDoorSwitch.setText("一键开门");
    }

    private void startReturnToChargingProcess() {
        Intent progressIntent = new Intent(this, ChargingReturnProgressActivity.class);
        startActivity(progressIntent);
        executeReturnToChargingTask();
    }

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
                        startChargingPolling();
                    } else {
                        handleChargingFailure("无法获取回桩任务ID");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析回桩响应失败", e);
                    handleChargingFailure("解析响应失败");
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "回桩任务创建失败", e);
                handleChargingFailure("回桩任务创建失败");
            }
        });
    }

    private void startChargingPolling() {
        handler.postDelayed(chargingPollingRunnable, 2000);
    }

    private final Runnable chargingPollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentActionId == null) {
                Log.e(TAG, "currentActionId 为空，无法轮询");
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

                            if (status == 4 && result == 0) {
                                onChargingComplete();
                            } else {
                                // 修复核心：将this替换为chargingPollingRunnable
                                handler.postDelayed(chargingPollingRunnable, 2000);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析回桩状态失败", e);
                        handler.postDelayed(chargingPollingRunnable, 2000);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "获取回桩状态失败", e);
                    handler.postDelayed(chargingPollingRunnable, 2000);
                }
            });
        }
    };

    private void onChargingComplete() {
        recyclingTaskManager.clearAllTaskRecords();
        Intent broadcastIntent = new Intent("ACTION_CHARGING_RETURN_COMPLETED");
        sendBroadcast(broadcastIntent);
        finish();
    }

    private void handleChargingFailure(String errorMsg) {
        Intent broadcastIntent = new Intent("ACTION_CHARGING_RETURN_FAILED");
        broadcastIntent.putExtra("ERROR_MSG", errorMsg);
        sendBroadcast(broadcastIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
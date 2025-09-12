package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
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

import com.silan.robotpeisongcontrl.model.PatrolPoint;
import com.silan.robotpeisongcontrl.model.PatrolScheme;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.PatrolSchemeManager;
import com.silan.robotpeisongcontrl.utils.RobotController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okio.ByteString;

public class PatrollingActivity extends BaseActivity {

    private static final String TAG = "PatrollingActivity";
    private int currentPointIndex = 0;
    private List<PatrolPoint> patrolPoints;
    private CountDownTimer waitTimer;
    private String currentActionId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int POLLING_INTERVAL = 2000; // 2秒轮询一次
    private boolean isMoving = false;
    private boolean isWaiting = false;
    private boolean shouldContinue = true;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 任务按钮相关
    private Button btnTask1, btnTask2, btnTask3, btnTask4, btnTask5, btnTask6;
    private int currentTask = 0;
    private Handler blinkHandler = new Handler();
    private Runnable blinkRunnable;
    private boolean isBlinking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patrolling);

        // 初始化任务按钮
        btnTask1 = findViewById(R.id.btn_task1);
        btnTask2 = findViewById(R.id.btn_task2);
        btnTask3 = findViewById(R.id.btn_task3);
        btnTask4 = findViewById(R.id.btn_task4);
        btnTask5 = findViewById(R.id.btn_task5);
        btnTask6 = findViewById(R.id.btn_task6);

        // 禁用任务按钮的点击（仅用于显示状态）
        btnTask1.setClickable(false);
        btnTask2.setClickable(false);
        btnTask3.setClickable(false);
        btnTask4.setClickable(false);
        btnTask5.setClickable(false);
        btnTask6.setClickable(false);

        // 闪烁动画逻辑
        blinkRunnable = new Runnable() {
            boolean isRed = true;
            @Override
            public void run() {
                if (isBlinking) {
                    Button activeButton = getTaskButton(currentTask);
                    if (activeButton != null) {
                        if (isRed) {
                            activeButton.setBackgroundResource(R.drawable.button_red_rect);
                        } else {
                            activeButton.setBackgroundResource(R.drawable.button_blue_rect);
                        }
                    }
                    isRed = !isRed;
                    blinkHandler.postDelayed(this, 500); // 每500ms切换一次
                }
            }
        };

        // 获取巡航方案
        int schemeId = getIntent().getIntExtra("scheme_id", -1);
        PatrolScheme scheme = PatrolSchemeManager.loadScheme(this, schemeId);
        if (scheme != null) {
            patrolPoints = scheme.getPoints();
            startPatrol();
        } else {
            Toast.makeText(this, "方案加载失败", Toast.LENGTH_SHORT).show();
            finish();
        }

        // 停止巡航按钮
        Button btnStopPatrol = findViewById(R.id.btn_stop_patrol);
        btnStopPatrol.setOnClickListener(v -> stopPatrol());
    }

    private void startPatrol() {
        if (patrolPoints == null || patrolPoints.isEmpty()) {
            Toast.makeText(this, "巡航点位为空", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        moveToNextPoint();
    }

    private void moveToNextPoint() {
        if (!shouldContinue) return;

        Log.d(TAG, "开始移动到下一个点位, index: " + currentPointIndex);
        isMoving = true;
        isWaiting = false;

        if (currentPointIndex >= patrolPoints.size()) {
            currentPointIndex = 0; // 循环
        }

        PatrolPoint nextPoint = patrolPoints.get(currentPointIndex);
        updateStatus("前往: " + nextPoint.toString());

        RobotController.createMoveAction(nextPoint.getPoi(), new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String json = responseData.string(StandardCharsets.UTF_8);
                    com.google.gson.JsonObject jsonObject = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                    if (jsonObject.has("action_id")) {
                        currentActionId = jsonObject.get("action_id").getAsString();
                        Log.d(TAG, "移动任务创建成功, action_id: " + currentActionId);
                        startPollingActionStatus();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析任务ID失败", e);
                    updateStatus("解析任务ID失败");
                    handler.postDelayed(() -> moveToNextPoint(), 3000);
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "创建移动任务失败", e);
                updateStatus("创建移动任务失败");
                handler.postDelayed(() -> moveToNextPoint(), 3000);
            }
        });
    }

    private void startPollingActionStatus() {
        handler.removeCallbacks(statusPollingRunnable);
        handler.postDelayed(statusPollingRunnable, POLLING_INTERVAL);
    }

    private final Runnable statusPollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentActionId == null || !shouldContinue) {
                return;
            }

            Log.d(TAG, "轮询任务状态, action_id: " + currentActionId);
            RobotController.pollActionStatus(currentActionId, new OkHttpUtils.ResponseCallback() {
                @Override
                public void onSuccess(ByteString responseData) {
                    try {
                        String json = responseData.string(StandardCharsets.UTF_8);
                        com.google.gson.JsonObject jsonObject = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                        if (jsonObject.has("state")) {
                            com.google.gson.JsonObject state = jsonObject.getAsJsonObject("state");
                            int status = state.get("status").getAsInt();
                            int result = state.get("result").getAsInt();

                            Log.d(TAG, "任务状态: status=" + status + ", result=" + result);

                            if (status == 4) { // 已结束
                                if (result == 0) { // 成功
                                    Log.d(TAG, "移动任务成功完成");
                                    handleArrivalAtPoint(patrolPoints.get(currentPointIndex));
                                } else { // 失败
                                    Log.d(TAG, "移动任务失败");
                                    updateStatus("移动失败，重试中...");
                                    handler.postDelayed(() -> moveToNextPoint(), 3000);
                                }
                            } else {
                                // 未结束，继续轮询
                                handler.postDelayed(statusPollingRunnable, POLLING_INTERVAL);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析状态响应失败", e);
                        handler.postDelayed(statusPollingRunnable, POLLING_INTERVAL);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "获取任务状态失败", e);
                    handler.postDelayed(statusPollingRunnable, POLLING_INTERVAL);
                }
            });
        }
    };

    private void handleArrivalAtPoint(PatrolPoint point) {
        runOnUiThread(() -> {
            currentTask = point.getTask();
            resetTaskButtonsUI();

            if (currentTask > 0) {
                // 更新UI：设置对应按钮为红色
                getTaskButton(currentTask).setBackgroundResource(R.drawable.button_red_rect);
                // 执行打开仓门操作
                openCargoDoor(currentTask);
            } else {
                // 没有任务，直接前往下一点
                startWaitingPeriod();
            }
        });
    }

    private Button getTaskButton(int taskId) {
        switch (taskId) {
            case 1: return btnTask1;
            case 2: return btnTask2;
            case 3: return btnTask3;
            case 4: return btnTask4;
            case 5: return btnTask5;
            case 6: return btnTask6;
            default: return null;
        }
    }

    // 重置按钮状态方法
    private void resetTaskButtonsUI() {
        btnTask1.setBackgroundResource(R.drawable.button_blue_rect);
        btnTask2.setBackgroundResource(R.drawable.button_blue_rect);
        btnTask3.setBackgroundResource(R.drawable.button_blue_rect);
        btnTask4.setBackgroundResource(R.drawable.button_blue_rect);
        btnTask5.setBackgroundResource(R.drawable.button_blue_rect);
        btnTask6.setBackgroundResource(R.drawable.button_blue_rect);
    }

    private void openCargoDoor(int doorId) {
        runOnUiThread(() -> {
            // 开始按钮闪烁
            startButtonBlink();
            // 显示开仓提示
            updateStatus("模拟打开仓门 " + doorId);
        });

        // 使用主线程Handler执行延迟操作
        mainHandler.postDelayed(() -> closeCargoDoor(doorId), 5000);
    }

    private void closeCargoDoor(int doorId) {
        runOnUiThread(() -> {
            // 显示关仓提示
            updateStatus("模拟关闭仓门 " + doorId);

            // 停止按钮闪烁
            stopButtonBlink();
            resetTaskButtonsUI();
            startWaitingPeriod();
        });
    }

    private void startButtonBlink() {
        isBlinking = true;
        blinkHandler.post(blinkRunnable);
    }

    private void stopButtonBlink() {
        isBlinking = false;
        blinkHandler.removeCallbacks(blinkRunnable);
        // 停止后恢复为蓝色
        resetTaskButtonsUI();
    }

    private void startWaitingPeriod() {
        runOnUiThread(() -> {
            Log.d(TAG, "开始等待6秒");
            isMoving = false;
            isWaiting = true;
            updateStatus("到达，等待6秒...");

            // 取消之前的等待计时器
            if (waitTimer != null) {
                waitTimer.cancel();
                waitTimer = null;
            }

            waitTimer = new CountDownTimer(6000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    if (!shouldContinue) {
                        cancel();
                        return;
                    }
                    updateStatus("等待: " + millisUntilFinished / 1000 + "秒");
                }

                @Override
                public void onFinish() {
                    if (!shouldContinue) return;

                    Log.d(TAG, "等待结束，前往下一个点位");
                    currentPointIndex++;
                    moveToNextPoint();
                }
            }.start();
        });
    }

    private void stopPatrol() {
        Log.d(TAG, "停止巡航");
        shouldContinue = false;

        // 取消所有计时器和轮询
        handler.removeCallbacks(statusPollingRunnable);
        blinkHandler.removeCallbacks(blinkRunnable);

        // 在UI线程中取消计时器
        runOnUiThread(() -> {
            if (waitTimer != null) {
                waitTimer.cancel();
                waitTimer = null;
            }
        });

        // 如果正在移动，需要取消当前移动任务
        if (isMoving && currentActionId != null) {
            Log.d(TAG, "取消当前移动任务");
            RobotController.cancelCurrentAction(new OkHttpUtils.ResponseCallback() {
                @Override
                public void onSuccess(ByteString responseData) {
                    Log.d(TAG, "取消移动任务成功");
                    returnToDock();
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "取消移动任务失败", e);
                    returnToDock();
                }
            });
        } else {
            returnToDock();
        }
    }

    private void returnToDock() {
        Log.d(TAG, "返回充电桩");
        updateStatus("正在返回充电...");

        // 返回充电
        RobotController.createReturnHomeAction(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                runOnUiThread(() -> {
                    Toast.makeText(PatrollingActivity.this, "正在返回充电", Toast.LENGTH_SHORT).show();
                    finishAndReturnToMain();
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(PatrollingActivity.this, "返航失败", Toast.LENGTH_SHORT).show();
                    finishAndReturnToMain();
                });
            }
        });
    }

    private void finishAndReturnToMain() {
        Intent intent = new Intent(PatrollingActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void updateStatus(String message) {
        runOnUiThread(() -> {
            TextView statusView = findViewById(R.id.tv_status);
            statusView.setText(message);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        shouldContinue = false;

        // 在UI线程中取消计时器
        runOnUiThread(() -> {
            if (waitTimer != null) {
                waitTimer.cancel();
                waitTimer = null;
            }
        });
        mainHandler.removeCallbacksAndMessages(null);
        handler.removeCallbacks(statusPollingRunnable);
        blinkHandler.removeCallbacks(blinkRunnable);
    }
}
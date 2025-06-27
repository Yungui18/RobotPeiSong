
package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okio.ByteString;

public class MovingActivity extends BaseActivity {
    private static final int POLLING_INTERVAL = 2000; // 2秒轮询一次

    private TextView statusText;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TaskManager taskManager = TaskManager.getInstance();
    private List<Poi> poiList;
    private String currentActionId;
    private static final String TAG = "MovingActivity";
    private boolean isReturningHome = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moving);
        statusText = findViewById(R.id.tv_status);

        // 获取POI列表
        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>() {
            }.getType();
            poiList = gson.fromJson(poiListJson, type);
        }

        startNextTask();
    }

    private void startNextTask() {
        Poi nextPoi = taskManager.getNextTask();

        if (nextPoi != null) {
            statusText.setText("正在前往点位: " + nextPoi.getDisplayName());
            moveToPoint(nextPoi);
        } else {
            returnToHome();
        }
    }

    private void moveToPoint(Poi poi) {
        isReturningHome = false; // 普通移动任务，重置标志
        RobotController.createMoveAction(poi, new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                runOnUiThread(() -> {
                    try {
                        String json = responseData.string(StandardCharsets.UTF_8);
                        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

                        if (jsonObject.has("action_id")) {
                            currentActionId = jsonObject.get("action_id").getAsString();

                            // 开始状态轮询
                            startStatusPolling();
                        } else {
                            handleMoveFailure("响应中未找到action_id");
                        }
                    } catch (Exception e) {
                        handleMoveFailure("解析响应失败: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() ->
                        handleMoveFailure("移动任务创建失败: " + e.getMessage())
                );
            }
        });
    }

    private void startStatusPolling() {
        handler.removeCallbacks(statusPollingRunnable);
        handler.postDelayed(statusPollingRunnable, POLLING_INTERVAL);
    }

    private final Runnable statusPollingRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentActionId == null) {
                Log.e(TAG, "currentActionId为空，无法轮询状态");
                return;
            }

            RobotController.pollActionStatus(currentActionId, new OkHttpUtils.ResponseCallback() {
                @Override
                public void onSuccess(ByteString responseData) {
                    try {
                        String json = responseData.string(StandardCharsets.UTF_8);
                        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();

                        if (jsonObject.has("state")) {
                            JsonObject state = jsonObject.getAsJsonObject("state");
                            int status = state.get("status").getAsInt();
                            int result = state.get("result").getAsInt();

                            runOnUiThread(() ->
                                    handleActionStatus(status, result)
                            );
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析状态响应失败", e);
                        continuePolling();
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "获取任务状态失败", e);
                    continuePolling();
                }
            });
        }

        private void continuePolling() {
            handler.postDelayed(this, POLLING_INTERVAL);
        }
    };

    private void handleActionStatus(int status, int result) {
        switch (status) {
            case 0: // 新创建
                statusText.setText("准备出发...");
                continuePolling();
                break;
            case 1: // 正在运行
                continuePolling();
                break;
            case 4: // 已结束
                if (result == 0) { // 成功
                    onMoveComplete();
                } else { // 失败
                    handleMoveFailure("任务失败，结果码: " + result);
                }
                break;
            default:
                Log.w(TAG, "未知状态: " + status);
                continuePolling();
        }
    }

    private void continuePolling() {
        // 继续轮询
        handler.postDelayed(statusPollingRunnable, POLLING_INTERVAL);
    }

    private void onMoveComplete() {
        Log.d(TAG, "移动任务完成");
        handler.removeCallbacks(statusPollingRunnable);
        if (isReturningHome) {
            // 回桩任务完成，回到主页面
            Intent intent = new Intent(MovingActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // 清除栈中所有中间的Activity
            startActivity(intent);
        } else {
            // 跳转到到达确认页面
            Intent intent = new Intent(MovingActivity.this, ArrivalConfirmationActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
            startActivity(intent);
        }
        finish();
    }

    private void handleMoveFailure(String errorMessage) {
        Log.e(TAG, "移动失败: " + errorMessage);
        handler.removeCallbacks(statusPollingRunnable);
        statusText.setText("移动失败: " + errorMessage);

        // 延迟后尝试下一个任务
        handler.postDelayed(() -> startNextTask(), 3000);
    }

    private void returnToHome() {
        isReturningHome = true; // 标记当前是回桩任务
        statusText.setText("正在前往充电桩");
        RobotController.createReturnHomeAction(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String json = responseData.string(StandardCharsets.UTF_8);
                    JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                    if (jsonObject.has("action_id")) {
                        currentActionId = jsonObject.get("action_id").getAsString();
                        Log.d(TAG, "回桩任务创建成功，action_id: " + currentActionId);

                        // 开始轮询任务状态
                        handler.postDelayed(statusPollingRunnable, POLLING_INTERVAL);
                    } else {
                        Log.e(TAG, "响应中未找到action_id");
                        handleReturnHomeFailure("无法获取回桩任务ID");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "解析响应失败: " + e.getMessage());
                    handleReturnHomeFailure("解析响应失败");
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "回桩任务创建失败: " + e.getMessage());
                handleReturnHomeFailure("回桩任务创建失败");
            }
        });
    }

    private void handleReturnHomeFailure(String errorMessage) {
        Log.e(TAG, "回桩失败: " + errorMessage);
        handler.removeCallbacks(statusPollingRunnable);
        statusText.setText("回桩失败: " + errorMessage);

        // 延迟后返回主页面
        handler.postDelayed(() -> {
            isReturningHome = false; // 重置标志
            Intent intent = new Intent(MovingActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }, 3000);
    }

    @Override
    public void onBackPressed() {
        if (isReturningHome) {
            // 如果正在回桩，则直接回到主页面
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 停止轮询
        handler.removeCallbacks(statusPollingRunnable);
    }
}
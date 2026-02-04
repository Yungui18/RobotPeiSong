
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
    import com.silan.robotpeisongcontrl.model.DeliveryFailure;
    import com.silan.robotpeisongcontrl.model.Poi;
    import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
    import com.silan.robotpeisongcontrl.utils.DeliveryFailureManager;
    import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
    import com.silan.robotpeisongcontrl.utils.RecyclingTaskManager;
    import com.silan.robotpeisongcontrl.utils.RobotController;
    import com.silan.robotpeisongcontrl.utils.SoundPlayerManager;
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
        private boolean isScheduledTask = false;
        private boolean[] selectedDoors = new boolean[4];
        private int retryCount = 0;
        private static final int MAX_RETRY_COUNT = 3; // 最大重试次数
        private Poi currentPoi;
        private boolean isRouteDelivery = false; // 是否是路线配送
        private final Handler doorHandler = new Handler(Looper.getMainLooper()); // 仓门操作计时器
        private static final int DOOR_OPEN_DURATION = 5000; // 仓门开启时长（5秒）

        // 回收任务相关
        private boolean isRecycleTask = false; // 是否为回收任务
        private String recyclePointId; // 回收点位ID
        private Poi recyclePoi; // 回收点位对象
        private boolean isNavigatingToRecyclePoint = false; // 标记是否正在前往回收点位
        private SoundPlayerManager soundPlayerManager;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_moving);
            statusText = findViewById(R.id.tv_status);

            soundPlayerManager = SoundPlayerManager.getInstance(this);
            // 获取POI列表
            Intent intent = getIntent();
            String poiListJson = intent.getStringExtra("poi_list");
            if (poiListJson != null) {
                Gson gson = new Gson();
                Type type = new TypeToken<ArrayList<Poi>>() {
                }.getType();
                poiList = gson.fromJson(poiListJson, type);
            }

            // 接收回收任务参数
            isRecycleTask = intent.getBooleanExtra("is_recycle", false);
            recyclePointId = intent.getStringExtra("recycle_point_id");
            // 查找回收点位对象
            if (isRecycleTask && recyclePointId != null && poiList != null && !poiList.isEmpty()) {
                boolean isPoiMatched = false;
                for (Poi poi : poiList) {
                    if (poi.getId().equals(recyclePointId)) {
                        recyclePoi = poi;
                        isPoiMatched = true;
                        Log.d(TAG, "匹配到回收点位：" + poi.getDisplayName() + "（ID：" + recyclePointId + "）");
                        break;
                    }
                }
                // 匹配失败时打印错误日志，便于排查
                if (!isPoiMatched) {
                    Log.e(TAG, "回收点位匹配失败！传递的ID：" + recyclePointId + "，poiList中无对应点位");
                }
            } else {
                // 传参缺失时打印日志
                Log.e(TAG, "回收任务初始化失败：isRecycleTask=" + isRecycleTask
                        + "，recyclePointId=" + recyclePointId
                        + "，poiList是否为空=" + (poiList == null || poiList.isEmpty()));
            }

            isScheduledTask = getIntent().getBooleanExtra("scheduled_task", false);
            isRouteDelivery = getIntent().getBooleanExtra("is_route_delivery", false);
            if (getIntent().hasExtra("selected_doors")) {
                selectedDoors = getIntent().getBooleanArrayExtra("selected_doors");
            }

            // 确保 selectedDoors 不为 null
            if (selectedDoors == null) {
                selectedDoors = new boolean[4]; // 默认4个false值
            }

            startNextTask();

            // 检查是否有配送失败的任务
            boolean hasFailures = checkForFailures();
            if (hasFailures) {
                showFailureScreen();
            }
        }

        private boolean checkForFailures() {
            // 这里简化处理，实际应根据任务执行情况判断
            return false;
        }

        private void showFailureScreen() {
            Intent intent = new Intent(this, DeliveryFailureActivity.class);
            startActivity(intent);
            finish();
        }

        private void startNextTask() {
            retryCount = 0;
            Poi nextPoi = taskManager.getNextTask();

            if (nextPoi != null) {
                currentPoi = nextPoi;
                statusText.setText(isRecycleTask ?
                        "正在前往回收点位: " + nextPoi.getDisplayName() :
                        "正在前往点位: " + nextPoi.getDisplayName());
                moveToPoint(nextPoi);
            } else {
                // 任务完成：回收任务导航到回收点位，配送任务回桩
                if (isRecycleTask) {
                    if (recyclePoi != null) {
                        navigateToRecyclePoint(); // 正常导航到回收点位
                    } else {
                        // 关键：添加空值日志，快速定位问题
                        Log.e(TAG, "回收任务完成，但回收点位为空！无法导航到回收点位，将回充电桩");
                        returnToHome(); // 兜底：回充电桩，避免流程卡死
                    }
                } else {
                    returnToHome(); // 配送任务：正常回桩
                }
            }
        }

        // 导航到回收点位
        private void navigateToRecyclePoint() {
            if (recyclePoi == null) {
                Log.e(TAG, "回收点位为空，无法导航");
                returnToHome();
                return;
            }
            isNavigatingToRecyclePoint = true;
            statusText.setText("任务完成，正在前往回收点位: " + recyclePoi.getDisplayName());
            RobotController.createMoveAction(this, recyclePoi, new OkHttpUtils.ResponseCallback() {
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
                            handleMoveFailure("回收点位导航任务创建失败: " + e.getMessage())
                    );
                }
            });
        }

        private void moveToPoint(Poi poi) {
            isReturningHome = false;
            isNavigatingToRecyclePoint = false;
            currentPoi = poi;
            RobotController.createMoveAction(this, poi, new OkHttpUtils.ResponseCallback() {
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
                case -2://被取消
                    statusText.setText("任务被取消");
                    continuePolling();
                    break;
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
                    } else if (result == -1 && !isReturningHome && retryCount < MAX_RETRY_COUNT) {
                        // 核心修改：只有「非回桩任务」才重试
                        handleRetry();
                    } else { // 其他失败情况
                        if (isNavigatingToRecyclePoint) {
                            handleRecycleNavigationFailure("回收点位导航失败，结果码: " + result);
                        } else if (isReturningHome) {
                            // 核心修改：回桩失败 → 直接返回主界面，不重试
                            handleReturnHomeFailure("回桩失败，结果码: " + result);
                        } else {
                            handleMoveFailure("任务失败，结果码: " + result);
                        }
                    }
                    break;
                default:
                    Log.w(TAG, "未知状态: " + status);
                    continuePolling();
            }
        }

        private void handleRetry() {
            Log.w(TAG, "任务失败（结果码-1），尝试重试... 重试次数: " + (retryCount + 1));
            retryCount++; // 增加重试计数
            handler.removeCallbacks(statusPollingRunnable);

            if (isNavigatingToRecyclePoint) {
                statusText.setText("回收点位导航失败，正在重试(" + retryCount + "/" + MAX_RETRY_COUNT + ")...");
                handler.postDelayed(() -> navigateToRecyclePoint(), 3000);
            } else if (currentPoi != null) {
                statusText.setText("移动失败，正在重试(" + retryCount + "/" + MAX_RETRY_COUNT + ")...");
                handler.postDelayed(() -> moveToPoint(currentPoi), 3000);
            } else {
                startNextTask();
            }
        }

        private void continuePolling() {
            // 继续轮询
            handler.postDelayed(statusPollingRunnable, POLLING_INTERVAL);
        }

        private void onMoveComplete() {
            Log.d(TAG, "移动任务完成");
            handler.removeCallbacks(statusPollingRunnable);

            // ================ 核心删除：移除路线配送自动仓门逻辑 ================
            // 原代码：if (isRouteDelivery && currentPoi != null) { openRouteDoorAndContinue(currentPoi); return; }
            // 直接删除上述整段代码，路线配送不再自动操作仓门，走后续统一流程
            // ===================================================================

            // 核心逻辑：区分「回桩完成」「回收点位导航完成」「普通任务完成」（路线配送归为普通任务）
            if (isReturningHome) {
                // 场景1：回桩任务完成 → 播放语音后延迟跳转主界面
                playTaskArriveSoundAndDelay(() -> finishAndReturnToMain());
            } else if (isNavigatingToRecyclePoint) {
                // 场景2：回收点位导航完成 → 带回去重校验跳汇总页
                playTaskArriveSoundAndDelay(() -> {
                    if (isRecycleTask && recyclePoi != null) {
                        List<Integer> bindDoorIds = taskManager.getDoorIdsForPoint(recyclePoi.getDisplayName());
                        if (bindDoorIds != null && !bindDoorIds.isEmpty()) {
                            RecyclingTaskManager recyclingTaskManager = RecyclingTaskManager.getInstance();
                            boolean isRecordExisted = false;
                            for (RecyclingTaskManager.RecyclingTaskRecord record : recyclingTaskManager.getExecutedTaskRecords()) {
                                if (record.getPointName().equals(recyclePoi.getDisplayName())
                                        && record.getDoorIds().equals(bindDoorIds)) {
                                    isRecordExisted = true;
                                    Log.w(TAG, "回收点位导航完成，跳过重复记录：" + recyclePoi.getDisplayName() + " → 仓门" + bindDoorIds);
                                    break;
                                }
                            }
                            if (!isRecordExisted) {
                                recyclingTaskManager.addTaskRecord(recyclePoi.getDisplayName(), bindDoorIds);
                                Log.d(TAG, "回收点位导航完成，同步记录成功：" + recyclePoi.getDisplayName() + " → 仓门" + bindDoorIds);
                            }
                        } else {
                            Log.w(TAG, "回收点位导航完成，无绑定仓门：" + recyclePoi.getDisplayName());
                        }
                    }
                    navigateToRecyclingSummaryPage();
                });
            } else {
                if (currentPoi != null) {
                    taskManager.removeTask(currentPoi);
                }
                // 场景3：普通任务/路线配送完成 → 跳到达确认页（路线配送从此处进入，预留操作空间）
                playTaskArriveSoundAndDelay(() -> {
                    if (isRecycleTask && currentPoi != null) {
                        List<Integer> bindDoorIds = taskManager.getDoorIdsForPoint(currentPoi.getDisplayName());
                        if (bindDoorIds != null && !bindDoorIds.isEmpty()) {
                            RecyclingTaskManager recyclingTaskManager = RecyclingTaskManager.getInstance();
                            boolean isRecordExisted = false;
                            for (RecyclingTaskManager.RecyclingTaskRecord record : recyclingTaskManager.getExecutedTaskRecords()) {
                                if (record.getPointName().equals(currentPoi.getDisplayName())
                                        && record.getDoorIds().equals(bindDoorIds)) {
                                    isRecordExisted = true;
                                    Log.w(TAG, "跳过重复回收记录：" + currentPoi.getDisplayName() + " → 仓门" + bindDoorIds);
                                    break;
                                }
                            }
                            if (!isRecordExisted) {
                                recyclingTaskManager.addTaskRecord(currentPoi.getDisplayName(), bindDoorIds);
                                Log.d(TAG, "回收记录同步成功：" + currentPoi.getDisplayName() + " → 仓门" + bindDoorIds);
                            }
                        } else {
                            Log.w(TAG, "回收点位无绑定仓门：" + currentPoi.getDisplayName());
                        }
                    }
                    Intent intent = new Intent(this, ArrivalConfirmationActivity.class);
                    intent.putExtra("poi_list", new Gson().toJson(poiList));
                    intent.putExtra("scheduled_task", isScheduledTask);
                    intent.putExtra("selected_doors", selectedDoors);
                    intent.putExtra("is_recycle", isRecycleTask);
                    intent.putExtra("recycle_point_id", recyclePointId);
                    // ================ 新增：透传路线配送标记到到达确认页 ================
                    intent.putExtra("is_route_delivery", isRouteDelivery);
                    // ===================================================================
                    startActivity(intent);
                    finish();
                });
            }
        }

        private void playTaskArriveSoundAndDelay(Runnable delayRunnable) {
            // 空指针校验，确保语音管理器初始化完成
            if (soundPlayerManager != null) {
                soundPlayerManager.playSound(SoundPlayerManager.KEY_TASK_ARRIVE);
            }
            // 延迟1秒执行后续逻辑，确保语音播放完成
            handler.postDelayed(delayRunnable, 4000);
        }

        private void navigateToRecyclingSummaryPage() {
            // 1. 存储回收点位到全局管理器（供新页面使用）
            RecyclingTaskManager.getInstance().setRecyclePoi(recyclePoi);
            // 2. 停止轮询
            handler.removeCallbacks(statusPollingRunnable);
            // 3. 跳转至新增的回收汇总页面，并关闭当前Activity
            Intent intent = new Intent(this, RecyclingSummaryActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
            startActivity(intent);
            finish(); // 关键：关闭MovingActivity，避免循环
        }

        private void checkForDeliveryFailures() {
            List<DeliveryFailure> failures = DeliveryFailureManager.loadAllFailures(this);
            if (!failures.isEmpty()) {
                showFailureScreen(failures);
            } else {
                finishAndReturnToMain();
            }
        }

        private void showFailureScreen(List<DeliveryFailure> failures) {
            Intent intent = new Intent(this, DeliveryFailureActivity.class);
            intent.putExtra("failures", new Gson().toJson(failures));
            startActivity(intent);
            finish();
        }

        private void returnToMain() {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        }

        private void finishAndReturnToMain() {
            // 核心修改：清空任务管理器，避免再次触发任务
            taskManager.clearTasks();
            // 停止轮询
            handler.removeCallbacks(statusPollingRunnable);

            // 检查等待队列（仅定时任务）
            if (TaskManager.hasPendingScheduledTask()) {
                ScheduledDeliveryTask task = TaskManager.getNextPendingScheduledTask();
                startScheduledTask(task);
            } else {
                // 正常返回主界面，终止当前Activity
                Intent intent = new Intent(MovingActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish(); // 关键：关闭当前MovingActivity，避免残留
            }
        }

        private void startScheduledTask(ScheduledDeliveryTask task) {
            Intent intent = new Intent(this, ScheduledDeliveryExecutionActivity.class);
            intent.putExtra("task_id", task.getId());
            startActivity(intent);
            finish();
        }

        private void handleMoveFailure(String errorMessage) {
            Log.e(TAG, "移动失败: " + errorMessage);
            handler.removeCallbacks(statusPollingRunnable);
            statusText.setText("移动失败: " + errorMessage);

            // 延迟后尝试下一个任务
            handler.postDelayed(() -> {
                if (currentPoi != null) {
                    moveToPoint(currentPoi); // 重试当前失败的任务
                    retryCount = 0; // 重置重试计数，避免累计
                } else {
                    startNextTask(); // 无当前任务时，再获取下一个
                }
            }, 3000);
        }

        private void handleRecycleNavigationFailure(String errorMessage) {
            Log.e(TAG, "回收点位导航失败: " + errorMessage);
            handler.removeCallbacks(statusPollingRunnable);
            statusText.setText("回收点位导航失败: " + errorMessage);

            // 回收导航失败不再继续重试，直接结束
            handler.postDelayed(() -> finishAndReturnToMain(), 3000);
        }

        private void returnToHome() {
            isReturningHome = true; // 标记当前是回桩任务
            isNavigatingToRecyclePoint = false;
            currentPoi = null;
            statusText.setText(isRecycleTask ? "正在前往回收点位" : "正在前往充电桩");

            // 配送任务：正常回桩
            RobotController.createReturnHomeAction(new OkHttpUtils.ResponseCallback() {
                @Override
                public void onSuccess(ByteString responseData) {
                    try {
                        String json = responseData.string(StandardCharsets.UTF_8);
                        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
                        if (jsonObject.has("action_id")) {
                            currentActionId = jsonObject.get("action_id").getAsString();
                            Log.d(TAG, "回桩任务创建成功，action_id: " + currentActionId);

                            startStatusPolling();
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
                // 如果正在回桩/前往回收点位，则直接回到主页面
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
            if (soundPlayerManager != null) {
                soundPlayerManager.stopSound();
            }
        }
    }
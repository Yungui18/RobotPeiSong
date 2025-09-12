package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.adapter.UsbSerialHelper;
import com.silan.robotpeisongcontrl.model.Constants;
import com.silan.robotpeisongcontrl.model.Poi;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okio.ByteString;

public class FollowModeManager {
    private static final String TAG = "FollowModeManager";

    // 基站ID常量
    private static final long FRONT_BASE_ID = 0x000002DB; // 731
    private static final long REAR_BASE_ID = 0x000002DC;  // 732
    private static final long LEFT_BASE_ID = 0x000002DD;  // 733
    private static final long RIGHT_BASE_ID = 0x000002DE; // 734

    // 机器人坐标系定义常量
    private static final double ROBOT_FRONT_OFFSET = 0.2; // 机器人中心到前端的距离（米）
    private static final double ROBOT_REAR_OFFSET = 0.2;  // 机器人中心到后端的距离（米）
    private static final double ROBOT_SIDE_OFFSET = 0.15; // 机器人中心到侧边的距离（米）

    // 基站负责的角度范围
    private static final double FRONT_BASE_ANGLE_RANGE = 60.0; // 前方±60度
    private static final double REAR_BASE_ANGLE_RANGE = 60.0;  // 后方±60度
    private static final double SIDE_BASE_ANGLE_RANGE = 60.0;  // 侧方±60度

    private Context context;
    private UsbSerialHelper usbHelper;
    private Handler handler;
    private ScheduledExecutorService moveExecutor;
    private boolean isFollowing = false;
    private boolean isWaitingForMoveCompletion = false;
    private String mCurrentActionId;
    private RobotController.RobotPose cachedPose;
    private PositionEstimate currentTarget;
    private AdaptiveKalmanFilter kalmanDistance, kalmanAzimuth, kalmanElevation;
    private Map<Long, BaseStationConfig> baseStationConfigs = new HashMap<>();
    private UwbData currentActiveBaseData;
    private long currentActiveBaseId = -1;
    private long lastActiveBaseTime = 0;
    private FollowModeListener listener;
    private Map<Long, BaseStationData> baseStationDataMap = new HashMap<>();

    // 数据统计
    private int uwbPacketCount = 0;
    private long lastPacketTime = 0;
    private float packetRate = 0;
    private Queue<PositionEstimate> moveTaskQueue = new LinkedList<>();
    private boolean isMoving = false;

    // 回调接口
    public interface FollowModeListener {
        void onUwbDataUpdate(double distance, double azimuth, double elevation);
        void onMoveStatusUpdate(String status);
        void onTargetUpdate(double globalX, double globalY);
        void onRobotPoseUpdate(double x, double y, double yaw);
        void onCoordTransformUpdate(double baseX, double baseY, double robotX, double robotY);
        void onBaseStationUpdate(long anchorId, boolean isActive);
        void onPacketStatsUpdate(int count, float rate);
    }

    // 基站配置类 - 修改字段为public
    public static class BaseStationConfig {
        public double offsetX, offsetY, installAngle;
        public int indicatorViewId; // 改为public
        public double confidence; // 置信度权重 (0-1)
        public double height;     // 安装高度 (米)
        public double minAngle;   // 最小有效角度
        public double maxAngle;   // 最大有效角度

        public BaseStationConfig(double offsetX, double offsetY, double installAngle,
                                 int indicatorViewId, double confidence, double height,
                                 double minAngle, double maxAngle) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetY = offsetY;
            this.installAngle = installAngle;
            this.indicatorViewId = indicatorViewId;
            this.confidence = confidence;
            this.height = height;
            this.minAngle = minAngle;
            this.maxAngle = maxAngle;
        }
    }

    // UWB数据类
    private static class UwbData {
        double distance;
        double azimuth;
        double elevation;
        long timestamp;

        UwbData(double distance, double azimuth, double elevation, long timestamp) {
            this.distance = distance;
            this.azimuth = azimuth;
            this.elevation = elevation;
            this.timestamp = timestamp;
        }
    }

    // 位置估计类
    private static class PositionEstimate {
        double x, y;

        PositionEstimate(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    // 基站数据存储类
    private static class BaseStationData {
        UwbData data;
        long lastUpdateTime;
        double confidence; // 置信度权重

        BaseStationData(UwbData data, long timestamp, double confidence) {
            this.data = data;
            this.lastUpdateTime = timestamp;
            this.confidence = confidence;
        }
    }

    // 卡尔曼滤波器类
    private static class AdaptiveKalmanFilter {
        private double x; // 估计值
        private double p = 1; // 估计误差协方差
        private final double q; // 过程噪声协方差
        private final double rNear; // 近距离测量噪声
        private final double rFar; // 远距离测量噪声
        private boolean isFirst = true;

        public AdaptiveKalmanFilter(double processNoise, double nearNoise, double farNoise) {
            this.q = processNoise;
            this.rNear = nearNoise;
            this.rFar = farNoise;
        }

        // 根据距离动态选择测量噪声
        public double filter(double measurement, double distance) {
            if (isFirst) {
                x = measurement;
                isFirst = false;
                return x;
            }

            // 预测
            double xPred = x;
            double pPred = p + q;

            // 动态调整测量噪声（距离越近，噪声越大）
            double r = distance < 0.5 ? rNear : rFar;

            // 更新
            double k = pPred / (pPred + r); // 卡尔曼增益
            x = xPred + k * (measurement - xPred);
            p = (1 - k) * pPred;

            return x;
        }
    }

    public FollowModeManager(Context context, FollowModeListener listener) {
        this.context = context;
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
        this.moveExecutor = Executors.newSingleThreadScheduledExecutor();
        this.usbHelper = new UsbSerialHelper(context);
        initKalmanFilters();
        initBaseStationConfigs();
    }

    private void initKalmanFilters() {
        kalmanDistance = new AdaptiveKalmanFilter(0.02, 0.03, 0);
        kalmanAzimuth = new AdaptiveKalmanFilter(0.02, 0.02, 0);
        kalmanElevation = new AdaptiveKalmanFilter(0.02, 0.02, 0);
    }

    private void initBaseStationConfigs() {
        // 前方基站：安装角度0°，负责-60°~60°（正面120°）
        baseStationConfigs.put(FRONT_BASE_ID, new BaseStationConfig(
                ROBOT_FRONT_OFFSET, 0.0, 0.0, R.id.tv_front_base, 0.9, 0.3,
                -60, 60
        ));

        // 后方基站：安装角度180°，负责120°~240°（归一化后120°~180°/-180°~-120°）
        baseStationConfigs.put(REAR_BASE_ID, new BaseStationConfig(
                -ROBOT_REAR_OFFSET, 0.0, 180.0, R.id.tv_rear_base, 0.8, 0.3,
                120, 240
        ));

        // 左侧基站：安装角度90°，负责30°~150°（左侧120°）
        baseStationConfigs.put(LEFT_BASE_ID, new BaseStationConfig(
                0.0, ROBOT_SIDE_OFFSET, 90.0, R.id.tv_left_base, 0.7, 0.3,
                30, 150
        ));

        // 右侧基站：安装角度-90°，负责-150°~-30°（右侧120°）
        baseStationConfigs.put(RIGHT_BASE_ID, new BaseStationConfig(
                0.0, -ROBOT_SIDE_OFFSET, -90.0, R.id.tv_right_base, 0.7, 0.3,
                -150, -30
        ));
    }

    public boolean isFollowing() {
        return isFollowing;
    }

    public BaseStationConfig getBaseStationConfig(long anchorId) {
        return baseStationConfigs.get(anchorId);
    }

    public void startFollowing() {
        if (isFollowing) return;
        isFollowing = true;

        // 重置计数器
        uwbPacketCount = 0;
        lastPacketTime = System.currentTimeMillis();
        packetRate = 0;
        if (listener != null) {
            listener.onPacketStatsUpdate(uwbPacketCount, packetRate);
        }

        UsbSerialHelper.UwbDataListener dataListener = (anchorId, tagId, distance, azimuth, elevation) -> {
            uwbPacketCount++;
            long currentTime = System.currentTimeMillis();
            if (lastPacketTime != 0) {
                float elapsed = (currentTime - lastPacketTime) / 1000.0f;
                packetRate = (packetRate * 0.9f) + (1.0f / elapsed * 0.1f);
            }
            lastPacketTime = currentTime;

            // 更新数据包统计
            if (listener != null) {
                listener.onPacketStatsUpdate(uwbPacketCount, packetRate);
            }

            // 在主线程处理UWB数据
            handler.post(() -> handleUwbDataUpdate(anchorId, tagId, distance, azimuth, elevation));
        };

        // 启动UWB连接
        usbHelper.open(dataListener);

        // 开始位姿更新
        startPoseUpdater();

        // 开始第一次定位
        isWaitingForMoveCompletion = false;
        processNextPosition();
    }

    public void stopFollowing() {
        if (!isFollowing) return;
        isFollowing = false;

        usbHelper.close();
        cancelCurrentAction();
        handler.removeCallbacksAndMessages(null);
        moveExecutor.shutdown();

        // 清空活跃基站数据
        currentActiveBaseData = null;
        currentActiveBaseId = -1;

        // 通知UI更新
        if (listener != null) {
            listener.onMoveStatusUpdate("已停止");
        }
    }

    public void cleanup() {
        stopFollowing();
    }

    private void handleUwbDataUpdate(long anchorId, long tagId, double distance, double azimuth, double elevation) {
        BaseStationConfig config = baseStationConfigs.get(anchorId);
        if (config == null) {
            Log.e("UWB", "未知基站ID: " + anchorId);
            return;
        }

        // 调整方位角（考虑基站安装角度）
        double adjustedAzimuth = normalizeAngle(azimuth + config.installAngle);

        // 检查是否在该基站的负责范围内
        if (isInBaseStationRange(adjustedAzimuth, anchorId)) {
            // 使用卡尔曼滤波平滑数据
            double filteredDistance = kalmanDistance.filter(distance, distance);
            double filteredAzimuth = kalmanAzimuth.filter(adjustedAzimuth, distance);
            double filteredElevation = kalmanElevation.filter(elevation, distance);

            // 存储基站数据
            UwbData newData = new UwbData(filteredDistance, filteredAzimuth, filteredElevation, System.currentTimeMillis());
            baseStationDataMap.put(anchorId, new BaseStationData(newData, System.currentTimeMillis(), config.confidence));

            // 通知UI更新基站状态
            if (listener != null) {
                listener.onBaseStationUpdate(anchorId, true);
                listener.onUwbDataUpdate(filteredDistance, filteredAzimuth, filteredElevation);
            }

            // 记录详细数据
            logUwbData(anchorId, filteredDistance, filteredAzimuth, filteredElevation);

            // 融合多基站数据
            fuseBaseStationData();

            // 只有在不等待移动完成时才处理新位置
            if (!isWaitingForMoveCompletion) {
                processNextPosition();
            }
        } else {
            // 不在负责范围内，忽略此数据
            Log.d("UWB", "基站 " + anchorId + " 数据不在负责范围内: " + adjustedAzimuth + "°");
        }
    }

    private void fuseBaseStationData() {
        if (baseStationDataMap.isEmpty()) {
            return;
        }

        // 移除过期数据（3秒无更新）
        long currentTime = System.currentTimeMillis();
        baseStationDataMap.entrySet().removeIf(entry ->
                currentTime - entry.getValue().lastUpdateTime > 3000);

        if (baseStationDataMap.isEmpty()) {
            if (listener != null) {
                listener.onMoveStatusUpdate("所有基站数据中断");
            }
            return;
        }

        // 加权平均融合算法
        double totalDistance = 0;
        double totalAzimuth = 0;
        double totalElevation = 0;
        double totalWeight = 0;

        for (Map.Entry<Long, BaseStationData> entry : baseStationDataMap.entrySet()) {
            BaseStationData bsData = entry.getValue();
            UwbData data = bsData.data;
            double weight = bsData.confidence;

            totalDistance += data.distance * weight;
            totalAzimuth += data.azimuth * weight;
            totalElevation += data.elevation * weight;
            totalWeight += weight;
        }

        // 计算加权平均值
        double fusedDistance = totalDistance / totalWeight;
        double fusedAzimuth = totalAzimuth / totalWeight;
        double fusedElevation = totalElevation / totalWeight;

        // 更新当前活跃数据
        currentActiveBaseData = new UwbData(fusedDistance, fusedAzimuth, fusedElevation, currentTime);

        // 使用信号最强的基站作为当前活跃基站
        currentActiveBaseId = getStrongestBaseStation();
    }

    private long getStrongestBaseStation() {
        long strongestId = -1;
        double maxConfidence = 0;

        for (Map.Entry<Long, BaseStationData> entry : baseStationDataMap.entrySet()) {
            if (entry.getValue().confidence > maxConfidence) {
                maxConfidence = entry.getValue().confidence;
                strongestId = entry.getKey();
            }
        }

        return strongestId;
    }

    /**
     * 角度归一化：将任意角度转换到[-180°, 180°]区间
     * @param angle 原始角度（单位：°）
     * @return 归一化后的角度
     */
    private double normalizeAngle(double angle) {
        while (angle > 180) {
            angle -= 360;
        }
        while (angle < -180) {
            angle += 360;
        }
        return angle;
    }

    // 检查角度是否在基站负责范围内
    private boolean isInBaseStationRange(double azimuth, long baseId) {
        // 归一化角度到[-180, 180]范围
        double normalizedAzimuth = normalizeAngle(azimuth);

        // 根据基站ID确定负责的角度范围
        double minAngle, maxAngle;

        switch ((int) baseId) {
            case 0x000002DB: // 前方基站
                minAngle = -60;
                maxAngle = 60;
                break;

            case 0x000002DC: // 后方基站
                // 后方基站负责120°~240°范围
                // 归一化后为120°~180°和-180°~-120°
                return (normalizedAzimuth >= 120 && normalizedAzimuth <= 180) ||
                        (normalizedAzimuth >= -180 && normalizedAzimuth <= -120);

            case 0x000002DD: // 左侧基站
                minAngle = 30;
                maxAngle = 150;
                break;

            case 0x000002DE: // 右侧基站
                minAngle = -150;
                maxAngle = -30;
                break;

            default:
                return false;
        }

        // 处理角度环绕情况
        if (minAngle > maxAngle) {
            return normalizedAzimuth >= minAngle || normalizedAzimuth <= maxAngle;
        } else {
            return normalizedAzimuth >= minAngle && normalizedAzimuth <= maxAngle;
        }
    }

    private void processNextPosition() {
        // 检查是否有活跃基站数据
        if (currentActiveBaseData == null) {
            if (listener != null) {
                listener.onMoveStatusUpdate("等待基站数据...");
            }
            return;
        }

        // 检查数据是否过期（3秒无新数据）
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastActiveBaseTime > 3000) {
            if (listener != null) {
                listener.onMoveStatusUpdate("基站数据中断");
            }
            currentActiveBaseData = null;
            currentActiveBaseId = -1;
            return;
        }

        // 获取机器人当前位姿
        if (cachedPose == null) {
            if (listener != null) {
                listener.onMoveStatusUpdate("等待机器人位姿...");
            }
            return;
        }

        // 使用当前活跃基站的数据计算目标位置
        PositionEstimate newTarget = calculatePosition(currentActiveBaseId, currentActiveBaseData);
        if (newTarget != null) {
            // 检查目标点是否在合理范围内
            if (Math.abs(newTarget.x) > 20 || Math.abs(newTarget.y) > 20) {
                if (listener != null) {
                    listener.onMoveStatusUpdate("目标点超出范围: (" + newTarget.x + ", " + newTarget.y + ")");
                }
                return;
            }

            currentTarget = newTarget;
            moveToTarget(currentTarget);
        }
    }

    private PositionEstimate calculatePosition(long anchorId, UwbData data) {
        BaseStationConfig config = baseStationConfigs.get(anchorId);
        if (config == null) return null;

        // 转换为弧度
        double radian = Math.toRadians(data.azimuth);

        // 计算目标点在基站坐标系中的位置
        double baseX = data.distance * Math.cos(radian);
        double baseY = data.distance * Math.sin(radian);

        // 将基站坐标系转换为机器人坐标系
        // 注意：机器人坐标系是x向前，y向左，原点在机器人中心
        double robotX, robotY;

        switch ((int) anchorId) {
            case 0x000002DB: // 前方基站
                robotX = baseX + config.offsetX;
                robotY = baseY + config.offsetY;
                break;

            case 0x000002DC: // 后方基站
                // 后方基站的数据需要翻转180度
                robotX = -baseX + config.offsetX;
                robotY = -baseY + config.offsetY;
                break;

            case 0x000002DD: // 左侧基站
                // 左侧基站的数据需要旋转90度
                robotX = -baseY + config.offsetX;
                robotY = baseX + config.offsetY;
                break;

            case 0x000002DE: // 右侧基站
                // 右侧基站的数据需要旋转-90度
                robotX = baseY + config.offsetX;
                robotY = -baseX + config.offsetY;
                break;

            default:
                return null;
        }

        // 调试日志
        Log.d("UWB定位", String.format(
                "基站[%d] 距离:%.2fm 角度:%.1f° → 机器人系坐标(%.2f, %.2f)",
                anchorId, data.distance, data.azimuth, robotX, robotY
        ));

        // 更新坐标转换显示
        if (listener != null) {
            listener.onCoordTransformUpdate(baseX, baseY, robotX, robotY);
        }
        return new PositionEstimate(robotX, robotY);
    }

    private void moveToTarget(PositionEstimate target) {
        isWaitingForMoveCompletion = true;
        if (listener != null) {
            listener.onMoveStatusUpdate("发送移动指令...");
        }

        // 获取机器人当前位姿
        if (cachedPose == null) {
            if (listener != null) {
                listener.onMoveStatusUpdate("错误: 未获取到位姿");
            }
            isWaitingForMoveCompletion = false;
            return;
        }

        // 将目标点从机器人坐标系转换为全局坐标系
        double globalX = cachedPose.x + target.x * Math.cos(cachedPose.yaw) - target.y * Math.sin(cachedPose.yaw);
        double globalY = cachedPose.y + target.x * Math.sin(cachedPose.yaw) + target.y * Math.cos(cachedPose.yaw);

        // 更新目标显示
        if (listener != null) {
            listener.onTargetUpdate(globalX, globalY);
        }

        Poi targetPoi = new Poi();
        targetPoi.setX(globalX);
        targetPoi.setY(globalY);
        targetPoi.setYaw(cachedPose.yaw); // 保持当前朝向

        RobotController.createMoveAction(targetPoi, new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String json = responseData.string(StandardCharsets.UTF_8);
                    com.google.gson.JsonObject jsonObject = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                    if (jsonObject.has("action_id")) {
                        mCurrentActionId = jsonObject.get("action_id").getAsString();
                        if (listener != null) {
                            listener.onMoveStatusUpdate("指令发送成功: " + mCurrentActionId);
                        }

                        // 添加任务完成监听
                        startActionStatusPolling(mCurrentActionId);
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onMoveStatusUpdate("解析action_id失败: " + e.getMessage());
                    }
                    mCurrentActionId = null;
                    isWaitingForMoveCompletion = false; // 标记移动结束
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (listener != null) {
                    listener.onMoveStatusUpdate("移动指令失败: " + e.getMessage());
                }
                mCurrentActionId = null;
                isWaitingForMoveCompletion = false; // 标记移动结束
            }
        });
    }

    private void startActionStatusPolling(String actionId) {
        handler.postDelayed(() -> {
            RobotController.pollActionStatus(actionId, new OkHttpUtils.ResponseCallback() {
                @Override
                public void onSuccess(ByteString responseData) {
                    try {
                        String json = responseData.string(StandardCharsets.UTF_8);
                        com.google.gson.JsonObject jsonObject = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                        if (jsonObject.has("state")) {
                            com.google.gson.JsonObject state = jsonObject.getAsJsonObject("state");
                            int status = state.get("status").getAsInt();

                            if (status == 4) { // 任务完成
                                mCurrentActionId = null;
                                if (listener != null) {
                                    listener.onMoveStatusUpdate("移动任务完成");
                                }
                                isWaitingForMoveCompletion = false;

                                // 到达目标点后，获取最新位姿并处理下一个位置
                                updateCachedPose();
                                processNextPosition();
                            } else {
                                // 继续轮询
                                startActionStatusPolling(actionId);
                            }
                        }
                    } catch (Exception e) {
                        Log.e("ActionPoll", "状态解析失败", e);
                        isWaitingForMoveCompletion = false;
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e("ActionPoll", "状态查询失败", e);
                    isWaitingForMoveCompletion = false;
                }
            });
        }, 1000);
    }

    private void cancelCurrentAction() {
        if (mCurrentActionId != null) {
            if (listener != null) {
                listener.onMoveStatusUpdate("取消任务: " + mCurrentActionId);
            }

            RobotController.cancelCurrentAction(new OkHttpUtils.ResponseCallback() {
                @Override
                public void onSuccess(ByteString responseData) {
                    Log.d("MOVE_CANCEL", "任务取消成功");
                    mCurrentActionId = null;
                    if (listener != null) {
                        listener.onMoveStatusUpdate("任务已取消");
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e("MOVE_CANCEL", "任务取消失败: " + e.getMessage());
                    if (listener != null) {
                        listener.onMoveStatusUpdate("取消失败: " + e.getMessage());
                    }
                }
            });
        }
    }

    private void startPoseUpdater() {
        handler.post(poseUpdateRunnable);
    }

    private Runnable poseUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateCachedPose();
            if (isFollowing) {
                handler.postDelayed(this, 100);
            }
        }
    };

    private void updateCachedPose() {
        RobotController.getRobotPose(new RobotController.RobotPoseCallback() {
            @Override
            public void onSuccess(RobotController.RobotPose pose) {
                cachedPose = pose;
                if (listener != null) {
                    listener.onRobotPoseUpdate(pose.x, pose.y, pose.yaw);
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (listener != null) {
                    listener.onMoveStatusUpdate("获取位姿失败: " + e.getMessage());
                }
            }
        });
    }

    private void logUwbData(long anchorId, double distance, double azimuth, double elevation) {
        if (Constants.DEBUG_MODE) {
            String baseStation = "";
            switch ((int) anchorId) {
                case 0x000002DB: baseStation = "前方"; break;
                case 0x000002DC: baseStation = "后方"; break;
                case 0x000002DD: baseStation = "左侧"; break;
                case 0x000002DE: baseStation = "右侧"; break;
            }

            String log = String.format("基站%s: 距离=%.2fm, 方位角=%.1f°, 仰角=%.1f°",
                    baseStation, distance, azimuth, elevation);
            Log.d("UWB_DATA", log);
        }
    }

    private void executeNextMoveTask() {
        if (moveTaskQueue.isEmpty()) {
            isMoving = false;
            return;
        }
        isMoving = true;
        PositionEstimate next = moveTaskQueue.poll();
        moveExecutor.schedule(() -> moveToPoint(next.x, next.y, cachedPose.yaw),
                50, TimeUnit.MILLISECONDS);
    }

    private void moveToPoint(double x, double y, double yaw) {
        if (listener != null) {
            listener.onMoveStatusUpdate("发送移动指令...");
        }

        Poi target = new Poi();
        target.setX(x);
        target.setY(y);
        target.setYaw(yaw);

        RobotController.createMoveAction(target, new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String json = responseData.string(StandardCharsets.UTF_8);
                    com.google.gson.JsonObject jsonObject = new com.google.gson.JsonParser().parse(json).getAsJsonObject();
                    if (jsonObject.has("action_id")) {
                        mCurrentActionId = jsonObject.get("action_id").getAsString();
                        if (listener != null) {
                            listener.onMoveStatusUpdate("指令发送成功: " + mCurrentActionId);
                        }

                        // 添加任务完成监听
                        startActionStatusPolling(mCurrentActionId);
                    }
                } catch (Exception e) {
                    if (listener != null) {
                        listener.onMoveStatusUpdate("解析action_id失败: " + e.getMessage());
                    }
                    mCurrentActionId = null;
                    isMoving = false; // 标记移动结束
                    executeNextMoveTask(); // 执行下一个任务
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (listener != null) {
                    listener.onMoveStatusUpdate("移动指令失败: " + e.getMessage());
                }
                mCurrentActionId = null;
                isMoving = false; // 标记移动结束
                executeNextMoveTask(); // 执行下一个任务
            }
        });
    }
}

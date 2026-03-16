package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.silan.robotpeisongcontrl.adapter.UsbSerialHelper;
import com.silan.robotpeisongcontrl.model.Poi;
import java.util.concurrent.locks.ReentrantLock;
import okio.ByteString;

public class FollowModeManager {
    private static final String TAG = "FollowManager_Step";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private enum State {IDLE, CALCULATING, MOVING}
    private State currentState = State.IDLE;

    private static final long FRONT_BASE_ID = 0x000002DC;
    private static final double SAFE_STOP_DIST = 0.8;
    private static final double MAX_FOLLOW_DIST = 5.0;
    private static final int SAMPLE_COUNT_TARGET = 5;
    private static final long MOVE_TIMEOUT_MS = 15000;
    private static final double ARRIVE_DIST = 0.4;
    private static final long STABILIZE_DELAY_MS = 1000;

    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean isFollowing = false;
    private volatile RobotController.RobotPose currentRobotPose;

    private final Context context;
    private UsbSerialHelper usbHelper;

    private double sumForward = 0, sumLeft = 0;
    private int currentSampleCount = 0;

    public FollowModeManager(Context context) {
        this.context = context.getApplicationContext();
    }

    private void startUwbReceiver() {
        if (usbHelper != null) {
            usbHelper.close();
            usbHelper = null;
            try { Thread.sleep(50); } catch (Exception ignored) {}
        }
        usbHelper = new UsbSerialHelper(context.getApplicationContext());
        usbHelper.open((anchorId, tagId, distance, azimuth, elevation) -> {
            if (anchorId == FRONT_BASE_ID) {
                // 维持你的逻辑：使用 elevation 作为水平角
                onUwbDataReceived(distance, elevation);
            }
        });
        Log.d(TAG, "UWB 已开启 → 开始采点");
    }

    private void stopUwbReceiver() {
        if (usbHelper != null) {
            usbHelper.close();
            usbHelper = null;
            try { Thread.sleep(80); } catch (Exception ignored) {}
        }
        Log.d(TAG, "UWB 已关闭 → 移动中不采点");
    }

    public void start(RobotController.RobotPose initialPose) {
        lock.lock();
        try {
            this.currentRobotPose = initialPose;
            this.isFollowing = true;
            this.currentState = State.CALCULATING;
            resetSampling();
            Log.i(TAG, "步进跟随启动 → 初始点: (" + initialPose.x + ", " + initialPose.y + ")");
            startUwbReceiver();
        } finally {
            lock.unlock();
        }
    }

    private void onUwbDataReceived(double dist, double horizontalAngleDeg) {
        if (!isFollowing || dist <= 0 || dist > MAX_FOLLOW_DIST) return;

        lock.lock();
        try {
            if (currentState == State.CALCULATING) {
                double angleRad = Math.toRadians(horizontalAngleDeg);
                double forward = dist * Math.cos(angleRad);
                double left    = dist * Math.sin(angleRad);

                sumForward += forward;
                sumLeft += left;
                currentSampleCount++;

                if (currentSampleCount >= SAMPLE_COUNT_TARGET) {
                    double avgF = sumForward / currentSampleCount;
                    double avgL = sumLeft / currentSampleCount;

                    if (Math.hypot(avgF, avgL) > SAFE_STOP_DIST) {
                        stopUwbReceiver();
                        dispatchMoveCommand(avgF, avgL);
                    } else {
                        resetSampling();
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void dispatchMoveCommand(double dForward, double dLeft) {
        lock.lock();
        RobotController.RobotPose pose;
        try {
            pose = this.currentRobotPose;
        } finally {
            lock.unlock();
        }

        if (pose == null) {
            resetSampling();
            startUwbReceiver();
            return;
        }

        currentState = State.MOVING;
        double yaw = pose.yaw;

        // ====================== ✅ 彻底修正：适配 [左X+, 后Y+, -1.57前] 的旋转矩阵 ======================
        // 这个公式是标准右手系旋转矩阵，完美适配你定义的坐标轴方向
        double dxGlobal = dForward * Math.cos(yaw) - dLeft * Math.sin(yaw);
        double dyGlobal = dForward * Math.sin(yaw) + dLeft * Math.cos(yaw);

        double gX = pose.x + dxGlobal;
        double gY = pose.y + dyGlobal;

        Poi target = new Poi();
        target.setX(gX);
        target.setY(gY);
        target.setYaw((float) Math.toDegrees(yaw));

        Log.i(TAG, String.format("生成任务：基于当前点(%.4f, %.4f) -> 下发全局(%.4f, %.4f) | 相对(前%.2f, 左%.2f) | Yaw: %.4f",
                pose.x, pose.y, gX, gY, dForward, dLeft, yaw));

        RobotController.createMoveAction(context, target, new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                startArrivalMonitor(target);
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "下发任务失败");
                lock.lock();
                try {
                    currentState = State.CALCULATING;
                    resetSampling();
                } finally { lock.unlock(); }
                startUwbReceiver();
            }
        });
    }

    private void startArrivalMonitor(Poi target) {
        new Thread(() -> {
            long start = System.currentTimeMillis();
            while (isFollowing && currentState == State.MOVING) {
                try {
                    Thread.sleep(300);
                    lock.lock();
                    try {
                        if (currentRobotPose == null) continue;
                        double dx = currentRobotPose.x - target.getX();
                        double dy = currentRobotPose.y - target.getY();
                        double dist = Math.hypot(dx, dy);

                        if (dist < ARRIVE_DIST || System.currentTimeMillis() - start > MOVE_TIMEOUT_MS) {
                            Log.i(TAG, String.format("到达目标点周围(剩余距离:%.2f)，准备同步最新位姿...", dist));
                            currentState = State.IDLE;
                            break;
                        }
                    } finally {
                        lock.unlock();
                    }
                } catch (Exception e) { break; }
            }

            // ====================== ✅ 到达后：使用你新改好的 getRobotPose 接口 ======================
            mainHandler.postDelayed(() -> {
                RobotController.getRobotPose(new RobotController.RobotPoseCallback() {
                    @Override
                    public void onSuccess(RobotController.RobotPose newPose) {
                        lock.lock();
                        try {
                            // 🔥 核心修复：这里 newPose 拿到了你提供的 (0.077, 0.004) 等真实数据
                            currentRobotPose = newPose;

                            currentState = State.CALCULATING;
                            resetSampling();
                            startUwbReceiver();
                            Log.i(TAG, String.format("✅ 位姿同步成功：新基准点(%.4f, %.4f) Yaw:%.4f",
                                    newPose.x, newPose.y, newPose.yaw));
                        } finally {
                            lock.unlock();
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "位姿同步失败，保持旧位姿重试");
                        lock.lock();
                        try {
                            currentState = State.CALCULATING;
                            resetSampling();
                            startUwbReceiver();
                        } finally {
                            lock.unlock();
                        }
                    }
                });
            }, STABILIZE_DELAY_MS);

        }).start();
    }

    public void stop() {
        isFollowing = false;
        currentState = State.IDLE;
        stopUwbReceiver();
        cancelMove();
    }

    public void updatePose(RobotController.RobotPose pose) {
        lock.lock();
        try { this.currentRobotPose = pose; } finally { lock.unlock(); }
    }

    public boolean isFollowing() { return isFollowing; }

    private void resetSampling() {
        sumForward = 0;
        sumLeft = 0;
        currentSampleCount = 0;
    }

    private void cancelMove() {
        RobotController.cancelCurrentAction(new OkHttpUtils.ResponseCallback() {
            @Override public void onSuccess(ByteString responseData) {}
            @Override public void onFailure(Exception e) {}
        });
    }
}
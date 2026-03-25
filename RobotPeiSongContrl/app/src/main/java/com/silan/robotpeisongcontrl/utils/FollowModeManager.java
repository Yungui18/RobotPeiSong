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

    private enum State {IDLE, FOLLOWING}
    private State currentState = State.IDLE;

    private static final long ID_FRONT = 0x000002DC;
    private static final long ID_BACK  = 0x000002DB;
    private static final long ID_LEFT  = 0x000002DD;
    private static final long ID_RIGHT = 0x000002DE;

    // ====================== 【你要的核心参数】 ======================
    private static final double ARRIVE_DISTANCE = 0.8;    // 小于0.8m = 到达
    private static final double RESTART_DISTANCE = 1.5;  // 大于1.5m = 重新跟随
    private static final double MAX_FOLLOW_DIST = 5.0;

    private static final int SAMPLE_INTERVAL = 3;
    private static final long UPDATE_TARGET_MS = 3000;
    private static final double YAW_TOLERANCE = Math.toRadians(5.0);

    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean isFollowing = false;
    private volatile RobotController.RobotPose currentRobotPose;

    private final Context context;
    private UsbSerialHelper usbHelper;

    private double sumForward = 0, sumLeft = 0;
    private int currentSampleCount = 0;

    private long lastBatchTime = 0;
    private double bestDist = Double.MAX_VALUE;
    private double bestF = 0, bestL = 0;
    private long lastUpdateTargetTime = 0;

    // 🔥 静止标记：一旦对准静止，就不再发任何指令
    private boolean isStayStill = false;

    public FollowModeManager(Context context) {
        this.context = context.getApplicationContext();
    }

    private void startUwb() {
        if (usbHelper != null) {
            usbHelper.close();
            usbHelper = null;
            try { Thread.sleep(50); } catch (Exception ignored) {}
        }
        usbHelper = new UsbSerialHelper(context);
        usbHelper.open((anchorId, tagId, dist, az, angle) -> {
            if (isFollowing) processAnchor(anchorId, dist, angle);
        });
    }

    private void stopUwb() {
        if (usbHelper != null) {
            usbHelper.close();
            usbHelper = null;
        }
    }

    private void processAnchor(long id, double dist, double angleDeg) {
        if (dist <= 0 || dist > MAX_FOLLOW_DIST || Math.abs(angleDeg) > 85) return;

        double rad = Math.toRadians(angleDeg);
        double f = 0, l = 0;

        if (id == ID_FRONT) { f = dist * Math.cos(rad); l = dist * Math.sin(rad); }
        else if (id == ID_BACK) { f = -dist * Math.cos(rad); l = -dist * Math.sin(rad); }
        else if (id == ID_LEFT) { f = -dist * Math.sin(rad); l = dist * Math.cos(rad); }
        else if (id == ID_RIGHT) { f = dist * Math.sin(rad); l = -dist * Math.cos(rad); }
        else return;

        long now = System.currentTimeMillis();
        lock.lock();
        try {
            if (now - lastBatchTime > 50) {
                if (bestDist != Double.MAX_VALUE) addSample(bestF, bestL);
                lastBatchTime = now;
                bestDist = dist;
                bestF = f;
                bestL = l;
            } else {
                if (dist < bestDist) {
                    bestDist = dist;
                    bestF = f;
                    bestL = l;
                }
            }
        } finally { lock.unlock(); }
    }

    private void addSample(double f, double l) {
        lock.lock();
        try {
            if (currentState != State.FOLLOWING) return;

            sumForward += f;
            sumLeft += l;
            currentSampleCount++;

            if (currentSampleCount >= SAMPLE_INTERVAL) {
                double avgF = sumForward / currentSampleCount;
                double avgL = sumLeft / currentSampleCount;
                double totalDist = Math.hypot(avgF, avgL);

                // ==============================================
                // 🔥 核心逻辑：到达静止 / 远离重启
                // ==============================================
                if (isStayStill) {
                    // 已经静止 → 只有超过1.5m才恢复行走
                    if (totalDist > RESTART_DISTANCE) {
                        Log.d(TAG, "目标远离 >1.5m → 恢复跟随");
                        isStayStill = false;
                    } else {
                        // 保持静止，什么都不做！
                        resetSampling();
                        return;
                    }
                } else {
                    // 未静止：小于0.8m → 进入对准+静止
                    if (totalDist < ARRIVE_DISTANCE) {
                        boolean ok = faceTarget(avgF, avgL);
                        if (ok) {
                            cancelMove();
                            isStayStill = true;
                            Log.d(TAG, "✅ 到达0.8m内，对准完成 → 保持静止");
                        }
                        resetSampling();
                        return;
                    }
                }

                // 正常跟随行走
                long now = System.currentTimeMillis();
                if (now - lastUpdateTargetTime > UPDATE_TARGET_MS) {
                    updateTarget(avgF, avgL);
                    lastUpdateTargetTime = now;
                }
                resetSampling();
            }
        } finally { lock.unlock(); }
    }

    private void updateTarget(double forward, double left) {
        RobotController.RobotPose pose = currentRobotPose;
        if (pose == null || isStayStill) return;

        double yaw = pose.yaw;
        double dx = forward * Math.cos(yaw) - left * Math.sin(yaw);
        double dy = forward * Math.sin(yaw) + left * Math.cos(yaw);

        Poi target = new Poi();
        target.setX(pose.x + dx);
        target.setY(pose.y + dy);
        target.setYaw((float) Math.toDegrees(yaw));

        RobotController.createMoveAction(context, target, new OkHttpUtils.ResponseCallback() {
            @Override public void onSuccess(ByteString body) {}
            @Override public void onFailure(Exception e) {}
        });
    }

    // 只对准一次，成功返回true
    private boolean faceTarget(double forward, double left) {
        RobotController.RobotPose pose = currentRobotPose;
        if (pose == null) return true;

        double rel = Math.atan2(left, forward);
        double wantYaw = pose.yaw + rel;

        double err = wantYaw - pose.yaw;
        while (err > Math.PI) err -= 2 * Math.PI;
        while (err < -Math.PI) err += 2 * Math.PI;

        if (Math.abs(err) < YAW_TOLERANCE) {
            return true;
        }

        Poi t = new Poi();
        t.setX(pose.x);
        t.setY(pose.y);
        t.setYaw((float) Math.toDegrees(wantYaw));

        RobotController.createMoveAction(context, t, new OkHttpUtils.ResponseCallback() {
            @Override public void onSuccess(ByteString b) {}
            @Override public void onFailure(Exception e) {}
        });
        return false;
    }

    public void start(RobotController.RobotPose initialPose) {
        lock.lock();
        try {
            currentRobotPose = initialPose;
            isFollowing = true;
            currentState = State.FOLLOWING;
            isStayStill = false;
            resetSampling();
            startUwb();
            Log.i(TAG, "✅ 跟随启动");
        } finally { lock.unlock(); }
    }

    public void stop() {
        lock.lock();
        try {
            isFollowing = false;
            currentState = State.IDLE;
            isStayStill = false;
            cancelMove();
            stopUwb();
            Log.i(TAG, "⏹️ 跟随停止");
        } finally { lock.unlock(); }
    }

    public void updatePose(RobotController.RobotPose pose) {
        lock.lock();
        try { this.currentRobotPose = pose; }
        finally { lock.unlock(); }
    }

    private void resetSampling() {
        sumForward = 0;
        sumLeft = 0;
        currentSampleCount = 0;
        bestDist = Double.MAX_VALUE;
    }

    private void cancelMove() {
        RobotController.cancelCurrentAction(new OkHttpUtils.ResponseCallback() {
            @Override public void onSuccess(ByteString b) {}
            @Override public void onFailure(Exception e) {}
        });
    }

    public boolean isFollowing() {
        return isFollowing;
    }
}
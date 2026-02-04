package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.silan.robotpeisongcontrl.fragments.BasicSettingsFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DoorStateManager {
    private static DoorStateManager instance;
    private Set<Integer> openedDoors = new CopyOnWriteArraySet<>();
    private Context context;
    private final List<BasicSettingsFragment.DoorInfo> enabledDoors;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private static final int CLOSE_INTERVAL_MS = 500;

    // 单点配送当前打开的仓门ID（互斥用）
    private Integer singleOpenedDoorId = null;

    // ===== 新增：多仓门开门队列核心变量（解决并发冲突）=====
    private final Queue<Integer> openDoorQueue = new LinkedList<>(); // 开门指令队列
    private static final int OPEN_SEND_INTERVAL = 300; // 指令发送间隔300ms，适配硬件/串口处理
    private boolean isSendingOpen = false; // 标记是否正在发送开门指令
    private static final int DOOR_OPEN_DELAY = 1000; // 仓门打开状态更新延迟（等待硬件执行）

    // ===== 新增：仓门状态监听接口 =====
    public interface OnDoorStateChangeListener {
        void onDoorOpened(int hardwareId); // 仓门打开回调
        void onDoorClosed(int hardwareId); // 仓门关闭回调
    }

    private OnDoorStateChangeListener doorStateChangeListener; // 监听实例

    // ===== 新增：设置监听器的方法 =====
    public void setOnDoorStateChangeListener(OnDoorStateChangeListener listener) {
        this.doorStateChangeListener = listener;
    }

    private DoorStateManager(Context context) {
        this.context = context.getApplicationContext();
        this.enabledDoors = BasicSettingsFragment.getEnabledDoors(context);
    }

    public static synchronized DoorStateManager getInstance(Context context) {
        if (instance == null) {
            instance = new DoorStateManager(context);
        }
        return instance;
    }

    // 单点配送打开仓门（互斥校验）- 原有方法，完全保留
    public boolean openSingleDoor(int doorId) {
        if (singleOpenedDoorId != null && singleOpenedDoorId != doorId) {
            Log.d("DoorStateManager", "单点配送已有仓门打开：" + singleOpenedDoorId + "，无法打开" + doorId);
            return false;
        }
        if (singleOpenedDoorId != null && singleOpenedDoorId == doorId) {
            closeDoor(doorId);
            singleOpenedDoorId = null;
            return true;
        }
        openDoor(doorId);
        singleOpenedDoorId = doorId;
        return true;
    }

    // 原有方法，完全保留
    public Integer getSingleOpenedDoorId() {
        return singleOpenedDoorId;
    }

    // 原有方法，完全保留
    public void clearSingleOpenedDoor() {
        singleOpenedDoorId = null;
    }

    // 原有方法，完全保留
    public void addOpenedDoor(int doorId) {
        boolean added = openedDoors.add(doorId);
        Log.d("DoorStateManager", "添加打开仓门: " + doorId + " | 成功: " + added + " | 当前打开集合: " + openedDoors);
        // 通知仓门打开
        notifyDoorOpened(doorId);
    }

    // 原有方法，完全保留
    public void removeClosedDoor(int doorId) {
        boolean removed = openedDoors.remove(doorId);
        Log.d("DoorStateManager", "移除关闭仓门: " + doorId + " | 成功: " + removed + " | 当前打开集合: " + openedDoors);
        if (singleOpenedDoorId != null && singleOpenedDoorId == doorId) {
            singleOpenedDoorId = null;
        }
        // 通知仓门关闭
        notifyDoorClosed(doorId);
    }

    // 原有方法，完全保留
    private void notifyDoorOpened(int hardwareId) {
        if (doorStateChangeListener != null) {
            mHandler.post(() -> doorStateChangeListener.onDoorOpened(hardwareId));
        }
    }

    // 原有方法，完全保留
    private void notifyDoorClosed(int hardwareId) {
        if (doorStateChangeListener != null) {
            mHandler.post(() -> doorStateChangeListener.onDoorClosed(hardwareId));
        }
    }

    // 原有方法，完全保留
    public Set<Integer> getOpenedDoors() {
        return new CopyOnWriteArraySet<>(openedDoors);
    }

    // 原有方法，完全保留
    public boolean isDoorOpened(int doorId) {
        boolean isOpened = openedDoors.contains(doorId);
        Log.d("DoorStateManager", "检查仓门[" + doorId + "]是否已打开：" + isOpened);
        return isOpened;
    }

    // 原有方法，完全保留
    public void closeAllOpenedDoors() {
        Log.d("DoorStateManager", "开始关闭所有仓门 | 待关闭列表: " + openedDoors);
        if (openedDoors.isEmpty()) {
            Log.d("DoorStateManager", "无打开的仓门，无需关闭");
            clearSingleOpenedDoor();
            return;
        }

        List<Integer> doorsToClose = new ArrayList<>(openedDoors);
        openedDoors.clear();
        clearSingleOpenedDoor();

        for (int i = 0; i < doorsToClose.size(); i++) {
            final int doorId = doorsToClose.get(i);
            final int delayMs = i * CLOSE_INTERVAL_MS;

            mHandler.postDelayed(() -> {
                Log.d("DoorStateManager", "延迟" + delayMs + "ms，关闭仓门: " + doorId);
                closeDoor(doorId);
            }, delayMs);
        }
    }

    // ===== 核心修改：openDoor方法（队列+延迟发送，解决多仓门并发）=====
    public void openDoor(int doorId) {
        if (isDoorOpened(doorId)) {
            Log.d("DoorStateManager", "仓门[" + doorId + "]已打开，跳过重复打开操作");
            return;
        }

        Log.d("DoorStateManager", "仓门[" + doorId + "]加入开门队列，等待发送指令");
        openDoorQueue.offer(doorId); // 指令入队，不直接发送
        if (!isSendingOpen) {
            startSendQueue(); // 未在发送时，启动队列处理
        }
    }

    // ===== 新增：处理开门队列，按间隔依次发送指令 =====
    private void startSendQueue() {
        if (openDoorQueue.isEmpty()) {
            isSendingOpen = false;
            Log.d("DoorStateManager", "开门队列为空，停止发送");
            return;
        }

        isSendingOpen = true;
        final int currentDoorId = openDoorQueue.poll(); // 取出队列头部指令
        Log.d("DoorStateManager", "开始处理队列：发送仓门[" + currentDoorId + "]开门指令");

        // 1. 获取仓门控制器，发送开门指令
        DoorController controller = getDoorController(currentDoorId);
        if (controller != null) {
            controller.open(); // 发送硬件开门指令
            Log.d("DoorStateManager", "仓门[" + currentDoorId + "]开门指令已发送，等待硬件执行");

            // 2. 延迟更新打开状态（等待硬件实际执行，避免状态超前）
            mHandler.postDelayed(() -> {
                addOpenedDoor(currentDoorId); // 延迟1秒添加到打开集合
                // 3. 间隔300ms后处理下一个队列指令
                mHandler.postDelayed(this::startSendQueue, OPEN_SEND_INTERVAL);
            }, DOOR_OPEN_DELAY);
        } else {
            Log.e("DoorStateManager", "仓门[" + currentDoorId + "]打开失败：未找到控制器");
            // 无控制器时，直接处理下一个指令
            mHandler.postDelayed(this::startSendQueue, OPEN_SEND_INTERVAL);
        }
    }

    // 原有方法，完全保留
    public void closeDoor(int doorId) {
        Log.d("DoorStateManager", "请求关闭仓门: " + doorId);
        DoorController controller = getDoorController(doorId);
        if (controller != null) {
            controller.close();
            removeClosedDoor(doorId);
        } else {
            Log.e("DoorStateManager", "关闭失败：未找到仓门" + doorId + "的控制器");
        }
    }

    // 原有方法，完全保留
    private DoorController getDoorController(int hardwareId) {
        BasicSettingsFragment.DoorInfo doorInfo = getDoorInfoByHardwareId(hardwareId);
        if (doorInfo == null) {
            return null;
        }
        return DoorControllerFactory.createDoorController(
                context,
                doorInfo.getType(),
                hardwareId
        );
    }

    // 原有方法，完全保留
    private BasicSettingsFragment.DoorInfo getDoorInfoByHardwareId(int hardwareId) {
        for (BasicSettingsFragment.DoorInfo info : enabledDoors) {
            if (info.getHardwareId() == hardwareId) {
                Log.d("DoorStateManager", "匹配到仓门：ID=" + hardwareId + ", 类型=" + info.getType());
                return info;
            }
        }
        Log.e("DoorStateManager", "未找到硬件ID为" + hardwareId + "的仓门信息");
        return null;
    }
}
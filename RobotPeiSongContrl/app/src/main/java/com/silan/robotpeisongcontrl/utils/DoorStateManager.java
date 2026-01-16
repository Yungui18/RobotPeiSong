package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.silan.robotpeisongcontrl.fragments.BasicSettingsFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    // 单点配送打开仓门（互斥校验）
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

    public Integer getSingleOpenedDoorId() {
        return singleOpenedDoorId;
    }

    public void clearSingleOpenedDoor() {
        singleOpenedDoorId = null;
    }

    public void addOpenedDoor(int doorId) {
        boolean added = openedDoors.add(doorId);
        Log.d("DoorStateManager", "添加打开仓门: " + doorId + " | 成功: " + added + " | 当前打开集合: " + openedDoors);
        // ===== 新增：通知仓门打开 =====
        notifyDoorOpened(doorId);
    }

    public void removeClosedDoor(int doorId) {
        boolean removed = openedDoors.remove(doorId);
        Log.d("DoorStateManager", "移除关闭仓门: " + doorId + " | 成功: " + removed + " | 当前打开集合: " + openedDoors);
        if (singleOpenedDoorId != null && singleOpenedDoorId == doorId) {
            singleOpenedDoorId = null;
        }
        // ===== 新增：通知仓门关闭 =====
        notifyDoorClosed(doorId);
    }

    // ===== 新增：分发仓门打开事件 =====
    private void notifyDoorOpened(int hardwareId) {
        if (doorStateChangeListener != null) {
            // 确保在主线程更新UI（避免线程异常）
            mHandler.post(() -> doorStateChangeListener.onDoorOpened(hardwareId));
        }
    }

    // ===== 新增：分发仓门关闭事件 =====
    private void notifyDoorClosed(int hardwareId) {
        if (doorStateChangeListener != null) {
            // 确保在主线程更新UI（避免线程异常）
            mHandler.post(() -> doorStateChangeListener.onDoorClosed(hardwareId));
        }
    }

    public Set<Integer> getOpenedDoors() {
        return new CopyOnWriteArraySet<>(openedDoors);
    }

    public boolean isDoorOpened(int doorId) {
        boolean isOpened = openedDoors.contains(doorId);
        Log.d("DoorStateManager", "检查仓门[" + doorId + "]是否已打开：" + isOpened);
        return isOpened;
    }

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

    public void openDoor(int doorId) {
        if (isDoorOpened(doorId)) {
            Log.d("DoorStateManager", "仓门[" + doorId + "]已打开，跳过重复打开操作");
            return;
        }

        Log.d("DoorStateManager", "请求打开仓门: " + doorId);
        DoorController controller = getDoorController(doorId);
        if (controller != null) {
            controller.open();
            addOpenedDoor(doorId);
        } else {
            Log.e("DoorStateManager", "打开失败：未找到仓门" + doorId + "的控制器");
        }
    }

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
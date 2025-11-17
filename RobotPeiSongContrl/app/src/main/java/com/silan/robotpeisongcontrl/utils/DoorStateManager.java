package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DoorStateManager {
    private static DoorStateManager instance;
    private Set<Integer> openedDoors = new CopyOnWriteArraySet<>();
    private Context context;
    // 新增：主线程Handler，用于延迟发送指令
    private Handler mHandler = new Handler(Looper.getMainLooper());
    // 新增：指令间隔时间（500ms，可根据硬件调整）
    private static final int CLOSE_INTERVAL_MS = 500;

    private DoorStateManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized DoorStateManager getInstance(Context context) {
        if (instance == null) {
            instance = new DoorStateManager(context);
        }
        return instance;
    }

    // 记录打开的仓门（不变）
    public void addOpenedDoor(int doorId) {
        boolean added = openedDoors.add(doorId);
        Log.d("DoorStateManager", "添加打开仓门: " + doorId + " | 成功: " + added + " | 当前打开集合: " + openedDoors);
    }

    // 移除已关闭的仓门（不变）
    public void removeClosedDoor(int doorId) {
        boolean removed = openedDoors.remove(doorId);
        Log.d("DoorStateManager", "移除关闭仓门: " + doorId + " | 成功: " + removed + " | 当前打开集合: " + openedDoors);
    }

    // 获取所有打开的仓门（不变）
    public Set<Integer> getOpenedDoors() {
        return new CopyOnWriteArraySet<>(openedDoors);
    }

    // 核心修改：关闭所有仓门（添加间隔延迟）
    public void closeAllOpenedDoors() {
        Log.d("DoorStateManager", "开始关闭所有仓门 | 待关闭列表: " + openedDoors);
        if (openedDoors.isEmpty()) {
            Log.d("DoorStateManager", "无打开的仓门，无需关闭");
            return;
        }

        // 1. 将待关闭的仓门转为有序列表（保证顺序）
        List<Integer> doorsToClose = new ArrayList<>(openedDoors);
        // 2. 清空原集合（避免后续操作干扰）
        openedDoors.clear();

        // 3. 逐个发送关门指令，每个间隔500ms
        for (int i = 0; i < doorsToClose.size(); i++) {
            final int doorId = doorsToClose.get(i);
            // 计算延迟时间：第1个0ms发送，第2个500ms，第3个1000ms...
            final int delayMs = i * CLOSE_INTERVAL_MS;

            // 延迟发送当前仓门的关门指令
            mHandler.postDelayed(() -> {
                Log.d("DoorStateManager", "延迟" + delayMs + "ms，关闭仓门: " + doorId);
                closeDoor(doorId); // 调用原有关闭单个仓门的方法
            }, delayMs);
        }
    }

    // 打开指定仓门（不变）
    public void openDoor(int doorId) {
        Log.d("DoorStateManager", "请求打开仓门: " + doorId);
        DoorController controller = getDoorController(doorId);
        if (controller != null) {
            controller.open();
            addOpenedDoor(doorId);
        } else {
            Log.e("DoorStateManager", "打开失败：未找到仓门" + doorId + "的控制器");
        }
    }

    // 关闭指定仓门（不变）
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

    // 获取对应仓门的控制器（不变）
    private DoorController getDoorController(int doorId) {
        switch (doorId) {
            case 1: return new Door1Controller(context);
            case 2: return new Door2Controller(context);
            case 3: return new Door3Controller(context);
            case 4: return new Door4Controller(context);
            case 5: return new Door5Controller(context);
            case 6: return new Door6Controller(context);
            case 7: return new Door7Controller(context);
            case 8: return new Door8Controller(context);
            case 9: return new Door9Controller(context);
            default:
                Log.e("DoorStateManager", "无效的仓门ID: " + doorId);
                return null;
        }
    }
}
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
    private final List<BasicSettingsFragment.DoorInfo> enabledDoors; // 存储完整仓门信息
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private static final int CLOSE_INTERVAL_MS = 500; // 指令间隔时间（500ms，可根据硬件调整）

    private DoorStateManager(Context context) {
        this.context = context.getApplicationContext();
        // 初始化时获取所有启用的仓门信息（包含类型和硬件ID）
        this.enabledDoors = BasicSettingsFragment.getEnabledDoors(context);
    }

    public static synchronized DoorStateManager getInstance(Context context) {
        if (instance == null) {
            instance = new DoorStateManager(context);
        }
        return instance;
    }

    // 记录打开的仓门
    public void addOpenedDoor(int doorId) {
        boolean added = openedDoors.add(doorId);
        Log.d("DoorStateManager", "添加打开仓门: " + doorId + " | 成功: " + added + " | 当前打开集合: " + openedDoors);
    }

    // 移除已关闭的仓门
    public void removeClosedDoor(int doorId) {
        boolean removed = openedDoors.remove(doorId);
        Log.d("DoorStateManager", "移除关闭仓门: " + doorId + " | 成功: " + removed + " | 当前打开集合: " + openedDoors);
    }

    // 获取所有打开的仓门
    public Set<Integer> getOpenedDoors() {
        return new CopyOnWriteArraySet<>(openedDoors);
    }

    // 判断仓门是否已打开
    public boolean isDoorOpened(int doorId) {
        boolean isOpened = openedDoors.contains(doorId);
        Log.d("DoorStateManager", "检查仓门[" + doorId + "]是否已打开：" + isOpened);
        return isOpened;
    }


    // 关闭所有仓门
    public void closeAllOpenedDoors() {
        Log.d("DoorStateManager", "开始关闭所有仓门 | 待关闭列表: " + openedDoors);
        if (openedDoors.isEmpty()) {
            Log.d("DoorStateManager", "无打开的仓门，无需关闭");
            return;
        }

        List<Integer> doorsToClose = new ArrayList<>(openedDoors);
        openedDoors.clear();

        // 逐个发送关门指令，每个间隔500ms
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

    // 打开指定仓门
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

    // 关闭指定仓门
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

    // 获取对应仓门的控制器
    private DoorController getDoorController(int hardwareId) {
        BasicSettingsFragment.DoorInfo doorInfo = getDoorInfoByHardwareId(hardwareId);
        if (doorInfo == null) {
            return null;
        }
        // 根据仓门信息中的真实类型创建控制器
        return DoorControllerFactory.createDoorController(
                context,
                doorInfo.getType(), // 传真实类型（0=电机，1=电磁锁，2=推杆）
                hardwareId
        );
    }

    // 根据硬件ID获取对应的DoorInfo
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
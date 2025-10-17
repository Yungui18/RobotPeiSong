package com.silan.robotpeisongcontrl.utils;

import android.content.Context;

import java.util.HashSet;
import java.util.Set;

public class DoorStateManager {
    private static DoorStateManager instance;
    private Set<Integer> openedDoors = new HashSet<>();
    private Context context;

    private DoorStateManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized DoorStateManager getInstance(Context context) {
        if (instance == null) {
            instance = new DoorStateManager(context);
        }
        return instance;
    }

    // 记录打开的仓门
    public void addOpenedDoor(int doorId) {
        openedDoors.add(doorId);
    }

    // 移除已关闭的仓门
    public void removeClosedDoor(int doorId) {
        openedDoors.remove(doorId);
    }

    // 获取所有打开的仓门
    public Set<Integer> getOpenedDoors() {
        return new HashSet<>(openedDoors);
    }

    // 关闭所有打开的仓门
    public void closeAllOpenedDoors() {
        for (int doorId : openedDoors) {
            closeDoor(doorId);
        }
        openedDoors.clear();
    }

    // 打开指定仓门
    public void openDoor(int doorId) {
        DoorController controller = getDoorController(doorId);
        if (controller != null) {
            controller.open();
            addOpenedDoor(doorId);
        }
    }

    // 关闭指定仓门
    public void closeDoor(int doorId) {
        DoorController controller = getDoorController(doorId);
        if (controller != null) {
            controller.close();
            removeClosedDoor(doorId);
        }
    }

    // 获取对应仓门的控制器
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
            default: return null;
        }
    }
}

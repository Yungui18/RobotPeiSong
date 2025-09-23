package com.silan.robotpeisongcontrl.utils;

import android.content.Context;

public class DoorControllerFactory {
    public static DoorController createDoorController(Context context, int doorId) {
        switch (doorId) {
            case 1:
                return new Door1Controller(context);
            case 2:
                return new Door2Controller(context);
            case 3:
                return new Door3Controller(context);
            case 4:
                return new Door4Controller(context);
            case 5:
                return new Door5Controller(context);
            case 6:
                return new Door6Controller(context);
            case 7:
                return new Door7Controller(context);
            case 8:
                return new Door8Controller(context);
            case 9:
                return new Door9Controller(context);
            default:
                throw new IllegalArgumentException("无效的仓门ID: " + doorId);
        }
    }
}

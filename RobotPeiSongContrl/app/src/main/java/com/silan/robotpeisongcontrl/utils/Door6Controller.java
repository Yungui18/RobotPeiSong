package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

// 6号仓门控制器（电磁铁控制）
public class Door6Controller extends DoorController {
    // 6号仓门控制寄存器地址
    private static final int DOOR6_CONTROL_REG = 0x43;

    public Door6Controller(Context context) {
        super(context, 6);
    }

    @Override
    public void open() {
        Log.d(TAG, "发送6号仓门开门指令");
        setCurrentState(DoorState.OPENING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR6_CONTROL_REG, OPEN_COMMAND);
    }

    @Override
    public void close() {
        Log.d(TAG, "发送6号仓门关门指令");
        setCurrentState(DoorState.CLOSING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR6_CONTROL_REG, CLOSE_COMMAND);
    }

    @Override
    protected void stopDoorOperation() {
        mSerialPortManager.sendModbusWriteCommand(0x01, 0x25, 0x0000); // 抽屉电磁铁关闭
    }
}

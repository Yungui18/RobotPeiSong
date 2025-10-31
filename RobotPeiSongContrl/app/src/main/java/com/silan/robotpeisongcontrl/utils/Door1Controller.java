package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

public class Door1Controller extends DoorController {
    // 1号仓门控制寄存器地址
    private static final int DOOR1_CONTROL_REG = 0x44;

    public Door1Controller(Context context) {
        super(context, 1);
    }

    @Override
    public void open() {
        Log.d(TAG, "发送1号仓门开门指令");
        setCurrentState(DoorState.OPENING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR1_CONTROL_REG, OPEN_COMMAND);
    }

    @Override
    public void close() {
        Log.d(TAG, "发送1号仓门关门指令");
        setCurrentState(DoorState.CLOSING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR1_CONTROL_REG, CLOSE_COMMAND);
    }

    @Override
    protected void stopDoorOperation() {
        mSerialPortManager.sendModbusWriteCommand(0x01, 0x20, 0x0000); // 直流电机1停止
    }
}

package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

// 5号仓门控制器（推杆电机控制）
public class Door5Controller extends DoorController {
    // 5号仓门控制寄存器地址
    private static final int DOOR5_CONTROL_REG = 0x42;

    public Door5Controller(Context context) {
        super(context, 5);
    }

    @Override
    public void open() {
        Log.d(TAG, "发送5号仓门开门指令");
        setCurrentState(DoorState.OPENING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR5_CONTROL_REG, OPEN_COMMAND);
    }

    @Override
    public void close() {
        Log.d(TAG, "发送5号仓门关门指令");
        setCurrentState(DoorState.CLOSING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR5_CONTROL_REG, CLOSE_COMMAND);
    }

    @Override
    protected void stopDoorOperation() {
        mSerialPortManager.sendModbusWriteCommand(0x01, 0x24, 0x0000); // 推杆电机1停止
    }
}
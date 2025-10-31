package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

// 2号仓门控制器
public class Door2Controller extends DoorController {
    // 2号仓门控制寄存器地址
    private static final int DOOR2_CONTROL_REG = 0x45;

    public Door2Controller(Context context) {
        super(context, 2);
    }

    @Override
    public void open() {
        Log.d(TAG, "发送2号仓门开门指令");
        setCurrentState(DoorState.OPENING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR2_CONTROL_REG, OPEN_COMMAND);
    }

    @Override
    public void close() {
        Log.d(TAG, "发送2号仓门关门指令");
        setCurrentState(DoorState.CLOSING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR2_CONTROL_REG, CLOSE_COMMAND);
    }

    @Override
    protected void stopDoorOperation() {
        mSerialPortManager.sendModbusWriteCommand(0x01, 0x21, 0x0000); // 直流电机2停止
    }

}


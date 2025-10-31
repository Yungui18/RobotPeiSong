package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

// 9号仓门控制器（控制电机1、2、3、4）
public class Door9Controller extends DoorController {
    // 9号仓门控制寄存器地址
    private static final int DOOR9_CONTROL_REG = 0x48;

    public Door9Controller(Context context) {
        super(context, 9);
    }

    @Override
    public void open() {
        Log.d(TAG, "发送9号仓门开门指令");
        setCurrentState(DoorState.OPENING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR9_CONTROL_REG, OPEN_COMMAND);
    }

    @Override
    public void close() {
        Log.d(TAG, "发送9号仓门关门指令");
        setCurrentState(DoorState.CLOSING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR9_CONTROL_REG, CLOSE_COMMAND);
    }

    @Override
    protected void  stopDoorOperation() {
        Log.d(TAG, "停止9号仓门操作（同时停止电机1、2、3、4）");
        mSerialPortManager.sendModbusWriteCommand(0x01, 0x20, 0x0000); // 直流电机1停止
        mSerialPortManager.sendModbusWriteCommand(0x01, 0x21, 0x0000); // 直流电机2停止
        mSerialPortManager.sendModbusWriteCommand(0x01, 0x22, 0x0000); // 直流电机3停止
        mSerialPortManager.sendModbusWriteCommand(0x01, 0x23, 0x0000); // 直流电机4停止
    }
}

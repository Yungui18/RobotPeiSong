package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

// 7号仓门控制器（控制电机1和2）
public class Door7Controller extends DoorController {
    // 7号仓门控制寄存器地址
    private static final int DOOR7_CONTROL_REG = 0x40;

    public Door7Controller(Context context) {
        super(context, 7);
    }

    @Override
    public void open() {
        Log.d(TAG, "发送7号仓门开门指令");
        setCurrentState(DoorState.OPENING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR7_CONTROL_REG, OPEN_COMMAND);
    }

    @Override
    public void close() {
        Log.d(TAG, "发送7号仓门关门指令");
        setCurrentState(DoorState.CLOSING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR7_CONTROL_REG, CLOSE_COMMAND);
    }

    @Override
    protected void stopDoorOperation() {
        Log.d(TAG, "停止7号仓门操作（同时停止电机1和2）");
        mSerialPortManager.sendModbusWriteCommand(0x01, 0x20, 0x0000); // 直流电机1停止
        mSerialPortManager.sendModbusWriteCommand(0x01, 0x21, 0x0000); // 直流电机2停止
    }

    @Override
    public void handleStateData(byte[] data) {
        if (data != null && data.length >= 2) {
            int state = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            switch (state) {
                case OPENING_STATE:
                    setCurrentState(DoorState.OPENING);
                    break;
                case OPENED_STATE:
                    setCurrentState(DoorState.OPENED);
                    break;
                case CLOSING_STATE:
                    setCurrentState(DoorState.CLOSING);
                    break;
                case CLOSED_STATE:
                    setCurrentState(DoorState.CLOSED);
                    break;
                default:
                    Log.d(TAG, "7号仓门收到未知状态码: " + state);
            }
        } else {
            Log.e(TAG, "7号仓门收到无效状态数据");
        }
    }
}

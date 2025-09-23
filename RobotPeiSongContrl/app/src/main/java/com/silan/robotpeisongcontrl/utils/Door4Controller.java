package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

// 4号仓门控制器
public class Door4Controller extends DoorController {
    // 4号仓门控制寄存器地址
    private static final int DOOR4_CONTROL_REG = 0x47;

    public Door4Controller(Context context) {
        super(context, 4);
    }

    @Override
    public void open() {
        Log.d(TAG, "发送4号仓门开门指令");
        setCurrentState(DoorState.OPENING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR4_CONTROL_REG, OPEN_COMMAND);
    }

    @Override
    public void close() {
        Log.d(TAG, "发送4号仓门关门指令");
        setCurrentState(DoorState.CLOSING);
        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR4_CONTROL_REG, CLOSE_COMMAND);
    }

    @Override
    protected void stopDoorOperation() {
        mSerialPortManager.sendModbusWriteCommand(0x01, 0x23, 0x0000); // 直流电机4停止
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
                    Log.d(TAG, "4号仓门收到未知状态码: " + state);
            }
        } else {
            Log.e(TAG, "4号仓门收到无效状态数据");
        }
    }
}

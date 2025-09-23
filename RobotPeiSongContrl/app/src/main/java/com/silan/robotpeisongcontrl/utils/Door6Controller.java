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
                    Log.d(TAG, "6号仓门收到未知状态码: " + state);
            }
        } else {
            Log.e(TAG, "6号仓门收到无效状态数据");
        }
    }
}

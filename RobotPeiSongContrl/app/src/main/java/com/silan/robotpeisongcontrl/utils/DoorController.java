package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

public abstract  class DoorController {
    protected static final String TAG = "DoorController";
    protected Context mContext;
    protected int mDoorId;
    protected DoorState mCurrentState = DoorState.IDLE;
    protected SerialPortManager mSerialPortManager;

    // 统一的控制指令和状态常量（所有仓门通用）
    public static final int OPEN_COMMAND = 0x0100; // 开门
    protected static final int OPENING_STATE = 0x0101; // 开门中
    protected static final int OPENED_STATE = 0x0102; // 开门完成
    public static final int CLOSE_COMMAND = 0x0200; // 关门
    protected static final int CLOSING_STATE = 0x0201; // 关门中
    protected static final int CLOSED_STATE = 0x0202; // 关门完成
    protected static final int IDLE_STATE = 0x0000; // 空闲/初始状态

    // 仓门状态枚举
    public enum DoorState {
        IDLE,      // 空闲/初始
        OPENING,   // 开门中
        OPENED,    // 已打开
        CLOSING,   // 关门中
        CLOSED,    // 已关闭
        PAUSED     // 暂停
    }

    public DoorController(Context context, int doorId) {
        mContext = context;
        mDoorId = doorId;
        mSerialPortManager = SerialPortManager.getInstance();
    }

    public abstract void open();
    public abstract void close();

    public void pause() {
        if (mCurrentState == DoorState.OPENING || mCurrentState == DoorState.CLOSING) {
            stopDoorOperation();
            mCurrentState = DoorState.PAUSED;
            Log.d(TAG, "仓门" + mDoorId + "已暂停");
        }
    }

    protected abstract void stopDoorOperation();

    public DoorState getCurrentState() {
        return mCurrentState;
    }

    public void setCurrentState(DoorState state) {
        mCurrentState = state;
    }

    public void handleStateData(byte[] data) {
        if (data == null || data.length < 2) {
            Log.e(TAG, "仓门" + mDoorId + "状态数据异常，长度不足");
            return;
        }

        // 将2字节byte数组转换为int状态码（Modbus数据为16位）
        int stateValue = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        Log.d(TAG, "仓门" + mDoorId + "收到状态码: 0x" + Integer.toHexString(stateValue));

        // 解析状态码并更新当前状态
        switch (stateValue) {
            case IDLE_STATE:
                mCurrentState = DoorState.IDLE;
                break;
            case OPENING_STATE:
                mCurrentState = DoorState.OPENING;
                break;
            case OPENED_STATE:
                mCurrentState = DoorState.OPENED;
                break;
            case CLOSING_STATE:
                mCurrentState = DoorState.CLOSING;
                break;
            case CLOSED_STATE:
                mCurrentState = DoorState.CLOSED;
                break;
            default:
                // 未知状态明确打印，便于调试
                Log.d(TAG, "仓门" + mDoorId + "收到未知状态码: 0x" + Integer.toHexString(stateValue)
                        + "（十进制：" + stateValue + "）");
                // 未知状态不修改当前状态，避免覆盖有效状态
                return;
        }

        Log.d(TAG, "仓门" + mDoorId + "状态更新为: " + mCurrentState);
    }
}

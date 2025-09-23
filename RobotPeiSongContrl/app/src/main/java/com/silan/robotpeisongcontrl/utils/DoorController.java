package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

public abstract  class DoorController {
    protected static final String TAG = "DoorController";
    protected Context mContext;
    protected int mDoorId;
    protected DoorState mCurrentState = DoorState.CLOSED;
    protected SerialPortManager mSerialPortManager;

    // 统一的控制指令和状态常量（所有仓门通用）
    protected static final int OPEN_COMMAND = 0x0100;
    protected static final int OPENING_STATE = 0x0101;
    protected static final int OPENED_STATE = 0x0102;
    protected static final int CLOSE_COMMAND = 0x0200;
    protected static final int CLOSING_STATE = 0x0201;
    protected static final int CLOSED_STATE = 0x0202;

    // 仓门状态枚举
    public enum DoorState {
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

    protected void setCurrentState(DoorState state) {
        mCurrentState = state;
    }

    public abstract void handleStateData(byte[] data);
}

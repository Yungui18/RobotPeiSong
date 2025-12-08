//package com.silan.robotpeisongcontrl.utils;
//
//import android.content.Context;
//import android.util.Log;
//
//// 3号仓门控制器
//public class Door3Controller extends DoorController {
//    // 3号仓门控制寄存器地址
//    private static final int DOOR3_CONTROL_REG = 0x46;
//
//    public Door3Controller(Context context) {
//        super(context, 3);
//    }
//
//    @Override
//    public void open() {
//        Log.d(TAG, "发送3号仓门开门指令");
//        setCurrentState(DoorState.OPENING);
//        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR3_CONTROL_REG, OPEN_COMMAND);
//    }
//
//    @Override
//    public void close() {
//        Log.d(TAG, "发送3号仓门关门指令");
//        setCurrentState(DoorState.CLOSING);
//        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR3_CONTROL_REG, CLOSE_COMMAND);
//    }
//
//    @Override
//    protected void stopDoorOperation() {
//        mSerialPortManager.sendModbusWriteCommand(0x01, 0x22, 0x0000); // 直流电机3停止
//    }
//}

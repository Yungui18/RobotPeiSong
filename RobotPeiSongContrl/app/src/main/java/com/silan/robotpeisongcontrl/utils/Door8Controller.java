//package com.silan.robotpeisongcontrl.utils;
//
//import android.content.Context;
//import android.util.Log;
//
//// 8号仓门控制器（控制电机3和4）
//public class Door8Controller extends DoorController {
//    // 8号仓门控制寄存器地址
//    private static final int DOOR8_CONTROL_REG = 0x41;
//
//    public Door8Controller(Context context) {
//        super(context, 8);
//    }
//
//    @Override
//    public void open() {
//        Log.d(TAG, "发送8号仓门开门指令");
//        setCurrentState(DoorState.OPENING);
//        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR8_CONTROL_REG, OPEN_COMMAND);
//    }
//
//    @Override
//    public void close() {
//        Log.d(TAG, "发送8号仓门关门指令");
//        setCurrentState(DoorState.CLOSING);
//        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR8_CONTROL_REG, CLOSE_COMMAND);
//    }
//
//    @Override
//    protected void stopDoorOperation() {
//        Log.d(TAG, "停止8号仓门操作（同时停止电机3和4）");
//        mSerialPortManager.sendModbusWriteCommand(0x01, 0x22, 0x0000); // 直流电机3停止
//        mSerialPortManager.sendModbusWriteCommand(0x01, 0x23, 0x0000); // 直流电机4停止
//    }
//}

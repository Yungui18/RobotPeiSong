//package com.silan.robotpeisongcontrl.utils;
//
//import android.content.Context;
//import android.util.Log;
//
//// 7号仓门控制器（控制电机1和2）
//public class Door7Controller extends DoorController {
//    // 7号仓门控制寄存器地址
//    private static final int DOOR7_CONTROL_REG = 0x40;
//
//    public Door7Controller(Context context) {
//        super(context, 7);
//    }
//
//    @Override
//    public void open() {
//        Log.d(TAG, "发送7号仓门开门指令");
//        setCurrentState(DoorState.OPENING);
//        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR7_CONTROL_REG, OPEN_COMMAND);
//    }
//
//    @Override
//    public void close() {
//        Log.d(TAG, "发送7号仓门关门指令");
//        setCurrentState(DoorState.CLOSING);
//        mSerialPortManager.sendModbusWriteCommand(0x01, DOOR7_CONTROL_REG, CLOSE_COMMAND);
//    }
//
//    @Override
//    protected void stopDoorOperation() {
//        Log.d(TAG, "停止7号仓门操作（同时停止电机1和2）");
//        mSerialPortManager.sendModbusWriteCommand(0x01, 0x20, 0x0000); // 直流电机1停止
//        mSerialPortManager.sendModbusWriteCommand(0x01, 0x21, 0x0000); // 直流电机2停止
//    }
//}

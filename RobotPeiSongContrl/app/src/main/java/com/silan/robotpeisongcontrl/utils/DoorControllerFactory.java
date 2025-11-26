package com.silan.robotpeisongcontrl.utils;

import android.content.Context;

public class DoorControllerFactory {
    public static DoorController createDoorController(Context context, int type, int hardwareId) {
        switch (type) {
            case 0: // 电机仓门
                return new MotorDoorController(context, hardwareId);
            case 1: // 电磁锁仓门
                return new ElectromagnetDoorController(context, hardwareId);
            case 2: // 推杆电机仓门
                return new PushRodDoorController(context, hardwareId);
            default:
                throw new IllegalArgumentException("未知仓门类型");
        }
    }

    // 电机仓门控制器
    static class MotorDoorController extends DoorController {
        private int controlReg;

        public MotorDoorController(Context context, int motorId) {
            super(context, motorId);
            // 一键指令寄存器：电机1=0x50，电机2=0x51，电机3=0x52，电机4=0x53
            this.controlReg = 0x50 + (motorId - 1);
        }

        @Override
        public void open() {
            SerialPortManager.getInstance().sendModbusWriteCommand(0x01, controlReg, 0x0100);
        }

        @Override
        public void close() {
            SerialPortManager.getInstance().sendModbusWriteCommand(0x01, controlReg, 0x0200);
        }

        @Override
        public void pause() {
            SerialPortManager.getInstance().sendModbusWriteCommand(0x01, controlReg-20, 0x00);
        }

        @Override
        protected void stopDoorOperation() {

        }
    }

    // 电磁锁仓门控制器
    static class ElectromagnetDoorController extends DoorController {
        private int controlReg;

        public ElectromagnetDoorController(Context context, int lockId) {
            super(context, lockId);
            this.controlReg = 0x58 + (lockId - 1);
        }

        @Override
        public void open() {
            SerialPortManager.getInstance().sendModbusWriteCommand(0x01, controlReg, 0x01
            );
        }

        @Override
        public void close() {
            SerialPortManager.getInstance().sendModbusWriteCommand(0x01, controlReg, 0x00);
        }

        @Override
        public void pause() {
            // 电磁锁无暂停状态
        }

        @Override
        protected void stopDoorOperation() {

        }
    }

    // 推杆电机仓门控制器
    static class PushRodDoorController extends DoorController {
        private static final int CONTROL_REG = 0x57; // 推杆控制寄存器

        public PushRodDoorController(Context context, int id) {
            super(context, id);
        }

        @Override
        public void open() {
            SerialPortManager.getInstance().sendModbusWriteCommand(0x01, CONTROL_REG, 0x0100);
        }

        @Override
        public void close() {
            SerialPortManager.getInstance().sendModbusWriteCommand(0x01, CONTROL_REG, 0x0200);
        }

        @Override
        public void pause() {
            SerialPortManager.getInstance().sendModbusWriteCommand(0x01, CONTROL_REG-20, 0x00);
        }

        @Override
        protected void stopDoorOperation() {

        }
    }


}

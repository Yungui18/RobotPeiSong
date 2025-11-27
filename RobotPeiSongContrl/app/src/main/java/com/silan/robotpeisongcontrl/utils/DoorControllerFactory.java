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
            // 适配单/双仓门的控制寄存器
            if (motorId >= 1 && motorId <= 4) { // 双仓门（独立）
                this.controlReg = 0x50 + (motorId - 1);
            } else if (motorId == 5) { // 单仓门（12舱门）
                this.controlReg = 0x54;
            } else if (motorId == 6) { // 单仓门（34舱门）
                this.controlReg = 0x55;
            } else if (motorId == 7) { // 单仓门（所有舱门）
                this.controlReg = 0x56;
            } else {
                this.controlReg = 0x50; // 默认值
            }
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
            // 分组舱门暂停：独立舱门暂停寄存器=controlReg-20，分组舱门按协议调整
            int pauseReg = (controlReg >= 0x54) ? (controlReg - 4) : (controlReg - 20);
            SerialPortManager.getInstance().sendModbusWriteCommand(0x01, pauseReg, 0x00);
        }

        @Override
        protected void stopDoorOperation() {}
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

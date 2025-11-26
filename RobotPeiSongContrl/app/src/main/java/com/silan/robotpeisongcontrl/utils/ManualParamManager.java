package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class ManualParamManager {
    private static final String PREFS_NAME = "manual_params";// 手动参数
    private static final String FIRST_BOOT_KEY = "first_boot";// 首次开机

    // 直流电机参数键名
    private static final String MOTOR_HIGH_SPEED = "motor_high_speed_%d";// 高速速度
    private static final String MOTOR_LOW_SPEED = "motor_low_speed_%d";//  低速速度
    private static final String MOTOR_HIGH_TIME = "motor_high_time_%d";// 高速时间
    // 新增：旋转方向、正转和反转时间参数键名
    private static final String MOTOR_DIRECTION = "motor_direction_%d";// 旋转方向 0:Normal 1:Reserval
    private static final String MOTOR_FORWARD_DELAY = "motor_forward_delay_%d";// 正转延时时间
    private static final String MOTOR_REVERSE_DELAY = "motor_reverse_delay_%d";// 反转延时时间

    // 推杆电机参数键名
    private static final String PUSHER_HIGH_SPEED = "pusher_high_speed";// 推杆电机高速速度
    private static final String PUSHER_LOW_SPEED = "pusher_low_speed";// 推杆电机低速速度
    private static final String PUSHER_HIGH_TIME = "pusher_high_time";// 推杆电机高速时间
    // 新增：推杆电机旋转方向、正转和反转时间
    private static final String PUSHER_DIRECTION = "pusher_direction";// 旋转方向
    private static final String PUSHER_FORWARD_DELAY = "pusher_forward_delay";// 正转延时
    private static final String PUSHER_REVERSE_DELAY = "pusher_reverse_delay";// 反转延时

    // 固定参数
    public static final int LOCK_CURRENT = 65535; // 3000mA(堵转电流)
    public static final int ELECTROMAGNET_TIME = 1000; // 1s = 1000ms(电磁铁通电触发时间)

    // 默认初始化参数
    private static final int DEFAULT_HIGH_SPEED = 9;
    private static final int DEFAULT_LOW_SPEED = 9;
    private static final int DEFAULT_HIGH_TIME = 60; // 6秒 = 60*100ms(高速时间)
    // 新增：默认旋转方向和延时时间
    private static final int DEFAULT_DIRECTION = 0; // 0:Normal
    private static final int DEFAULT_FORWARD_DELAY = 0; // 默认正转延时
    private static final int DEFAULT_REVERSE_DELAY = 0; // 默认反转延时

    private static ManualParamManager instance;
    private SharedPreferences prefs;

    private ManualParamManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ManualParamManager getInstance(Context context) {
        if (instance == null) {
            instance = new ManualParamManager(context);
        }
        return instance;
    }

    // 检查是否首次开机
    public boolean isFirstBoot() {
        return prefs.getBoolean(FIRST_BOOT_KEY, true);
    }

    // 初始化参数并写入串口
    public void initParams(SerialPortManager serialPortManager) {
        boolean firstBoot = isFirstBoot();

        // 提前打开串口，避免频繁开关
        if (!serialPortManager.openSerialPort()) {
            Log.e("TAG", "串口初始化失败，参数无法发送");
            return;
        }

        // 初始化直流电机1-4参数
        for (int i = 1; i <= 4; i++) {
            int highSpeed = firstBoot ? DEFAULT_HIGH_SPEED : getMotorHighSpeed(i);
            int lowSpeed = firstBoot ? DEFAULT_LOW_SPEED : getMotorLowSpeed(i);
            int highTime = firstBoot ? DEFAULT_HIGH_TIME : getMotorHighTime(i);
            // 新增：初始化旋转方向和延时时间
            int direction = firstBoot ? DEFAULT_DIRECTION : getMotorDirection(i);
            int forwardDelay = firstBoot ? DEFAULT_FORWARD_DELAY : getMotorForwardDelay(i);
            int reverseDelay = firstBoot ? DEFAULT_REVERSE_DELAY : getMotorReverseDelay(i);

            // 保存参数
            saveMotorParams(i, highSpeed, lowSpeed, highTime, direction, forwardDelay, reverseDelay);

            // 发送到串口
            sendMotorParamsToSerial(serialPortManager, i, highSpeed, lowSpeed, highTime, direction, forwardDelay, reverseDelay);

            // 添加延迟，避免连续发送导致指令丢失
            try {
                Thread.sleep(100); // 50ms延迟
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
        }

        // 初始化推杆电机参数
        int pusherHighSpeed = firstBoot ? DEFAULT_HIGH_SPEED : getPusherHighSpeed();
        int pusherLowSpeed = firstBoot ? DEFAULT_LOW_SPEED : getPusherLowSpeed();
        int pusherHighTime = firstBoot ? DEFAULT_HIGH_TIME : getPusherHighTime();
        // 新增：推杆电机方向和延时
        int pusherDirection = firstBoot ? DEFAULT_DIRECTION : getPusherDirection();
        int pusherForwardDelay = firstBoot ? DEFAULT_FORWARD_DELAY : getPusherForwardDelay();
        int pusherReverseDelay = firstBoot ? DEFAULT_REVERSE_DELAY : getPusherReverseDelay();



        // 保存参数
        savePusherParams(pusherHighSpeed, pusherLowSpeed, pusherHighTime, pusherDirection, pusherForwardDelay, pusherReverseDelay);


        // 发送到串口
        sendPusherParamsToSerial(serialPortManager, pusherHighSpeed, pusherLowSpeed, pusherHighTime, pusherDirection, pusherForwardDelay, pusherReverseDelay);

        // 推杆指令发送后添加延迟
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }

        // 发送固定参数
        sendFixedParamsToSerial(serialPortManager);

        // 标记为非首次开机
        if (firstBoot) {
            prefs.edit().putBoolean(FIRST_BOOT_KEY, false).apply();
        }
    }

    // 发送直流电机参数到串口（用0x10批量发送,8位指令发送方向和延时）
    public void sendMotorParamsToSerial(SerialPortManager serialPortManager, int motorId,
                                        int highSpeed, int lowSpeed, int highTime,
                                        int direction, int forwardDelay, int reverseDelay) {
        // 寄存器地址根据电机ID计算
        int baseAddr = 0x00 + (motorId - 1) * 4;
        // 构建数据列表：高速速度、低速速度、高速时间、堵转电流
        int[] dataList = {highSpeed, lowSpeed, highTime, LOCK_CURRENT};
        // 使用0x10功能码批量发送4个寄存器数据
        serialPortManager.sendModbusWriteMultipleRegisters(0x01, baseAddr, dataList);

        try {
            Thread.sleep(100); // 短暂延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        //  发送旋转方向（协议1字节，使用8位指令）
        int directionAddr = 0x18 + (motorId - 1);
        serialPortManager.sendModbusWrite8BitCommand(0x01, directionAddr, direction); // 关键修改：用8位指令
        try { Thread.sleep(50); } catch (InterruptedException e) { e.printStackTrace(); }


        // 新增：发送正转延时时间 (0x1D-0x20)
        int forwardDelayAddr = 0x1D + (motorId - 1);
        serialPortManager.sendModbusWriteCommand(0x01, forwardDelayAddr, forwardDelay);

        try {
            Thread.sleep(100); // 短暂延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 新增：发送反转延时时间 (0x22-0x25)
        int reverseDelayAddr = 0x22 + (motorId - 1);
        serialPortManager.sendModbusWriteCommand(0x01, reverseDelayAddr, reverseDelay);
    }

    // 发送推杆电机参数到串口(用0x10批量发送）
    public void sendPusherParamsToSerial(SerialPortManager serialPortManager,
                                         int highSpeed, int lowSpeed, int highTime,
                                         int direction, int forwardDelay, int reverseDelay) {
        // 发送基本参数（高速、低速、高速时间）
        int[] baseDataList = {highSpeed, lowSpeed, highTime};
        serialPortManager.sendModbusWriteMultipleRegisters(0x01, 0x10, baseDataList);

        try {
            Thread.sleep(100); // 短暂延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 旋转方向（0x1C，8位指令）
        serialPortManager.sendModbusWrite8BitCommand(0x01, 0x1C, direction); // 关键修改
        try { Thread.sleep(100); } catch (InterruptedException e) { e.printStackTrace(); }


        // 新增：发送推杆电机正转延时 (0x21)
        serialPortManager.sendModbusWriteCommand(0x01, 0x21, forwardDelay);

        try {
            Thread.sleep(100); // 短暂延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 新增：发送推杆电机反转延时 (0x26)
        serialPortManager.sendModbusWriteCommand(0x01, 0x26, reverseDelay);
    }

    // 发送固定参数到串口（单寄存器仍用0x06）
    private void sendFixedParamsToSerial(SerialPortManager serialPortManager) {
        // 电磁铁1-4持续时间寄存器：0x14-0x17
        for (int i = 0; i < 4; i++) {
            serialPortManager.sendModbusWriteCommand(0x01, 0x14 + i, ELECTROMAGNET_TIME);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // 保存直流电机参数
    public void saveMotorParams(int motorId, int highSpeed, int lowSpeed, int highTime,
                                int direction, int forwardDelay, int reverseDelay) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(String.format(MOTOR_HIGH_SPEED, motorId), highSpeed);
        editor.putInt(String.format(MOTOR_LOW_SPEED, motorId), lowSpeed);
        editor.putInt(String.format(MOTOR_HIGH_TIME, motorId), highTime);
        editor.putInt(String.format(MOTOR_DIRECTION, motorId), direction);
        editor.putInt(String.format(MOTOR_FORWARD_DELAY, motorId), forwardDelay);
        editor.putInt(String.format(MOTOR_REVERSE_DELAY, motorId), reverseDelay);
        editor.apply();
    }

    // 保存推杆电机参数
    public void savePusherParams(int highSpeed, int lowSpeed, int highTime,
                                 int direction, int forwardDelay, int reverseDelay) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PUSHER_HIGH_SPEED, highSpeed);
        editor.putInt(PUSHER_LOW_SPEED, lowSpeed);
        editor.putInt(PUSHER_HIGH_TIME, highTime);
        editor.putInt(PUSHER_DIRECTION, direction);
        editor.putInt(PUSHER_FORWARD_DELAY, forwardDelay);
        editor.putInt(PUSHER_REVERSE_DELAY, reverseDelay);
        editor.apply();
    }

    // 获取直流电机参数
    public int getMotorHighSpeed(int motorId) {
        return prefs.getInt(String.format(MOTOR_HIGH_SPEED, motorId), DEFAULT_HIGH_SPEED);
    }

    public int getMotorLowSpeed(int motorId) {
        return prefs.getInt(String.format(MOTOR_LOW_SPEED, motorId), DEFAULT_LOW_SPEED);
    }

    public int getMotorHighTime(int motorId) {
        return prefs.getInt(String.format(MOTOR_HIGH_TIME, motorId), DEFAULT_HIGH_TIME);
    }

    // 新增：获取电机旋转方向
    public int getMotorDirection(int motorId) {
        return prefs.getInt(String.format(MOTOR_DIRECTION, motorId), DEFAULT_DIRECTION);
    }

    // 新增：获取电机正转延时
    public int getMotorForwardDelay(int motorId) {
        return prefs.getInt(String.format(MOTOR_FORWARD_DELAY, motorId), DEFAULT_FORWARD_DELAY);
    }

    // 新增：获取电机反转延时
    public int getMotorReverseDelay(int motorId) {
        return prefs.getInt(String.format(MOTOR_REVERSE_DELAY, motorId), DEFAULT_REVERSE_DELAY);
    }


    // 获取推杆电机参数
    public int getPusherHighSpeed() {
        return prefs.getInt(PUSHER_HIGH_SPEED, DEFAULT_HIGH_SPEED);
    }

    public int getPusherLowSpeed() {
        return prefs.getInt(PUSHER_LOW_SPEED, DEFAULT_LOW_SPEED);
    }

    public int getPusherHighTime() {
        return prefs.getInt(PUSHER_HIGH_TIME, DEFAULT_HIGH_TIME);
    }

    // 新增：获取推杆电机方向和延时
    public int getPusherDirection() {
        return prefs.getInt(PUSHER_DIRECTION, DEFAULT_DIRECTION);
    }

    public int getPusherForwardDelay() {
        return prefs.getInt(PUSHER_FORWARD_DELAY, DEFAULT_FORWARD_DELAY);
    }

    public int getPusherReverseDelay() {
        return prefs.getInt(PUSHER_REVERSE_DELAY, DEFAULT_REVERSE_DELAY);
    }
}

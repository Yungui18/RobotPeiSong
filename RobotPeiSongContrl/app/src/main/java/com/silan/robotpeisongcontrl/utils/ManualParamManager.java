package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class ManualParamManager {
    private static final String PREFS_NAME = "manual_params";// 手动参数
    private static final String FIRST_BOOT_KEY = "first_boot";// 首次开机

    // 直流电机参数键名
    private static final String MOTOR_HIGH_SPEED = "motor_high_speed_%d";// 高速速度
    private static final String MOTOR_LOW_SPEED = "motor_low_speed_%d";//  低速速度
    private static final String MOTOR_HIGH_TIME = "motor_high_time_%d";// 高速时间

    // 推杆电机参数键名
    private static final String PUSHER_HIGH_SPEED = "pusher_high_speed";// 推杆电机高速速度
    private static final String PUSHER_LOW_SPEED = "pusher_low_speed";// 推杆电机低速速度
    private static final String PUSHER_HIGH_TIME = "pusher_high_time";// 推杆电机高速时间

    // 固定参数
    public static final int LOCK_CURRENT = 3000; // 3000mA(堵转电流)
    public static final int ELECTROMAGNET_TIME = 1000; // 1s = 1000ms(电磁铁通电触发时间)

    // 默认初始化参数
    private static final int DEFAULT_HIGH_SPEED = 9;
    private static final int DEFAULT_LOW_SPEED = 8;
    private static final int DEFAULT_HIGH_TIME = 60; // 6秒 = 60*100ms(高速时间)

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

        // 初始化直流电机1-4参数
        for (int i = 1; i <= 4; i++) {
            int highSpeed = firstBoot ? DEFAULT_HIGH_SPEED : getMotorHighSpeed(i);
            int lowSpeed = firstBoot ? DEFAULT_LOW_SPEED : getMotorLowSpeed(i);
            int highTime = firstBoot ? DEFAULT_HIGH_TIME : getMotorHighTime(i);

            // 保存参数
            saveMotorParams(i, highSpeed, lowSpeed, highTime);

            // 发送到串口
            sendMotorParamsToSerial(serialPortManager, i, highSpeed, lowSpeed, highTime);

            // 添加延迟，避免连续发送导致指令丢失
            try {
                Thread.sleep(50); // 50ms延迟
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }
        }

        // 初始化推杆电机参数
        int pusherHighSpeed = firstBoot ? DEFAULT_HIGH_SPEED : getPusherHighSpeed();
        int pusherLowSpeed = firstBoot ? DEFAULT_LOW_SPEED : getPusherLowSpeed();
        int pusherHighTime = firstBoot ? DEFAULT_HIGH_TIME : getPusherHighTime();

        // 保存参数
        savePusherParams(pusherHighSpeed, pusherLowSpeed, pusherHighTime);

        // 发送到串口
        sendPusherParamsToSerial(serialPortManager, pusherHighSpeed, pusherLowSpeed, pusherHighTime);

        // 推杆指令发送后添加延迟
        try {
            Thread.sleep(50);
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

    // 发送直流电机参数到串口（用0x10批量发送）
    public void sendMotorParamsToSerial(SerialPortManager serialPortManager, int motorId,
                                        int highSpeed, int lowSpeed, int highTime) {
        // 寄存器地址根据电机ID计算
        int baseAddr = 0x00 + (motorId - 1) * 4;
        // 构建数据列表：高速速度、低速速度、高速时间、堵转电流
        int[] dataList = {highSpeed, lowSpeed, highTime, LOCK_CURRENT};
        // 使用0x10功能码批量发送4个寄存器数据
        serialPortManager.sendModbusWriteMultipleRegisters(0x01, baseAddr, dataList);

    }

    // 发送推杆电机参数到串口(用0x10批量发送）
    public void sendPusherParamsToSerial(SerialPortManager serialPortManager,
                                         int highSpeed, int lowSpeed, int highTime) {
        // 推杆电机连续3个寄存器地址：0x10,0x11,0x12
        int[] dataList = {highSpeed, lowSpeed, highTime};
        serialPortManager.sendModbusWriteMultipleRegisters(0x01, 0x10, dataList);
    }

    // 发送固定参数到串口（单寄存器仍用0x06）
    private void sendFixedParamsToSerial(SerialPortManager serialPortManager) {
        // 发送电磁铁持续通电时间
        serialPortManager.sendModbusWriteCommand(0x01, 0x13, ELECTROMAGNET_TIME);
    }

    // 保存直流电机参数
    public void saveMotorParams(int motorId, int highSpeed, int lowSpeed, int highTime) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(String.format(MOTOR_HIGH_SPEED, motorId), highSpeed);
        editor.putInt(String.format(MOTOR_LOW_SPEED, motorId), lowSpeed);
        editor.putInt(String.format(MOTOR_HIGH_TIME, motorId), highTime);
        editor.apply();
    }

    // 保存推杆电机参数
    public void savePusherParams(int highSpeed, int lowSpeed, int highTime) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(PUSHER_HIGH_SPEED, highSpeed);
        editor.putInt(PUSHER_LOW_SPEED, lowSpeed);
        editor.putInt(PUSHER_HIGH_TIME, highTime);
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
}

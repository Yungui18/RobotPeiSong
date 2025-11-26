package com.silan.robotpeisongcontrl.model;

/**
 * 电机扩展参数模型
 */
public class MotorExtendedParams {
    private int motorType; // 0:直流电机1-4  1:抽屉推杆电机
    private int hardwareId; // 硬件编号（1-4对应直流电机，1对应推杆电机）
    private int rotationDir; // 旋转方向 0:Normal 1:Reserval
    private int forwardDelay; // 正转延时时间（单位：0.02ms）
    private int reverseDelay; // 反转延时时间（单位：0.02ms）

    // 构造方法
    public MotorExtendedParams(int motorType, int hardwareId) {
        this.motorType = motorType;
        this.hardwareId = hardwareId;
        // 默认值
        this.rotationDir = 0; // 默认正向
        this.forwardDelay = 50; // 默认1ms（50 * 0.02ms）
        this.reverseDelay = 50;
    }

    // getter和setter
    public int getRotationDir() { return rotationDir; }
    public void setRotationDir(int rotationDir) { this.rotationDir = rotationDir; }
    public int getForwardDelay() { return forwardDelay; }
    public void setForwardDelay(int forwardDelay) { this.forwardDelay = forwardDelay; }
    public int getReverseDelay() { return reverseDelay; }
    public void setReverseDelay(int reverseDelay) { this.reverseDelay = reverseDelay; }
    public int getMotorType() { return motorType; }
    public int getHardwareId() { return hardwareId; }
}

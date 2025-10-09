package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.Arrays;

public class SerialPortManager {
    private static final String TAG = "SerialPortManager";
    private static SerialPortManager sInstance;
    private SerialPort mSerialPort;
    private OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;
    private OnDataReceivedListener mDataReceivedListener;
    private static final String DEVICE_PATH = "/dev/ttyUSB5"; // 根据实际设备修改
    private static final int BAUDRATE = 9600; // Modbus RTU常用波特率
    private Thread mReceiveThread;
    private volatile boolean mIsStopReceive;

    private SerialPortManager() {}

    public static synchronized SerialPortManager getInstance() {
        if (sInstance == null) {
            sInstance = new SerialPortManager();
        }
        return sInstance;
    }

    /**
     * 打开串口
     */
    public boolean openSerialPort() {
        Log.d("SerialPortDebug", "=== 开始打开串口 ===");
        try {
            // 1. 核对串口参数（必须与硬件一致！）
            String portPath = "/dev/ttyUSB5"; // 串口路径
            int baudRate = 9600;            // 波特率
            int dataBits = 8; // 数据位：8位
            int stopBits = 1; // 停止位：1位
            int parity = 0;  // 校验位：无

            Log.d("SerialPortDebug", "串口参数：路径=" + portPath + "，波特率=" + baudRate);
            // 2. 打开串口
            mSerialPort = new SerialPort(new File(portPath), baudRate, 0);
            // 3. 初始化输入输出流
            mInputStream = mSerialPort.getInputStream();
            mOutputStream = mSerialPort.getOutputStream();
            Log.d("SerialPortDebug", "串口打开成功！输入流：" + mInputStream + "，输出流：" + mOutputStream);

            // 4. 启动接收线程（若未启动，无法接收响应）
            startReceiveThread();
            return true;
        } catch (SecurityException e) {
            Log.e("SerialPortDebug", "串口打开失败：权限不足", e);
        } catch (IOException e) {
            Log.e("SerialPortDebug", "串口打开失败：IO异常（路径错误/波特率不匹配）", e);
        } catch (Exception e) {
            Log.e("SerialPortDebug", "串口打开失败：未知异常", e);
        }
        Log.e("SerialPortDebug", "串口打开失败！");
        return false;
    }

    // 确认接收线程是否启动（接收响应必需）
    private void startReceiveThread() {
        // 停止已有线程（避免重复启动）
        if (mReceiveThread != null && mReceiveThread.isAlive()) {
            stopReceiveThread();
        }

        mIsStopReceive = false; // 重置停止标记
        mReceiveThread = new Thread(() -> {
            Log.d("SerialPortDebug", "接收线程启动，开始监听串口数据");
            byte[] buffer = new byte[1024]; // 数据缓存区
            int len; // 每次读取的字节数

            try {
                // 循环读取串口数据（直到线程被停止）
                while (!mIsStopReceive && mInputStream != null) {
                    len = mInputStream.read(buffer); // 阻塞读取（无数据时等待）
                    if (len > 0) {
                        // 截取有效数据（去掉缓存区空字节）
                        byte[] receivedData = Arrays.copyOf(buffer, len);
                        Log.d("SerialPortDebug", "接收到串口数据（" + len + "字节）：" + bytesToHexString(receivedData));

                        // 通知外部监听器（如DeliverySettingsFragment处理数据）
                        if (mDataReceivedListener != null) {
                            mDataReceivedListener.onDataReceived(receivedData);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("SerialPortDebug", "接收线程异常：", e);
            } finally {
                Log.d("SerialPortDebug", "接收线程停止");
            }
        });
        mReceiveThread.start(); // 启动线程
    }

    // 停止串口接收线程
    private void stopReceiveThread() {
        mIsStopReceive = true;
        if (mReceiveThread != null) {
            try {
                mReceiveThread.interrupt(); // 中断线程
                mReceiveThread.join(1000);  // 等待线程退出（最多1秒）
            } catch (InterruptedException e) {
                Log.e("SerialPortDebug", "停止接收线程异常：", e);
            } finally {
                mReceiveThread = null;
            }
        }
    }

    /**
     * 关闭串口
     */
    public void closeSerialPort() {
        if (mReadThread != null) {
            mReadThread.interrupt();
            mReadThread = null;
        }

        if (mSerialPort != null) {
            mSerialPort.close();
            mSerialPort = null;
        }

        mOutputStream = null;
        mInputStream = null;
    }

    /**
     * 发送Modbus写指令
     * @param deviceCode 设备ID
     * @param regAddr 寄存器地址
     * @param data 数据
     */
    public void sendModbusWriteCommand(int deviceCode, int regAddr, int data) {
        Log.d("SerialDebug", "=== 开始组装Modbus指令 ===");
        // 1. 组装Modbus RTU帧（功能码0x06：写单个寄存器，协议要求）
        byte[] frame = new byte[8]; // Modbus RTU写单个寄存器固定8字节
        frame[0] = (byte) deviceCode;       // 设备地址（1字节）
        frame[1] = 0x06;                    // 功能码：写单个寄存器（必须是0x06，协议要求）
        frame[2] = (byte) (regAddr >> 8);   // 寄存器地址高8位
        frame[3] = (byte) (regAddr & 0xFF); // 寄存器地址低8位
        frame[4] = (byte) (data >> 8);      // 数据高8位（如0x0100 → 0x01）
        frame[5] = (byte) (data & 0xFF);    // 数据低8位（如0x0100 → 0x00）

        // 2. 计算CRC校验（Modbus RTU必须加CRC，否则硬件不识别）
        byte[] crc = calculateCRC(frame, 6); // 前6字节计算CRC
        frame[6] = crc[0]; // CRC低8位
        frame[7] = crc[1]; // CRC高8位

        // 3. 打印组装后的指令帧（关键！核对是否符合协议）
        Log.d("SerialDebug", "组装的Modbus指令帧（16进制）：" + bytesToHexString(frame));

        // 4. 写入串口输出流
        try {
            if (mOutputStream != null) {
                Log.d("SerialDebug", "向串口写入指令帧，长度：" + frame.length + "字节");
                mOutputStream.write(frame);
                mOutputStream.flush(); // 强制刷新输出流（避免数据缓存）
                Log.d("SerialDebug", "指令发送成功");
            } else {
                Log.e("SerialDebug", "mOutputStream为null，无法写入指令");
            }
        } catch (IOException e) {
            Log.e("SerialDebug", "写入串口失败！", e); // 捕获IO异常（如权限、硬件错误）
        }
    }

    /**
     * 发送Modbus读指令
     * @param deviceId 设备ID
     * @param register 寄存器地址
     * @param length 读取长度
     */
    public void sendModbusReadCommand(int deviceId, int register, int length) {
        if (mOutputStream == null) {
            Log.e(TAG, "串口未打开，无法发送数据");
            return;
        }

        // 构建Modbus RTU读寄存器指令 (功能码0x03)
        byte[] command = new byte[8];
        command[0] = (byte) deviceId;
        command[1] = 0x03; // 读寄存器功能码
        command[2] = (byte) (register >> 8); // 寄存器地址高字节
        command[3] = (byte) (register & 0xFF); // 寄存器地址低字节
        command[4] = (byte) (length >> 8); // 长度高字节
        command[5] = (byte) (length & 0xFF); // 长度低字节

        // 计算CRC校验
        byte[] crc = calculateCRC(command, 6);
        command[6] = crc[0]; // CRC低字节
        command[7] = crc[1]; // CRC高字节

        try {
            mOutputStream.write(command);
            mOutputStream.flush();
            Log.d(TAG, "发送Modbus读指令: " + bytesToHexString(command));
        } catch (IOException e) {
            Log.e(TAG, "发送数据失败", e);
            if (mDataReceivedListener != null) {
                mDataReceivedListener.onError("发送数据失败: " + e.getMessage());
            }
        }
    }

    /**
     * 计算Modbus CRC校验
     */
    private byte[]  calculateCRC(byte[] buffer, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= buffer[i] & 0xFF;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc >>= 1;
                    crc ^= 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return new byte[]{(byte) (crc & 0xFF), (byte) (crc >> 8)};
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * 设置数据接收监听器
     */
    public void setOnDataReceivedListener(OnDataReceivedListener listener) {
        mDataReceivedListener = listener;
    }

    /**
     * 读取串口数据的线程
     */
    private class ReadThread extends Thread {
        @Override
        public void run() {
            super.run();
            byte[] buffer = new byte[1024];
            int size;

            while (!isInterrupted()) {
                if (mInputStream == null) return;
                try {
                    size = mInputStream.read(buffer);
                    if (size > 0) {
                        byte[] data = new byte[size];
                        System.arraycopy(buffer, 0, data, 0, size);
                        Log.d(TAG, "接收到数据: " + bytesToHexString(data));

                        if (mDataReceivedListener != null) {
                            mDataReceivedListener.onDataReceived(data);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "读取数据失败", e);
                    if (mDataReceivedListener != null) {
                        mDataReceivedListener.onError("读取数据失败: " + e.getMessage());
                    }
                    return;
                }
            }
        }
    }
}

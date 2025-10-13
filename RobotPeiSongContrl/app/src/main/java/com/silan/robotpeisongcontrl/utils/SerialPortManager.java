package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
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
    private static final String DEVICE_PATH = "/dev/ttyUSB6"; // 根据实际设备修改
    private static final int BAUDRATE = 9600; // Modbus RTU常用波特率

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
        try {
            mSerialPort = new SerialPort(new File(DEVICE_PATH), BAUDRATE, 0);
            mOutputStream = mSerialPort.getOutputStream();
            mInputStream = mSerialPort.getInputStream();

            // 启动读取线程
            mReadThread = new ReadThread();
            mReadThread.start();
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "打开串口失败: 权限被拒绝", e);
        } catch (IOException e) {
            Log.e(TAG, "打开串口失败: IO错误", e);
        } catch (InvalidParameterException e) {
            Log.e(TAG, "打开串口失败: 参数无效", e);
        }
        return false;
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
     * @param deviceId 设备ID
     * @param register 寄存器地址
     * @param data 数据
     */
    public void sendModbusWriteCommand(int deviceId, int register, int data) {
        if (mOutputStream == null) {
            Log.e(TAG, "串口未打开，无法发送数据");
            return;
        }

        // 构建Modbus RTU写单个寄存器指令 (功能码0x06)
        // 格式: [设备地址][功能码][寄存器地址高字节][寄存器地址低字节][数据高字节][数据低字节][CRC高字节][CRC低字节]
        byte[] command = new byte[8];
        command[0] = (byte) deviceId;
        command[1] = 0x06; // 写单个寄存器功能码
        command[2] = (byte) (register >> 8); // 寄存器地址高字节
        command[3] = (byte) (register & 0xFF); // 寄存器地址低字节
        command[4] = (byte) (data >> 8); // 数据高字节
        command[5] = (byte) (data & 0xFF); // 数据低字节

        // 计算CRC校验
        int crc = calculateCRC(command, 6);
        command[6] = (byte) (crc & 0xFF); // CRC低字节
        command[7] = (byte) (crc >> 8); // CRC高字节

        try {
            mOutputStream.write(command);
            mOutputStream.flush();
            Log.d(TAG, "发送Modbus指令: " + bytesToHexString(command));
        } catch (IOException e) {
            Log.e(TAG, "发送数据失败", e);
            if (mDataReceivedListener != null) {
                mDataReceivedListener.onError("发送数据失败: " + e.getMessage());
            }
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
        int crc = calculateCRC(command, 6);
        command[6] = (byte) (crc & 0xFF); // CRC低字节
        command[7] = (byte) (crc >> 8); // CRC高字节

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
    private int calculateCRC(byte[] buffer, int length) {
        int crc = 0xFFFF;
        for (int i = 0; i < length; i++) {
            crc ^= (buffer[i] & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc >>= 1;
                    crc ^= 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }
        return crc;
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        if (bytes == null || bytes.length <= 0) {
            return null;
        }
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() < 2) {
                sb.append(0);
            }
            sb.append(hex).append(" ");
        }
        return sb.toString();
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
        private static final long FRAME_TIMEOUT_MS = 100; // 帧超时时间（根据波特率调整，1.5字符时间）
        private ByteArrayOutputStream frameBuffer = new ByteArrayOutputStream();
        private long lastReceiveTime;

        @Override
        public void run() {
            super.run();
            byte[] buffer = new byte[1024];
            int size;
            lastReceiveTime = System.currentTimeMillis();

            while (!isInterrupted()) {
                if (mInputStream == null) return;
                try {
                    size = mInputStream.read(buffer);
                    if (size > 0) {
                        frameBuffer.write(buffer, 0, size);
                        lastReceiveTime = System.currentTimeMillis();
                    }

                    // 检查是否超时（超过FRAME_TIMEOUT_MS未收到数据，认为一帧结束）
                    if (System.currentTimeMillis() - lastReceiveTime > FRAME_TIMEOUT_MS
                            && frameBuffer.size() > 0) {
                        byte[] completeFrame = frameBuffer.toByteArray();
                        frameBuffer.reset(); // 重置缓冲区
                        Log.d(TAG, "接收到完整帧: " + bytesToHexString(completeFrame));
                        if (mDataReceivedListener != null) {
                            mDataReceivedListener.onDataReceived(completeFrame);
                        }
                    }

                    // 短暂休眠，避免CPU占用过高
                    Thread.sleep(10);
                } catch (IOException e) {
                    Log.e(TAG, "读取数据失败", e);
                    if (mDataReceivedListener != null) {
                        mDataReceivedListener.onError("读取数据失败: " + e.getMessage());
                    }
                    return;
                } catch (InterruptedException e) {
                    interrupt();
                    return;
                }
            }
        }
    }
}

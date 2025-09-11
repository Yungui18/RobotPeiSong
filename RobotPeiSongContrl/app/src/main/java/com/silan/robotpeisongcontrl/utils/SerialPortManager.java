package com.silan.robotpeisongcontrl.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class SerialPortManager {
    private static final String TAG = "SerialPortManager";
    private static final String DEVICE_PATH = "/dev/ttyS1"; // 串口设备路径
    private static final int BAUDRATE = 9600; // 波特率

    private SerialPort serialPort;
    private OutputStream outputStream;
    private InputStream inputStream;
    private ReadThread readThread;
    private OnDataReceivedListener dataReceivedListener;

    // 指令帧格式：起始符(0xAA) + 设备码(1字节) + 地址(1字节) + 数据长度(1字节) + 数据(n字节) + 校验和(1字节) + 结束符(0x55)
    private static final byte START_FLAG = (byte) 0xAA;
    private static final byte END_FLAG = (byte) 0x55;
    private static final byte DEVICE_CODE = 0x01; // 设备码固定为01

    public interface OnDataReceivedListener {
        void onDataReceived(byte[] data);
        void onError(String error);
    }

    public SerialPortManager(Context context, OnDataReceivedListener listener) {
        this.dataReceivedListener = listener;
        try {
            serialPort = new SerialPort(new File(DEVICE_PATH), BAUDRATE, 0);
            outputStream = serialPort.getOutputStream();
            inputStream = serialPort.getInputStream();
            startReadThread();
        } catch (IOException e) {
            Log.e(TAG, "Serial port init failed: " + e.getMessage());
            listener.onError("串口初始化失败: " + e.getMessage());
        }
    }

    // 启动读取线程
    private void startReadThread() {
        readThread = new ReadThread();
        readThread.start();
    }

    // 发送指令
    public void sendCommand(byte address, byte[] data) {
        if (outputStream == null) {
            dataReceivedListener.onError("串口未初始化");
            return;
        }

        try {
            // 组装指令帧
            byte[] frame = assembleFrame(address, data);
            outputStream.write(frame);
            outputStream.flush();
            Log.d(TAG, "发送指令: " + Arrays.toString(frame));
        } catch (IOException e) {
            Log.e(TAG, "发送指令失败: " + e.getMessage());
            dataReceivedListener.onError("发送指令失败: " + e.getMessage());
        }
    }

    // 组装指令帧
    private byte[] assembleFrame(byte address, byte[] data) {
        int dataLen = data.length;
        byte[] frame = new byte[4 + dataLen + 1 + 1]; // 起始符+设备码+地址+长度+数据+校验+结束符
        int index = 0;

        frame[index++] = START_FLAG;
        frame[index++] = DEVICE_CODE;
        frame[index++] = address;
        frame[index++] = (byte) dataLen;

        System.arraycopy(data, 0, frame, index, dataLen);
        index += dataLen;

        // 计算校验和（异或校验）
        byte checkSum = 0;
        for (int i = 1; i < index; i++) { // 从设备码开始校验
            checkSum ^= frame[i];
        }
        frame[index++] = checkSum;
        frame[index++] = END_FLAG;

        return frame;
    }

    // 读取线程
    private class ReadThread extends Thread {
        @Override
        public void run() {
            super.run();
            byte[] buffer = new byte[1024];
            int len;
            while (!isInterrupted() && inputStream != null) {
                try {
                    if ((len = inputStream.read(buffer)) > 0) {
                        byte[] received = Arrays.copyOf(buffer, len);
                        Log.d(TAG, "收到数据: " + Arrays.toString(received));
                        if (dataReceivedListener != null) {
                            dataReceivedListener.onDataReceived(received);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "读取数据失败: " + e.getMessage());
                    dataReceivedListener.onError("读取数据失败: " + e.getMessage());
                    break;
                }
            }
        }
    }

    // 关闭串口
    public void close() {
        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
        if (serialPort != null) {
            serialPort.close();
            serialPort = null;
        }
        outputStream = null;
        inputStream = null;
    }
}

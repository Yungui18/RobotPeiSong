package com.silan.robotpeisongcontrl.adapter;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsbSerialHelper {
    private static final String TAG = "UsbSerialHelper";
    private static final boolean DEBUG_MODE = true;
    private static final int MAX_RETRY_COUNT = 3;

    private final Context context;
    private UsbSerialDevice serialPort;
    private UsbDeviceConnection connection;
    private UwbDataListener listener;
    private int retryCount = 0;
    // 新增：多设备存储列表（存储串口设备+对应连接）
    private List<UsbSerialDevice> serialPortList = new ArrayList<>();
    private List<UsbDeviceConnection> connectionList = new ArrayList<>();
    // 记录每个设备的基站ID（后续用于区分数据来源）
    private Map<UsbSerialDevice, Long> deviceAnchorIdMap = new HashMap<>();
    private Map<Long, Long> baseStationLastHeartbeat = new HashMap<>();
    private Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private Runnable heartbeatCheckRunnable;

    public interface UwbDataListener {
        void onUwbData(long anchorId, long tagId, double distance, double azimuth, double elevation);
    }

    public UsbSerialHelper(Context context) {
        this.context = context;
    }

    public void open(UwbDataListener listener) {
        this.listener = listener;
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null || usbManager.getDeviceList().isEmpty()) {
            logError("未检测到USB设备");
            return;
        }

        // 遍历所有USB设备，初始化所有基站（取消原"找到一个就返回"逻辑）
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            // 尝试初始化当前设备，成功则加入列表（不返回，继续遍历）
            tryInitializeDevice(usbManager, device);
        }

        // 初始化完成后，校验设备数量（若为0则提示，若<4则提示可能硬件问题）
        if (serialPortList.isEmpty()) {
            logError("没有基站设备初始化成功");
        } else {
            logInfo("成功初始化 " + serialPortList.size() + " 个基站设备");
        }
    }

    private boolean tryInitializeDevice(UsbManager usbManager, UsbDevice device) {
        try {
            // 1. 检查设备权限
            if (!usbManager.hasPermission(device)) {
                logInfo("设备" + device.getDeviceName() + "无权限，跳过");
                return false;
            }

            // 2. 建立设备连接
            UsbDeviceConnection conn = usbManager.openDevice(device);
            if (conn == null) {
                logError("无法打开设备" + device.getDeviceName() + "连接");
                return false;
            }

            // 3. 创建串口设备（匹配UWB基站的串口协议：文档1-40/51规定UART(TTL 115200bps)）
            UsbSerialDevice serial = UsbSerialDevice.createUsbSerialDevice(device, conn);
            if (serial == null) {
                logError("设备" + device.getDeviceName() + "不是有效串口设备（非UWB基站）");
                conn.close(); // 释放无效连接
                return false;
            }

            // 4. 尝试匹配波特率（优先115200，文档1-40规定基站通信波特率为115200bps）
            int targetBaud = 115200; // 优先使用规格书规定的波特率，减少尝试次数
            if (tryBaudRate(serial, conn, targetBaud)) {
                // 5. 初始化成功：加入设备列表，并记录设备（后续用于区分基站ID）
                serialPortList.add(serial);
                connectionList.add(conn);
                // （可选）若基站ID已预设，可在此绑定设备与AnchorID，如通过设备PID/VID区分）
                // deviceAnchorIdMap.put(serial, getAnchorIdByDevice(device));
                logInfo("设备" + device.getDeviceName() + "初始化成功（波特率：" + targetBaud + "bps）");
                return true;
            } else {
                // 波特率匹配失败，释放资源
                serial.close();
                conn.close();
                logError("设备" + device.getDeviceName() + "波特率匹配失败（仅支持115200bps，文档1-40）");
                return false;
            }
        } catch (Exception e) {
            logError("设备" + device.getDeviceName() + "初始化异常：" + e.getMessage());
            return false;
        }
    }

    private boolean tryBaudRate(UsbSerialDevice serial, UsbDeviceConnection conn, int baudRate) {
        try {
            if (!serial.open()) return false;
            // 配置串口参数（严格匹配文档1-40/67：8位数据位、1位停止位、无校验、无流控）
            serial.setBaudRate(baudRate);
            serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
            serial.setStopBits(UsbSerialInterface.STOP_BITS_1);
            serial.setParity(UsbSerialInterface.PARITY_NONE);
            serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
            // 为每个设备注册独立的数据读取回调（关键：多设备同时监听）
            serial.read(data -> processIncomingData(serial, data));
            return true;
        } catch (Exception e) {
            logError("波特率" + baudRate + "配置失败：" + e.getMessage());
            return false;
        }
    }

    private void processIncomingData(UsbSerialDevice serial, byte[] data) {
        if (data == null || data.length < 28) {
            logWarning("无效数据包（长度不足）：" + (data == null ? 0 : data.length) + "字节");
            return;
        }

        // 1. 校验数据包头部（4个连续0xFF为消息开始）
        if (data[0] != (byte)0xFF || data[1] != (byte)0xFF ||
                data[2] != (byte)0xFF || data[3] != (byte)0xFF) {
            logWarning("无效包头，跳过（非UWB定位数据）");
            return;
        }

        // 2. 校验命令码（文档1-70：定位数据命令码为0x2001，心跳包为0x2002）
        int command = ((data[8] & 0xFF) << 8) | (data[9] & 0xFF);
        if (command != 0x2001) {
            logDebug("忽略非定位命令（如心跳包）：0x" + Integer.toHexString(command));
            return;
        }

        // 3. 解析基站ID（AnchorID，文档1-72：4字节无符号整数，大端序）
        long anchorId = ((long)(data[12] & 0xFF) << 24) |
                ((long)(data[13] & 0xFF) << 16) |
                ((long)(data[14] & 0xFF) << 8) |
                (data[15] & 0xFF);

        // 4. 解析核心数据（距离、方位角、仰角，文档1-72/40）
        int distanceRaw = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) |
                ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
        double distance = distanceRaw / 100.0; // 转换为米（文档1-40：距离单位cm）
        int azimuth = (short)((data[24] & 0xFF) << 8 | (data[25] & 0xFF)); // 方位角（度）
        int elevation = (short)((data[26] & 0xFF) << 8 | (data[27] & 0xFF)); // 仰角（度）

        // 5. 数据有效性校验（文档1-33/40：距离0-100m，角度范围±180°，有效角度120°内）
        if (distance < 0 || distance > 100 || Math.abs(azimuth) > 180 || Math.abs(elevation) > 90) {
            logWarning("无效UWB数据（基站ID：0x" + Long.toHexString(anchorId) + "）：距离=" + distance + "m，方位角=" + azimuth + "°");
            return;
        }

        // 6. 回调给监听器（传递基站ID，确保监控面板能区分“前/后/左/右”基站）
        if (listener != null) {
            listener.onUwbData(anchorId, /*tagId=*/0, distance, azimuth, elevation);
            logDebug("接收基站数据（ID：0x" + Long.toHexString(anchorId) + "）：距离=" + distance + "m，方位角=" + azimuth + "°，仰角=" + elevation + "°");
        }
    }

    public void close() {
        try {
            // 遍历所有初始化的设备，关闭串口和连接
            for (int i = 0; i < serialPortList.size(); i++) {
                UsbSerialDevice serial = serialPortList.get(i);
                UsbDeviceConnection conn = connectionList.get(i);
                if (serial != null && serial.isOpen()) {
                    serial.close();
                }
                if (conn != null) {
                    conn.close();
                }
                logInfo("已释放设备" + i + "资源");
            }
            // 清空列表
            serialPortList.clear();
            connectionList.clear();
            deviceAnchorIdMap.clear();
        } catch (Exception e) {
            logError("关闭设备资源异常：" + e.getMessage());
        }
    }

    // 日志方法
    private void logInfo(String message) {
        Log.i(TAG, message);
    }

    private void logDebug(String message) {
        if (DEBUG_MODE) Log.d(TAG, message);
    }

    private void logWarning(String message) {
        Log.w(TAG, message);
    }

    private void logError(String message) {
        Log.e(TAG, message);
    }
}

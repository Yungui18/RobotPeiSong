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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class UsbSerialHelper {
    private static final String TAG = "UsbSerialHelper";
    private static final boolean DEBUG_MODE = true;
    private static final int MAX_RETRY_COUNT = 3;

    private final Context context;
    private List<UsbSerialDevice> serialPortList = new ArrayList<>();
    private List<UsbDeviceConnection> connectionList = new ArrayList<>();
    private Map<UsbSerialDevice, Long> deviceAnchorIdMap = new HashMap<>();
    private Map<Long, Long> baseStationLastHeartbeat = new HashMap<>();
    private Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private Runnable heartbeatCheckRunnable;
    private UwbDataListener listener;

    // 新增：数据缓存队列（避免数据丢失）
    private Map<Long, Queue<byte[]>> dataCacheQueue = new HashMap<>();
    private static final int CACHE_QUEUE_SIZE = 10; // 缓存队列大小

    public interface UwbDataListener {
        // 【已修改】回调时：仰角 变成 水平角，方位角变成俯仰角（只换名字与顺序）
        void onUwbData(long anchorId, long tagId, double distance, double 俯仰角, double 水平角);
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

        // 初始化缓存队列
        dataCacheQueue.clear();

        // 遍历所有USB设备，初始化所有基站
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            tryInitializeDevice(usbManager, device);
        }

        // 初始化完成后，校验设备数量
        if (serialPortList.isEmpty()) {
            logError("没有基站设备初始化成功");
        } else {
            logInfo("成功初始化 " + serialPortList.size() + " 个基站设备");
        }

        // 启动心跳检查（保证数据连接稳定性）
        startHeartbeatCheck();
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

            // 3. 创建串口设备
            UsbSerialDevice serial = UsbSerialDevice.createUsbSerialDevice(device, conn);
            if (serial == null) {
                logError("设备" + device.getDeviceName() + "不是有效串口设备（非UWB基站）");
                conn.close();
                return false;
            }

            // 4. 配置串口参数
            int targetBaud = 115200;
            if (tryBaudRate(serial, conn, targetBaud)) {
                // 初始化该设备的缓存队列
                dataCacheQueue.put((long) serial.hashCode(), new LinkedList<>());
                // 加入设备列表
                serialPortList.add(serial);
                connectionList.add(conn);
                logInfo("设备" + device.getDeviceName() + "初始化成功（波特率：" + targetBaud + "bps）");
                return true;
            } else {
                serial.close();
                conn.close();
                logError("设备" + device.getDeviceName() + "波特率匹配失败");
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
            // 严格匹配串口参数
            serial.setBaudRate(baudRate);
            serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
            serial.setStopBits(UsbSerialInterface.STOP_BITS_1);
            serial.setParity(UsbSerialInterface.PARITY_NONE);
            serial.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
            // 为每个设备注册独立的数据读取回调（优化：异步处理数据）
            serial.read(data -> {
                // 异步处理数据，避免阻塞串口读取
                new Thread(() -> processIncomingData(serial, data)).start();
            });
            return true;
        } catch (Exception e) {
            logError("波特率" + baudRate + "配置失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 优化：异步处理UWB数据，增加缓存机制
     */
    private void processIncomingData(UsbSerialDevice serial, byte[] data) {
        try {
            // 1. 缓存数据（避免数据丢失）
            Queue<byte[]> cacheQueue = dataCacheQueue.get((long) serial.hashCode());
            if (cacheQueue != null) {
                cacheQueue.offer(data);
                if (cacheQueue.size() > CACHE_QUEUE_SIZE) {
                    cacheQueue.poll(); // 超出大小则移除最早数据
                }
            }

            if (data == null || data.length < 28) {
                logWarning("无效数据包（长度不足）：" + (data == null ? 0 : data.length) + "字节");
                return;
            }

            // 2. 校验数据包头部
            if (data[0] != (byte) 0xFF || data[1] != (byte) 0xFF ||
                    data[2] != (byte) 0xFF || data[3] != (byte) 0xFF) {
                logWarning("无效包头，跳过（非UWB定位数据）");
                return;
            }

            // 3. 校验命令码
            int command = ((data[8] & 0xFF) << 8) | (data[9] & 0xFF);
            if (command != 0x2001) {
                // 记录心跳包
                if (command == 0x2002) {
                    long anchorId = ((long) (data[12] & 0xFF) << 24) |
                            ((long) (data[13] & 0xFF) << 16) |
                            ((long) (data[14] & 0xFF) << 8) |
                            (data[15] & 0xFF);
                    baseStationLastHeartbeat.put(anchorId, System.currentTimeMillis());
                }
                logDebug("忽略非定位命令（如心跳包）：0x" + Integer.toHexString(command));
                return;
            }

            // 4. 解析基站ID
            long anchorId = ((long) (data[12] & 0xFF) << 24) |
                    ((long) (data[13] & 0xFF) << 16) |
                    ((long) (data[14] & 0xFF) << 8) |
                    (data[15] & 0xFF);

            // 5. 解析核心数据
            int distanceRaw = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) |
                    ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
            double distance = distanceRaw / 100.0; // 转换为米
            int azimuth = (short) ((data[24] & 0xFF) << 8 | (data[25] & 0xFF)); // 方位角（度）
            int elevation = (short) ((data[26] & 0xFF) << 8 | (data[27] & 0xFF)); // 仰角（度）

            // 6. 数据有效性校验
            if (distance < 0 || distance > 100 || Math.abs(azimuth) > 180 || Math.abs(elevation) > 90) {
                logWarning("无效UWB数据（基站ID：0x" + Long.toHexString(anchorId) + "）：距离=" + distance + "m，方位角=" + azimuth + "°");
                return;
            }

            // 7. 回调给监听器（主线程执行）
            if (listener != null) {
                heartbeatHandler.post(() -> listener.onUwbData(anchorId, 0, distance, azimuth, elevation));

                // ======================== 【已修改】日志显示互换 ========================
                logDebug(String.format("UWB原始输出 -> 距离:%.2fm, 俯仰(azimuth):%d, 水平(elevation):%d",
                        distance, azimuth, elevation));
            }
        } catch (Exception e) {
            logError("处理UWB数据异常：" + e.getMessage());
        }
    }

    /**
     * 启动心跳检查（保证基站连接稳定性）
     */
    private void startHeartbeatCheck() {
        heartbeatCheckRunnable = new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                // 检查各基站心跳（超时10秒则提示）
                for (Map.Entry<Long, Long> entry : baseStationLastHeartbeat.entrySet()) {
                    long anchorId = entry.getKey();
                    long lastHeartbeat = entry.getValue();
                    if (currentTime - lastHeartbeat > 10000) {
                        logWarning("基站" + anchorId + "心跳超时，可能连接异常");
                    }
                }
                // 每隔5秒检查一次
                heartbeatHandler.postDelayed(this, 5000);
            }
        };
        heartbeatHandler.post(heartbeatCheckRunnable);
    }

    public void close() {
        try {
            // 停止心跳检查
            heartbeatHandler.removeCallbacks(heartbeatCheckRunnable);
            // 清空缓存队列
            dataCacheQueue.clear();
            // 关闭所有设备
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
            serialPortList.clear();
            connectionList.clear();
            deviceAnchorIdMap.clear();
            baseStationLastHeartbeat.clear();
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
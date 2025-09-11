package com.silan.robotpeisongcontrl.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ModbusRtuProtocol {
    // 设备地址
    public static final int DEVICE_ADDRESS = 0x01;
    // 功能码
    public static final int FUNCTION_WRITE = 0x06; // 写入单个寄存器
    public static final int FUNCTION_READ = 0x03;  // 读取单个寄存器

    /**
     * 构建写入寄存器指令
     * @param registerAddr 寄存器地址
     * @param data 数据(16位)
     * @return 完整Modbus指令字节数组
     */
    public static byte[] buildWriteCommand(int registerAddr, int data) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) DEVICE_ADDRESS);          // 设备地址
        buffer.put((byte) FUNCTION_WRITE);          // 功能码
        buffer.putShort((short) registerAddr);      // 寄存器地址
        buffer.putShort((short) data);              // 数据
        short crc = calculateCRC(buffer.array(), 6); // 计算CRC校验
        buffer.putShort(crc);                       // 校验位
        return buffer.array();
    }

    /**
     * 构建读取寄存器指令
     * @param registerAddr 寄存器地址
     * @return 完整Modbus指令字节数组
     */
    public static byte[] buildReadCommand(int registerAddr) {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put((byte) DEVICE_ADDRESS);
        buffer.put((byte) FUNCTION_READ);
        buffer.putShort((short) registerAddr);
        buffer.putShort((short) 0x0001); // 读取长度1
        short crc = calculateCRC(buffer.array(), 6);
        buffer.putShort(crc);
        return buffer.array();
    }

    /**
     * 解析状态响应数据
     * @param response 响应字节数组
     * @return 状态值(0x0000-0xFFFF)
     */
    public static int parseStatusResponse(byte[] response) {
        if (response.length != 7) return -1; // 无效响应长度
        if (response[1] != FUNCTION_READ) return -1; // 功能码不匹配
        return ((response[3] & 0xFF) << 8) | (response[4] & 0xFF); // 解析数据
    }

    // CRC16校验计算(Modbus RTU标准)
    private static short calculateCRC(byte[] buffer, int length) {
        short crc = (short) 0xFFFF;
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
}

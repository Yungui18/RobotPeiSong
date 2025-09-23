package com.silan.robotpeisongcontrl.utils;

/**
 * 串口数据接收监听器接口
 */
public interface OnDataReceivedListener {
    /**
     * 当接收到数据时回调
     * @param data 接收到的字节数据
     */
    void onDataReceived(byte[] data);

    /**
     * 当发生错误时回调
     * @param error 错误信息
     */
    void onError(String error);
}

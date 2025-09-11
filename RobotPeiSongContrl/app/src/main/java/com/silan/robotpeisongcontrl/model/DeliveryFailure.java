package com.silan.robotpeisongcontrl.model;

import android.content.Context;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;

/**
 * 配送失败信息实体类
 * 用于记录配送任务执行失败的详细信息，包括失败点位、原因、时间等
 * 支持失败数据的存储和查询，便于后续问题排查和任务重发
 */
public class DeliveryFailure {
    private String pointName; // 失败点位名称
    private boolean[] doorsToOpen;
    private long timestamp; // 失败时间戳（毫秒）

    public DeliveryFailure(String pointName, boolean[] doorsToOpen, long timestamp) {
        this.pointName = pointName;
        this.doorsToOpen = Arrays.copyOf(doorsToOpen, doorsToOpen.length);
        this.timestamp = timestamp;
    }

    // Getters
    /**
     * 获取配送失败的点位名称
     * @return 失败点位的名称字符串
     */
    public String getPointName() { return pointName; }
    public boolean[] getDoorsToOpen() { return Arrays.copyOf(doorsToOpen, doorsToOpen.length); }
    public long getTimestamp() { return timestamp; }

    public String getFormattedTime(Context context) {
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        return format.format(new Date(timestamp));
    }
}

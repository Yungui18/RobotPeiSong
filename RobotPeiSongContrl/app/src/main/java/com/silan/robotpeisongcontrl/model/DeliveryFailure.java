package com.silan.robotpeisongcontrl.model;

import android.content.Context;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;

public class DeliveryFailure {
    private String pointName;
    private boolean[] doorsToOpen;
    private long timestamp;

    public DeliveryFailure(String pointName, boolean[] doorsToOpen, long timestamp) {
        this.pointName = pointName;
        this.doorsToOpen = Arrays.copyOf(doorsToOpen, doorsToOpen.length);
        this.timestamp = timestamp;
    }

    // Getters
    public String getPointName() { return pointName; }
    public boolean[] getDoorsToOpen() { return Arrays.copyOf(doorsToOpen, doorsToOpen.length); }
    public long getTimestamp() { return timestamp; }

    public String getFormattedTime(Context context) {
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        return format.format(new Date(timestamp));
    }
}

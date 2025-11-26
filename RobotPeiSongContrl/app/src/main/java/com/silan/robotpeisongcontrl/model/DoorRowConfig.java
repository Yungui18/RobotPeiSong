package com.silan.robotpeisongcontrl.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * 每行仓门配置模型
 */
public class DoorRowConfig implements Parcelable {
    private boolean isEnabled; // 是否启用该行
    private int type; // 0:电机仓门 1:电磁锁仓门 2:推杆电机仓门
    private int layout; // 0:单仓门 1:双仓门

    public DoorRowConfig() {
        this.isEnabled = false;
        this.type = 0;
        this.layout = 0;
    }

    protected DoorRowConfig(Parcel in) {
        isEnabled = in.readByte() != 0;
        type = in.readInt();
        layout = in.readInt();
    }

    public static final Creator<DoorRowConfig> CREATOR = new Creator<DoorRowConfig>() {
        @Override
        public DoorRowConfig createFromParcel(Parcel in) {
            return new DoorRowConfig(in);
        }

        @Override
        public DoorRowConfig[] newArray(int size) {
            return new DoorRowConfig[size];
        }
    };

    public boolean isEnabled() {
        return isEnabled;
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public int getLayout() {
        return layout;
    }

    public void setLayout(int layout) {
        this.layout = layout;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeByte((byte) (isEnabled ? 1 : 0));
        parcel.writeInt(type);
        parcel.writeInt(layout);
    }
}

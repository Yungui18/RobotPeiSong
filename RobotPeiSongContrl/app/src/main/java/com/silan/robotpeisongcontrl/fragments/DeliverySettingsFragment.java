package com.silan.robotpeisongcontrl.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;

public class DeliverySettingsFragment extends Fragment {

    private int currentOpenDoor = 0; // 0表示没有打开的仓门，1-4表示打开的仓门编号
    private TextView tvStatus;

    // 仓门视图
    private View door1Indicator, door2Indicator, door3Indicator, door4Indicator;
    private Button btnDoor1Open, btnDoor1Close;
    private Button btnDoor2Open, btnDoor2Close;
    private Button btnDoor3Open, btnDoor3Close;
    private Button btnDoor4Open, btnDoor4Close;
    private Switch switchVerification;
    private LinearLayout layoutPasswordSettings;
    private EditText etPickupPassword, etDeliveryPassword;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_delivery_settings, container, false);

        // 初始化视图
        initViews(view);
        // 设置按钮点击监听器
        setupButtonListeners();

        return view;
    }

    private void initViews(View view) {
        tvStatus = view.findViewById(R.id.tv_status);

        // 仓门1
        door1Indicator = view.findViewById(R.id.door1_indicator);
        btnDoor1Open = view.findViewById(R.id.btn_door1_open);
        btnDoor1Close = view.findViewById(R.id.btn_door1_close);

        // 仓门2
        door2Indicator = view.findViewById(R.id.door2_indicator);
        btnDoor2Open = view.findViewById(R.id.btn_door2_open);
        btnDoor2Close = view.findViewById(R.id.btn_door2_close);

        // 仓门3
        door3Indicator = view.findViewById(R.id.door3_indicator);
        btnDoor3Open = view.findViewById(R.id.btn_door3_open);
        btnDoor3Close = view.findViewById(R.id.btn_door3_close);

        // 仓门4
        door4Indicator = view.findViewById(R.id.door4_indicator);
        btnDoor4Open = view.findViewById(R.id.btn_door4_open);
        btnDoor4Close = view.findViewById(R.id.btn_door4_close);
    }

    private void setupButtonListeners() {
        // 仓门1开关
        btnDoor1Open.setOnClickListener(v -> openDoor(1));
        btnDoor1Close.setOnClickListener(v -> closeDoor(1));

        // 仓门2开关
        btnDoor2Open.setOnClickListener(v -> openDoor(2));
        btnDoor2Close.setOnClickListener(v -> closeDoor(2));

        // 仓门3开关
        btnDoor3Open.setOnClickListener(v -> openDoor(3));
        btnDoor3Close.setOnClickListener(v -> closeDoor(3));

        // 仓门4开关
        btnDoor4Open.setOnClickListener(v -> openDoor(4));
        btnDoor4Close.setOnClickListener(v -> closeDoor(4));
    }

    private void openDoor(int doorId) {
        // 如果有其他仓门打开，先关闭它
        if (currentOpenDoor != 0 && currentOpenDoor != doorId) {
            closeDoor(currentOpenDoor);
        }

        // 打开新仓门
        currentOpenDoor = doorId;
        updateDoorIndicator(doorId, true);

        // 播放开仓动画
        playDoorAnimation(doorId, true);

        // 更新状态文本
        tvStatus.setText("仓门状态: 仓门" + doorId + "已打开");

        // 显示提示
        Toast.makeText(getContext(), "模拟打开仓门" + doorId, Toast.LENGTH_SHORT).show();

        // 禁用当前仓门的打开按钮
        setDoorOpenButtonEnabled(doorId, false);
    }

    private void closeDoor(int doorId) {
        if (currentOpenDoor == doorId) {
            currentOpenDoor = 0;
            updateDoorIndicator(doorId, false);

            // 播放关仓动画
            playDoorAnimation(doorId, false);

            // 更新状态文本
            tvStatus.setText("仓门状态: 全部关闭");

            // 显示提示
            Toast.makeText(getContext(), "模拟关闭仓门" + doorId, Toast.LENGTH_SHORT).show();
        }

        // 启用当前仓门的打开按钮
        setDoorOpenButtonEnabled(doorId, true);
    }

    private void updateDoorIndicator(int doorId, boolean isOpen) {
        View indicator = getDoorIndicator(doorId);
        if (indicator != null) {
            indicator.setBackgroundResource(isOpen ?
                    R.drawable.door_indicator_open : R.drawable.door_indicator_closed);
        }
    }

    private void setDoorOpenButtonEnabled(int doorId, boolean enabled) {
        Button openButton = getDoorOpenButton(doorId);
        if (openButton != null) {
            openButton.setEnabled(enabled);
            openButton.setAlpha(enabled ? 1.0f : 0.5f);
        }
    }

    private void playDoorAnimation(int doorId, boolean isOpening) {
        View indicator = getDoorIndicator(doorId);
        if (indicator != null) {
            Animation animation = AnimationUtils.loadAnimation(getContext(),
                    isOpening ? R.anim.door_open : R.anim.door_close);
            indicator.startAnimation(animation);
        }
    }

    private View getDoorIndicator(int doorId) {
        switch (doorId) {
            case 1: return door1Indicator;
            case 2: return door2Indicator;
            case 3: return door3Indicator;
            case 4: return door4Indicator;
            default: return null;
        }
    }

    private Button getDoorOpenButton(int doorId) {
        switch (doorId) {
            case 1: return btnDoor1Open;
            case 2: return btnDoor2Open;
            case 3: return btnDoor3Open;
            case 4: return btnDoor4Open;
            default: return null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }
}
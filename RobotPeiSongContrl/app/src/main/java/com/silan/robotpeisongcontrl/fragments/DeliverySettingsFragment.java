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
import com.silan.robotpeisongcontrl.adapter.UsbSerialHelper;
import com.silan.robotpeisongcontrl.utils.ModbusRtuProtocol;

import java.util.HashMap;
import java.util.Map;

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

    private UsbSerialHelper usbSerialHelper;
    private Handler statusCheckHandler = new Handler(Looper.getMainLooper());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Map<Integer, Integer> doorStatus = new HashMap() {{
        put(1, 0); // 0:空闲, 1:开门中, 2:关门中
        put(2, 0);
        put(3, 0);
        put(4, 0);
    }};

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 初始化串口助手
        usbSerialHelper = new UsbSerialHelper(getContext());
        usbSerialHelper.open(null); // 无需UWB监听，传null
        usbSerialHelper.setModbusListener(this::onModbusStatusReceived);
    }

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

    // 处理Modbus状态响应
    private void onModbusStatusReceived(int registerAddr, int status) {
        // 匹配仓门对应的寄存器地址
        int doorId = -1;
        for (Map.Entry<Integer, Integer> entry : usbSerialHelper.doorRegisterMap.entrySet()) {
            if (entry.getValue() == registerAddr) {
                doorId = entry.getKey();
                break;
            }
        }
        if (doorId == -1) return;

        // 解析状态码(协议定义)
        if (status == 0x0101) { // 开门中
            doorStatus.put(doorId, 1);
            tvStatus.setText("仓门状态: 仓门" + doorId + "开门中");
            usbSerialHelper.setLedState(doorId, 2, true, true, true); // LED2:闪烁, RGB全亮
        } else if (status == 0x0102) { // 开门完成
            doorStatus.put(doorId, 0);
            tvStatus.setText("仓门状态: 仓门" + doorId + "已打开");
            usbSerialHelper.setLedState(doorId, 1, true, false, false); // LED1:常亮(红色)
            updateDoorIndicator(doorId, true);
            setDoorOpenButtonEnabled(doorId, false);
        } else if (status == 0x0201) { // 关门中
            doorStatus.put(doorId, 2);
            tvStatus.setText("仓门状态: 仓门" + doorId + "关门中");
            usbSerialHelper.setLedState(doorId, 2, true, true, true); // LED闪烁
        } else if (status == 0x0202) { // 关门完成
            doorStatus.put(doorId, 0);
            tvStatus.setText("仓门状态: 仓门" + doorId + "已关闭");
            usbSerialHelper.setLedState(doorId, 0, false, false, false); // LED关闭
            updateDoorIndicator(doorId, false);
            setDoorOpenButtonEnabled(doorId, true);
        }
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
        if (doorStatus.get(doorId) != 0) {
            Toast.makeText(getContext(), "仓门" + doorId + "正在操作中", Toast.LENGTH_SHORT).show();
            return;
        }
        // 发送开门指令(协议:0100=开门)
        Integer registerAddr = usbSerialHelper.doorRegisterMap.get(doorId);
        if (registerAddr != null) {
            byte[] command = ModbusRtuProtocol.buildWriteCommand(registerAddr, 0x0100);
            usbSerialHelper.sendModbusCommand(command);
            // 启动状态查询(500ms一次)
            startStatusCheck(doorId);
        }
    }


    private void closeDoor(int doorId) {
        if (doorStatus.get(doorId) != 0) {
            Toast.makeText(getContext(), "仓门" + doorId + "正在操作中", Toast.LENGTH_SHORT).show();
            return;
        }
        // 发送关门指令(协议:0200=关门)
        Integer registerAddr = usbSerialHelper.doorRegisterMap.get(doorId);
        if (registerAddr != null) {
            byte[] command = ModbusRtuProtocol.buildWriteCommand(registerAddr, 0x0200);
            usbSerialHelper.sendModbusCommand(command);
            // 启动状态查询
            startStatusCheck(doorId);
        }
    }

    // 定时查询仓门状态
    private void startStatusCheck(int doorId) {
        statusCheckHandler.removeCallbacksAndMessages(doorId);
        statusCheckHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (doorStatus.get(doorId) == 0) return; // 已完成
                // 发送状态查询指令
                Integer registerAddr = usbSerialHelper.doorRegisterMap.get(doorId);
                if (registerAddr != null) {
                    byte[] command = ModbusRtuProtocol.buildReadCommand(registerAddr);
                    usbSerialHelper.sendModbusCommand(command);
                    // 继续查询
                    statusCheckHandler.postDelayed(this, 500);
                }
            }
        }, 500);
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

//    private void playDoorAnimation(int doorId, boolean isOpening) {
//        View indicator = getDoorIndicator(doorId);
//        if (indicator != null) {
//            Animation animation = AnimationUtils.loadAnimation(getContext(),
//                    isOpening ? R.anim.door_open : R.anim.door_close);
//            indicator.startAnimation(animation);
//        }
//    }

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
        statusCheckHandler.removeCallbacksAndMessages(null);
        usbSerialHelper.close();
    }
}
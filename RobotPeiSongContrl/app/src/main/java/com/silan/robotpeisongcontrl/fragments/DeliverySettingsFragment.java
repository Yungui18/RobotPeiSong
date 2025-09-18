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

import java.util.HashSet;
import java.util.Set;

public class DeliverySettingsFragment extends Fragment {

    // 使用Set存储所有打开的仓门
    private Set<Integer> openDoors = new HashSet<>();
    private TextView tvStatus;

    // 动态存储仓门相关视图
    private View[] doorIndicators;
    private Button[] btnDoorOpens;
    private Button[] btnDoorCloses;
    private Button[] btnDoorPauses;

    private int doorCount;
    private final Handler handler = new Handler(Looper.getMainLooper());


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_delivery_settings, container, false);
        // 获取设置的仓门数量
        doorCount = WarehouseDoorSettingsFragment.getDoorCount(requireContext());

        // 初始化视图容器
        doorIndicators = new View[doorCount + 1]; // 1-based index
        btnDoorOpens = new Button[doorCount + 1];
        btnDoorCloses = new Button[doorCount + 1];
        btnDoorPauses = new Button[doorCount + 1];
        // 初始化视图
        initViews(view);
        // 设置按钮点击监听器
        setupButtonListeners();

        return view;
    }

    private void initViews(View view) {
        tvStatus = view.findViewById(R.id.tv_status);
        LinearLayout doorsContainer = view.findViewById(R.id.doors_container);

        // 清空容器
        doorsContainer.removeAllViews();

        // 根据仓门数量动态添加视图
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 1; i <= doorCount; i++) {
            // 加载单个仓门的布局
            View doorView = inflater.inflate(R.layout.item_door_control, doorsContainer, false);

            // 设置标题
            TextView tvDoorTitle = doorView.findViewById(R.id.tv_door_title);
            tvDoorTitle.setText("仓门" + i);

            // 获取并存储视图引用
            doorIndicators[i] = doorView.findViewById(R.id.door_indicator);
            btnDoorOpens[i] = doorView.findViewById(R.id.btn_door_open);
            btnDoorCloses[i] = doorView.findViewById(R.id.btn_door_close);
            btnDoorPauses[i] = doorView.findViewById(R.id.btn_door_pause);

            // 初始状态：关闭状态，打开按钮可用，关闭和暂停按钮不可用
            updateDoorIndicator(i, false);
            btnDoorCloses[i].setEnabled(false);
            btnDoorCloses[i].setAlpha(0.5f);
            btnDoorPauses[i].setEnabled(false);
            btnDoorPauses[i].setAlpha(0.5f);

            // 添加到容器
            doorsContainer.addView(doorView);
        }
    }

    private void setupButtonListeners() {
        for (int i = 1; i <= doorCount; i++) {
            final int doorId = i;
            btnDoorOpens[doorId].setOnClickListener(v -> openDoor(doorId));
            btnDoorCloses[doorId].setOnClickListener(v -> closeDoor(doorId));
            btnDoorPauses[doorId].setOnClickListener(v -> pauseDoor(doorId));
        }
    }

    private void openDoor(int doorId) {
        // 添加到打开的仓门集合
        openDoors.add(doorId);
        updateDoorIndicator(doorId, true);

        // 播放开仓动画
        playDoorAnimation(doorId, true);

        // 更新状态文本
        updateStatusText();

        // 显示提示
        Toast.makeText(getContext(), "模拟打开仓门" + doorId, Toast.LENGTH_SHORT).show();

        // 更新按钮状态
        updateDoorButtonStates(doorId);
    }

    private void closeDoor(int doorId) {
        if (openDoors.contains(doorId)) {
            openDoors.remove(doorId);
            updateDoorIndicator(doorId, false);

            // 播放关仓动画
            playDoorAnimation(doorId, false);

            // 更新状态文本
            updateStatusText();

            // 显示提示
            Toast.makeText(getContext(), "模拟关闭仓门" + doorId, Toast.LENGTH_SHORT).show();
        }

        // 更新按钮状态
        updateDoorButtonStates(doorId);
    }

    private void pauseDoor(int doorId) {
        // 暂停仓门操作逻辑
        Toast.makeText(getContext(), "模拟暂停仓门" + doorId + "操作", Toast.LENGTH_SHORT).show();

        // 暂停动画（如果有）
        if (doorIndicators[doorId] != null) {
            doorIndicators[doorId].clearAnimation();
        }
    }

    private void updateDoorIndicator(int doorId, boolean isOpen) {
        View indicator = doorIndicators[doorId];
        if (indicator != null) {
            indicator.setBackgroundResource(isOpen ?
                    R.drawable.door_indicator_open : R.drawable.door_indicator_closed);
        }
    }

    private void updateDoorButtonStates(int doorId) {
        boolean isOpen = openDoors.contains(doorId);

        btnDoorOpens[doorId].setEnabled(!isOpen);
        btnDoorOpens[doorId].setAlpha(!isOpen ? 1.0f : 0.5f);

        btnDoorCloses[doorId].setEnabled(isOpen);
        btnDoorCloses[doorId].setAlpha(isOpen ? 1.0f : 0.5f);

        btnDoorPauses[doorId].setEnabled(isOpen);
        btnDoorPauses[doorId].setAlpha(isOpen ? 1.0f : 0.5f);
    }

    private void playDoorAnimation(int doorId, boolean isOpening) {
        View indicator = doorIndicators[doorId];
        if (indicator != null) {
            Animation animation = AnimationUtils.loadAnimation(getContext(),
                    isOpening ? R.anim.door_open : R.anim.door_close);
            indicator.startAnimation(animation);
        }
    }

    private void updateStatusText() {
        if (openDoors.isEmpty()) {
            tvStatus.setText("仓门状态: 全部关闭");
        } else {
            StringBuilder sb = new StringBuilder("仓门状态: 已打开 ");
            for (int doorId : openDoors) {
                sb.append("仓门").append(doorId).append(" ");
            }
            tvStatus.setText(sb.toString().trim());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }
}
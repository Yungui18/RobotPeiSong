package com.silan.robotpeisongcontrl.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;

/**
 * 配送密码设置页面
 * 主要功能：
 * 1. 启用/禁用配送密码验证
 * 2. 设置送物和取物密码
 */
public class DeliveryPasswordSettingsFragment extends Fragment {

    private Switch switchEnableVerification;
    private LinearLayout layoutPasswordSettings;
    private EditText etDeliveryPassword, etPickupPassword;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 加载布局文件
        View view = inflater.inflate(R.layout.fragment_delivery_password_settings, container, false);

        // 初始化视图组件
        switchEnableVerification = view.findViewById(R.id.switch_enable_verification);
        layoutPasswordSettings = view.findViewById(R.id.layout_password_settings);
        etDeliveryPassword = view.findViewById(R.id.et_delivery_password);
        etPickupPassword = view.findViewById(R.id.et_pickup_password);
        Button btnSave = view.findViewById(R.id.btn_save_passwords);

        // 从SharedPreferences加载设置
        SharedPreferences prefs = requireActivity().getSharedPreferences("delivery_prefs", 0);
        boolean isEnabled = prefs.getBoolean("verification_enabled", false);
        switchEnableVerification.setChecked(isEnabled);
        layoutPasswordSettings.setVisibility(isEnabled ? View.VISIBLE : View.GONE);

        // 开关监听
        switchEnableVerification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutPasswordSettings.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            prefs.edit().putBoolean("verification_enabled", isChecked).apply();
        });

        // 加载保存的密码
        etDeliveryPassword.setText(prefs.getString("delivery_password", ""));
        etPickupPassword.setText(prefs.getString("pickup_password", ""));

        // 保存按钮点击事件
        btnSave.setOnClickListener(v -> savePasswords());

        return view;
    }

    /**
     * 保存密码设置
     */
    private void savePasswords() {
        String deliveryPass = etDeliveryPassword.getText().toString();
        String pickupPass = etPickupPassword.getText().toString();

        // 保存到SharedPreferences
        SharedPreferences prefs = requireActivity().getSharedPreferences("delivery_prefs", 0);
        prefs.edit()
                .putString("delivery_password", deliveryPass)
                .putString("pickup_password", pickupPass)
                .apply();

        Toast.makeText(getContext(), "配送密码已保存", Toast.LENGTH_SHORT).show();
    }
}
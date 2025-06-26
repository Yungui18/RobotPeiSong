package com.silan.robotpeisongcontrl.fragments;


import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;

public class DeliveryVerificationFragment extends Fragment {

    private Switch switchVerification;
    private LinearLayout layoutPasswordSettings;
    private TextView tvPickupPassword, tvDeliveryPassword;
    private Button btnSetPickupPassword, btnSetDeliveryPassword;
    private AlertDialog passwordDialog;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 加载布局文件
        View view = inflater.inflate(R.layout.fragment_delivery_verification, container, false);

        // 初始化视图组件
        switchVerification = view.findViewById(R.id.switch_verification);
        layoutPasswordSettings = view.findViewById(R.id.layout_password_settings);
        tvPickupPassword = view.findViewById(R.id.tv_pickup_password);
        tvDeliveryPassword = view.findViewById(R.id.tv_delivery_password);
        btnSetPickupPassword = view.findViewById(R.id.btn_set_pickup_password);
        btnSetDeliveryPassword = view.findViewById(R.id.btn_set_delivery_password);

        // 加载保存的设置
        loadVerificationSettings();

        // 设置开关监听器
        switchVerification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutPasswordSettings.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            saveVerificationSettings();
        });

        // 设置密码按钮点击事件
        btnSetPickupPassword.setOnClickListener(v -> showPasswordDialog("取物密码", "pickup_password"));
        btnSetDeliveryPassword.setOnClickListener(v -> showPasswordDialog("送物密码", "delivery_password"));

        return view;
    }

    /**
     * 加载配送验证设置
     */
    private void loadVerificationSettings() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("delivery_prefs", Context.MODE_PRIVATE);
        boolean verificationEnabled = prefs.getBoolean("verification_enabled", false);
        String pickupPassword = prefs.getString("pickup_password", "");
        String deliveryPassword = prefs.getString("delivery_password", "");

        switchVerification.setChecked(verificationEnabled);
        tvPickupPassword.setText(getMaskedPassword(pickupPassword));
        tvDeliveryPassword.setText(getMaskedPassword(deliveryPassword));
        layoutPasswordSettings.setVisibility(verificationEnabled ? View.VISIBLE : View.GONE);
    }

    /**
     * 保存配送验证设置
     */
    private void saveVerificationSettings() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("delivery_prefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean("verification_enabled", switchVerification.isChecked())
                .apply();
    }

    /**
     * 显示密码设置对话框
     * @param title 对话框标题
     * @param passwordType 密码类型（"pickup_password" 或 "delivery_password"）
     */
    private void showPasswordDialog(String title, String passwordType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_password_auth, null);
        builder.setView(dialogView);

        // 设置标题
        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);
        // 关闭按钮
        ImageButton btnClose = dialogView.findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> {
            if (passwordDialog != null && passwordDialog.isShowing()) {
                passwordDialog.dismiss();
            }
        });

        // 初始化视图
        LinearLayout dotsContainer = dialogView.findViewById(R.id.dots_container);
        Button[] numberButtons = new Button[10];
        numberButtons[0] = dialogView.findViewById(R.id.btn_0);
        numberButtons[1] = dialogView.findViewById(R.id.btn_1);
        numberButtons[2] = dialogView.findViewById(R.id.btn_2);
        numberButtons[3] = dialogView.findViewById(R.id.btn_3);
        numberButtons[4] = dialogView.findViewById(R.id.btn_4);
        numberButtons[5] = dialogView.findViewById(R.id.btn_5);
        numberButtons[6] = dialogView.findViewById(R.id.btn_6);
        numberButtons[7] = dialogView.findViewById(R.id.btn_7);
        numberButtons[8] = dialogView.findViewById(R.id.btn_8);
        numberButtons[9] = dialogView.findViewById(R.id.btn_9);
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);

        // 初始化密码圆点
        createPasswordDots(dotsContainer);

        // 当前输入的密码
        StringBuilder currentPassword = new StringBuilder();

        passwordDialog = builder.create();
        passwordDialog.setCanceledOnTouchOutside(false);
        passwordDialog.show();

        // 设置数字按钮点击事件
        for (int i = 0; i < numberButtons.length; i++) {
            final int digit = i;
            numberButtons[i].setOnClickListener(v -> {
                if (currentPassword.length() < 4) {
                    currentPassword.append(digit);
                    updateDotsDisplay(dotsContainer, currentPassword.length());

                    // 自动保存密码
                    if (currentPassword.length() == 4) {
                        savePassword(passwordType, currentPassword.toString());
                        passwordDialog.dismiss();
                    }
                }
            });
        }

        // 设置删除按钮点击事件
        btnDelete.setOnClickListener(v -> {
            if (currentPassword.length() > 0) {
                currentPassword.deleteCharAt(currentPassword.length() - 1);
                updateDotsDisplay(dotsContainer, currentPassword.length());
            }
        });

        // 重置输入状态
        currentPassword.setLength(0);
        updateDotsDisplay(dotsContainer, 0);
    }

    /**
     * 创建密码圆点指示器
     */
    private void createPasswordDots(LinearLayout dotsContainer) {
        dotsContainer.removeAllViews();

        int dotSize = getResources().getDimensionPixelSize(R.dimen.password_dot_size);
        int margin = getResources().getDimensionPixelSize(R.dimen.password_dot_margin);

        for (int i = 0; i < 4; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
            params.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(params);

            // 创建圆形背景
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(getResources().getDimensionPixelSize(R.dimen.password_dot_stroke), Color.GRAY);
            dot.setBackground(bg);

            dotsContainer.addView(dot);
        }
    }

    /**
     * 更新圆点显示状态
     */
    private void updateDotsDisplay(LinearLayout dotsContainer, int length) {
        for (int i = 0; i < dotsContainer.getChildCount(); i++) {
            View dot = dotsContainer.getChildAt(i);
            if (dot != null) {
                GradientDrawable bg = (GradientDrawable) dot.getBackground();
                if (i < length) {
                    // 填充的圆点
                    bg.setColor(Color.BLACK);
                    bg.setStroke(0, Color.TRANSPARENT);
                } else {
                    // 空心的圆点
                    bg.setColor(Color.TRANSPARENT);
                    bg.setStroke(getResources().getDimensionPixelSize(R.dimen.password_dot_stroke), Color.GRAY);
                }
            }
        }
    }

    /**
     * 保存密码
     */
    private void savePassword(String passwordType, String password) {
        SharedPreferences prefs = requireActivity().getSharedPreferences("delivery_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString(passwordType, password).apply();

        // 更新UI显示
        if (passwordType.equals("pickup_password")) {
            tvPickupPassword.setText(getMaskedPassword(password));
        } else {
            tvDeliveryPassword.setText(getMaskedPassword(password));
        }

        Toast.makeText(requireContext(), "密码设置成功", Toast.LENGTH_SHORT).show();
    }

    /**
     * 获取掩码密码（显示为星号）
     */
    private String getMaskedPassword(String password) {
        if (password.isEmpty()) {
            return "未设置";
        }
        return "●●●●";
    }
}
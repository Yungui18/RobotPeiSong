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
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.utils.PasswordManager;

/**
 * 管理员密码设置页面
 * 主要功能：
 * 1. 修改管理员密码
 * 2. 重置密码为默认值
 * 3. 验证密码规则（4位数字）
 */
public class AdminPasswordSettingsFragment extends Fragment {

    private TextView tvStatus;
    private EditText etCurrentPassword, etNewPassword, etConfirmPassword;
    private AlertDialog passwordDialog;

    // 存储实际密码值
    private String realCurrentPassword = "";
    private String realNewPassword = "";
    private String realConfirmPassword = "";

    // 当前正在编辑的字段
    private int currentEditingField = -1; // 0=当前密码, 1=新密码, 2=确认密码

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_password_settings, container, false);

        tvStatus = view.findViewById(R.id.tv_status);
        Button btnSave = view.findViewById(R.id.btn_save_password);
        Button btnReset = view.findViewById(R.id.btn_reset_password);

        etCurrentPassword = view.findViewById(R.id.et_current_password);
        etNewPassword = view.findViewById(R.id.et_new_password);
        etConfirmPassword = view.findViewById(R.id.et_confirm_password);

        // 设置输入框不可编辑（只能通过弹窗输入）
        etCurrentPassword.setFocusable(false);
        etNewPassword.setFocusable(false);
        etConfirmPassword.setFocusable(false);
        etCurrentPassword.setLongClickable(false);
        etNewPassword.setLongClickable(false);
        etConfirmPassword.setLongClickable(false);

        // 点击输入框时弹出数字键盘
        etCurrentPassword.setOnClickListener(v -> {
            currentEditingField = 0;
            showPasswordInputDialog("输入当前密码");
        });

        etNewPassword.setOnClickListener(v -> {
            currentEditingField = 1;
            showPasswordInputDialog("输入新密码");
        });

        etConfirmPassword.setOnClickListener(v -> {
            currentEditingField = 2;
            showPasswordInputDialog("确认新密码");
        });

        btnSave.setOnClickListener(v -> savePassword());
        btnReset.setOnClickListener(v -> resetPassword());

        return view;
    }

    private void showPasswordInputDialog(String title) {
        // 创建弹窗
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_password_auth, null);
        builder.setView(dialogView);

        // 设置标题
        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        // 关闭按钮
        ImageButton btnClose = dialogView.findViewById(R.id.btn_close);

        // 初始化密码圆点
        LinearLayout dotsContainer = dialogView.findViewById(R.id.dots_container);
        createPasswordDots(dotsContainer, 4);

        // 数字按钮
        Button[] numberButtons = new Button[10];
        for (int i = 0; i < 10; i++) {
            int resId = getResources().getIdentifier("btn_" + i, "id", requireContext().getPackageName());
            numberButtons[i] = dialogView.findViewById(resId);
        }
        Button btnDelete = dialogView.findViewById(R.id.btn_delete);

        // 当前输入的密码
        StringBuilder enteredPassword = new StringBuilder();

        passwordDialog = builder.create();
        passwordDialog.setCanceledOnTouchOutside(false);
        passwordDialog.show();

        // 设置数字按钮事件
        for (int i = 0; i < numberButtons.length; i++) {
            final int digit = i;
            numberButtons[i].setOnClickListener(v -> {
                if (enteredPassword.length() < 4) {
                    enteredPassword.append(digit);
                    updateDotsDisplay(dotsContainer, enteredPassword.length());

                    // 自动完成
                    if (enteredPassword.length() == 4) {
                        updatePasswordField(enteredPassword.toString());
                        passwordDialog.dismiss();
                    }
                }
            });
        }

        // 删除按钮
        btnDelete.setOnClickListener(v -> {
            if (enteredPassword.length() > 0) {
                enteredPassword.deleteCharAt(enteredPassword.length() - 1);
                updateDotsDisplay(dotsContainer, enteredPassword.length());
            }
        });

        // 关闭按钮事件
        btnClose.setOnClickListener(v -> passwordDialog.dismiss());

        // 更新圆点显示
        updateDotsDisplay(dotsContainer, enteredPassword.length());
    }

    private void updatePasswordField(String password) {
        // 存储实际密码值
        switch (currentEditingField) {
            case 0: // 当前密码
                realCurrentPassword = password;
                etCurrentPassword.setText(getMaskedPassword(password));
                break;
            case 1: // 新密码
                realNewPassword = password;
                etNewPassword.setText(getMaskedPassword(password));
                break;
            case 2: // 确认密码
                realConfirmPassword = password;
                etConfirmPassword.setText(getMaskedPassword(password));
                break;
        }
    }

    private String getMaskedPassword(String password) {
        // 使用全角圆点字符
        return "●".repeat(password.length());
    }

    private void createPasswordDots(LinearLayout container, int count) {
        container.removeAllViews();
        int dotSize = getResources().getDimensionPixelSize(R.dimen.password_dot_size);
        int margin = getResources().getDimensionPixelSize(R.dimen.password_dot_margin);

        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
            params.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(params);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(getResources().getDimensionPixelSize(R.dimen.password_dot_stroke), Color.GRAY);
            dot.setBackground(bg);

            container.addView(dot);
        }
    }

    private void updateDotsDisplay(LinearLayout dotsContainer, int length) {
        for (int i = 0; i < dotsContainer.getChildCount(); i++) {
            View dot = dotsContainer.getChildAt(i);
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

    private void savePassword() {
        // 使用存储的实际密码值
        String current = realCurrentPassword;
        String newPass = realNewPassword;
        String confirm = realConfirmPassword;

        // 验证输入是否完整
        if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
            tvStatus.setText("所有字段必须填写");
            return;
        }

        // 验证新密码长度
        if (newPass.length() != 4) {
            tvStatus.setText("新密码必须是4位数字");
            return;
        }

        // 验证两次输入的新密码是否一致
        if (!newPass.equals(confirm)) {
            tvStatus.setText("两次输入的新密码不一致");
            return;
        }

        // 获取存储的密码
        String savedPassword = PasswordManager.getSettingsPassword(requireContext());

        // 验证当前密码是否正确
        if (!current.equals(savedPassword)) {
            tvStatus.setText("当前密码不正确");
            return;
        }

        // 保存新密码
        PasswordManager.saveSettingsPassword(requireContext(), newPass);
        tvStatus.setText("");

        // 清空输入框
        etCurrentPassword.setText("");
        etNewPassword.setText("");
        etConfirmPassword.setText("");

        // 清空实际密码值
        realCurrentPassword = "";
        realNewPassword = "";
        realConfirmPassword = "";

        Toast.makeText(requireContext(), "管理员密码已更新", Toast.LENGTH_SHORT).show();
    }

    private void resetPassword() {
        showPasswordInputDialog("验证管理员密码");

        passwordDialog.setOnDismissListener(dialog -> {
            // 重置为默认密码
            PasswordManager.saveSettingsPassword(requireContext(), PasswordManager.DEFAULT_PASSWORD);

            // 清空输入框
            etCurrentPassword.setText("");
            etNewPassword.setText("");
            etConfirmPassword.setText("");

            // 清空实际密码值
            realCurrentPassword = "";
            realNewPassword = "";
            realConfirmPassword = "";

            Toast.makeText(requireContext(),
                    "密码已重置为 " + PasswordManager.DEFAULT_PASSWORD,
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (passwordDialog != null && passwordDialog.isShowing()) {
            passwordDialog.dismiss();
            passwordDialog = null;
        }
    }
}
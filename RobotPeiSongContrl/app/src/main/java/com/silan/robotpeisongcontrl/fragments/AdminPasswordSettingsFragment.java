package com.silan.robotpeisongcontrl.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;

/**
 * 管理员密码设置页面
 * 主要功能：
 * 1. 修改管理员密码
 * 2. 重置密码为默认值
 * 3. 验证密码规则（4位数字）
 */
public class AdminPasswordSettingsFragment extends Fragment {

    private EditText etCurrentPassword, etNewPassword, etConfirmPassword;
    private TextView tvStatus;
    private Button btnSave;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 加载布局文件
        View view = inflater.inflate(R.layout.fragment_admin_password_settings, container, false);

        // 初始化视图组件
        etCurrentPassword = view.findViewById(R.id.et_current_password);
        etNewPassword = view.findViewById(R.id.et_new_password);
        etConfirmPassword = view.findViewById(R.id.et_confirm_password);
        tvStatus = view.findViewById(R.id.tv_status);
        btnSave = view.findViewById(R.id.btn_save_password);

        // 保存按钮点击事件
        btnSave.setOnClickListener(v -> savePassword());

        return view;
    }

    /**
     * 保存密码
     */
    private void savePassword() {
        String current = etCurrentPassword.getText().toString().trim();
        String newPass = etNewPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

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
        SharedPreferences prefs = requireActivity().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE);
        String savedPassword = prefs.getString("admin_password", "1234"); // 默认密码

        // 验证当前密码是否正确
        if (!current.equals(savedPassword)) {
            tvStatus.setText("当前密码不正确");
            return;
        }

        // 保存新密码
        prefs.edit().putString("admin_password", newPass).apply();
        tvStatus.setText("");

        // 清空输入框
        etCurrentPassword.setText("");
        etNewPassword.setText("");
        etConfirmPassword.setText("");

        Toast.makeText(getContext(), "管理员密码已更新", Toast.LENGTH_SHORT).show();
    }

    /**
     * 重置密码按钮点击事件
     * @param view 被点击的视图
     */
    public void onResetPasswordClick(View view) {
        // 重置为默认密码 "1234"
        SharedPreferences prefs = requireActivity().getSharedPreferences("admin_prefs", Context.MODE_PRIVATE);
        prefs.edit().putString("admin_password", "1234").apply();

        // 清空输入框
        etCurrentPassword.setText("");
        etNewPassword.setText("");
        etConfirmPassword.setText("");
        tvStatus.setText("");

        Toast.makeText(getContext(), "密码已重置为1234", Toast.LENGTH_SHORT).show();
    }
}
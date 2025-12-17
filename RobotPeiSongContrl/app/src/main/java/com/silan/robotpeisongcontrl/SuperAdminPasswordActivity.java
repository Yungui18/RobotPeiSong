package com.silan.robotpeisongcontrl;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.silan.robotpeisongcontrl.utils.PasswordManager;

public class SuperAdminPasswordActivity extends BaseActivity {

    private AlertDialog passwordDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_super_admin_password);

        // 返回按钮
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 修改超级管理员密码
        findViewById(R.id.btn_change_super_admin_password).setOnClickListener(v -> {
            showPasswordInputDialog("修改超级管理员密码", PasswordManager.PASSWORD_TYPE_SUPER_ADMIN);
        });

        // 重置普通管理员密码
        findViewById(R.id.btn_reset_admin_password).setOnClickListener(v -> {
            PasswordManager.saveSettingsPassword(this, PasswordManager.DEFAULT_PASSWORD);
            Toast.makeText(this, "普通管理员密码已重置为默认密码", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected boolean isAdminPage() {
        return true; // 标记为超级管理员页面
    }

    private void showPasswordInputDialog(String title, String passwordType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_password_auth, null);
        builder.setView(dialogView);

        // 设置标题
        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        // 关闭按钮
        ImageButton btnClose = dialogView.findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> {
            if (passwordDialog != null) {
                passwordDialog.dismiss();
            }
        });

        // 初始化视图
        LinearLayout dotsContainer = dialogView.findViewById(R.id.dots_container);
        int passwordLength = PasswordManager.PASSWORD_TYPE_SUPER_ADMIN.equals(passwordType) ? 6 : 4;
        createPasswordDots(dotsContainer, passwordLength);

        Button[] numberButtons = new Button[10];
        for (int i = 0; i < 10; i++) {
            int resId = getResources().getIdentifier("btn_" + i, "id", getPackageName());
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
                if (enteredPassword.length() < passwordLength) {
                    enteredPassword.append(digit);
                    updateDotsDisplay(dotsContainer, enteredPassword.length());

                    // 自动完成
                    if (enteredPassword.length() == passwordLength) {
                        savePassword(passwordType, enteredPassword.toString());
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

        // 更新圆点显示
        updateDotsDisplay(dotsContainer, enteredPassword.length());
    }

    private void createPasswordDots(LinearLayout container, int count) {
        container.removeAllViews();
        int dotSize = getResources().getDimensionPixelSize(R.dimen.password_dot_size);
        int margin = getResources().getDimensionPixelSize(R.dimen.password_dot_margin);

        for (int i = 0; i < count; i++) {
            View dot = new View(this);
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

    private void savePassword(String passwordType, String password) {
        if (PasswordManager.PASSWORD_TYPE_SUPER_ADMIN.equals(passwordType)) {
            PasswordManager.saveSuperAdminPassword(this, password);
            Toast.makeText(this, "超级管理员密码已更新", Toast.LENGTH_SHORT).show();
        } else {
            PasswordManager.saveSettingsPassword(this, password);
            Toast.makeText(this, "普通管理员密码已更新", Toast.LENGTH_SHORT).show();
        }
    }
}
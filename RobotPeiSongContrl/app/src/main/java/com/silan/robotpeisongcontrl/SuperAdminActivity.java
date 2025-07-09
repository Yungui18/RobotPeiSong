package com.silan.robotpeisongcontrl;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.silan.robotpeisongcontrl.adapter.SettingsAdapter;
import com.silan.robotpeisongcontrl.utils.PasswordManager;

public class SuperAdminActivity extends BaseActivity  {

    private static final String[] SUPER_ADMIN_MENU = {
            "服务设置",
            "密码设置",
            "硬件版本",
            "恢复出厂设置"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_super_admin);

        // 标题
        TextView title = findViewById(R.id.tv_super_admin_title);
        title.setText("超级管理员设置");

        // 菜单列表
        ListView listView = findViewById(R.id.super_admin_list);
        SettingsAdapter adapter = new SettingsAdapter(this, SUPER_ADMIN_MENU);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0: // 服务设置
                    startActivity(new Intent(this, ServiceSettingsActivity.class));
                    break;
                case 1: // 密码设置
                    startActivity(new Intent(this, AdminPasswordSettingsActivity.class)
                            .putExtra("is_super_admin", true));
                    break;
                case 2: // 硬件版本
                    showHardwareInfo();
                    break;
                case 3: // 恢复出厂设置
                    performFactoryReset();
                    break;
            }
        });
    }

    private void showHardwareInfo() {
        // 获取硬件信息并显示
        new AlertDialog.Builder(this)
                .setTitle("硬件信息")
                .setMessage("机器人型号: RPA-3000\n硬件版本: v2.1\n序列号: SN-20230704-001")
                .setPositiveButton("确定", null)
                .show();
    }

    private void performFactoryReset() {
        new AlertDialog.Builder(this)
                .setTitle("恢复出厂设置")
                .setMessage("确定要恢复出厂设置吗？所有设置将被重置！")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 清除所有设置
                    getSharedPreferences("personalization_prefs", MODE_PRIVATE).edit().clear().apply();
                    getSharedPreferences("delivery_prefs", MODE_PRIVATE).edit().clear().apply();
                    getSharedPreferences("password_prefs", MODE_PRIVATE).edit().clear().apply();
                    getSharedPreferences("PatrolSchemes", MODE_PRIVATE).edit().clear().apply();

                    // 重置密码为默认值
                    PasswordManager.saveSettingsPassword(this, PasswordManager.DEFAULT_PASSWORD);
                    PasswordManager.saveSuperAdminPassword(this, PasswordManager.DEFAULT_SUPER_ADMIN_PASSWORD);

                    Toast.makeText(this, "已恢复出厂设置", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
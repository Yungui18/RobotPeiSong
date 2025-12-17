package com.silan.robotpeisongcontrl;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.adapter.SettingsAdapter;
import com.silan.robotpeisongcontrl.fragments.BasicSettingsFragment;
import com.silan.robotpeisongcontrl.utils.PasswordManager;

public class SuperAdminActivity extends BaseActivity {

    private static final String[] SUPER_ADMIN_MENU = {
            "服务设置",
            "密码设置",
            "基础设置",
            "手动参数设置",
            "硬件版本",
            "恢复出厂设置"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_super_admin);

        // 返回按钮
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.super_admin_menu);
        SettingsAdapter adapter = new SettingsAdapter(this, SUPER_ADMIN_MENU);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0: // 服务设置
                    startActivity(new Intent(SuperAdminActivity.this, ServiceSettingsActivity.class));
                    break;
                case 1: // 密码设置
                    startActivity(new Intent(SuperAdminActivity.this, SuperAdminPasswordActivity.class));
                    break;
                case 2: // 基础设置
                    startActivity(new Intent(SuperAdminActivity.this, BasicSettingsHostActivity.class));
                    break;
                case 3: // 手动参数设置
                    startActivity(new Intent(SuperAdminActivity.this, ManualParamsSettingsActivity.class));
                    break;
                case 4: // 硬件版本
                    showHardwareVersion();
                    break;
                case 5: // 恢复出厂设置
                    performFactoryReset();
                    break;
            }
        });
    }

    @Override
    protected boolean isAdminPage() {
        return true; // 标记为超级管理员页面
    }

    private void showHardwareVersion() {
        // 获取硬件版本信息
        String hardwareInfo = "机器人型号: R1\n" +
                "硬件版本: v2.0\n" +
                "序列号: SN-202405001";

        new AlertDialog.Builder(this)
                .setTitle("硬件版本")
                .setMessage(hardwareInfo)
                .setPositiveButton("确定", null)
                .show();
    }

    private void performFactoryReset() {
        new AlertDialog.Builder(this)
                .setTitle("恢复出厂设置")
                .setMessage("确定要恢复出厂设置吗？此操作将清除所有自定义设置。")
                .setPositiveButton("确定", (dialog, which) -> {
                    // 执行恢复出厂设置
                    resetAllSettings();
                    Toast.makeText(this, "已恢复出厂设置", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void resetAllSettings() {
        // 重置所有设置
        SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
        prefs.edit().clear().apply();

        prefs = getSharedPreferences("personalization_prefs", MODE_PRIVATE);
        prefs.edit().clear().apply();

        prefs = getSharedPreferences("service_prefs", MODE_PRIVATE);
        prefs.edit().clear().apply();

        // 重置管理员密码
        PasswordManager.saveSettingsPassword(this, PasswordManager.DEFAULT_PASSWORD);

        // 重置超级管理员密码
        PasswordManager.saveSuperAdminPassword(this, PasswordManager.DEFAULT_SUPER_ADMIN_PASSWORD);
    }
}
package com.silan.robotpeisongcontrl;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ServiceSettingsActivity extends BaseActivity  {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_settings_main);

        // 设置标题
        TextView title = findViewById(R.id.tv_settings_title);
        title.setText("服务设置");

        // 设置菜单项
        Button btnServiceEnable = findViewById(R.id.btn_volume_settings);
        btnServiceEnable.setText("服务启用");

        Button btnStandbySettings = findViewById(R.id.btn_personalization_settings);
        btnStandbySettings.setText("待机设置");

        // 隐藏不需要的按钮
        findViewById(R.id.btn_admin_password_settings).setVisibility(View.GONE);
        findViewById(R.id.btn_delivery_settings).setVisibility(View.GONE);
        findViewById(R.id.btn_delivery_verification).setVisibility(View.GONE);

        // 设置点击事件
        btnServiceEnable.setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, new ServiceEnableFragment())
                    .commit();
        });

        btnStandbySettings.setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, new StandbySettingsFragment())
                    .commit();
        });
    }
}
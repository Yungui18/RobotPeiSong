package com.silan.robotpeisongcontrl;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.silan.robotpeisongcontrl.fragments.ServiceEnableFragment;
import com.silan.robotpeisongcontrl.fragments.StandbySettingsFragment;

public class ServiceSettingsActivity extends BaseActivity  {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_settings);

        // 返回按钮
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 加载服务启用Fragment作为默认
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content_frame, new ServiceEnableFragment())
                .commit();

        // 设置菜单点击事件
        findViewById(R.id.btn_service_enable).setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, new ServiceEnableFragment())
                    .commit();
        });

        findViewById(R.id.btn_standby_settings).setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, new StandbySettingsFragment())
                    .commit();
        });
    }
}
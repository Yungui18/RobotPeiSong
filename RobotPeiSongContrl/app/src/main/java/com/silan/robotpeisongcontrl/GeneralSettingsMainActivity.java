package com.silan.robotpeisongcontrl;


import android.os.Bundle;
import android.widget.ImageButton;


import androidx.appcompat.app.AppCompatActivity;


import com.silan.robotpeisongcontrl.fragments.AdminPasswordSettingsFragment;
import com.silan.robotpeisongcontrl.fragments.DeliverySettingsFragment;
import com.silan.robotpeisongcontrl.fragments.DeliveryVerificationFragment;
import com.silan.robotpeisongcontrl.fragments.PersonalizationSettingsFragment;

/**
 * 通用设置主页面
 * 主要功能：
 * 1. 提供音量设置、个性化设置、管理员密码设置、配送设置入口
 */
public class GeneralSettingsMainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_settings_main);
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 设置按钮点击事件
        findViewById(R.id.btn_volume_settings).setOnClickListener(v -> {
//            startActivity(new Intent(this, VolumeSettingsActivity.class));
        });

        findViewById(R.id.btn_personalization_settings).setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, new PersonalizationSettingsFragment())
                    .commit();
        });

        findViewById(R.id.btn_admin_password_settings).setOnClickListener(v -> {
            // 直接加载管理员密码设置Fragment
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, new AdminPasswordSettingsFragment())
                    .commit();
        });

        findViewById(R.id.btn_delivery_settings).setOnClickListener(v -> {
            // 直接加载配送设置Fragment
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, new DeliverySettingsFragment())
                    .commit();
        });

        findViewById(R.id.btn_delivery_verification).setOnClickListener(v -> {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_frame, new DeliveryVerificationFragment())
                    .commit();
        });
    }
}
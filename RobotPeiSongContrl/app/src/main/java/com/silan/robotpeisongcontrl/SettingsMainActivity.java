package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import com.silan.robotpeisongcontrl.adapter.SettingsAdapter;

/**
 * 设置主设置界面，包含导航
 * 主要功能：
 * 1. 显示设置中心标题
 * 2. 提供垂直排列导航菜单列表
 * 3. 处理导航项点击事件
 */
public class SettingsMainActivity extends BaseActivity{

    private static final String[] SETTINGS_MENU = {
            "通用设置",
            "语言选择",
            "声音选择",
            "巡航配送设置",
            "关于本机"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_main);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        ListView listView = findViewById(R.id.settings_list);
        SettingsAdapter adapter = new SettingsAdapter(this, SETTINGS_MENU);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            switch (position) {
                case 0: // 通用设置
                    startActivity(new Intent(this, GeneralSettingsMainActivity.class));
                    break;
                case 1: // 语言选择
                    startActivity(new Intent(this, LanguageSettingsActivity.class));
                    break;
                case 2: // 声音选择
                    startActivity(new Intent(this, SoundSettingsActivity.class));
                    break;
                case 3:
                    startActivity(new Intent(this, PatrolSettingsActivity.class));
                    break;
                case 4: // 关于本机
                    startActivity(new Intent(this, AboutDeviceActivity.class));
                    break;
            }
        });
    }
}

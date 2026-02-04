package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.silan.robotpeisongcontrl.adapter.SettingsAdapter;
import com.silan.robotpeisongcontrl.utils.LoadingDialogUtil;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;

import okio.ByteString;

/**
 * 设置主设置界面，包含导航
 * 主要功能：
 * 1. 显示设置中心标题
 * 2. 提供垂直排列导航菜单列表
 * 3. 处理导航项点击事件
 */
public class SettingsMainActivity extends BaseActivity implements MainActivity.OnMainInitCompleteListener {
    private static final String TAG = "SettingsMainActivity";
    private static final String[] SETTINGS_MENU = {
            "通用设置",
            "语言选择",
            "声音选择",
            "巡航配送设置",
            "定时配送",
            "图表管理设置",
            "关于本机"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_main);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            // ===== 修复1：增加重复点击保护 =====
            if (LoadingDialogUtil.isDialogShowing()) {
                return;
            }

            // 1. 显示加载弹窗
            LoadingDialogUtil.showLoadingDialog(this, "主界面初始化中，请稍候...");

            // 2. 先移除旧监听器，再添加新监听器
            MainActivity.removeMainInitListener(this);
            MainActivity.addMainInitListener(this);

            // 3. 增加超时保护：3秒后强制关闭弹窗
            new android.os.Handler().postDelayed(() -> {
                if (LoadingDialogUtil.isDialogShowing()) {
                    LoadingDialogUtil.dismissLoadingDialog();
                    // 直接返回主界面
                    Intent intent = new Intent(SettingsMainActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                }
            }, 3000);
        });

        Button btnPersistSave = findViewById(R.id.btn_persist_save);
        btnPersistSave.setOnClickListener(v -> {
            // 显示加载弹窗
            LoadingDialogUtil.showLoadingDialog(SettingsMainActivity.this, "正在执行持久化保存...");
            // 调用RobotController中的接口方法
            RobotController.persistMapSave(new OkHttpUtils.ResponseCallback() {
                @Override
                public void onSuccess(ByteString responseData) {
                    runOnUiThread(() -> {
                        LoadingDialogUtil.dismissLoadingDialog();
                        Toast.makeText(SettingsMainActivity.this, "持久化保存成功", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() -> {
                        LoadingDialogUtil.dismissLoadingDialog();
                        Toast.makeText(SettingsMainActivity.this, "持久化保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e(TAG, "持久化保存失败", e);
                    });
                }
            });
        });

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
                case 3: // 巡航设置
                    startActivity(new Intent(this, PatrolSettingsActivity.class));
                    break;
                case 4: // 定时配送
                    startActivity(new Intent(this, ScheduledDeliveryActivity.class));
                    break;
                case 5: // 图表管理设置
                    startActivity(new Intent(this, ChartManagementActivity.class));
                    break;
                case 6: // 关于本机
                    startActivity(new Intent(this, AboutDeviceActivity.class));
                    break;
            }
        });
    }

    @Override
    public void onInitComplete() {
        // 关闭加载弹窗
        LoadingDialogUtil.dismissLoadingDialog();
        // 跳转主界面
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onInitFailed() {
        // 初始化失败，关闭弹窗并提示
        LoadingDialogUtil.dismissLoadingDialog();
        Log.i(TAG, "onInitFailed: 主界面初始化失败，请重试");
        Toast.makeText(this, "主界面初始化失败，请重试", Toast.LENGTH_SHORT).show();

        // 提供备选方案：直接跳转到主界面
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 移除监听器，防止内存泄漏
        MainActivity.removeMainInitListener(this);
        // 关闭弹窗，避免残留
        LoadingDialogUtil.dismissLoadingDialog();
    }

    // ===== 修复：重写返回键 =====
    @Override
    public void onBackPressed() {
        // 直接返回主界面，不走监听流程
        super.onBackPressed();
        LoadingDialogUtil.dismissLoadingDialog();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}

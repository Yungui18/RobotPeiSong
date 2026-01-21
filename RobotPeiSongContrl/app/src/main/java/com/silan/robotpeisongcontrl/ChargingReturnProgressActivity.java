package com.silan.robotpeisongcontrl;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ChargingReturnProgressActivity extends BaseActivity  {
    private TextView tvChargingProgress;
    private BroadcastReceiver chargingReturnReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_charging_return_progress);

        initViews();
        registerBroadcastReceiver();
    }

    private void initViews() {
        tvChargingProgress = findViewById(R.id.tv_charging_progress);
    }

    /**
     * 注册广播接收器，接收回桩完成/失败通知
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerBroadcastReceiver() {
        chargingReturnReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (TextUtils.isEmpty(action)) return;

                switch (action) {
                    case "ACTION_CHARGING_RETURN_COMPLETED":
                        // 回桩完成，跳转主界面
                        jumpToMainActivity();
                        break;
                    case "ACTION_CHARGING_RETURN_FAILED":
                        // 回桩失败，展示错误信息
                        String errorMsg = intent.getStringExtra("ERROR_MSG");
                        tvChargingProgress.setText("回桩失败：" + errorMsg);
                        // 3秒后返回回收页面
                        new Handler(Looper.getMainLooper()).postDelayed(() -> finish(), 3000);
                        break;
                }
            }
        };

        // 注册广播
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("ACTION_CHARGING_RETURN_COMPLETED");
        intentFilter.addAction("ACTION_CHARGING_RETURN_FAILED");
        registerReceiver(chargingReturnReceiver, intentFilter);
    }

    /**
     * 跳转至主界面，并关闭所有中间页面
     */
    private void jumpToMainActivity() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        // 清除任务栈，避免返回至之前的回收页面
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(mainIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 注销广播，避免内存泄漏（贴合项目原有内存管理规范）
        if (chargingReturnReceiver != null) {
            unregisterReceiver(chargingReturnReceiver);
        }
    }
}
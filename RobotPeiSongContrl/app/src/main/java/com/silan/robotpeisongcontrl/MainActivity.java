package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.silan.robotpeisongcontrl.dialog.LoginDialog;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.RobotStatus;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;

import java.util.List;

public class MainActivity extends AppCompatActivity {
    private int clickCount = 0;
    private CountDownTimer resetTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startDelivery = findViewById(R.id.btn_start_delivery);
        startDelivery.setOnClickListener(v -> {
            // 获取机器人状态
            getRobotStatus();
        });

        // 透明按钮（右上角）
        View secretButton = findViewById(R.id.secret_button);
        secretButton.setOnClickListener(v -> {
            clickCount++;

            if (resetTimer != null) {
                resetTimer.cancel();
            }

            resetTimer = new CountDownTimer(3000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {}

                @Override
                public void onFinish() {
                    clickCount = 0;
                }
            }.start();

            if (clickCount >= 5) {
                showLoginDialog();
                clickCount = 0;
            }
        });
    }

    private void getRobotStatus() {
        RobotController.getRobotStatus(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(String response) {
                // 解析机器人状态
                RobotStatus status = RobotController.parseRobotStatus(response);
                if (status != null) {
                    // 检查电量是否足够
                    if (status.getBatteryPercentage() < 20) {
                        Toast.makeText(MainActivity.this, "电量不足，请充电", Toast.LENGTH_SHORT).show();
                    } else {
                        // 获取POI信息
                        getPoiList();
                    }
                }
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(MainActivity.this, "获取机器人状态失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getPoiList() {
        RobotController.getPoiList(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(String response) {
                List<Poi> poiList = RobotController.parsePoiList(response);
                // 跳转到任务选择页面，并传递POI列表
                Intent intent = new Intent(MainActivity.this, TaskSelectionActivity.class);
                // 将poiList转换为JSON字符串传递
                intent.putExtra("poi_list", new Gson().toJson(poiList));
                startActivity(intent);
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(MainActivity.this, "获取POI信息失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoginDialog() {
        LoginDialog loginDialog = new LoginDialog(this);
        loginDialog.show();
    }
}
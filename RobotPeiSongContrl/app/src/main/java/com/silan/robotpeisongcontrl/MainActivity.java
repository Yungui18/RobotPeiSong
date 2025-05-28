package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.silan.robotpeisongcontrl.dialog.LoginDialog;

public class MainActivity extends AppCompatActivity {

    private int clickCount = 0;
    private CountDownTimer resetTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button startDelivery = findViewById(R.id.btn_start_delivery);
        startDelivery.setOnClickListener(v -> {
            // 在实际应用中，这里应该获取机器人状态和POI信息
            // 但为了简化流程，我们直接跳转到任务选择页面
            Intent intent = new Intent(MainActivity.this, TaskSelectionActivity.class);
            startActivity(intent);
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

    private void showLoginDialog() {
        LoginDialog loginDialog = new LoginDialog(this);
        loginDialog.show();
    }
}
package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.silan.robotpeisongcontrl.utils.TaskManager;

public class ArrivalConfirmationActivity extends AppCompatActivity {

    private CountDownTimer timer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arrival_confirmation);

        TextView countdownText = findViewById(R.id.tv_countdown);
        Button btnPickup = findViewById(R.id.btn_pickup);
        Button btnComplete = findViewById(R.id.btn_complete);

        // 倒计时150秒
        timer = new CountDownTimer(150000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdownText.setText(String.format("%ds", millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                proceedToNextTask();
            }
        }.start();

        btnPickup.setOnClickListener(v -> {
            // 取物操作逻辑
            Toast.makeText(ArrivalConfirmationActivity.this, "取物操作", Toast.LENGTH_SHORT).show();
        });

        btnComplete.setOnClickListener(v -> proceedToNextTask());
    }

    private void proceedToNextTask() {
        timer.cancel();

        if (TaskManager.getInstance().hasTasks()) {
            // 还有任务，继续下一个
            Intent intent = new Intent(this, MovingActivity.class);
            startActivity(intent);
        } else {
            // 所有任务完成，回桩
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("return_home", true);
            startActivity(intent);
        }
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

public class MovingActivity extends AppCompatActivity {

    private TextView statusText;
    private final Handler handler = new Handler();
    private final TaskManager taskManager = TaskManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moving);
        statusText = findViewById(R.id.tv_status);

        startNextTask();
    }

    private void startNextTask() {
        String nextTask = taskManager.getNextTask();

        if (nextTask != null) {
            statusText.setText("正在前往点位: " + nextTask);
            moveToPoint(nextTask);
        } else {
            returnToHome();
        }
    }

    private void moveToPoint(String pointId) {
        RobotController.createMoveAction(pointId, new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(String response) {
                // 假设移动完成
                handler.postDelayed(() -> {
                    Intent intent = new Intent(MovingActivity.this, ArrivalConfirmationActivity.class);
                    startActivity(intent);
                }, 5000); // 5秒后跳转
            }

            @Override
            public void onFailure(Exception e) {
                statusText.setText("移动失败: " + e.getMessage());
            }
        });
    }

    private void returnToHome() {
        statusText.setText("正在前往充电桩");
        RobotController.createReturnHomeAction(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(String response) {
                handler.postDelayed(() -> {
                    Intent intent = new Intent(MovingActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }, 5000);
            }

            @Override
            public void onFailure(Exception e) {
                statusText.setText("回桩失败: " + e.getMessage());
            }
        });
    }
}
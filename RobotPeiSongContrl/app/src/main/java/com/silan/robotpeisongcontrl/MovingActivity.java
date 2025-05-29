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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class MovingActivity extends AppCompatActivity {
    private TextView statusText;
    private final Handler handler = new Handler();
    private final TaskManager taskManager = TaskManager.getInstance();
    private List<Poi> poiList; // 存储POI列表

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_moving);
        statusText = findViewById(R.id.tv_status);

        // 获取POI列表
        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>() {
            }.getType();
            poiList = gson.fromJson(poiListJson, type);
        }

        startNextTask();
    }

    private void startNextTask() {
        Poi nextPoi = taskManager.getNextTask();

        if (nextPoi != null) {
            statusText.setText("正在前往点位: " + nextPoi.getDisplayName());
            moveToPoint(nextPoi);
        } else {
            returnToHome();
        }
    }

    private void moveToPoint(Poi poi) {
        RobotController.createMoveAction(poi, new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(String response) {
                // 移动任务创建成功
                // 注意：这里只是表示任务创建成功，不代表任务已完成
                // 实际应用中需要轮询任务状态

                // 模拟移动完成
                handler.postDelayed(() -> {
                    Intent intent = new Intent(MovingActivity.this, ArrivalConfirmationActivity.class);
                    // 传递POI列表
                    intent.putExtra("poi_list", new Gson().toJson(poiList));
                    startActivity(intent);
                    finish();
                }, 5000); // 5秒后跳转
            }

            @Override
            public void onFailure(Exception e) {
                statusText.setText("移动失败: " + e.getMessage());
                // 失败后继续下一个任务
                handler.postDelayed(() -> startNextTask(), 2000);
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
                    finish();
                }, 5000);
            }

            @Override
            public void onFailure(Exception e) {
                statusText.setText("回桩失败: " + e.getMessage());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
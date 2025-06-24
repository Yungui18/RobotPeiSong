package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.RobotStatus;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;

import com.silan.robotpeisongcontrl.utils.RobotController;

import java.nio.charset.StandardCharsets;
import java.util.List;

import okio.ByteString;

public class MainActivity extends AppCompatActivity {
    private int clickCount = 0;
    private CountDownTimer resetTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 配送按钮
        Button startDeliveryBtn = findViewById(R.id.btn_start_delivery);
        adjustButtonSize(startDeliveryBtn);

        // 巡游模式按钮
        Button patrolModeBtn = findViewById(R.id.btn_patrol_mode);
        adjustButtonSize(patrolModeBtn);

        // 设置按钮
        ImageButton btnSettings = findViewById(R.id.btn_settings);

        startDeliveryBtn.setOnClickListener(v -> getRobotStatus());

        patrolModeBtn.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PatrolActivity.class);
            startActivity(intent);
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PasswordAuthActivity.class);
            intent.putExtra("auth_type", PasswordAuthActivity.AUTH_TYPE_SETTINGS);
            startActivity(intent);
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        adjustButtonSize(findViewById(R.id.btn_start_delivery));
        adjustButtonSize(findViewById(R.id.btn_patrol_mode));
    }

    private void adjustButtonSize(Button button) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int buttonWidth = (int) (screenWidth * 0.5);
        buttonWidth = Math.max(dpToPx(200), Math.min(buttonWidth, dpToPx(400)));

        ViewGroup.LayoutParams params = button.getLayoutParams();
        params.width = buttonWidth;
        params.height = dpToPx(80);
        button.setLayoutParams(params);
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    private void getRobotStatus() {
        RobotController.getRobotStatus(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                RobotStatus status = RobotController.parseRobotStatus(json);
                if (status != null && status.getBatteryPercentage() >= 20) {
                    getPoiList();
                } else {
                    Toast.makeText(MainActivity.this, "电量不足，请充电", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.d("TAG", "获取机器人状态失败");
            }
        });
    }

    private void getPoiList() {
        RobotController.getPoiList(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                List<Poi> poiList = RobotController.parsePoiList(json);
                Intent intent = new Intent(MainActivity.this, TaskSelectionActivity.class);
                intent.putExtra("poi_list", new Gson().toJson(poiList));
                startActivity(intent);
            }

            @Override
            public void onFailure(Exception e) {
                Log.d("TAG", "获取POI信息失败"+e);
            }
        });
    }
}
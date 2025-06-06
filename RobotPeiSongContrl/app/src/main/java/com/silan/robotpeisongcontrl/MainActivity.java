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

import java.nio.charset.StandardCharsets;
import java.util.List;

import okio.ByteString;

public class MainActivity extends AppCompatActivity {
    private int clickCount = 0;
    private CountDownTimer resetTimer;
    private Button startDeliveryBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startDeliveryBtn = findViewById(R.id.btn_start_delivery);

        // 调整按钮大小以适应屏幕
        adjustButtonSize();

        startDeliveryBtn.setOnClickListener(v -> {
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 屏幕方向或尺寸变化时重新调整按钮
        adjustButtonSize();
    }

    private void adjustButtonSize() {
        // 获取屏幕尺寸
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;

        // 计算按钮尺寸（基于屏幕尺寸的比例）
        int buttonWidth = (int) (screenWidth * 0.5);  // 按钮宽度为屏幕宽度的50%
        int buttonHeight = (int) (screenHeight * 0.1); // 按钮高度为屏幕高度的10%

        // 设置最小和最大尺寸限制
        int minWidth = dpToPx(200); // 200dp
        int maxWidth = dpToPx(400); // 400dp
        int minHeight = dpToPx(80); // 80dp
        int maxHeight = dpToPx(120); // 120dp

        // 应用尺寸限制
        buttonWidth = Math.max(minWidth, Math.min(buttonWidth, maxWidth));
        buttonHeight = Math.max(minHeight, Math.min(buttonHeight, maxHeight));

        // 设置按钮尺寸
        ViewGroup.LayoutParams params = startDeliveryBtn.getLayoutParams();
        params.width = buttonWidth;
        params.height = buttonHeight;
        startDeliveryBtn.setLayoutParams(params);

        // 计算内边距（宽度8%，高度4%）
        int horizontalPadding = (int) (buttonWidth * 0.08);
        int verticalPadding = (int) (buttonHeight * 0.04);
        startDeliveryBtn.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        // 根据屏幕尺寸调整文字大小
        float textSize = Math.min(screenWidth, screenHeight) * 0.03f; // 文字大小为屏幕最小尺寸的3%
        textSize = Math.max(spToPx(18), Math.min(textSize, spToPx(32))); // 限制在18sp到32sp之间
        startDeliveryBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
    }

    // 将dp值转换为px值
    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    // 将sp值转换为px值
    private float spToPx(int sp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                getResources().getDisplayMetrics()
        );
    }

    private void getRobotStatus() {
        RobotController.getRobotStatus(new OkHttpUtils.ResponseCallback() {

            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                RobotStatus status = RobotController.parseRobotStatus(json);
                if (status != null) {
                    if (status.getBatteryPercentage() < 20) {
                        Toast.makeText(MainActivity.this, "电量不足，请充电", Toast.LENGTH_SHORT).show();
                    } else {
                        getPoiList();
                    }
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.d("TAG", "onFailure: 获取机器人状态失败");
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
                Log.d("TAG", "onFailure: 获取POI信息失败"+e);
            }
        });
    }

    private void showLoginDialog() {
        LoginDialog loginDialog = new LoginDialog(this);
        loginDialog.show();
    }
}
package com.silan.robotpeisongcontrl;


import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;

import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.RobotStatus;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;

import com.silan.robotpeisongcontrl.utils.RobotController;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import okio.ByteString;

public class MainActivity extends  BaseActivity{
    private String enteredPassword = "";
    private LinearLayout dotsContainer;
    private Button[] numberButtons = new Button[10];
    private Button btnDelete;
    private AlertDialog passwordDialog;
    private TextView tvTime;
    private RelativeLayout mainLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化时间显示
        tvTime = findViewById(R.id.tv_time);
        startTimeUpdater();

        mainLayout = findViewById(R.id.main_layout);

        // 应用背景
        applyBackground();

        // 配送按钮
        Button startDeliveryBtn = findViewById(R.id.btn_start_delivery);
        adjustButtonSize(startDeliveryBtn);

        // 巡游模式按钮
        Button patrolModeBtn = findViewById(R.id.btn_patrol_mode);
        adjustButtonSize(patrolModeBtn);

        // 多点配送按钮
        Button multiDeliveryBtn = findViewById(R.id.btn_multi_delivery);
        adjustButtonSize(multiDeliveryBtn);

        // 设置按钮
        ImageButton btnSettings = findViewById(R.id.btn_settings);

        startDeliveryBtn.setOnClickListener(v -> {
            // 检查是否启用了配送验证
            SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
            boolean verificationEnabled = prefs.getBoolean("verification_enabled", false);

            if (verificationEnabled) {
                // 显示送物密码验证对话框
                showDeliveryPasswordDialog(false);
            } else {
                // 直接开始配送流程
                getRobotStatus(false);
            }
        });

        multiDeliveryBtn.setOnClickListener(v -> {
            // 检查是否启用了配送验证
            SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
            boolean verificationEnabled = prefs.getBoolean("verification_enabled", false);

            if (verificationEnabled) {
                // 显示送物密码验证对话框
                showDeliveryPasswordDialog(true);
            } else {
                // 直接开始多点配送流程
                getRobotStatus(true);
            }
        });

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

    //应用背景
    private void applyBackground() {
        SharedPreferences prefs = getSharedPreferences("personalization_prefs", MODE_PRIVATE);
        int bgResId = prefs.getInt("background_res", R.drawable.bg_default);
        mainLayout.setBackgroundResource(bgResId);
    }

    //时区更新
    private void startTimeUpdater() {
        final Handler handler = new Handler();
        final Runnable timeUpdater = new Runnable() {
            @Override
            public void run() {
                updateTime();
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timeUpdater);
    }

    private void updateTime() {
        SharedPreferences prefs = getSharedPreferences("personalization_prefs", MODE_PRIVATE);
        String timezoneId = prefs.getString("selected_timezone", "Asia/Shanghai");

        TimeZone tz = TimeZone.getTimeZone(timezoneId);
        Calendar calendar = Calendar.getInstance(tz);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        sdf.setTimeZone(tz);

        tvTime.setText(sdf.format(calendar.getTime()));
    }

    // 将密码验证相关方法重构为通用方法
    private void showPasswordDialog(String title, String passwordType, boolean isMultiDelivery) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_password_auth, null);
        builder.setView(dialogView);

        // 设置标题
        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        tvTitle.setText(title);

        // 关闭按钮
        ImageButton btnClose = dialogView.findViewById(R.id.btn_close);
        btnClose.setOnClickListener(v -> {
            if (passwordDialog != null && passwordDialog.isShowing()) {
                passwordDialog.dismiss();
            }
        });

        // 初始化视图
        dotsContainer = dialogView.findViewById(R.id.dots_container);
        numberButtons[0] = dialogView.findViewById(R.id.btn_0);
        numberButtons[1] = dialogView.findViewById(R.id.btn_1);
        numberButtons[2] = dialogView.findViewById(R.id.btn_2);
        numberButtons[3] = dialogView.findViewById(R.id.btn_3);
        numberButtons[4] = dialogView.findViewById(R.id.btn_4);
        numberButtons[5] = dialogView.findViewById(R.id.btn_5);
        numberButtons[6] = dialogView.findViewById(R.id.btn_6);
        numberButtons[7] = dialogView.findViewById(R.id.btn_7);
        numberButtons[8] = dialogView.findViewById(R.id.btn_8);
        numberButtons[9] = dialogView.findViewById(R.id.btn_9);
        btnDelete = dialogView.findViewById(R.id.btn_delete);

        // 初始化密码圆点
        createPasswordDots();

        // 设置数字按钮点击事件
        for (int i = 0; i < numberButtons.length; i++) {
            final int digit = i;
            numberButtons[i].setOnClickListener(v -> addDigit(String.valueOf(digit),isMultiDelivery));
        }
        // 设置删除按钮点击事件
        btnDelete.setOnClickListener(v -> removeDigit());

        // 创建对话框
        passwordDialog = builder.create();
        passwordDialog.setCanceledOnTouchOutside(false);
        passwordDialog.show();

        // 重置输入状态
        enteredPassword = "";
        updateDotsDisplay();
    }

    /**
     * 显示送物密码验证对话框
     */
    private void showDeliveryPasswordDialog(boolean isMultiDelivery) {
        showPasswordDialog("送物验证", "delivery_password",isMultiDelivery);
    }

    /**
     * 验证送物密码是否正确
     */
    private boolean validateDeliveryPassword(String enteredPassword) {
        SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
        String correctPassword = prefs.getString("delivery_password", "");
        return enteredPassword.equals(correctPassword);
    }

    /**
     * 创建密码圆点指示器
     */
    private void createPasswordDots() {
        dotsContainer.removeAllViews();

        int dotSize = getResources().getDimensionPixelSize(R.dimen.password_dot_size);
        int margin = getResources().getDimensionPixelSize(R.dimen.password_dot_margin);

        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
            params.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(params);

            // 创建圆形背景
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(getResources().getDimensionPixelSize(R.dimen.password_dot_stroke), Color.GRAY);
            dot.setBackground(bg);

            dotsContainer.addView(dot);
        }
    }

    /**
     * 添加数字到密码
     */
    private void addDigit(String digit, boolean isMultiDelivery) {
        if (enteredPassword.length() < 4) {
            enteredPassword += digit;
            updateDotsDisplay();
            // 自动检查密码
            if (enteredPassword.length() == 4) {
                if (validateDeliveryPassword(enteredPassword)) {
                    // 验证通过，开始配送流程
                    getRobotStatus(isMultiDelivery);

                    // 关闭对话框
                    if (passwordDialog != null && passwordDialog.isShowing()) {
                        passwordDialog.dismiss();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
                    enteredPassword = "";
                    updateDotsDisplay();
                }
            }
        }
    }

    /**
     * 删除最后一个数字
     */
    private void removeDigit() {
        if (enteredPassword.length() > 0) {
            enteredPassword = enteredPassword.substring(0, enteredPassword.length() - 1);
            updateDotsDisplay();
        }
    }

    /**
     * 更新圆点显示状态
     */
    private void updateDotsDisplay() {
        for (int i = 0; i < dotsContainer.getChildCount(); i++) {
            View dot = dotsContainer.getChildAt(i);
            if (dot != null) {
                GradientDrawable bg = (GradientDrawable) dot.getBackground();
                if (i < enteredPassword.length()) {
                    // 填充的圆点
                    bg.setColor(Color.BLACK);
                    bg.setStroke(0, Color.TRANSPARENT);
                } else {
                    // 空心的圆点
                    bg.setColor(Color.TRANSPARENT);
                    bg.setStroke(getResources().getDimensionPixelSize(R.dimen.password_dot_stroke), Color.GRAY);
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        adjustButtonSize(findViewById(R.id.btn_start_delivery));
        adjustButtonSize(findViewById(R.id.btn_patrol_mode));
        adjustButtonSize(findViewById(R.id.btn_multi_delivery));
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

    private void getRobotStatus(boolean isMultiDelivery) {
        RobotController.getRobotStatus(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                RobotStatus status = RobotController.parseRobotStatus(json);
                if (status != null && status.getBatteryPercentage() >= 20) {
                    getPoiList(isMultiDelivery);
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

    private void getPoiList(boolean isMultiDelivery) {
        RobotController.getPoiList(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                String json = responseData.string(StandardCharsets.UTF_8);
                List<Poi> poiList = RobotController.parsePoiList(json);
                Intent intent;
                if (isMultiDelivery) {
                    intent = new Intent(MainActivity.this, MultiDeliveryTaskSelectionActivity.class);
                } else {
                    intent = new Intent(MainActivity.this, TaskSelectionActivity.class);
                }
                intent.putExtra("poi_list", new Gson().toJson(poiList));
                startActivity(intent);
            }

            @Override
            public void onFailure(Exception e) {
                Log.d("TAG", "获取POI信息失败" + e);
            }
        });
    }
}
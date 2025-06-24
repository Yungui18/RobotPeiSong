package com.silan.robotpeisongcontrl;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 到达确认界面
 * 主要功能：
 * 1. 显示倒计时
 * 2. 提供取物和完成按钮
 * 3. 根据设置决定是否进行密码验证
 * 4. 处理配送流程中的密码验证逻辑
 */
public class ArrivalConfirmationActivity extends AppCompatActivity {

    private CountDownTimer timer;
    private List<Poi> poiList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arrival_confirmation);

        // 获取POI列表
        Intent intent = getIntent();
        String poiListJson = intent.getStringExtra("poi_list");
        if (poiListJson != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<Poi>>(){}.getType();
            poiList = gson.fromJson(poiListJson, type);
        }

        TextView countdownText = findViewById(R.id.tv_countdown);
        Button btnPickup = findViewById(R.id.btn_pickup);
        Button btnComplete = findViewById(R.id.btn_complete);

        // 初始化60秒倒计时
        timer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdownText.setText(String.format("%ds", millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                proceedToNextTask();
            }
        }.start();

        // 取物按钮点击事件
        btnPickup.setOnClickListener(v -> handlePickupAction());

        // 完成按钮点击事件
        btnComplete.setOnClickListener(v -> handleCompleteAction());
    }

    /**
     * 处理取物操作
     * 根据设置决定是否显示密码验证对话框
     */
    private void handlePickupAction() {
        // 检查是否启用了配送验证
        SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
        boolean verificationEnabled = prefs.getBoolean("verification_enabled", false);

        if (verificationEnabled) {
            showPasswordDialog(true); // 显示取物密码验证对话框
        } else {
            performPickupAction(); // 直接执行取物操作
        }
    }

    /**
     * 处理完成操作
     * 根据设置决定是否显示密码验证对话框
     */
    private void handleCompleteAction() {
        // 检查是否启用了配送验证
        SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
        boolean verificationEnabled = prefs.getBoolean("verification_enabled", false);

        if (verificationEnabled) {
            showPasswordDialog(false); // 显示送物密码验证对话框
        } else {
            proceedToNextTask(); // 直接继续下一个任务
        }
    }

    /**
     * 显示密码验证对话框
     * @param isPickup 是否为取物操作
     */
    private void showPasswordDialog(boolean isPickup) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isPickup ? "取物验证" : "送物验证");

        // 创建密码输入框
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("请输入4位密码");
        builder.setView(input);

        // 确定按钮
        builder.setPositiveButton("确定", (dialog, which) -> {
            String enteredPassword = input.getText().toString();
            if (validatePassword(enteredPassword, isPickup)) {
                if (isPickup) {
                    performPickupAction(); // 验证通过执行取物
                } else {
                    proceedToNextTask(); // 验证通过继续任务
                }
            } else {
                Toast.makeText(ArrivalConfirmationActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
            }
        });

        // 取消按钮
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    /**
     * 验证密码是否正确
     * @param enteredPassword 用户输入的密码
     * @param isPickup 是否为取物操作
     * @return 验证结果
     */
    private boolean validatePassword(String enteredPassword, boolean isPickup) {
        SharedPreferences prefs = getSharedPreferences("delivery_prefs", MODE_PRIVATE);
        String correctPassword;

        // 根据操作类型获取对应的密码
        if (isPickup) {
            correctPassword = prefs.getString("pickup_password", "");
        } else {
            correctPassword = prefs.getString("delivery_password", "");
        }

        return enteredPassword.equals(correctPassword);
    }

    /**
     * 执行取物操作
     */
    private void performPickupAction() {
        // 模拟取物操作
        Toast.makeText(this, "取物操作", Toast.LENGTH_SHORT).show();
    }

    /**
     * 继续下一个任务或回桩
     */
    private void proceedToNextTask() {
        timer.cancel(); // 停止倒计时

        if (TaskManager.getInstance().hasTasks()) {
            // 还有任务，继续下一个
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
            startActivity(intent);
        } else {
            // 所有任务完成，回桩
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
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
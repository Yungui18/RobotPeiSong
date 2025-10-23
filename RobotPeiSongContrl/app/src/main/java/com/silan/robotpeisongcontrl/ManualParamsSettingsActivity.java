package com.silan.robotpeisongcontrl;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.silan.robotpeisongcontrl.utils.ManualParamManager;
import com.silan.robotpeisongcontrl.utils.SerialPortManager;

public class ManualParamsSettingsActivity extends AppCompatActivity {

    private ManualParamManager paramManager;
    private SerialPortManager serialPortManager;

    // 所有直流电机的统一参数输入框
    private EditText etAllMotorHighSpeed, etAllMotorLowSpeed, etAllMotorHighTime;
    // 推杆电机参数输入框
    private EditText etPusherHighSpeed, etPusherLowSpeed, etPusherHighTime;
    // 固定参数显示
    private TextView tvLockCurrent, tvElectromagnetTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 加载原Fragment的布局（可直接复用，无需修改）
        setContentView(R.layout.activity_manual_params_settings);

        // 初始化管理器
        paramManager = ManualParamManager.getInstance(this);
        serialPortManager = SerialPortManager.getInstance();

        // 初始化视图（直接通过Activity findViewById）
        initViews();

        // 加载当前参数
        loadCurrentParams();

        // 保存按钮点击事件
        Button btnSave = findViewById(R.id.btn_save_params);
        btnSave.setOnClickListener(v -> saveParams());

        // 返回按钮（若布局中有返回按钮，添加点击事件）
        if (findViewById(R.id.btn_back) != null) {
            findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        }
    }

    private void initViews() {
        // 所有直流电机的统一参数输入框
        etAllMotorHighSpeed = findViewById(R.id.et_all_motor_high_speed);
        etAllMotorLowSpeed = findViewById(R.id.et_all_motor_low_speed);
        etAllMotorHighTime = findViewById(R.id.et_all_motor_high_time);

        // 推杆电机参数输入框
        etPusherHighSpeed = findViewById(R.id.et_pusher_high_speed);
        etPusherLowSpeed = findViewById(R.id.et_pusher_low_speed);
        etPusherHighTime = findViewById(R.id.et_pusher_high_time);

        // 固定参数显示
        tvLockCurrent = findViewById(R.id.tv_lock_current);
        tvElectromagnetTime = findViewById(R.id.tv_electromagnet_time);
    }

    private void loadCurrentParams() {
        // 加载所有直流电机的统一参数（取电机1的参数作为参考）
        etAllMotorHighSpeed.setText(String.valueOf(paramManager.getMotorHighSpeed(1)));
        etAllMotorLowSpeed.setText(String.valueOf(paramManager.getMotorLowSpeed(1)));
        etAllMotorHighTime.setText(String.valueOf(paramManager.getMotorHighTime(1)));

        // 加载推杆电机参数
        etPusherHighSpeed.setText(String.valueOf(paramManager.getPusherHighSpeed()));
        etPusherLowSpeed.setText(String.valueOf(paramManager.getPusherLowSpeed()));
        etPusherHighTime.setText(String.valueOf(paramManager.getPusherHighTime()));

        // 显示固定参数
        tvLockCurrent.setText(String.valueOf(ManualParamManager.LOCK_CURRENT) + " mA (不可修改)");
        tvElectromagnetTime.setText(String.valueOf(ManualParamManager.ELECTROMAGNET_TIME) + " ms (不可修改)");
    }

    private void saveParams() {
        try {
            // 读取所有直流电机的统一参数
            int allMotorHigh = Integer.parseInt(etAllMotorHighSpeed.getText().toString().trim());
            int allMotorLow = Integer.parseInt(etAllMotorLowSpeed.getText().toString().trim());
            int allMotorTime = Integer.parseInt(etAllMotorHighTime.getText().toString().trim());

            // 验证直流电机参数范围
            if (!isValidSpeed(allMotorHigh) || !isValidSpeed(allMotorLow) || !isValidTime(allMotorTime)) {
                showInvalidParamToast();
                return;
            }

            // 应用到所有4个直流电机
            for (int motorId = 1; motorId <= 4; motorId++) {
                paramManager.saveMotorParams(motorId, allMotorHigh, allMotorLow, allMotorTime);
            }

            // 读取并保存推杆电机参数
            int pusherHigh = Integer.parseInt(etPusherHighSpeed.getText().toString().trim());
            int pusherLow = Integer.parseInt(etPusherLowSpeed.getText().toString().trim());
            int pusherTime = Integer.parseInt(etPusherHighTime.getText().toString().trim());

            if (!isValidSpeed(pusherHigh) || !isValidSpeed(pusherLow) || !isValidTime(pusherTime)) {
                showInvalidParamToast();
                return;
            }
            paramManager.savePusherParams(pusherHigh, pusherLow, pusherTime);

            // 发送所有参数到串口
            sendAllParamsToSerial(allMotorHigh, allMotorLow, allMotorTime, pusherHigh, pusherLow, pusherTime);

            Toast.makeText(this, "参数保存成功", Toast.LENGTH_SHORT).show();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "参数格式错误，请输入数字", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendAllParamsToSerial(int motorHigh, int motorLow, int motorTime,
                                       int pusherHigh, int pusherLow, int pusherTime) {
        // 发送统一参数到所有4个直流电机
        for (int motorId = 1; motorId <= 4; motorId++) {
            paramManager.sendMotorParamsToSerial(serialPortManager, motorId, motorHigh, motorLow, motorTime);
        }

        // 发送推杆电机参数
        paramManager.sendPusherParamsToSerial(serialPortManager, pusherHigh, pusherLow, pusherTime);
    }

    private boolean isValidSpeed(int speed) {
        return speed >= 1 && speed <= 9;
    }

    private boolean isValidTime(int time) {
        return time >= 0 && time <= 100;
    }

    private void showInvalidParamToast() {
        Toast.makeText(this, "参数范围错误：速度1-9，时间0-100", Toast.LENGTH_SHORT).show();
    }
}
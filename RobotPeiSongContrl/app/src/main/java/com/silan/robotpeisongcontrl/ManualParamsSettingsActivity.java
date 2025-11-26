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
    // 新增：每个电机的旋转方向、正转和反转时间输入框
    private EditText etMotor1Direction, etMotor1ForwardDelay, etMotor1ReverseDelay;
    private EditText etMotor2Direction, etMotor2ForwardDelay, etMotor2ReverseDelay;
    private EditText etMotor3Direction, etMotor3ForwardDelay, etMotor3ReverseDelay;
    private EditText etMotor4Direction, etMotor4ForwardDelay, etMotor4ReverseDelay;

    // 推杆电机参数输入框
    private EditText etPusherHighSpeed, etPusherLowSpeed, etPusherHighTime;
    // 新增：推杆电机方向和延时
    private EditText etPusherDirection, etPusherForwardDelay, etPusherReverseDelay;
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

        // 新增：初始化每个电机的方向和延时输入框
        etMotor1Direction = findViewById(R.id.et_motor1_direction);
        etMotor1ForwardDelay = findViewById(R.id.et_motor1_forward_delay);
        etMotor1ReverseDelay = findViewById(R.id.et_motor1_reverse_delay);

        etMotor2Direction = findViewById(R.id.et_motor2_direction);
        etMotor2ForwardDelay = findViewById(R.id.et_motor2_forward_delay);
        etMotor2ReverseDelay = findViewById(R.id.et_motor2_reverse_delay);

        etMotor3Direction = findViewById(R.id.et_motor3_direction);
        etMotor3ForwardDelay = findViewById(R.id.et_motor3_forward_delay);
        etMotor3ReverseDelay = findViewById(R.id.et_motor3_reverse_delay);

        etMotor4Direction = findViewById(R.id.et_motor4_direction);
        etMotor4ForwardDelay = findViewById(R.id.et_motor4_forward_delay);
        etMotor4ReverseDelay = findViewById(R.id.et_motor4_reverse_delay);

        // 推杆电机参数输入框
        etPusherHighSpeed = findViewById(R.id.et_pusher_high_speed);
        etPusherLowSpeed = findViewById(R.id.et_pusher_low_speed);
        etPusherHighTime = findViewById(R.id.et_pusher_high_time);

        // 新增：推杆电机方向和延时输入框
        etPusherDirection = findViewById(R.id.et_pusher_direction);
        etPusherForwardDelay = findViewById(R.id.et_pusher_forward_delay);
        etPusherReverseDelay = findViewById(R.id.et_pusher_reverse_delay);


        // 固定参数显示
        tvLockCurrent = findViewById(R.id.tv_lock_current);
        tvElectromagnetTime = findViewById(R.id.tv_electromagnet_time);
    }

    private void loadCurrentParams() {
        // 加载所有直流电机的统一参数（取电机1的参数作为参考）
        etAllMotorHighSpeed.setText(String.valueOf(paramManager.getMotorHighSpeed(1)));
        etAllMotorLowSpeed.setText(String.valueOf(paramManager.getMotorLowSpeed(1)));
        etAllMotorHighTime.setText(String.valueOf(paramManager.getMotorHighTime(1)));

        // 新增：加载每个电机的方向和延时参数
        etMotor1Direction.setText(String.valueOf(paramManager.getMotorDirection(1)));
        etMotor1ForwardDelay.setText(String.valueOf(paramManager.getMotorForwardDelay(1)));
        etMotor1ReverseDelay.setText(String.valueOf(paramManager.getMotorReverseDelay(1)));

        etMotor2Direction.setText(String.valueOf(paramManager.getMotorDirection(2)));
        etMotor2ForwardDelay.setText(String.valueOf(paramManager.getMotorForwardDelay(2)));
        etMotor2ReverseDelay.setText(String.valueOf(paramManager.getMotorReverseDelay(2)));

        etMotor3Direction.setText(String.valueOf(paramManager.getMotorDirection(3)));
        etMotor3ForwardDelay.setText(String.valueOf(paramManager.getMotorForwardDelay(3)));
        etMotor3ReverseDelay.setText(String.valueOf(paramManager.getMotorReverseDelay(3)));

        etMotor4Direction.setText(String.valueOf(paramManager.getMotorDirection(4)));
        etMotor4ForwardDelay.setText(String.valueOf(paramManager.getMotorForwardDelay(4)));
        etMotor4ReverseDelay.setText(String.valueOf(paramManager.getMotorReverseDelay(4)));

        // 加载推杆电机参数
        etPusherHighSpeed.setText(String.valueOf(paramManager.getPusherHighSpeed()));
        etPusherLowSpeed.setText(String.valueOf(paramManager.getPusherLowSpeed()));
        etPusherHighTime.setText(String.valueOf(paramManager.getPusherHighTime()));

        // 新增：加载推杆电机方向和延时
        etPusherDirection.setText(String.valueOf(paramManager.getPusherDirection()));
        etPusherForwardDelay.setText(String.valueOf(paramManager.getPusherForwardDelay()));
        etPusherReverseDelay.setText(String.valueOf(paramManager.getPusherReverseDelay()));

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

            // 新增：读取每个电机的方向和延时参数并验证
            int motor1Dir = Integer.parseInt(etMotor1Direction.getText().toString().trim());
            int motor1Forward = Integer.parseInt(etMotor1ForwardDelay.getText().toString().trim());
            int motor1Reverse = Integer.parseInt(etMotor1ReverseDelay.getText().toString().trim());

            int motor2Dir = Integer.parseInt(etMotor2Direction.getText().toString().trim());
            int motor2Forward = Integer.parseInt(etMotor2ForwardDelay.getText().toString().trim());
            int motor2Reverse = Integer.parseInt(etMotor2ReverseDelay.getText().toString().trim());

            int motor3Dir = Integer.parseInt(etMotor3Direction.getText().toString().trim());
            int motor3Forward = Integer.parseInt(etMotor3ForwardDelay.getText().toString().trim());
            int motor3Reverse = Integer.parseInt(etMotor3ReverseDelay.getText().toString().trim());

            int motor4Dir = Integer.parseInt(etMotor4Direction.getText().toString().trim());
            int motor4Forward = Integer.parseInt(etMotor4ForwardDelay.getText().toString().trim());
            int motor4Reverse = Integer.parseInt(etMotor4ReverseDelay.getText().toString().trim());

//            // 验证方向和延时参数
//            if (!isValidDirection(motor1Dir) || !isValidDelay(motor1Forward) || !isValidDelay(motor1Reverse) ||
//                    !isValidDirection(motor2Dir) || !isValidDelay(motor2Forward) || !isValidDelay(motor2Reverse) ||
//                    !isValidDirection(motor3Dir) || !isValidDelay(motor3Forward) || !isValidDelay(motor3Reverse) ||
//                    !isValidDirection(motor4Dir) || !isValidDelay(motor4Forward) || !isValidDelay(motor4Reverse)) {
//                showInvalidDirectionOrDelayToast();
//                return;
//            }

            // 应用到所有4个直流电机，包括新添加的参数
            paramManager.saveMotorParams(1, allMotorHigh, allMotorLow, allMotorTime, motor1Dir, motor1Forward, motor1Reverse);
            paramManager.saveMotorParams(2, allMotorHigh, allMotorLow, allMotorTime, motor2Dir, motor2Forward, motor2Reverse);
            paramManager.saveMotorParams(3, allMotorHigh, allMotorLow, allMotorTime, motor3Dir, motor3Forward, motor3Reverse);
            paramManager.saveMotorParams(4, allMotorHigh, allMotorLow, allMotorTime, motor4Dir, motor4Forward, motor4Reverse);

            // 读取并保存推杆电机参数
            int pusherHigh = Integer.parseInt(etPusherHighSpeed.getText().toString().trim());
            int pusherLow = Integer.parseInt(etPusherLowSpeed.getText().toString().trim());
            int pusherTime = Integer.parseInt(etPusherHighTime.getText().toString().trim());

            // 新增：读取推杆电机方向和延时
            int pusherDir = Integer.parseInt(etPusherDirection.getText().toString().trim());
            int pusherForward = Integer.parseInt(etPusherForwardDelay.getText().toString().trim());
            int pusherReverse = Integer.parseInt(etPusherReverseDelay.getText().toString().trim());

//            if (!isValidSpeed(pusherHigh) || !isValidSpeed(pusherLow) || !isValidTime(pusherTime) ||
//                    !isValidDirection(pusherDir) || !isValidDelay(pusherForward) || !isValidDelay(pusherReverse)) {
//                showInvalidParamToast();
//                return;
//            }

            // 保存推杆电机所有参数
            paramManager.savePusherParams(pusherHigh, pusherLow, pusherTime, pusherDir, pusherForward, pusherReverse);

            // 发送所有参数到串口
            sendAllParamsToSerial(allMotorHigh, allMotorLow, allMotorTime,
                    motor1Dir, motor1Forward, motor1Reverse,
                    motor2Dir, motor2Forward, motor2Reverse,
                    motor3Dir, motor3Forward, motor3Reverse,
                    motor4Dir, motor4Forward, motor4Reverse,
                    pusherHigh, pusherLow, pusherTime,
                    pusherDir, pusherForward, pusherReverse);

            Toast.makeText(this, "参数保存成功", Toast.LENGTH_SHORT).show();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "参数格式错误，请输入数字", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendAllParamsToSerial(int motorHigh, int motorLow, int motorTime,
                                       int motor1Dir, int motor1Forward, int motor1Reverse,
                                       int motor2Dir, int motor2Forward, int motor2Reverse,
                                       int motor3Dir, int motor3Forward, int motor3Reverse,
                                       int motor4Dir, int motor4Forward, int motor4Reverse,
                                       int pusherHigh, int pusherLow, int pusherTime,
                                       int pusherDir, int pusherForward, int pusherReverse) {
        // 发送参数到所有4个直流电机（包含新参数）
        paramManager.sendMotorParamsToSerial(serialPortManager, 1, motorHigh, motorLow, motorTime, motor1Dir, motor1Forward, motor1Reverse);
        try { Thread.sleep(50); } catch (InterruptedException e) { e.printStackTrace(); }

        paramManager.sendMotorParamsToSerial(serialPortManager, 2, motorHigh, motorLow, motorTime, motor2Dir, motor2Forward, motor2Reverse);
        try { Thread.sleep(50); } catch (InterruptedException e) { e.printStackTrace(); }

        paramManager.sendMotorParamsToSerial(serialPortManager, 3, motorHigh, motorLow, motorTime, motor3Dir, motor3Forward, motor3Reverse);
        try { Thread.sleep(50); } catch (InterruptedException e) { e.printStackTrace(); }

        paramManager.sendMotorParamsToSerial(serialPortManager, 4, motorHigh, motorLow, motorTime, motor4Dir, motor4Forward, motor4Reverse);
        try { Thread.sleep(50); } catch (InterruptedException e) { e.printStackTrace(); }

        // 发送推杆电机参数（包含新参数）
        paramManager.sendPusherParamsToSerial(serialPortManager, pusherHigh, pusherLow, pusherTime, pusherDir, pusherForward, pusherReverse);
    }

    private boolean isValidSpeed(int speed) {
        return speed >= 1 && speed <= 9;
    }

    private boolean isValidTime(int time) {
        return time >= 0 && time <= 100;
    }

    // 新增：验证方向参数（0或1）
    private boolean isValidDirection(int direction) {
        return direction == 0 || direction == 1;
    }

    // 新增：验证延时参数（0-1000）
    private boolean isValidDelay(int delay) {
        return delay >= 0 && delay <= 1000;
    }

    private void showInvalidParamToast() {
        Toast.makeText(this, "参数范围错误：速度1-9，时间0-100", Toast.LENGTH_SHORT).show();
    }

    // 新增：显示方向或延时参数错误提示
    private void showInvalidDirectionOrDelayToast() {
        Toast.makeText(this, "参数范围错误：方向0或1，延时0-1000", Toast.LENGTH_SHORT).show();
    }
}
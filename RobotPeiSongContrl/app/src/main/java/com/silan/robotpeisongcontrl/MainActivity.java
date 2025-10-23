package com.silan.robotpeisongcontrl;


import static java.nio.charset.StandardCharsets.UTF_8;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;

import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.gson.Gson;
import com.silan.robotpeisongcontrl.fragments.StandbySettingsFragment;
import com.silan.robotpeisongcontrl.model.Constants;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.RobotStatus;
import com.silan.robotpeisongcontrl.utils.ExactAlarmPermissionHelper;
import com.silan.robotpeisongcontrl.utils.FollowModeManager;
import com.silan.robotpeisongcontrl.utils.ManualParamManager;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;

import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.SerialPortManager;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import java.util.List;

import java.util.TimeZone;

import okio.ByteString;

public class MainActivity extends BaseActivity implements StandbySettingsFragment.OnStandbySettingsChangedListener {
    private String enteredPassword = "";
    private LinearLayout dotsContainer;
    private Button[] numberButtons = new Button[10];
    private Button btnDelete;
    private AlertDialog passwordDialog;
    private TextView tvTime;
    private RelativeLayout mainLayout;
    private ActivityResultLauncher<Intent> alarmPermissionLauncher;
    private VideoView standbyAnimationView;
    private Handler standbyHandler = new Handler();
    private Runnable standbyRunnable;
    private long standbyTimeout = 60000; // 默认1分钟
    private boolean standbyEnabled = true;
    private boolean isStandbyActive = false;
    private MediaController mediaController;
    private boolean isMainActivityActive = false;
    private TextView tvDebugInfo;

    // 监控面板UI元素
    private LinearLayout debugContainer;
    private TextView tvPacketCount, tvPacketRate;
    private TextView tvUwbData, tvCoordTransform;
    private TextView tvRobotPose, tvTargetPosition;
    private TextView tvMoveStatus;

    private TextView tvFrontBase, tvRearBase, tvLeftBase, tvRightBase;

    // 跟随模式管理器
    private FollowModeManager followModeManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 获取基站指示器视图
        tvFrontBase = findViewById(R.id.tv_front_base);
        tvRearBase = findViewById(R.id.tv_rear_base);
        tvLeftBase = findViewById(R.id.tv_left_base);
        tvRightBase = findViewById(R.id.tv_right_base);

        // 初始化监控面板
        initDebugPanel();


        // 初始化权限请求
        alarmPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> ExactAlarmPermissionHelper.handlePermissionResult(this)
        );

        // 检查精确闹钟权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !ExactAlarmPermissionHelper.canScheduleExactAlarms(this)) {

            // 请求权限
            ExactAlarmPermissionHelper.requestExactAlarmPermission(this, alarmPermissionLauncher);
        }

        // 初始化时间显示
        tvTime = findViewById(R.id.tv_time);
        startTimeUpdater();

        mainLayout = findViewById(R.id.main_layout);

        // 应用背景
        applyBackground();

        // 应用服务设置
        applyServiceSettings();

        // 初始化待机动画视图
        standbyAnimationView = findViewById(R.id.standby_animation);

        // 初始化调试视图
        tvDebugInfo = findViewById(R.id.tv_debug_info);

        initVideoView();

        // 加载待机设置
        loadStandbySettings();

        // 初始化待机检测
        initStandbyDetection();

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

        Button btnFollowMode = findViewById(R.id.btn_follow_mode);
        adjustButtonSize(btnFollowMode);
        btnFollowMode.setOnClickListener(v -> toggleFollowMode());
        // 初始化跟随模式管理器
        followModeManager = new FollowModeManager(this, new FollowModeManager.FollowModeListener() {
            @Override
            public void onUwbDataUpdate(double distance, double azimuth, double elevation) {
                updateUwbDataDisplay(distance, azimuth, elevation);
            }

            @Override
            public void onMoveStatusUpdate(String status) {
                updateMoveStatus(status);
            }

            @Override
            public void onTargetUpdate(double globalX, double globalY) {
                updateTargetDisplay(globalX, globalY);
            }

            @Override
            public void onRobotPoseUpdate(double x, double y, double yaw) {
                updateRobotPoseDisplay(x, y, yaw);
            }

            @Override
            public void onCoordTransformUpdate(double baseX, double baseY, double robotX, double robotY) {
                updateCoordTransformDisplay(baseX, baseY, robotX, robotY);
            }

            @Override
            public void onBaseStationUpdate(long anchorId, boolean isActive) {
                updateBaseStationIndicator(anchorId, isActive);
            }

            @Override
            public void onPacketStatsUpdate(int count, float rate) {
                updatePacketStats(count, rate);
            }
        });

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

        // 初始化电机串口
        SerialPortManager serialPortManager = SerialPortManager.getInstance();
        if (serialPortManager.openSerialPort()) {
            // 初始化手动参数
            ManualParamManager paramManager = ManualParamManager.getInstance(this);
            paramManager.initParams(serialPortManager);
        } else {
            Toast.makeText(this, "串口打开失败，无法初始化参数", Toast.LENGTH_SHORT).show();
        }
    }

    // 更新基站状态指示器
    private void updateBaseStationIndicator(long anchorId, boolean isActive) {
        runOnUiThread(() -> {
            // 使用FollowModeManager提供的基站配置
            FollowModeManager.BaseStationConfig config = followModeManager.getBaseStationConfig(anchorId);
            if (config != null) {
                TextView indicator = findViewById(config.indicatorViewId);
                if (indicator != null) {
                    int bgRes = isActive ? R.drawable.indicator_green : R.drawable.indicator_gray;
                    indicator.setBackgroundResource(bgRes);

                    // 添加闪烁动画
                    if (isActive) {
                        Animation blink = AnimationUtils.loadAnimation(this, R.anim.blink);
                        indicator.startAnimation(blink);
                    } else {
                        indicator.clearAnimation();
                    }
                }
            }
        });
    }

    private void initDebugPanel() {
        // 获取UI引用
        debugContainer = findViewById(R.id.debug_container);
        tvPacketCount = findViewById(R.id.tv_packet_count);
        tvPacketRate = findViewById(R.id.tv_packet_rate);
        tvUwbData = findViewById(R.id.tv_uwb_data);
        tvCoordTransform = findViewById(R.id.tv_coord_transform);
        tvRobotPose = findViewById(R.id.tv_robot_pose);
        tvTargetPosition = findViewById(R.id.tv_target_position);
        tvMoveStatus = findViewById(R.id.tv_move_status);

        // 默认在调试模式下显示面板
        if (Constants.DEBUG_MODE) {
            debugContainer.setVisibility(View.VISIBLE);
        }
        // 初始状态
        clearDebugLogs();
        // 切换面板按钮
        Button btnToggleDebug = findViewById(R.id.btn_toggle_debug);
        btnToggleDebug.setOnClickListener(v -> {
            if (debugContainer.getVisibility() == View.VISIBLE) {
                debugContainer.setVisibility(View.GONE);
                btnToggleDebug.setText("显示监控面板");
            } else {
                debugContainer.setVisibility(View.VISIBLE);
                btnToggleDebug.setText("隐藏监控面板");
            }
        });

        // 清除日志按钮
        Button btnClearLogs = findViewById(R.id.btn_clear_logs);
        btnClearLogs.setOnClickListener(v -> clearDebugLogs());
    }

    private void clearDebugLogs() {
        runOnUiThread(() -> {
            tvPacketCount.setText("0");
            tvPacketRate.setText("0 Hz");
            tvUwbData.setText("UWB数据: 等待数据...");
            tvCoordTransform.setText("坐标系转换: 未计算");
            tvRobotPose.setText("机器人位姿: 未获取");
            tvTargetPosition.setText("目标位置: 未计算");
            tvMoveStatus.setText("移动状态: 空闲");
        });
    }


    // 更新UWB数据显示
    private void updateUwbDataDisplay(double distance, double azimuth, double elevation) {
        runOnUiThread(() -> {
            String text = String.format("UWB数据: 距离=%.2fm, 方位角=%.1f°, 仰角=%.1f°",
                    distance, azimuth, elevation);
            tvUwbData.setText(text);
        });
    }

    // 更新坐标系转换显示
    private void updateCoordTransformDisplay(double baseX, double baseY,
                                             double robotX, double robotY) {
        runOnUiThread(() -> {
            String text = String.format("坐标转换: 基站系(%.2f, %.2f) → 机器人系(%.2f, %.2f)",
                    baseX, baseY, robotX, robotY);
            tvCoordTransform.setText(text);
        });
    }

    // 更新机器人位姿显示
    private void updateRobotPoseDisplay(double x, double y, double yaw) {
        runOnUiThread(() -> {
            String text = String.format("机器人位姿: X=%.2f, Y=%.2f, Yaw=%.1f°",
                    x, y, Math.toDegrees(yaw));
            tvRobotPose.setText(text);
        });
    }

    // 更新目标位置显示
    private void updateTargetDisplay(double globalX, double globalY) {
        runOnUiThread(() -> {
            String text = String.format("目标位置: 全局系(%.2f, %.2f)", globalX, globalY);
            tvTargetPosition.setText(text);
        });
    }

    // 更新移动状态显示
    private void updateMoveStatus(String status) {
        runOnUiThread(() -> {
            String text = "移动状态: " + status;
            tvMoveStatus.setText(text);
        });
    }

    // 更新数据包统计
    private void updatePacketStats(int count, float rate) {
        runOnUiThread(() -> {
            tvPacketCount.setText(String.valueOf(count));
            tvPacketRate.setText(String.format("%.1f Hz", rate));
        });
    }

    private void initVideoView() {
        // 设置视频源（从raw目录加载）
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.standby_animation);
        standbyAnimationView.setVideoURI(videoUri);

        // 设置循环播放：监听视频结束事件，重新开始播放
        standbyAnimationView.setOnPreparedListener(mp -> {
            mp.setLooping(true); // 关键：设置循环播放
            if (isStandbyActive) { // 仅在待机状态下播放
                standbyAnimationView.start();
            }
        });

        standbyAnimationView.setOnCompletionListener(mp -> {
            if (isStandbyActive) {
                standbyAnimationView.seekTo(0);
                standbyAnimationView.start();
            }
        });

        // 可选：隐藏播放控制条（若不需要用户操作）
        mediaController = new MediaController(this);
        standbyAnimationView.setMediaController(mediaController);
        mediaController.setVisibility(View.GONE);  // 隐藏控制条

        standbyAnimationView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (isStandbyActive) {
                    // 检查是否已设置过视频源
                    if (standbyAnimationView.isPlaying()) {
                        // 如果正在播放，继续播放
                        standbyAnimationView.resume();
                    } else {
                        // 否则重新设置视频源并开始加载
                        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.standby_animation);
                        standbyAnimationView.setVideoURI(videoUri);
                        // 准备完成后会通过 onPrepared 回调自动开始播放
                    }
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
            }
        });
    }

    // 切换跟随模式
    private void toggleFollowMode() {
        if (followModeManager.isFollowing()) {
            followModeManager.stopFollowing();
            // 更新按钮状态
            Button btn = findViewById(R.id.btn_follow_mode);
            btn.setBackgroundResource(R.drawable.button_blue_rect);
            btn.setText("开始跟随");

            // 隐藏监控面板
            if (debugContainer != null) {
                debugContainer.setVisibility(View.GONE);
            }
        } else {
            followModeManager.startFollowing();
            // 更新按钮状态
            Button btn = findViewById(R.id.btn_follow_mode);
            btn.setBackgroundResource(R.drawable.button_red_rect);
            btn.setText("停止跟随");

            // 显示监控面板
            if (debugContainer != null) {
                debugContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    private void loadStandbySettings() {
        SharedPreferences prefs = getSharedPreferences("standby_prefs", MODE_PRIVATE);
        boolean newEnabled = prefs.getBoolean("enabled", true);
        long newTimeout = prefs.getLong("timeout", 60000);
        Log.d("StandbySettings", "加载设置 - 启用: " + newEnabled + ", 超时: " + newTimeout + "ms");
        if (!newEnabled && isStandbyActive) {
            exitStandbyMode();
        }
        standbyEnabled = newEnabled;
        standbyTimeout = prefs.getLong("timeout", 60000);
    }


    private void initStandbyDetection() {
        standbyRunnable = () -> {
            if (isMainActivityActive && standbyEnabled && !isStandbyActive) {
                enterStandbyMode();
            }
        };
        resetStandbyTimer();
    }

    private void resetStandbyTimer() {
        standbyHandler.removeCallbacks(standbyRunnable);
        if (standbyEnabled) {
            standbyHandler.postDelayed(standbyRunnable, standbyTimeout);
        }
    }

    private void enterStandbyMode() {
        Log.d("Standby", "进入待机模式");
        if (!standbyEnabled || isStandbyActive) {
            return;
        }
        isStandbyActive = true;

        // 确保视频视图在最上层并可见
        standbyAnimationView.setVisibility(View.VISIBLE);
        standbyAnimationView.bringToFront();

        // 触发视频加载/播放（无论之前状态如何）
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.standby_animation);
        standbyAnimationView.setVideoURI(videoUri); // 重新加载确保资源有效
        standbyAnimationView.requestFocus(); // 获取焦点确保播放

        // 淡入动画（在视图可见后执行）
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(standbyAnimationView, "alpha", 0f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(standbyAnimationView, "scaleX", 0.9f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(standbyAnimationView, "scaleY", 0.9f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(fadeIn, scaleX, scaleY);
        set.setDuration(1000);
        set.start();
    }

    private void exitStandbyMode() {
        Log.d("Standby", "退出待机模式");
        if (!isStandbyActive) {
            return;
        }
        isStandbyActive = false;

        // 淡出动画（结束后隐藏并停止视频）
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(standbyAnimationView, "alpha", 1f, 0f);
        fadeOut.setDuration(500);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                standbyAnimationView.setVisibility(View.GONE);
                standbyAnimationView.stopPlayback(); // 完全停止播放释放资源
            }
        });
        fadeOut.start();

        if (isMainActivityActive) {
            resetStandbyTimer();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isStandbyActive) {
            exitStandbyMode();
            return true;
        }
        if (isMainActivityActive) {
            resetStandbyTimer();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isMainActivityActive = true;
        if (isStandbyActive && standbyAnimationView != null) {
            standbyAnimationView.seekTo(0);
            standbyAnimationView.start();
        }
        loadStandbySettings();
        resetStandbyTimer();
        applyBackground();
        applyServiceSettings();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isMainActivityActive = false;
        if (standbyAnimationView != null && standbyAnimationView.isPlaying()) {
            standbyAnimationView.pause();  // 暂停播放
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (standbyAnimationView != null && standbyAnimationView.isPlaying()) {
            standbyAnimationView.pause();
        }
    }


    @Override
    public void onStandbySettingsChanged() {
        loadStandbySettings();
        resetStandbyTimer();
    }

    private void applyServiceSettings() {
        SharedPreferences prefs = getSharedPreferences("service_prefs", MODE_PRIVATE);

        // 默认所有服务都启用
        boolean deliveryEnabled = prefs.getBoolean("delivery_enabled", true);
        boolean patrolEnabled = prefs.getBoolean("patrol_enabled", true);
        boolean multiDeliveryEnabled = prefs.getBoolean("multi_delivery_enabled", true);

        // 设置按钮可见性
        findViewById(R.id.btn_start_delivery).setVisibility(deliveryEnabled ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_patrol_mode).setVisibility(patrolEnabled ? View.VISIBLE : View.GONE);
        findViewById(R.id.btn_multi_delivery).setVisibility(multiDeliveryEnabled ? View.VISIBLE : View.GONE);

        // 调整布局
        adjustLayoutForServiceSettings();
    }

    private void adjustLayoutForServiceSettings() {
        LinearLayout buttonContainer = findViewById(R.id.button_container);
        int visibleButtonCount = 0;

        for (int i = 0; i < buttonContainer.getChildCount(); i++) {
            View child = buttonContainer.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                visibleButtonCount++;
            }
        }

        // 根据可见按钮数量调整布局
        if (visibleButtonCount == 1) {
            buttonContainer.setGravity(Gravity.CENTER);
        } else {
            buttonContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        }
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
            numberButtons[i].setOnClickListener(v -> addDigit(String.valueOf(digit), isMultiDelivery));
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
        showPasswordDialog("送物验证", "delivery_password", isMultiDelivery);
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
        buttonWidth = Math.max(dpToPx(150), Math.min(buttonWidth, dpToPx(200)));

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
                String json = responseData.string(UTF_8);
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
                String json = responseData.string(UTF_8);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaController != null) {
            mediaController.setAnchorView(null);
            mediaController = null;
        }
        standbyHandler.removeCallbacksAndMessages(null);
        if (standbyAnimationView != null) {
            standbyAnimationView.stopPlayback();  // 停止播放并释放资源
        }
        if (followModeManager != null) {
            followModeManager.cleanup();
        }

    }

}


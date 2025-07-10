package com.silan.robotpeisongcontrl;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.silan.robotpeisongcontrl.utils.PasswordManager;

public class PasswordAuthActivity extends BaseActivity {

    public static final int AUTH_TYPE_SETTINGS = 1;  // 4位密码
    public static final int AUTH_TYPE_SUPER_ADMIN = 2; // 6位密码

    private int passwordLength;
    private String enteredPassword = "";
    private int clickCount = 0;
    private CountDownTimer resetTimer;
    private AlertDialog passwordDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_auth);
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        int authType = getIntent().getIntExtra("auth_type", AUTH_TYPE_SETTINGS);
        passwordLength = (authType == AUTH_TYPE_SETTINGS) ? 4 : 6;

        setupUI(authType);
        createPasswordDots();
        createNumberPad();
        setupSecretButton();
    }

    private void setupUI(int authType) {
        TextView titleView = findViewById(R.id.tv_title);
        titleView.setText(authType == AUTH_TYPE_SETTINGS ?
                "请输入4位密码" : "请输入6位密码");
    }

    private void createPasswordDots() {
        LinearLayout dotsContainer = findViewById(R.id.dots_container);
        dotsContainer.removeAllViews();

        int dotSize = dpToPx(20);
        int margin = dpToPx(8);

        for (int i = 0; i < passwordLength; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dotSize, dotSize);
            params.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(params);

            // 创建圆形背景
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dpToPx(2), Color.GRAY);
            dot.setBackground(bg);

            dotsContainer.addView(dot);
        }
    }

    private void createNumberPad() {
        LinearLayout numberPad = findViewById(R.id.number_pad);
        numberPad.removeAllViews();

        // 数字按钮布局参数
        int buttonSize = dpToPx(80);
        int margin = dpToPx(10);
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        buttonParams.setMargins(margin, margin, margin, margin);

        // 第一行：1-3
        LinearLayout row1 = createRow();
        for (int i = 1; i <= 3; i++) {
            Button btn = createNumberButton(String.valueOf(i), buttonParams);
            row1.addView(btn);
        }
        numberPad.addView(row1);

        // 第二行：4-6
        LinearLayout row2 = createRow();
        for (int i = 4; i <= 6; i++) {
            Button btn = createNumberButton(String.valueOf(i), buttonParams);
            row2.addView(btn);
        }
        numberPad.addView(row2);

        // 第三行：7-9
        LinearLayout row3 = createRow();
        for (int i = 7; i <= 9; i++) {
            Button btn = createNumberButton(String.valueOf(i), buttonParams);
            row3.addView(btn);
        }
        numberPad.addView(row3);

        // 第四行：0和删除
        LinearLayout row4 = createRow();

        // 空视图占位
        View spacer = new View(this);
        spacer.setLayoutParams(buttonParams);
        row4.addView(spacer);

        // 0按钮
        Button btn0 = createNumberButton("0", buttonParams);
        row4.addView(btn0);

        // 删除按钮
        Button btnDelete = new Button(this);
        btnDelete.setLayoutParams(buttonParams);
        btnDelete.setText("");
        btnDelete.setBackgroundResource(R.drawable.ic_backspace);
        btnDelete.setOnClickListener(v -> removeDigit());
        row4.addView(btnDelete);

        numberPad.addView(row4);
    }

    private LinearLayout createRow() {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private Button createNumberButton(String number, LinearLayout.LayoutParams params) {
        Button btn = new Button(this);
        btn.setLayoutParams(params);
        btn.setText(number);
        btn.setTextSize(30);
        btn.setTextColor(Color.BLACK);

        // 设置圆形按钮背景
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.WHITE);
        bg.setStroke(dpToPx(1), Color.LTGRAY);
        btn.setBackground(bg);

        btn.setOnClickListener(v -> addDigit(number));
        return btn;
    }

    private void setupSecretButton() {
        // 左侧隐藏按钮
        LinearLayout leftArea = findViewById(R.id.left_area);
        View leftSecretButton = new View(this);
        leftSecretButton.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        leftSecretButton.setBackgroundColor(Color.TRANSPARENT);
        leftSecretButton.setOnClickListener(v -> handleSecretButtonClick());
        leftArea.addView(leftSecretButton);

        // 右侧隐藏按钮
        LinearLayout rightArea = findViewById(R.id.right_area);
        View rightSecretButton = new View(this);
        rightSecretButton.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rightSecretButton.setBackgroundColor(Color.TRANSPARENT);
        rightSecretButton.setOnClickListener(v -> handleSecretButtonClick());
        rightArea.addView(rightSecretButton);
    }

    private void handleSecretButtonClick() {
        clickCount++;
        if (resetTimer != null) resetTimer.cancel();

        resetTimer = new CountDownTimer(3000, 1000) {
            public void onFinish() {
                clickCount = 0;
            }

            public void onTick(long millisUntilFinished) {
            }
        }.start();

        if (clickCount >= 5) {
            Intent intent = new Intent(PasswordAuthActivity.this, PasswordAuthActivity.class);
            intent.putExtra("auth_type", PasswordAuthActivity.AUTH_TYPE_SUPER_ADMIN);
            startActivity(intent);
            clickCount = 0;
        }
    }

    private void addDigit(String digit) {
        if (enteredPassword.length() < passwordLength) {
            enteredPassword += digit;
            updateDotsDisplay();

            // 自动检查密码
            if (enteredPassword.length() == passwordLength) {
                checkPassword();
            }
        }
    }

    private void removeDigit() {
        if (enteredPassword.length() > 0) {
            enteredPassword = enteredPassword.substring(0, enteredPassword.length() - 1);
            updateDotsDisplay();
        }
    }

    private void updateDotsDisplay() {
        LinearLayout dotsContainer = findViewById(R.id.dots_container);
        for (int i = 0; i < passwordLength; i++) {
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
                    bg.setStroke(dpToPx(2), Color.GRAY);
                }
            }
        }
    }

    private void checkPassword() {
        int authType = getIntent().getIntExtra("auth_type", AUTH_TYPE_SETTINGS);
        String correctPassword;

        if (authType == AUTH_TYPE_SETTINGS) {
            correctPassword = PasswordManager.getSettingsPassword(this);
        } else {
            correctPassword = PasswordManager.getSuperAdminPassword(this);
        }

        if (enteredPassword.equals(correctPassword)) {
            // 验证成功
            if (authType == AUTH_TYPE_SETTINGS) {
                Intent intent = new Intent(this, SettingsMainActivity.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, SuperAdminActivity.class);
                startActivity(intent);
            }
            finish();
        } else {
            Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show();
            enteredPassword = "";
            updateDotsDisplay();
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (resetTimer != null) {
            resetTimer.cancel();
        }
    }
}
package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.util.ArrayList;
import java.util.List;

public class TaskSelectionActivity extends AppCompatActivity {

    private TextView countdownText;
    private CountDownTimer timer;
    private final TaskManager taskManager = TaskManager.getInstance();
    private int currentSelectedButtonIndex = -1;
    private List<String> poiList = new ArrayList<>(); // 模拟POI列表
    private final Button[] taskButtons = new Button[4];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_selection);

        countdownText = findViewById(R.id.tv_countdown);
        Button btnStart = findViewById(R.id.btn_start);

        // 初始化任务按钮
        taskButtons[0] = findViewById(R.id.btn_task1);
        taskButtons[1] = findViewById(R.id.btn_task2);
        taskButtons[2] = findViewById(R.id.btn_task3);
        taskButtons[3] = findViewById(R.id.btn_task4);

        // 初始化模拟POI列表
        poiList.add("A101");
        poiList.add("A102");
        poiList.add("A103");
        poiList.add("A104");

        // 任务按钮点击事件
        for (int i = 0; i < taskButtons.length; i++) {
            int index = i;
            taskButtons[i].setOnClickListener(v -> selectTask(index));
        }

        // 开始任务按钮
        btnStart.setOnClickListener(v -> startTasks());

        // 数字按钮
        setupNumberButtons();

        // 倒计时150秒
        startCountdown();
    }

    private void startCountdown() {
        timer = new CountDownTimer(150000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                countdownText.setText(String.format("%ds", millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                taskManager.clearTasks();
                clearTaskButtons();
                finish();
            }
        }.start();
    }

    private void setupNumberButtons() {
        int[] numberButtonIds = {
                R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3,
                R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7,
                R.id.btn_8, R.id.btn_9, R.id.btn_clear, R.id.btn_done
        };

        TextView display = findViewById(R.id.tv_display);

        for (int id : numberButtonIds) {
            Button btn = findViewById(id);
            btn.setOnClickListener(v -> {
                if (id == R.id.btn_clear) {
                    display.setText("");
                } else if (id == R.id.btn_done) {
                    validatePoint(display.getText().toString());
                    display.setText("");
                } else {
                    display.append(((Button) v).getText());
                }
            });
        }
    }

    private void validatePoint(String pointId) {
        if (currentSelectedButtonIndex == -1) {
            Toast.makeText(this, "请先选择一个任务按钮", Toast.LENGTH_SHORT).show();
            return;
        }

        if (poiList.contains(pointId)) {
            taskManager.addTask(pointId);
            taskButtons[currentSelectedButtonIndex].setText(pointId);
            // 设置验证通过的按钮为绿色直角
            taskButtons[currentSelectedButtonIndex].setBackgroundResource(R.drawable.button_green_rect);
        } else {
            Toast.makeText(this, "点位不存在", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectTask(int index) {
        // 重置所有按钮状态
        for (Button button : taskButtons) {
            button.setBackgroundColor(getResources().getColor(R.color.blue));
        }

        // 设置选中按钮状态
        taskButtons[index].setBackgroundColor(getResources().getColor(R.color.red));
        currentSelectedButtonIndex = index;
    }

    private void clearTaskButtons() {
        for (Button button : taskButtons) {
            button.setText("");
            // 重置为蓝色直角
            button.setBackgroundResource(R.drawable.button_blue_rect);
        }
        currentSelectedButtonIndex = -1;
    }

    private void startTasks() {
        if (taskManager.hasTasks()) {
            timer.cancel();
            Intent intent = new Intent(this, MovingActivity.class);
            startActivity(intent);
            finish();
        } else {
            Toast.makeText(this, "请先创建任务", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
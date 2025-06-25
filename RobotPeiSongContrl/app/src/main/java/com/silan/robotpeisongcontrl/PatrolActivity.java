package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.silan.robotpeisongcontrl.model.PatrolScheme;
import com.silan.robotpeisongcontrl.utils.PatrolSchemeManager;

import java.util.Map;

public class PatrolActivity extends AppCompatActivity {

    private LinearLayout patrolSchemeContainer;
    private int selectedSchemeId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patrol);

        // 标题
        TextView title = findViewById(R.id.tv_title);
        title.setText("巡游模式");

        // 方案容器
        patrolSchemeContainer = findViewById(R.id.patrol_scheme_container);
        loadPatrolSchemes();

        // 开始巡游按钮
        Button btnStartPatrol = findViewById(R.id.btn_start_patrol);
        btnStartPatrol.setOnClickListener(v -> startPatrol());

        // 返回按钮
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPatrolSchemes();
    }

    private void loadPatrolSchemes() {
        patrolSchemeContainer.removeAllViews();
        Map<Integer, PatrolScheme> schemes = PatrolSchemeManager.loadSchemes(this);

        for (Map.Entry<Integer, PatrolScheme> entry : schemes.entrySet()) {
            Button schemeBtn = new Button(this);
            schemeBtn.setText("方案 " + entry.getKey());
            schemeBtn.setTag(entry.getKey());
            schemeBtn.setBackgroundResource(R.drawable.button_blue_rect);
            schemeBtn.setTextSize(16);
            schemeBtn.setPadding(10, 10, 10, 10);

            schemeBtn.setOnClickListener(v -> selectScheme(v));

            patrolSchemeContainer.addView(schemeBtn);
        }
    }


    private void selectScheme(View v) {
        // 重置所有按钮颜色
        for (int i = 0; i < patrolSchemeContainer.getChildCount(); i++) {
            View child = patrolSchemeContainer.getChildAt(i);
            if (child instanceof Button) {
                child.setBackgroundResource(R.drawable.button_blue_rect);
            }
        }
        // 设置选中按钮为红色
        v.setBackgroundResource(R.drawable.button_red_rect);
        selectedSchemeId = (int) v.getTag();
    }

    private void startPatrol() {
        if (selectedSchemeId != -1) {
            Intent intent = new Intent(this, PatrollingActivity.class);
            intent.putExtra("scheme_id", selectedSchemeId);
            startActivity(intent);
        } else {
            Toast.makeText(this, "请先选择巡航方案", Toast.LENGTH_SHORT).show();
        }
    }
}
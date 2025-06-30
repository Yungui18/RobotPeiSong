package com.silan.robotpeisongcontrl;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;

public class ExistingTasksActivity extends AppCompatActivity {

    private ListView listViewExistingTasks;
    private List<String> existingTasks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_existing_tasks);

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 初始化视图
        listViewExistingTasks = findViewById(R.id.list_view_existing_tasks);

        // 模拟已有任务数据
        initExistingTasks();

        // 设置列表适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_2, android.R.id.text1, existingTasks) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);

                String taskInfo = existingTasks.get(position);
                String[] parts = taskInfo.split(", ");
                text1.setText(parts[0]);
                text2.setText(parts[1]);

                Button deleteBtn = new Button(ExistingTasksActivity.this);
                deleteBtn.setText("删除");
                deleteBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        deleteTask(position);
                    }
                });

                LinearLayout layout = new LinearLayout(ExistingTasksActivity.this);
                layout.setOrientation(LinearLayout.HORIZONTAL);
                layout.addView(view);
                layout.addView(deleteBtn);

                return layout;
            }
        };
        listViewExistingTasks.setAdapter(adapter);
    }

    private void initExistingTasks() {
        // 模拟已有任务数据
        existingTasks.add("点位1, 仓门1 仓门2, 10:30, 优先级A");
        existingTasks.add("路线2, 仓门3, 14:45, 优先级B");
    }

    private void deleteTask(int position) {
        existingTasks.remove(position);
        ((ArrayAdapter) listViewExistingTasks.getAdapter()).notifyDataSetChanged();
        Toast.makeText(this, "任务已删除", Toast.LENGTH_SHORT).show();
    }
}
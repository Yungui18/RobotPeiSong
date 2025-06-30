package com.silan.robotpeisongcontrl;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;


import com.silan.robotpeisongcontrl.model.ScheduledTask;
import com.silan.robotpeisongcontrl.utils.ScheduledTaskManager;

import java.util.List;

public class ScheduledTasksActivity extends BaseActivity {

    private ListView listViewTasks;
    private ArrayAdapter<ScheduledTask> adapter;
    private List<ScheduledTask> tasks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheduled_tasks);

        listViewTasks = findViewById(R.id.list_view_tasks);

        // 加载任务列表
        tasks = ScheduledTaskManager.loadTasks(this);

        // 创建适配器
        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, tasks);

        listViewTasks.setAdapter(adapter);

        // 设置删除按钮点击监听
        listViewTasks.setOnItemClickListener((parent, view, position, id) -> {
            ScheduledTask task = tasks.get(position);
            ScheduledTaskManager.deleteTask(this, task.getId());
            tasks.remove(position);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "任务已删除", Toast.LENGTH_SHORT).show();
        });
    }
}
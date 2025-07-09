package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.silan.robotpeisongcontrl.model.PatrolPoint;
import com.silan.robotpeisongcontrl.model.PatrolScheme;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.utils.PatrolSchemeManager;
import com.silan.robotpeisongcontrl.utils.ScheduledDeliveryManager;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.util.ArrayList;
import java.util.List;

public class ScheduledDeliveryExecutionActivity extends BaseActivity {

    private ScheduledDeliveryTask task;
    private TaskManager taskManager = TaskManager.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheduled_delivery_execution);

        String taskId = getIntent().getStringExtra("task_id");
        if (taskId == null) {
            finish();
            return;
        }

        task = ScheduledDeliveryManager.loadTask(this, taskId);
        if (task == null) {
            finish();
            return;
        }

        // 根据任务类型准备配送任务
        prepareDeliveryTasks();

        // 启动配送流程
        startDeliveryProcess();
    }

    private void prepareDeliveryTasks() {
        taskManager.clearTasks();

        if (task.getTaskType() == ScheduledDeliveryTask.TYPE_POINT) {
            // 点位配送
            taskManager.addTask(task.getPoi());
        } else {
            // 路线配送
            PatrolScheme scheme = PatrolSchemeManager.loadScheme(this, task.getSchemeId());
            if (scheme != null) {
                for (PatrolPoint point : scheme.getPoints()) {
                    taskManager.addTask(point.getPoi());
                }
            }
        }
    }

    private void startDeliveryProcess() {
        // 获取POI列表（简化处理）
        List<Poi> poiList = new ArrayList<>();
        if (task.getTaskType() == ScheduledDeliveryTask.TYPE_POINT) {
            poiList.add(task.getPoi());
        } else {
            PatrolScheme scheme = PatrolSchemeManager.loadScheme(this, task.getSchemeId());
            if (scheme != null) {
                for (PatrolPoint point : scheme.getPoints()) {
                    poiList.add(point.getPoi());
                }
            }
        }

        Intent intent = new Intent(this, MovingActivity.class);
        intent.putExtra("poi_list", new Gson().toJson(poiList));
        intent.putExtra("scheduled_task", true);
        intent.putExtra("selected_doors", task.getSelectedDoors());
        startActivity(intent);
        finish();
    }
}
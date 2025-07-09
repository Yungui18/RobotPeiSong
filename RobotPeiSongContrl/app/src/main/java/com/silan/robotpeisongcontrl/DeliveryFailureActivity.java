package com.silan.robotpeisongcontrl;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.DeliveryFailure;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.utils.DeliveryFailureManager;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import okio.ByteString;

public class DeliveryFailureActivity extends BaseActivity {

    private List<DeliveryFailure> failures = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_failure);

        // 获取失败任务
        String failuresJson = getIntent().getStringExtra("failures");
        if (failuresJson != null) {
            Type type = new TypeToken<List<DeliveryFailure>>(){}.getType();
            failures = new Gson().fromJson(failuresJson, type);
        }

        // 设置UI
        ListView listView = findViewById(R.id.list_failures);
        FailureAdapter adapter = new FailureAdapter(failures);
        listView.setAdapter(adapter);

        Button btnComplete = findViewById(R.id.btn_complete);
        btnComplete.setOnClickListener(v -> completeProcessing());
    }

    private void completeProcessing() {
        // 清除所有失败记录
        DeliveryFailureManager.clearFailures(this);
        if (TaskManager.hasPendingScheduledTask()) {
            ScheduledDeliveryTask task = TaskManager.getNextPendingScheduledTask();
            startScheduledTask(task);
        } else {
            Intent intent = new Intent(DeliveryFailureActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
        finish();
    }

    private void startScheduledTask(ScheduledDeliveryTask task) {
        Intent intent = new Intent(this, ScheduledDeliveryExecutionActivity.class);
        intent.putExtra("task_id", task.getId());
        startActivity(intent);
        finish();
    }

    private class FailureAdapter extends ArrayAdapter<DeliveryFailure> {
        public FailureAdapter(List<DeliveryFailure> failures) {
            super(DeliveryFailureActivity.this, 0, failures);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DeliveryFailure failure = getItem(position);

            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_failed_delivery, parent, false);
            }

            // 设置失败信息
            TextView tvInfo = convertView.findViewById(R.id.tv_failure_info);
            tvInfo.setText(String.format("%s - %s",
                    failure.getPointName(),
                    failure.getFormattedTime(getContext())));

            // 设置仓门信息
            TextView tvDoors = convertView.findViewById(R.id.tv_doors);
            tvDoors.setText(getDoorsString(failure.getDoorsToOpen()));

            // 打开按钮
            Button btnOpen = convertView.findViewById(R.id.btn_open);
            btnOpen.setOnClickListener(v -> openDoors(failure));

            return convertView;
        }

        private String getDoorsString(boolean[] doors) {
            StringBuilder sb = new StringBuilder("需要打开仓门: ");
            boolean hasDoors = false;
            for (int i = 0; i < doors.length; i++) {
                if (doors[i]) {
                    if (hasDoors) sb.append(", ");
                    sb.append(i + 1);
                    hasDoors = true;
                }
            }
            if (!hasDoors) {
                sb.append("无");
            }
            return sb.toString();
        }

        private void openDoors(DeliveryFailure failure) {
            // 打开所有需要打开的仓门
            for (int i = 0; i < failure.getDoorsToOpen().length; i++) {
                if (failure.getDoorsToOpen()[i]) {
                    openCargoDoor(i + 1);
                }
            }

            // 显示操作提示
            Toast.makeText(getContext(), "仓门已打开，请取物", Toast.LENGTH_SHORT).show();

            // 自动关闭仓门（5秒后）
            new Handler().postDelayed(() -> {
                for (int i = 0; i < failure.getDoorsToOpen().length; i++) {
                    if (failure.getDoorsToOpen()[i]) {
                        closeCargoDoor(i + 1);
                    }
                }
                Toast.makeText(getContext(), "仓门已关闭", Toast.LENGTH_SHORT).show();

                // 从列表中移除
                failures.remove(failure);
                notifyDataSetChanged();
            }, 5000);
        }

        private void openCargoDoor(int doorId) {
            // 实际调用打开仓门接口
            RobotController.openCargoDoor(doorId, new RobotController.SimpleCallback() {
                @Override
                public void onSuccess() {
                    Log.d("DeliveryFailure", "Door " + doorId + " opened");
                }

                @Override
                public void onFailure(String error) {
                    Log.e("DeliveryFailure", "Failed to open door " + doorId + ": " + error);
                }
            });
        }

        private void closeCargoDoor(int doorId) {
            // 实际调用关闭仓门接口
            RobotController.closeCargoDoor(doorId, new RobotController.SimpleCallback() {
                @Override
                public void onSuccess() {
                    Log.d("DeliveryFailure", "Door " + doorId + " closed");
                }

                @Override
                public void onFailure(String error) {
                    Log.e("DeliveryFailure", "Failed to close door " + doorId + ": " + error);
                }
            });

        }
    }
}
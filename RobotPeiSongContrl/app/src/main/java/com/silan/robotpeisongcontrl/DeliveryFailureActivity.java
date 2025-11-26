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
import com.silan.robotpeisongcontrl.fragments.BasicSettingsFragment;
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
    private List<BasicSettingsFragment.DoorInfo> enabledDoors;
    private int doorCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_failure);

        // 获取仓门配置
        enabledDoors = BasicSettingsFragment.getEnabledDoors(this);

        // 获取失败任务
        String failuresJson = getIntent().getStringExtra("failures");
        if (failuresJson != null) {
            Type type = new TypeToken<List<DeliveryFailure>>(){}.getType();
            failures = new Gson().fromJson(failuresJson, type);
        }

        // 设置UI
        ListView listView = findViewById(R.id.list_failures);
        FailureAdapter adapter = new FailureAdapter(this, failures, enabledDoors);
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

    private static class FailureAdapter extends ArrayAdapter<DeliveryFailure> {
        private final Context context;
        private final List<DeliveryFailure> failures;
        private final List<BasicSettingsFragment.DoorInfo> enabledDoors;
        public FailureAdapter(Context context, List<DeliveryFailure> failures, List<BasicSettingsFragment.DoorInfo> enabledDoors) {
            super(context, 0, failures);
            this.context = context;
            this.failures = failures;
            this.enabledDoors = enabledDoors;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DeliveryFailure failure = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_failed_delivery, parent, false);
            }

            // 设置失败信息
            TextView tvInfo = convertView.findViewById(R.id.tv_failure_info);
            tvInfo.setText(String.format("%s - %s",
                    failure.getPointName(),
                    failure.getFormattedTime(context)));

            // 设置仓门信息
            TextView tvDoors = convertView.findViewById(R.id.tv_doors);
            tvDoors.setText(getDoorsString(failure.getDoorsToOpen()));

            // 打开按钮
            Button btnOpen = convertView.findViewById(R.id.btn_open);
            btnOpen.setOnClickListener(v -> openDoors(failure));

            return convertView;
        }

        private String getDoorsString(boolean[] doors) {
            if (enabledDoors == null || enabledDoors.isEmpty()) {
                return "需要打开仓门: 无";
            }

            StringBuilder sb = new StringBuilder("需要打开仓门: ");
            boolean hasDoors = false;

            // 遍历任务中记录的 doors 数组
            for (int i = 0; i < doors.length; i++) {
                // 检查：1. 该逻辑索引是否被选中  2. 该逻辑索引是否在当前启用的仓门范围内
                if (doors[i] && i < enabledDoors.size()) {
                    if (hasDoors) sb.append(", ");
                    // 关键：从 enabledDoors 中获取对应的硬件ID
                    int hardwareDoorId = enabledDoors.get(i).getHardwareId();
                    sb.append(hardwareDoorId);
                    hasDoors = true;
                }
            }
            if (!hasDoors) {
                sb.append("无");
            }
            return sb.toString();
        }

        private void openDoors(DeliveryFailure failure) {
            if (enabledDoors == null || enabledDoors.isEmpty()) {
                Toast.makeText(context, "无可用仓门配置", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean[] doorsToOpen = failure.getDoorsToOpen();

            // 打开所有需要打开的仓门
            for (int i = 0; i < doorsToOpen.length; i++) {
                if (doorsToOpen[i] && i < enabledDoors.size()) {
                    int hardwareDoorId = enabledDoors.get(i).getHardwareId();
                    openCargoDoor(hardwareDoorId);
                }
            }

            Toast.makeText(context, "仓门已打开，请取物", Toast.LENGTH_SHORT).show();

            // 自动关闭仓门（5秒后）
            new Handler().postDelayed(() -> {
                for (int i = 0; i < doorsToOpen.length; i++) {
                    if (doorsToOpen[i] && i < enabledDoors.size()) {
                        int hardwareDoorId = enabledDoors.get(i).getHardwareId();
                        closeCargoDoor(hardwareDoorId);
                    }
                }
                Toast.makeText(context, "仓门已关闭", Toast.LENGTH_SHORT).show();

                // 从列表中移除并刷新UI
                failures.remove(failure);
                notifyDataSetChanged();
            }, 5000);
        }

        private void openCargoDoor(int hardwareDoorId) {
            // 调用 RobotController 打开指定硬件ID的仓门
            RobotController.openCargoDoor(hardwareDoorId, new RobotController.SimpleCallback() {
                @Override
                public void onSuccess() {
                    Log.d("DeliveryFailure", "硬件仓门 " + hardwareDoorId + " 打开成功");
                }

                @Override
                public void onFailure(String error) {
                    Log.e("DeliveryFailure", "打开硬件仓门 " + hardwareDoorId + " 失败: " + error);
                    Toast.makeText(context, "打开仓门 " + hardwareDoorId + " 失败", Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void closeCargoDoor(int hardwareDoorId) {
            // 调用 RobotController 关闭指定硬件ID的仓门
            RobotController.closeCargoDoor(hardwareDoorId, new RobotController.SimpleCallback() {
                @Override
                public void onSuccess() {
                    Log.d("DeliveryFailure", "硬件仓门 " + hardwareDoorId + " 关闭成功");
                }

                @Override
                public void onFailure(String error) {
                    Log.e("DeliveryFailure", "关闭硬件仓门 " + hardwareDoorId + " 失败: " + error);
                }
            });

        }
    }
}
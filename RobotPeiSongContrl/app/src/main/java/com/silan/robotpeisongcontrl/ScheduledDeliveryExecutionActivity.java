package com.silan.robotpeisongcontrl;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.silan.robotpeisongcontrl.model.DeliveryRoutePlan;
import com.silan.robotpeisongcontrl.model.PatrolPoint;
import com.silan.robotpeisongcontrl.model.PatrolScheme;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.PointWithDoors;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.utils.DeliveryRoutePlanManager;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.PatrolSchemeManager;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.ScheduledDeliveryManager;
import com.silan.robotpeisongcontrl.utils.TaskManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import okio.ByteString;

public class ScheduledDeliveryExecutionActivity extends BaseActivity {

    private static final String TAG = "ScheduledDeliveryExec";
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

        // 核心修改：借鉴PointDeliveryFragment，异步加载POI后再准备任务+启动流程
        // 替换原有直接调用prepareDeliveryTasks()和startDeliveryProcess()
        loadPoiAndPrepareTask();
    }

    /**
     * 借鉴PointDeliveryFragment的异步POI加载逻辑
     * 1. 异步请求POI列表 2. 主线程解析 3. 准备配送任务 4. 启动配送流程
     */
    private void loadPoiAndPrepareTask() {
        RobotController.getPoiList(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    // 解析POI列表（复用RobotController已有方法，与PointDeliveryFragment同源）
                    String json = responseData.string(StandardCharsets.UTF_8);
                    List<Poi> poiList = RobotController.parsePoiList(json);

                    // 主线程执行：准备任务+启动流程（避免子线程操作UI/任务管理器）
                    runOnUiThread(() -> {
                        prepareDeliveryTasks(poiList); // 传入解析后的POI列表
                        startDeliveryProcess();
                    });
                } catch (Exception e) {
                    Log.e(TAG, "解析POI列表失败", e);
                    finish(); // 解析失败，关闭页面
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "加载POI列表失败", e);
                finish(); // 请求失败，关闭页面
            }
        });
    }

    /**
     * 重构准备配送任务方法：移除不存在的方法调用，复用已有合法方法
     * @param poiList 异步加载并解析后的全量POI列表
     */
    private void prepareDeliveryTasks(List<Poi> poiList) {
        taskManager.clearTasks();

        if (task.getTaskType() == ScheduledDeliveryTask.TYPE_POINT) {
            // 点位配送：原有逻辑不变，无需修改
            taskManager.addTask(task.getPoi());
        } else {
            // 路线配送：核心修复——按hashCode匹配查找原始路线方案，添加详细调试日志
            DeliveryRoutePlan routePlan = null;
            // 1. 加载所有已保存的路线方案
            List<DeliveryRoutePlan> allPlans = DeliveryRoutePlanManager.loadAllPlans(this);
            // 新增调试日志：打印加载到的方案总数和每个方案的关键信息
            Log.d(TAG, "【路线配送】加载到本地的路线方案总数：" + allPlans.size());
            for (int i = 0; i < allPlans.size(); i++) {
                DeliveryRoutePlan p = allPlans.get(i);
                Log.d(TAG, "【路线配送】方案" + (i+1) + "：名称=" + p.getPlanName() + "，planId=" + p.getPlanId() + "，schemeId(hashCode)=" + p.getSchemeId() + "，是否启用=" + p.isEnabled());
            }
            Log.d(TAG, "【路线配送】任务中需要匹配的schemeId：" + task.getSchemeId());

            // 2. 遍历匹配：任务中的schemeId（hashCode） == 方案的getSchemeId() + 方案启用校验
            for (DeliveryRoutePlan plan : allPlans) {
                if (plan.getSchemeId() == task.getSchemeId() && plan.isEnabled()) {
                    routePlan = plan;
                    Log.d(TAG, "【路线配送】找到匹配的路线方案：" + plan.getPlanName());
                    break;
                }
            }

            // 3. 方案找到后，原有逻辑不变，继续加载点位和仓门
            if (routePlan != null && routePlan.getPointList() != null && !routePlan.getPointList().isEmpty() && !poiList.isEmpty()) {
                Log.d(TAG, "【路线配送】开始加载方案中的点位和仓门，共" + routePlan.getPointList().size() + "个点位");
                for (PointWithDoors pointWithDoors : routePlan.getPointList()) {
                    // 复用原有按名称匹配点位的逻辑
                    Poi poi = RobotController.findPoiByName(pointWithDoors.getPointName(), poiList);
                    if (poi != null) {
                        // 添加点位到任务管理器，并绑定仓门ID
                        taskManager.addPointWithDoors(poi, pointWithDoors.getDoorIds());
                        Log.d(TAG, "【路线配送】成功添加点位：" + pointWithDoors.getPointName() + "，绑定仓门ID：" + pointWithDoors.getDoorIds());
                    } else {
                        Log.w(TAG, "【路线配送】未找到匹配的点位：" + pointWithDoors.getPointName());
                    }
                }
            } else {
                // 方案未找到时，打印详细失败原因（方便调试）
                String failReason = "";
                if (allPlans.isEmpty()) {
                    failReason = "本地无任何已持久化保存的路线方案（方案未保存或被清除）";
                } else if (routePlan == null) {
                    failReason = "无方案的schemeId与任务匹配，或匹配方案已被禁用";
                } else if (routePlan.getPointList() == null || routePlan.getPointList().isEmpty()) {
                    failReason = "匹配到的方案无任何点位配置";
                } else if (poiList.isEmpty()) {
                    failReason = "未从机器人加载到任何POI点位列表";
                }
                Log.e(TAG, "路线方案加载失败：" + failReason + "，目标schemeId[" + task.getSchemeId() + "]");
            }
        }
    }

    private void startDeliveryProcess() {
        TaskManager taskManager = TaskManager.getInstance();
        if (task.getTaskType() == ScheduledDeliveryTask.TYPE_POINT) {
            // 点位配送：原有逻辑完全不变，保持兼容
            List<Poi> poiList = new ArrayList<>();
            poiList.add(task.getPoi());
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(poiList));
            intent.putExtra("scheduled_task", true);
            intent.putExtra("selected_doors", task.getSelectedDoors());
            startActivity(intent);
        } else {
            // 路线配送：核心修改——跳MovingActivity，传递完整参数
            List<Poi> routePoiList = taskManager.getTasks(); // 获取已加载的有序点位
            if (routePoiList == null || routePoiList.isEmpty()) {
                Log.e(TAG, "【路线配送】任务管理器无有效点位，执行终止");
                Toast.makeText(this, "路线无有效点位", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            Intent intent = new Intent(this, MovingActivity.class);
            intent.putExtra("poi_list", new Gson().toJson(routePoiList)); // 有序点位列表
            intent.putExtra("scheduled_task", true); // 标记定时任务
            intent.putExtra("is_route_delivery", true); // 新增：标记是路线配送
            startActivity(intent);
        }
        finish();
    }
}
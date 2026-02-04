package com.silan.robotpeisongcontrl.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.model.DeliveryRoutePlan;
import com.silan.robotpeisongcontrl.model.PatrolScheme;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.PointWithDoors;
import com.silan.robotpeisongcontrl.model.ScheduledDeliveryTask;
import com.silan.robotpeisongcontrl.utils.DeliveryRoutePlanManager;
import com.silan.robotpeisongcontrl.utils.ExactAlarmPermissionHelper;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.PatrolSchemeManager;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.ScheduledDeliveryManager;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import okio.ByteString;

public class RouteDeliveryFragment extends Fragment {
    private static final String TAG = "RouteDeliveryFragment";
    // UI组件
    private Button btnCreatePlan; // 新建方案按钮
    private Spinner spinnerPlans; // 方案选择下拉框
    private LinearLayout doorButtonsContainer; // 仓门按钮容器（弹窗用）
    private TimePicker timePicker; // 配送时间选择
    private Button btnConfirmTask; // 确认创建定时任务按钮
    private TextView tvPlanDesc; // 方案详情展示（点位+仓门）

    // 数据缓存
    private List<Poi> poiList = new ArrayList<>(); // 所有可用点位（与点位配送同源）
    private List<BasicSettingsFragment.DoorInfo> enabledDoors; // 启用的仓门列表（复用）
    private List<DeliveryRoutePlan> routePlanList = new ArrayList<>(); // 已创建的路线方案
    private DeliveryRoutePlan selectedPlan; // 当前选中的路线方案
    private boolean[] tempSelectedDoors; // 弹窗中临时选中的仓门（逐点位绑定用）
    private Button[] tempDoorButtons; // 弹窗中临时仓门按钮

    // 权限相关
    private ActivityResultLauncher<Intent> alarmPermissionLauncher;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_route_delivery, container, false);
        // 初始化权限启动器（复用点位配送逻辑）
        initAlarmPermissionLauncher();
        // 初始化UI组件
        initView(view);
        // 加载基础数据：启用仓门、POI列表
        loadBaseData();
        // 绑定点击事件
        bindClickEvents();
        return view;
    }

    /**
     * 初始化精确闹钟权限启动器（完全复用PointDeliveryFragment）
     */
    private void initAlarmPermissionLauncher() {
        alarmPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> ExactAlarmPermissionHelper.handlePermissionResult(requireContext())
        );
    }

    /**
     * 初始化UI组件（对应新布局fragment_route_delivery_new.xml）
     */
    private void initView(View view) {
        btnCreatePlan = view.findViewById(R.id.btn_create_plan);
        spinnerPlans = view.findViewById(R.id.spinner_plans);
        timePicker = view.findViewById(R.id.time_picker);
        btnConfirmTask = view.findViewById(R.id.btn_confirm_task);
        tvPlanDesc = view.findViewById(R.id.tv_plan_desc);
        // 适配Android 12+ TimePicker样式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            timePicker.setHour(10);
            timePicker.setMinute(0);
        }
    }

    /**
     * 绑定所有点击事件
     */
    private void bindClickEvents() {
        // 新建方案按钮：弹出方案创建弹窗
        btnCreatePlan.setOnClickListener(v -> showCreatePlanDialog());
        // 确认创建定时任务按钮
        btnConfirmTask.setOnClickListener(v -> saveScheduledRouteTask());
        // 方案选择下拉框：选择后展示方案详情
        spinnerPlans.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < routePlanList.size()) {
                    selectedPlan = routePlanList.get(position);
                    showPlanDetail(selectedPlan);
                } else {
                    selectedPlan = null;
                    tvPlanDesc.setText("请选择或新建路线方案");
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                selectedPlan = null;
                tvPlanDesc.setText("请选择或新建路线方案");
            }
        });
    }

    /**
     * 加载基础数据：1.启用的仓门列表 2.POI点位列表（完全复用PointDeliveryFragment逻辑）
     */
    private void loadBaseData() {
        // 1. 加载启用的仓门列表（复用BasicSettingsFragment）
        enabledDoors = BasicSettingsFragment.getEnabledDoors(requireContext());
        routePlanList = DeliveryRoutePlanManager.loadAllPlans(requireContext());
        // 2. 加载POI点位列表（复用RobotController，与点位配送同源）
        loadPoiList();
    }

    /**
     * 加载POI列表（完全复制PointDeliveryFragment的loadPoiList方法）
     */
    private void loadPoiList() {
        RobotController.getPoiList(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                // 成功回调：切换到主线程解析+更新UI（避免子线程操作UI）
                requireActivity().runOnUiThread(() -> {
                    try {
                        String json = responseData.string(StandardCharsets.UTF_8);
                        poiList = RobotController.parsePoiList(json);
                        initPlanSpinner(); // 主线程更新下拉框，安全无崩溃
                    } catch (Exception e) {
                        Log.e(TAG, "解析POI列表失败", e);
                        Toast.makeText(requireContext(), "点位列表解析失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                // 失败回调：切换到主线程打日志+弹Toast（核心修复：解决子线程弹Toast崩溃）
                requireActivity().runOnUiThread(() -> {
                    Log.e(TAG, "加载POI列表失败", e);
                    Toast.makeText(requireContext(), "无法连接机器人，请检查网络连接", Toast.LENGTH_LONG).show();
                    initPlanSpinner(); // 即使失败，也初始化下拉框，避免空指针
                });
            }
        });
    }

    /**
     * 初始化路线方案选择下拉框（暂无方案时提示）
     */
    private void initPlanSpinner() {
        List<String> planNames = new ArrayList<>();
        // 双层兜底：先判断路线方案，再判断POI列表
        if (routePlanList.isEmpty()) {
            planNames.add(poiList.isEmpty() ? "暂无点位，请检查网络" : "暂无路线方案，请点击新建");
        } else {
            for (DeliveryRoutePlan plan : routePlanList) {
                planNames.add(plan.getPlanName());
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                planNames
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPlans.setAdapter(adapter);
    }

    /**
     * 显示路线方案创建弹窗（核心逻辑：逐点位选择+绑定专属仓门）
     */
    private void showCreatePlanDialog() {
        // 前置校验：POI或仓门为空时无法创建
        if (poiList.isEmpty()) {
            Toast.makeText(requireContext(), "暂无可用点位，无法创建方案", Toast.LENGTH_SHORT).show();
            return;
        }
        if (enabledDoors.isEmpty()) {
            Toast.makeText(requireContext(), "暂无启用的仓门，无法创建方案", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. 初始化弹窗布局
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_create_route_plan, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);

        // 2. 获取弹窗内组件
        EditText etPlanName = dialogView.findViewById(R.id.et_plan_name); // 方案名称输入框
        Spinner spinnerPoi = dialogView.findViewById(R.id.spinner_poi); // 点位选择下拉框
        Button btnAddPoint = dialogView.findViewById(R.id.btn_add_point); // 添加点位到方案
        LinearLayout llPointList = dialogView.findViewById(R.id.ll_point_list); // 已选点位+仓门展示列表
        doorButtonsContainer = dialogView.findViewById(R.id.door_buttons_container); // 仓门按钮容器
        Button btnSavePlan = dialogView.findViewById(R.id.btn_save_plan); // 保存方案按钮

        // 3. 初始化点位选择下拉框
        List<String> poiNames = new ArrayList<>();
        for (Poi poi : poiList) {
            poiNames.add(poi.getDisplayName());
        }
        ArrayAdapter<String> poiAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                poiNames
        );
        poiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPoi.setAdapter(poiAdapter);

        // 4. 初始化临时仓门按钮（复用PointDeliveryFragment的布局逻辑）
        initTempDoorButtons();
        // 存储当前方案的点位+仓门列表
        List<PointWithDoors> currentPointList = new ArrayList<>();

        // 5. 添加点位按钮点击事件：选择点位+绑定仓门后添加到方案
        btnAddPoint.setOnClickListener(v -> {
            int selectedPoiPos = spinnerPoi.getSelectedItemPosition();
            if (selectedPoiPos < 0 || selectedPoiPos >= poiList.size()) {
                Toast.makeText(requireContext(), "请选择点位", Toast.LENGTH_SHORT).show();
                return;
            }
            // 校验：至少选择一个仓门
            boolean hasDoorSelected = false;
            for (boolean selected : tempSelectedDoors) {
                if (selected) {
                    hasDoorSelected = true;
                    break;
                }
            }
            if (!hasDoorSelected) {
                Toast.makeText(requireContext(), "请为该点位选择至少一个仓门", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取选中的点位信息
            Poi selectedPoi = poiList.get(selectedPoiPos);
            // 收集该点位绑定的仓门ID和名称
            List<Integer> bindDoorIds = new ArrayList<>();
            List<String> bindDoorNames = new ArrayList<>();
            for (int i = 0; i < enabledDoors.size(); i++) {
                if (tempSelectedDoors[i]) {
                    BasicSettingsFragment.DoorInfo doorInfo = enabledDoors.get(i);
                    bindDoorIds.add(doorInfo.getHardwareId());
                    bindDoorNames.add(BasicSettingsFragment.getStandardDoorButtonText(doorInfo));
                }
            }

            // 创建点位-仓门绑定对象
            PointWithDoors pointWithDoors = new PointWithDoors(
                    selectedPoi.getId(),
                    selectedPoi.getDisplayName(),
                    bindDoorIds,
                    bindDoorNames
            );
            currentPointList.add(pointWithDoors);

            // 动态添加到已选点位列表（展示：点位名称 + 绑定的仓门）
            String doorStr = String.join("、", bindDoorNames);
            TextView tvPointItem = new TextView(requireContext());
            tvPointItem.setText(String.format("%d. %s → 仓门：%s", currentPointList.size(), selectedPoi.getDisplayName(), doorStr));
            tvPointItem.setTextSize(14);
            tvPointItem.setTextColor(Color.BLACK);
            tvPointItem.setPadding(0, 8, 0, 8);
            llPointList.addView(tvPointItem);

            // 重置仓门选择状态
            resetTempDoorButtons();
        });

        // 6. 保存方案按钮点击事件
        btnSavePlan.setOnClickListener(v -> {
            String planName = etPlanName.getText().toString().trim();
            // 校验：方案名称非空、至少添加一个点位
            if (planName.isEmpty()) {
                Toast.makeText(requireContext(), "请输入方案名称", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentPointList.isEmpty()) {
                Toast.makeText(requireContext(), "请至少添加一个点位到方案", Toast.LENGTH_SHORT).show();
                return;
            }

            // 创建新的路线方案
            DeliveryRoutePlan newPlan = new DeliveryRoutePlan(planName, currentPointList);
            routePlanList.add(newPlan);
            DeliveryRoutePlanManager.savePlan(requireContext(), newPlan);
            // 更新方案选择下拉框
            initPlanSpinner();
            // 关闭弹窗
            dialog.dismiss();
            Toast.makeText(requireContext(), "路线方案创建成功", Toast.LENGTH_SHORT).show();
        });

        // 显示弹窗
        dialog.show();
    }

    /**
     * 初始化临时仓门按钮（弹窗内使用，复用PointDeliveryFragment的布局和样式逻辑）
     */
    private void initTempDoorButtons() {
        doorButtonsContainer.removeAllViews();
        tempDoorButtons = new Button[enabledDoors.size()];
        tempSelectedDoors = new boolean[enabledDoors.size()];
        for (int i = 0; i < enabledDoors.size(); i++) {
            BasicSettingsFragment.DoorInfo doorInfo = enabledDoors.get(i);
            Button button = new Button(requireContext());
            button.setId(View.generateViewId());
            // 标准化仓门按钮文本（复用BasicSettingsFragment）
            button.setText(BasicSettingsFragment.getStandardDoorButtonText(doorInfo));
            // 复用原有样式
            button.setBackgroundResource(R.drawable.button_sky_blue_rect);
            button.setTextColor(Color.WHITE);
            button.setTextSize(16);
            // 布局参数（均分宽度）
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
            );
            params.setMargins(8, 8, 8, 8);
            button.setLayoutParams(params);
            // 仓门选择点击事件
            final int index = i;
            button.setOnClickListener(v -> {
                tempSelectedDoors[index] = !tempSelectedDoors[index];
                updateTempDoorButtonState(index);
            });
            // 添加到容器
            doorButtonsContainer.addView(button);
            tempDoorButtons[i] = button;
        }
    }

    /**
     * 更新临时仓门按钮状态（蓝色=未选，红色=已选，复用原有逻辑）
     */
    private void updateTempDoorButtonState(int index) {
        if (tempSelectedDoors[index]) {
            tempDoorButtons[index].setBackgroundResource(R.drawable.button_red_rect);
        } else {
            tempDoorButtons[index].setBackgroundResource(R.drawable.button_sky_blue_rect);
        }
    }

    /**
     * 重置临时仓门按钮选择状态
     */
    private void resetTempDoorButtons() {
        Arrays.fill(tempSelectedDoors, false);
        for (int i = 0; i < tempDoorButtons.length; i++) {
            updateTempDoorButtonState(i);
        }
    }

    /**
     * 展示选中路线方案的详情（点位顺序+每个点位绑定的仓门）
     */
    private void showPlanDetail(DeliveryRoutePlan plan) {
        if (plan == null || plan.getPointList().isEmpty()) {
            tvPlanDesc.setText("方案无有效点位");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("方案详情（配送顺序）：\n");
        List<PointWithDoors> pointList = plan.getPointList();
        for (int i = 0; i < pointList.size(); i++) {
            PointWithDoors pwd = pointList.get(i);
            String doorNames = String.join("、", pwd.getDoorNames());
            sb.append(String.format("%d. %s → 开启仓门：%s\n", i + 1, pwd.getPointName(), doorNames));
        }
        tvPlanDesc.setText(sb.toString());
    }

    /**
     * 保存路线配送定时任务（复用点位配送的权限校验、任务调度逻辑）
     */
    private void saveScheduledRouteTask() {
        // 1. 权限校验：Android 12+ 检查精确闹钟权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !ExactAlarmPermissionHelper.canScheduleExactAlarms(requireContext())) {
            ExactAlarmPermissionHelper.requestExactAlarmPermission((AppCompatActivity) requireActivity(), alarmPermissionLauncher);
            return;
        }

        // 2. 业务校验：选择路线方案、配送时间
        if (selectedPlan == null) {
            Toast.makeText(requireContext(), "请选择有效的路线方案", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedPlan.getPointList().isEmpty()) {
            Toast.makeText(requireContext(), "选中方案无有效点位", Toast.LENGTH_SHORT).show();
            return;
        }

        // 3. 获取配送时间（适配Android版本）
        int hour = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? timePicker.getHour() : timePicker.getCurrentHour();
        int minute = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? timePicker.getMinute() : timePicker.getCurrentMinute();

        // 4. 创建定时任务（核心修复：直接用planId的hashCode作为schemeId，唯一且可反向匹配）
        ScheduledDeliveryTask task = new ScheduledDeliveryTask();
        task.setTaskType(ScheduledDeliveryTask.TYPE_ROUTE);
        // 关键修复：移除所有进制转换/取模，直接存储hashCode
        task.setSchemeId(selectedPlan.getSchemeId());

        task.setHour(hour);
        task.setMinute(minute);
        task.setEnabled(true);
        task.updateLastModified();

        // 5. 保存任务并调度闹钟（复用原有逻辑，无需修改）
        try {
            ScheduledDeliveryManager.saveTask(requireContext(), task);
            ScheduledDeliveryManager.scheduleTask(requireContext(), task);
            Toast.makeText(requireContext(), "路线配送定时任务创建成功", Toast.LENGTH_SHORT).show();
            // 重置选择状态
            selectedPlan = null;
            tvPlanDesc.setText("请选择或新建路线方案");
        } catch (SecurityException e) {
            Log.e(TAG, "创建定时任务失败：权限异常", e);
            Toast.makeText(requireContext(), "创建失败：无法获取精确闹钟权限", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "创建定时任务失败", e);
            Toast.makeText(requireContext(), "创建失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ========== 生命周期管理 ==========
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        poiList.clear();
        routePlanList.clear();
        enabledDoors.clear();
    }
}
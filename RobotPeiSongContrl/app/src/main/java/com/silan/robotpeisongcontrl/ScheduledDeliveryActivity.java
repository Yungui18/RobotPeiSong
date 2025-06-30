package com.silan.robotpeisongcontrl;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.model.PatrolScheme;
import com.silan.robotpeisongcontrl.model.Poi;
import com.silan.robotpeisongcontrl.model.ScheduledTask;
import com.silan.robotpeisongcontrl.service.ScheduledTaskService;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.PatrolSchemeManager;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.ScheduledTaskManager;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import okio.ByteString;

public class ScheduledDeliveryActivity extends BaseActivity {

    private Switch switchEnable;
    private Button btnViewTasks;
    private LinearLayout layoutForm;
    private RadioGroup rgDeliveryType;
    private RadioButton rbPointDelivery;
    private RadioButton rbRouteDelivery;
    private LinearLayout layoutPointDelivery;
    private LinearLayout layoutRouteDelivery;
    private Spinner spinnerPois; // 改为Spinner选择点位
    private TextView tvSelectedPoi;
    private LinearLayout layoutDoors;
    private TextView tvDoorsInfo;
    private Spinner spinnerSchemes;
    private Button btnSelectTime;
    private TextView tvSelectedTime;
    private RadioGroup rgPriority;
    private RadioButton rbPriorityA;
    private RadioButton rbPriorityB;
    private Button btnCreateTask;

    private List<Poi> poiList = new ArrayList<>(); // 存储所有POI点位
    private Poi selectedPoi;
    private List<Integer> selectedDoors = new ArrayList<>();
    private Calendar selectedTime;
    private int selectedPriority = 0; // 0:优先级A, 1:优先级B
    private static final String PREFS_NAME = "ScheduledDeliveryPrefs";
    private static final String KEY_ENABLED = "enabled";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheduled_delivery);

        // 初始化视图
        switchEnable = findViewById(R.id.switch_enable);
        btnViewTasks = findViewById(R.id.btn_view_tasks);
        layoutForm = findViewById(R.id.layout_form);
        rgDeliveryType = findViewById(R.id.rg_delivery_type);
        rbPointDelivery = findViewById(R.id.rb_point_delivery);
        rbRouteDelivery = findViewById(R.id.rb_route_delivery);
        layoutPointDelivery = findViewById(R.id.layout_point_delivery);
        layoutRouteDelivery = findViewById(R.id.layout_route_delivery);
        spinnerPois = findViewById(R.id.spinner_pois);
        tvSelectedPoi = findViewById(R.id.tv_selected_poi);
        layoutDoors = findViewById(R.id.layout_doors);
        tvDoorsInfo = findViewById(R.id.tv_doors_info);
        spinnerSchemes = findViewById(R.id.spinner_schemes);
        btnSelectTime = findViewById(R.id.btn_select_time);
        tvSelectedTime = findViewById(R.id.tv_selected_time);
        rgPriority = findViewById(R.id.rg_priority);
        rbPriorityA = findViewById(R.id.rb_priority_a);
        rbPriorityB = findViewById(R.id.rb_priority_b);
        btnCreateTask = findViewById(R.id.btn_create_task);

        // 恢复开关状态
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(KEY_ENABLED, false);
        switchEnable.setChecked(enabled);
        layoutForm.setVisibility(enabled ? View.VISIBLE : View.GONE);

        // 设置开关监听
        switchEnable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // 保存开关状态
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            editor.putBoolean(KEY_ENABLED, isChecked);
            editor.apply();

            layoutForm.setVisibility(isChecked ? View.VISIBLE : View.GONE);

            // 更新服务状态
            updateServiceState(isChecked);
        });

        // 确保服务状态与开关一致
        updateServiceState(enabled);

        // 查看已有任务按钮
        btnViewTasks.setOnClickListener(v -> {
            startActivity(new Intent(this, ScheduledTasksActivity.class));
        });

        // 配送类型选择监听
        rgDeliveryType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_point_delivery) {
                layoutPointDelivery.setVisibility(View.VISIBLE);
                layoutRouteDelivery.setVisibility(View.GONE);
            } else {
                layoutPointDelivery.setVisibility(View.GONE);
                layoutRouteDelivery.setVisibility(View.VISIBLE);
            }
        });

        // 初始化仓门选择按钮
        initDoorButtons();

        // 初始化巡游方案下拉框
        initSchemeSpinner();

        // 初始化POI点位下拉框
        initPoiSpinner();

        // 选择时间按钮
        btnSelectTime.setOnClickListener(v -> showTimePickerDialog());

        // 创建任务按钮
        btnCreateTask.setOnClickListener(v -> createScheduledTask());
//
//        // 默认隐藏表单
//        layoutForm.setVisibility(View.GONE);
    }
    private void updateServiceState(boolean enabled) {
        Intent serviceIntent = new Intent(this, ScheduledTaskService.class);
        if (enabled) {
            startService(serviceIntent);
        } else {
            stopService(serviceIntent);
        }
    }

    private void initDoorButtons() {
        // 创建4个仓门按钮
        for (int i = 1; i <= 4; i++) {
            Button doorButton = new Button(this);
            doorButton.setText("仓门 " + i);
            doorButton.setTag(i);
            doorButton.setBackgroundResource(R.drawable.button_blue_rect);
            doorButton.setPadding(8, 8, 8, 8);
            doorButton.setTextSize(14);

            // 设置点击监听
            doorButton.setOnClickListener(v -> {
                int doorId = (int) v.getTag();
                if (selectedDoors.contains(doorId)) {
                    selectedDoors.remove(Integer.valueOf(doorId));
                    doorButton.setBackgroundResource(R.drawable.button_blue_rect);
                } else {
                    selectedDoors.add(doorId);
                    doorButton.setBackgroundResource(R.drawable.button_red_rect);
                }

                // 更新已选仓门显示
                updateSelectedDoorsDisplay();
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.0f
            );
            params.setMargins(4, 4, 4, 4);
            doorButton.setLayoutParams(params);

            layoutDoors.addView(doorButton);
        }
    }

    private void initSchemeSpinner() {
        // 加载巡游方案
        Map<Integer, PatrolScheme> schemes = PatrolSchemeManager.loadSchemes(this);
        List<String> schemeNames = new ArrayList<>();
        schemeNames.add("请选择巡游方案");
        for (Map.Entry<Integer, PatrolScheme> entry : schemes.entrySet()) {
            schemeNames.add("方案 " + entry.getKey());
        }

        // 创建适配器
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, schemeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSchemes.setAdapter(adapter);
    }

    private void initPoiSpinner() {
        // 从SharedPreferences获取缓存的POI列表
        SharedPreferences prefs = getSharedPreferences("app_cache", Context.MODE_PRIVATE);
        String poiListJson = prefs.getString("cached_poi_list", null);

        if (poiListJson != null) {
            // 解析缓存的POI列表
            Type listType = new TypeToken<ArrayList<Poi>>(){}.getType();
            poiList = new Gson().fromJson(poiListJson, listType);
            populatePoiSpinner();
        }

        // 从网络获取POI列表 - 使用正确的回调接口
        RobotController.getPoiList(new OkHttpUtils.ResponseCallback() {
            @Override
            public void onSuccess(ByteString responseData) {
                try {
                    String json = responseData.string(StandardCharsets.UTF_8);
                    List<Poi> pois = RobotController.parsePoiList(json);
                    poiList.clear();
                    poiList.addAll(pois);

                    // 保存到缓存
                    SharedPreferences prefs = getSharedPreferences("app_cache", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("cached_poi_list", new Gson().toJson(pois));
                    editor.apply();

                    runOnUiThread(() -> populatePoiSpinner());
                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(ScheduledDeliveryActivity.this, "解析点位数据失败", Toast.LENGTH_SHORT).show()
                    );
                }
            }

            @Override
            public void onFailure(Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(ScheduledDeliveryActivity.this, "获取点位列表失败", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void populatePoiSpinner() {
        List<String> poiNames = new ArrayList<>();
        poiNames.add("请选择配送点位");
        for (Poi poi : poiList) {
            poiNames.add(poi.getDisplayName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, poiNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPois.setAdapter(adapter);

        // 设置选择监听
        spinnerPois.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    selectedPoi = poiList.get(position - 1);
                    tvSelectedPoi.setText(selectedPoi.getDisplayName());
                } else {
                    selectedPoi = null;
                    tvSelectedPoi.setText("未选择");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedPoi = null;
                tvSelectedPoi.setText("未选择");
            }
        });
    }

    private void showTimePickerDialog() {
        Calendar currentTime = Calendar.getInstance();
        int hour = currentTime.get(Calendar.HOUR_OF_DAY);
        int minute = currentTime.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minuteOfHour) -> {
                    selectedTime = Calendar.getInstance();
                    selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedTime.set(Calendar.MINUTE, minuteOfHour);
                    selectedTime.set(Calendar.SECOND, 0);
                    tvSelectedTime.setText(String.format("%02d:%02d", hourOfDay, minuteOfHour));
                }, hour, minute, true);

        timePickerDialog.show();
    }

    private void createScheduledTask() {
        if (selectedTime == null) {
            Toast.makeText(this, "请选择时间", Toast.LENGTH_SHORT).show();
            return;
        }

        if (rbPointDelivery.isChecked() && selectedPoi == null) {
            Toast.makeText(this, "请选择配送点位", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedDoors.isEmpty()) {
            Toast.makeText(this, "请选择至少一个仓门", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查优先级选择
        int selectedPriorityId = rgPriority.getCheckedRadioButtonId();
        if (selectedPriorityId == R.id.rb_priority_a) {
            selectedPriority = 0;
        } else if (selectedPriorityId == R.id.rb_priority_b) {
            selectedPriority = 1;
        } else {
            Toast.makeText(this, "请选择优先级", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建定时任务
        ScheduledTask task = new ScheduledTask();
        task.setId(UUID.randomUUID().toString());

        if (rbPointDelivery.isChecked()) {
            task.setType(0);
            task.setName(selectedPoi.getDisplayName());
            task.setPoiId(selectedPoi.getId());
        } else {
            task.setType(1);
            int selectedPosition = spinnerSchemes.getSelectedItemPosition();
            if (selectedPosition <= 0) {
                Toast.makeText(this, "请选择巡游方案", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取方案ID
            Map<Integer, PatrolScheme> schemes = PatrolSchemeManager.loadSchemes(this);
            int schemeId = new ArrayList<>(schemes.keySet()).get(selectedPosition - 1);

            task.setName("方案 " + schemeId);
            task.setSchemeId(schemeId);
        }

        task.setDoors(new ArrayList<>(selectedDoors));
        task.setTriggerTime(selectedTime);
        task.setPriority(selectedPriority);

        // 保存任务
        ScheduledTaskManager.saveTask(this, task);

        Toast.makeText(this, "定时任务创建成功", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void updateSelectedDoorsDisplay() {
        StringBuilder doorsInfo = new StringBuilder("已选仓门: ");
        for (int door : selectedDoors) {
            doorsInfo.append("仓门").append(door).append(" ");
        }
        if (selectedDoors.isEmpty()) {
            doorsInfo.append("无");
        }

        tvDoorsInfo.setText(doorsInfo.toString());
    }
}
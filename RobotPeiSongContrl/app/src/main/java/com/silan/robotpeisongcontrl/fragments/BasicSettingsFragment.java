package com.silan.robotpeisongcontrl.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.model.DoorRowConfig;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;


public class BasicSettingsFragment extends Fragment {
    private static final String PREFS_NAME = "BasicDoorSettings";
    private static final String ROW_CONFIGS_KEY = "row_configs";

    private List<CheckBox> enableChecks = new ArrayList<>();
    private List<Spinner> typeSpinners = new ArrayList<>();
    private List<Spinner> layoutSpinners = new ArrayList<>();
    private List<DoorRowConfig> rowConfigs = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_basic_settings, container, false);
        initViews(view);
        loadSavedConfigs();
        return view;
    }

    private void initViews(View view) {
        // 添加5行控件（可根据实际布局调整数量）
        addRowControls(view, R.id.cb_enable_1, R.id.spinner_type_1, R.id.spinner_layout_1);
        addRowControls(view, R.id.cb_enable_2, R.id.spinner_type_2, R.id.spinner_layout_2);
        addRowControls(view, R.id.cb_enable_3, R.id.spinner_type_3, R.id.spinner_layout_3);
        addRowControls(view, R.id.cb_enable_4, R.id.spinner_type_4, R.id.spinner_layout_4);
        addRowControls(view, R.id.cb_enable_5, R.id.spinner_type_5, R.id.spinner_layout_5);

        // 设置Spinner适配器
        initSpinnerAdapters();

        // 保存按钮点击事件
        Button btnSave = view.findViewById(R.id.btn_save_basic);
        if (btnSave != null) {
            btnSave.setOnClickListener(v -> saveConfigs());
        }
    }

    /**
     * 批量添加一行的控件到列表
     */
    private void addRowControls(View view, int checkBoxId, int typeSpinnerId, int layoutSpinnerId) {
        CheckBox checkBox = view.findViewById(checkBoxId);
        Spinner typeSpinner = view.findViewById(typeSpinnerId);
        Spinner layoutSpinner = view.findViewById(layoutSpinnerId);

        // 空安全检查：避免布局中缺少控件导致崩溃
        if (checkBox != null && typeSpinner != null && layoutSpinner != null) {
            enableChecks.add(checkBox);
            typeSpinners.add(typeSpinner);
            layoutSpinners.add(layoutSpinner);
        }
    }

    /**
     * 初始化所有Spinner的适配器
     */
    private void initSpinnerAdapters() {
        Context context = requireContext();
        // 仓门类型适配器（0:电机, 1:电磁锁, 2:推杆电机）
        ArrayAdapter<CharSequence> typeAdapter = ArrayAdapter.createFromResource(
                context, R.array.door_type_array, android.R.layout.simple_spinner_item
        );
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // 仓门布局适配器（0:单仓, 1:双仓）
        ArrayAdapter<CharSequence> layoutAdapter = ArrayAdapter.createFromResource(
                context, R.array.door_layout_array, android.R.layout.simple_spinner_item
        );
        layoutAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // 为所有Spinner设置适配器
        for (Spinner spinner : typeSpinners) {
            spinner.setAdapter(typeAdapter);
        }
        for (Spinner spinner : layoutSpinners) {
            spinner.setAdapter(layoutAdapter);
        }
    }

    private void loadSavedConfigs() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(ROW_CONFIGS_KEY, null);

        // 反序列化配置
        if (json != null) {
            Type type = new TypeToken<List<DoorRowConfig>>() {}.getType();
            rowConfigs = new Gson().fromJson(json, type);
        } else {
            // 初始化默认配置（与控件数量一致）
            rowConfigs = new ArrayList<>();
            for (int i = 0; i < enableChecks.size(); i++) {
                rowConfigs.add(new DoorRowConfig());
            }
        }

        // 更新UI：确保配置数量与控件数量匹配
        updateUIWithConfigs();
    }

    /**
     * 根据配置更新UI控件状态
     */
    private void updateUIWithConfigs() {
        // 避免配置数量与控件数量不匹配导致越界
        int minCount = Math.min(rowConfigs.size(), enableChecks.size());
        for (int i = 0; i < minCount; i++) {
            DoorRowConfig config = rowConfigs.get(i);
            enableChecks.get(i).setChecked(config.isEnabled());
            typeSpinners.get(i).setSelection(config.getType());
            layoutSpinners.get(i).setSelection(config.getLayout());
        }
    }

    private void saveConfigs() {
        // 从UI读取当前状态并更新配置
        int minCount = Math.min(rowConfigs.size(), enableChecks.size());
        for (int i = 0; i < minCount; i++) {
            DoorRowConfig config = rowConfigs.get(i);
            config.setEnabled(enableChecks.get(i).isChecked());
            config.setType(typeSpinners.get(i).getSelectedItemPosition());
            config.setLayout(layoutSpinners.get(i).getSelectedItemPosition());
        }

        // 验证资源占用是否超限
        if (!validateResourceUsage()) {
            return;
        }

        // 序列化并保存配置
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = new Gson().toJson(rowConfigs);
        prefs.edit().putString(ROW_CONFIGS_KEY, json).apply();

        Toast.makeText(requireContext(), "基础设置已保存", Toast.LENGTH_SHORT).show();
    }

    // 提供静态方法获取配置
    public static List<DoorRowConfig> getRowConfigs(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        String json = prefs.getString(ROW_CONFIGS_KEY, null);
        if (json != null) {
            Type type = new TypeToken<List<DoorRowConfig>>(){}.getType();
            return new Gson().fromJson(json, type);
        }
        List<DoorRowConfig> defaultConfigs = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            defaultConfigs.add(new DoorRowConfig());
        }
        return defaultConfigs;
    }

    /**
     * 验证资源占用是否超限
     */
    private boolean validateResourceUsage() {
        int motorCount = 0; // 独立电机仓门数
        int groupMotorCount = 0; // 分组电机仓门数
        int lockCount = 0;
        int pushRodCount = 0;

        for (DoorRowConfig config : rowConfigs) {
            if (config.isEnabled()) {
                int count = config.getLayout() == 0 ? 1 : 2;
                switch (config.getType()) {
                    case 0: // 电机仓门
                        if (config.getLayout() == 1) { // 双仓门（独立）
                            motorCount += count;
                            if (motorCount > 4) {
                                Toast.makeText(requireContext(), "双仓门（独立电机）数量不能超过4个", Toast.LENGTH_SHORT).show();
                                return false;
                            }
                        } else { // 单仓门（分组）
                            groupMotorCount += count;
                            if (groupMotorCount > 3) { // 分组最多3个：12/34/所有
                                Toast.makeText(requireContext(), "单仓门（分组电机）数量不能超过3个", Toast.LENGTH_SHORT).show();
                                return false;
                            }
                        }
                        break;
                    case 1: // 电磁锁仓门
                        lockCount += count;
                        if (lockCount > 4) {
                            Toast.makeText(requireContext(), "电磁锁仓门数量不能超过4个", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        break;
                    case 2: // 推杆电机仓门（功能不变）
                        pushRodCount += count;
                        if (pushRodCount > 1) {
                            Toast.makeText(requireContext(), "推杆电机仓门数量不能超过1个", Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        break;
                }
            }
        }
        return true;
    }

    // 静态方法：获取启用的仓门列表
    public static List<DoorInfo> getEnabledDoors(android.content.Context context) {
        List<DoorRowConfig> configs = getRowConfigs(context);
        List<DoorInfo> doors = new ArrayList<>();
        int motorIndex = 1; // 双仓门独立ID（1-4）
        int groupMotorIndex = 5; // 单仓门分组ID（5=12舱门、6=34舱门、7=所有舱门）
        int lockIndex = 1;  // 电磁锁编号从1开始
        int pushRodIndex = 1; // 推杆电机编号保持1不变

        for (int rowIndex = 0; rowIndex < configs.size(); rowIndex++) {
            DoorRowConfig config = configs.get(rowIndex);
            if (!config.isEnabled()) continue;

            int currentRow = rowIndex + 1;
            int count = config.getLayout() == 0 ? 1 : 2; // 单仓门1个，双仓门2个

            for (int i = 0; i < count; i++) {
                DoorInfo info = new DoorInfo();
                info.setType(config.getType());
                info.setRow(currentRow);
                info.setPosition(i + 1);

                switch (config.getType()) {
                    case 0: // 电机仓门
                        if (config.getLayout() == 1) { // 双仓门（独立舱门）
                            info.setHardwareId(motorIndex++);
                            // 限制独立ID不超过4
                            if (motorIndex > 4) motorIndex = 4;
                        } else { // 单仓门（分组舱门）
                            info.setHardwareId(groupMotorIndex++);
                            // 限制分组ID不超过7（5=12、6=34、7=所有）
                            if (groupMotorIndex > 7) groupMotorIndex = 7;
                        }
                        break;
                    case 1: // 电磁锁仓门
                        info.setHardwareId(lockIndex++);
                        if (lockIndex > 4) lockIndex = 4;
                        break;
                    case 2: // 推杆电机仓门（功能不变）
                        info.setHardwareId(pushRodIndex);
                        break;
                }
                doors.add(info);
            }
        }
        return doors;
    }

    // 仓门信息模型
    public static class DoorInfo {
        private int row; // 行号1-5
        private int position; // 位置1-2
        private int type; // 类型0-2
        private int hardwareId; // 硬件编号

        public int getRow() {
            return row;
        }

        public void setRow(int row) {
            this.row = row;
        }

        public int getHardwareId() {
            return hardwareId;
        }

        public void setHardwareId(int hardwareId) {
            this.hardwareId = hardwareId;
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }
    }
}
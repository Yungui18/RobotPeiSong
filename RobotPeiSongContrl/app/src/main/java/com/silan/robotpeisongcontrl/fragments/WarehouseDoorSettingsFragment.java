package com.silan.robotpeisongcontrl.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;

public class WarehouseDoorSettingsFragment extends Fragment {
    private static final String PREFS_NAME = "WarehouseSettings";
    private static final String DOOR_COUNT_KEY = "door_count";
    public static final int DEFAULT_DOOR_COUNT = 6;

    private RadioGroup radioGroupDoorCount;
    private Button btnSave;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_warehouse_door_settings, container, false);

        radioGroupDoorCount = view.findViewById(R.id.radio_group_door_count);
        btnSave = view.findViewById(R.id.btn_save_door_settings);

        // 加载保存的设置
        loadSavedSettings();

        btnSave.setOnClickListener(v -> saveDoorSettings());

        return view;
    }

    private void loadSavedSettings() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        int doorCount = prefs.getInt(DOOR_COUNT_KEY, DEFAULT_DOOR_COUNT);

        switch (doorCount) {
            case 3:
                ((RadioButton) radioGroupDoorCount.findViewById(R.id.radio_3_doors)).setChecked(true);
                break;
            case 4:
                ((RadioButton) radioGroupDoorCount.findViewById(R.id.radio_4_doors)).setChecked(true);
                break;
            case 6:
                ((RadioButton) radioGroupDoorCount.findViewById(R.id.radio_6_doors)).setChecked(true);
                break;
        }
    }

    private void saveDoorSettings() {
        int selectedDoorCount = DEFAULT_DOOR_COUNT;

        int checkedId = radioGroupDoorCount.getCheckedRadioButtonId();
        if (checkedId == R.id.radio_3_doors) {
            selectedDoorCount = 3;
        } else if (checkedId == R.id.radio_4_doors) {
            selectedDoorCount = 4;
        } else if (checkedId == R.id.radio_6_doors) {
            selectedDoorCount = 6;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(DOOR_COUNT_KEY, selectedDoorCount);
        editor.apply();

        Toast.makeText(getContext(), "仓门数量设置已保存", Toast.LENGTH_SHORT).show();
    }

    // 提供静态方法获取当前设置的仓门数量
    public static int getDoorCount(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getInt(DOOR_COUNT_KEY, DEFAULT_DOOR_COUNT);
    }
}
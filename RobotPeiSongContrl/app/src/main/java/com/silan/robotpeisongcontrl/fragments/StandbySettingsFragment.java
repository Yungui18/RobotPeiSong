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
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;

public class StandbySettingsFragment extends Fragment {
    private Switch switchAutoSleep;
    private EditText etSleepDelay;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_standby_settings, container, false);

        prefs = requireActivity().getSharedPreferences("service_prefs", Context.MODE_PRIVATE);

        switchAutoSleep = view.findViewById(R.id.switch_auto_sleep);
        etSleepDelay = view.findViewById(R.id.et_sleep_delay);
        Button btnSave = view.findViewById(R.id.btn_save_settings);

        // 加载设置
        boolean autoSleep = prefs.getBoolean("auto_sleep", false);
        int sleepDelay = prefs.getInt("sleep_delay", 30); // 默认30分钟

        switchAutoSleep.setChecked(autoSleep);
        etSleepDelay.setText(String.valueOf(sleepDelay));

        // 保存按钮
        btnSave.setOnClickListener(v -> saveSettings());

        return view;
    }

    private void saveSettings() {
        try {
            int delay = Integer.parseInt(etSleepDelay.getText().toString());
            if (delay < 5 || delay > 120) {
                Toast.makeText(getContext(), "延时需在5-120分钟之间", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.edit()
                    .putBoolean("auto_sleep", switchAutoSleep.isChecked())
                    .putInt("sleep_delay", delay)
                    .apply();

            Toast.makeText(getContext(), "设置已保存", Toast.LENGTH_SHORT).show();
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "请输入有效的数字", Toast.LENGTH_SHORT).show();
        }
    }
}
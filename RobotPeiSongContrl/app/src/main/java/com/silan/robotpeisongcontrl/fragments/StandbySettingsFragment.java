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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;

public class StandbySettingsFragment extends Fragment {
    private Switch switchStandbyEnabled;
    private Spinner spinnerTimeout;
    private View rootView;
    private TextView tvSettings;
    private final long[] timeoutValues = {
            30000,    //30秒
            60000,    // 1分钟
            180000,   // 3分钟
            300000,   // 5分钟
            600000,   // 10分钟
            1200000   // 20分钟
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_standby_settings, container, false);

        switchStandbyEnabled = view.findViewById(R.id.switch_standby_enabled);
        spinnerTimeout = view.findViewById(R.id.spinner_timeout);

        // 设置下拉选项
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.standby_timeout_options,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerTimeout.setAdapter(adapter);

        spinnerTimeout.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveSettings();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不需要处理
            }
        });

        switchStandbyEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSettings();
        });

        // 加载保存的设置
        loadSettings();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        switchStandbyEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSettings(); // 立即保存设置
        });

        spinnerTimeout.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                saveSettings(); // 立即保存设置
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadSettings() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("standby_prefs", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("enabled", true);
        long timeout = prefs.getLong("timeout", 60000);

        switchStandbyEnabled.setChecked(enabled);

        // 查找对应的索引
        int position = 0;
        for (int i = 0; i < timeoutValues.length; i++) {
            if (timeoutValues[i] == timeout) {
                position = i;
                break;
            }
        }
        spinnerTimeout.setSelection(position);

        // 更新设置显示
        updateCurrentSettingsDisplay();
    }

    private void updateCurrentSettingsDisplay() {
        if (tvSettings == null) return;

        String status = switchStandbyEnabled.isChecked() ? "启用" : "禁用";
        int position = spinnerTimeout.getSelectedItemPosition();
        String timeoutText = getResources().getStringArray(R.array.standby_timeout_options)[position];

        String displayText = status + " | " + timeoutText;
        tvSettings.setText(displayText);
    }

    @Override
    public void onPause() {
        super.onPause();
        saveSettings();
    }

    private void saveSettings() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("standby_prefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("enabled", switchStandbyEnabled.isChecked());
        editor.putLong("timeout", timeoutValues[spinnerTimeout.getSelectedItemPosition()]);

        editor.apply();

        if (requireActivity() instanceof OnStandbySettingsChangedListener) {
            ((OnStandbySettingsChangedListener) requireActivity()).onStandbySettingsChanged();
        }
        updateCurrentSettingsDisplay();
    }

    public interface OnStandbySettingsChangedListener {
        void onStandbySettingsChanged();
    }
}
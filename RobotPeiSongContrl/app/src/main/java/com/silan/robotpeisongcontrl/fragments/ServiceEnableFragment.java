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
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;

public class ServiceEnableFragment extends Fragment {
    private Switch switchDelivery, switchPatrol, switchMultiDelivery;
    private Switch switchSingleRecycle, switchMultiRecycle;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_service_enable, container, false);

        prefs = requireActivity().getSharedPreferences("service_prefs", Context.MODE_PRIVATE);

        switchDelivery = view.findViewById(R.id.switch_delivery);
        switchPatrol = view.findViewById(R.id.switch_patrol);
        switchMultiDelivery = view.findViewById(R.id.switch_multi_delivery);
        switchSingleRecycle = view.findViewById(R.id.switch_single_recycle);
        switchMultiRecycle = view.findViewById(R.id.switch_multi_recycle);

        // 加载保存的设置
        loadSettings();

        // 设置监听器
        switchDelivery.setOnCheckedChangeListener((buttonView, isChecked) -> saveSetting("delivery_enabled", isChecked));
        switchPatrol.setOnCheckedChangeListener((buttonView, isChecked) -> saveSetting("patrol_enabled", isChecked));
        switchMultiDelivery.setOnCheckedChangeListener((buttonView, isChecked) -> saveSetting("multi_delivery_enabled", isChecked));
        switchSingleRecycle.setOnCheckedChangeListener((buttonView, isChecked) -> saveSetting("single_recycle_enabled", isChecked));
        switchMultiRecycle.setOnCheckedChangeListener((buttonView, isChecked) -> saveSetting("multi_recycle_enabled", isChecked));
        return view;
    }

    private void loadSettings() {
        boolean deliveryEnabled = prefs.getBoolean("delivery_enabled", true);
        boolean patrolEnabled = prefs.getBoolean("patrol_enabled", true);
        boolean multiDeliveryEnabled = prefs.getBoolean("multi_delivery_enabled", true);
        boolean singleRecycleEnabled = prefs.getBoolean("single_recycle_enabled", true);
        boolean multiRecycleEnabled = prefs.getBoolean("multi_recycle_enabled", true);

        switchDelivery.setChecked(deliveryEnabled);
        switchPatrol.setChecked(patrolEnabled);
        switchMultiDelivery.setChecked(multiDeliveryEnabled);
        switchSingleRecycle.setChecked(singleRecycleEnabled);
        switchMultiRecycle.setChecked(multiRecycleEnabled);
    }

    private void saveSetting(String key, boolean value) {
        prefs.edit().putBoolean(key, value).apply();
    }
}
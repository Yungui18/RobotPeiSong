package com.silan.robotpeisongcontrl.fragments;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.silan.robotpeisongcontrl.R;

public class ServiceEnableFragment extends Fragment {
    private Switch switchDelivery, switchPatrol, switchMultiDelivery;
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

        // 加载保存的设置
        switchDelivery.setChecked(prefs.getBoolean("delivery_enabled", true));
        switchPatrol.setChecked(prefs.getBoolean("patrol_enabled", true));
        switchMultiDelivery.setChecked(prefs.getBoolean("multi_delivery_enabled", true));

        // 设置监听器
        switchDelivery.setOnCheckedChangeListener(this::saveSetting);
        switchPatrol.setOnCheckedChangeListener(this::saveSetting);
        switchMultiDelivery.setOnCheckedChangeListener(this::saveSetting);

        return view;
    }

    private void saveSetting(CompoundButton buttonView, boolean isChecked) {
        SharedPreferences.Editor editor = prefs.edit();

        switch (buttonView.getId()) {
            case R.id.switch_delivery:
                editor.putBoolean("delivery_enabled", isChecked);
                break;
            case R.id.switch_patrol:
                editor.putBoolean("patrol_enabled", isChecked);
                break;
            case R.id.switch_multi_delivery:
                editor.putBoolean("multi_delivery_enabled", isChecked);
                break;
        }

        editor.apply();
    }
}
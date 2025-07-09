package com.silan.robotpeisongcontrl.fragments;

import android.content.Context;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.silan.robotpeisongcontrl.R;

public class StandbySettingsFragment extends Fragment {
    private SharedPreferences prefs;
    private SeekBar seekBarTimeout;
    private TextView tvTimeoutValue;
    private Spinner spinnerAnimation;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_standby_settings, container, false);

        prefs = requireActivity().getSharedPreferences("standby_prefs", Context.MODE_PRIVATE);

        seekBarTimeout = view.findViewById(R.id.seekbar_timeout);
        tvTimeoutValue = view.findViewById(R.id.tv_timeout_value);
        spinnerAnimation = view.findViewById(R.id.spinner_animation);

        // 设置超时时间选择器 (1-30分钟)
        int timeout = prefs.getInt("timeout_minutes", 5);
        seekBarTimeout.setProgress(timeout - 1);
        tvTimeoutValue.setText(timeout + " 分钟");

        seekBarTimeout.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int minutes = progress + 1;
                tvTimeoutValue.setText(minutes + " 分钟");
                prefs.edit().putInt("timeout_minutes", minutes).apply();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 设置动画选择器
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.standby_animations,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAnimation.setAdapter(adapter);

        String selectedAnimation = prefs.getString("animation", "默认动画");
        spinnerAnimation.setSelection(adapter.getPosition(selectedAnimation));

        spinnerAnimation.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putString("animation", parent.getItemAtPosition(position).toString()).apply();
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        return view;
    }
}
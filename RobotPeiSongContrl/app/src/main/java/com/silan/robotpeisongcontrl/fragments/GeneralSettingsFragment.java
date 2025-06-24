package com.silan.robotpeisongcontrl.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.silan.robotpeisongcontrl.R;


public class GeneralSettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_general_settings, container, false);

        TextView title = view.findViewById(R.id.tv_title);
        title.setText("通用设置");

        // 这里可以添加通用设置的具体内容

        return view;
    }
}
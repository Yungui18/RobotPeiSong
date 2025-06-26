package com.silan.robotpeisongcontrl.fragments;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.AdImplantActivity;
import com.silan.robotpeisongcontrl.BackgroundChoiceActivity;
import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.TimeDisplayActivity;

/**
 * 个性化设置页面
 * 主要功能：
 * 1. 提供背景选择、时间显示方式、广告植入入口
 * 2. 提供主题颜色选择
 * 3. 提供字体大小调整
 */
public class PersonalizationSettingsFragment extends Fragment {

    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 加载布局文件
        View view = inflater.inflate(R.layout.fragment_personalization_settings, container, false);

        // 获取SharedPreferences实例
        prefs = requireActivity().getSharedPreferences("personalization_prefs", Context.MODE_PRIVATE);

        // 设置背景选择点击事件
        view.findViewById(R.id.btn_background_choice).setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), BackgroundChoiceActivity.class));
        });

        // 设置时间显示方式点击事件
        view.findViewById(R.id.btn_time_display).setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), TimeDisplayActivity.class));
        });

        // 设置广告植入点击事件
        view.findViewById(R.id.btn_ad_implant).setOnClickListener(v -> {
            startActivity(new Intent(getActivity(), AdImplantActivity.class));
        });
        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == getActivity().RESULT_OK) {
            if (requestCode == 100) {
                Toast.makeText(getContext(), "背景已设置", Toast.LENGTH_SHORT).show();
            } else if (requestCode == 101) {
                Toast.makeText(getContext(), "时区已设置", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void saveBackgroundPreference(int bgResId) {
        SharedPreferences prefs = requireActivity().getSharedPreferences("personalization_prefs", Context.MODE_PRIVATE);
        prefs.edit().putInt("background_res", bgResId).apply();
    }

    /**
     * 主题颜色选择处理
     * @param view 被点击的颜色视图
     */
    private void onThemeColorSelected(View view) {
        String colorName = (String) view.getTag();

        // 保存选择的主题颜色
        prefs.edit().putString("theme_color", colorName).apply();

        // 提示用户
        Toast.makeText(getContext(), "主题颜色已设置为: " + getColorDisplayName(colorName), Toast.LENGTH_SHORT).show();
    }

    /**
     * 获取颜色显示名称
     * @param colorName 颜色标识
     * @return 颜色显示名称
     */
    private String getColorDisplayName(String colorName) {
        switch (colorName) {
            case "mint_green": return "薄荷绿";
            case "blue": return "蓝色";
            case "purple": return "紫色";
            case "teal": return "蓝绿色";
            default: return colorName;
        }
    }
}
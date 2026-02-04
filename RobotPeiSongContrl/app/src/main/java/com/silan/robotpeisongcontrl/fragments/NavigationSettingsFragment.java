package com.silan.robotpeisongcontrl.fragments;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.utils.MotionConfigSPUtils;

/**
 * 导航设置Fragment
 */
public class NavigationSettingsFragment extends Fragment {

    private Spinner mNavModeSpinner;
    private EditText mPrecisionEt;
    private Button mSaveConfigBtn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // 加载导航设置专属布局
        View view = inflater.inflate(R.layout.fragment_navigation_settings, container, false);
        initViews(view); // 初始化控件
        initConfigData(); // 回显已保存配置
        setSaveListener(); // 设置保存按钮监听
        return view;
    }

    /**
     * 初始化专属布局控件
     */
    private void initViews(View view) {
        mNavModeSpinner = view.findViewById(R.id.spinner_nav_mode);
        mPrecisionEt = view.findViewById(R.id.et_precision);
        mSaveConfigBtn = view.findViewById(R.id.btn_save_nav_config); // 匹配专属布局的按钮ID
    }

    /**
     * 回显SP中持久化的导航模式和到点精度
     */
    private void initConfigData() {
        if (getActivity() == null) return;
        // 回显导航模式（无保存值则用默认值0）
        int savedMode = MotionConfigSPUtils.getNavMode(getActivity());
        mNavModeSpinner.setSelection(savedMode);
        // 回显到点精度，保留1位小数（无保存值则用默认值0.1f）
        float savedPrecision = MotionConfigSPUtils.getAcceptablePrecision(getActivity());
        mPrecisionEt.setText(String.format("%.1f", savedPrecision));
    }

    /**
     * 保存按钮监听：校验输入 + 持久化保存到SP
     */
    private void setSaveListener() {
        mSaveConfigBtn.setOnClickListener(v -> {
            if (getActivity() == null) return;

            // 1. 获取导航模式（Spinner已限制选项，直接获取索引）
            int navMode = mNavModeSpinner.getSelectedItemPosition();

            // 2. 获取并校验到点精度
            String precisionStr = mPrecisionEt.getText().toString().trim();
            if (precisionStr.isEmpty()) {
                Toast.makeText(getActivity(), "请输入到点精度", Toast.LENGTH_SHORT).show();
                return;
            }
            float precision;
            try {
                precision = Float.parseFloat(precisionStr);
            } catch (NumberFormatException e) {
                Toast.makeText(getActivity(), "请输入有效的数字", Toast.LENGTH_SHORT).show();
                return;
            }
            // 提前校验范围，友好提示用户（工具类会二次限制）
            if (precision < 0 || precision > MotionConfigSPUtils.MAX_PRECISION) {
                Toast.makeText(getActivity(), "到点精度范围为0~2米", Toast.LENGTH_SHORT).show();
                return;
            }

            // 3. 持久化保存到SP（核心逻辑不变，业务层可自动读取）
            MotionConfigSPUtils.saveNavMode(getActivity(), navMode);
            MotionConfigSPUtils.saveAcceptablePrecision(getActivity(), precision);

            // 4. 保存成功提示
            Toast.makeText(getActivity(), "导航配置保存成功", Toast.LENGTH_SHORT).show();
        });
    }
}
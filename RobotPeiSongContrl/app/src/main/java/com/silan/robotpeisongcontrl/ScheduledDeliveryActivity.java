package com.silan.robotpeisongcontrl;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.silan.robotpeisongcontrl.fragments.EnableScheduledDeliveryFragment;
import com.silan.robotpeisongcontrl.fragments.PointDeliveryFragment;
import com.silan.robotpeisongcontrl.fragments.RouteDeliveryFragment;
import com.silan.robotpeisongcontrl.fragments.ViewTasksFragment;

public class ScheduledDeliveryActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scheduled_delivery);

        // 设置返回按钮
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 设置菜单项点击事件
        findViewById(R.id.btn_enable).setOnClickListener(v ->
                loadFragment(new EnableScheduledDeliveryFragment()));

        findViewById(R.id.btn_point_delivery).setOnClickListener(v ->
                loadFragment(new PointDeliveryFragment()));

        findViewById(R.id.btn_route_delivery).setOnClickListener(v ->
                loadFragment(new RouteDeliveryFragment()));

        findViewById(R.id.btn_view_tasks).setOnClickListener(v ->
                loadFragment(new ViewTasksFragment()));

        // 默认加载启用定时配送页面
        loadFragment(new EnableScheduledDeliveryFragment());
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content_frame, fragment)
                .commit();

        // 更新菜单项选中状态
        updateMenuSelection(fragment);
    }

    private void updateMenuSelection(Fragment fragment) {
        int selectedId = -1;

        if (fragment instanceof EnableScheduledDeliveryFragment) {
            selectedId = R.id.btn_enable;
        } else if (fragment instanceof PointDeliveryFragment) {
            selectedId = R.id.btn_point_delivery;
        } else if (fragment instanceof RouteDeliveryFragment) {
            selectedId = R.id.btn_route_delivery;
        } else if (fragment instanceof ViewTasksFragment) {
            selectedId = R.id.btn_view_tasks;
        }

        // 重置所有按钮背景
        int[] menuIds = {
                R.id.btn_enable,
                R.id.btn_point_delivery,
                R.id.btn_route_delivery,
                R.id.btn_view_tasks
        };

        for (int id : menuIds) {
            Button btn = findViewById(id);
            btn.setBackgroundResource(R.drawable.menu_item_background);
        }

        // 设置选中按钮背景
        if (selectedId != -1) {
            Button selectedBtn = findViewById(selectedId);
            selectedBtn.setBackgroundResource(R.drawable.menu_item_selected_background);
        }
    }
}
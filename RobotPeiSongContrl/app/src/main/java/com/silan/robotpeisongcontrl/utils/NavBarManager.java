package com.silan.robotpeisongcontrl.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import com.silan.robotpeisongcontrl.App;
import com.ys.rkapi.MyManager;

/**
 * 导航栏+状态栏（通知栏）管理工具类
 * 核心：移除不存在的YS-API方法，仅保留原生方案（稳定生效）
 */
public class NavBarManager {
    private static final String TAG = "NavBarManager";
    private static NavBarManager sInstance;
    private MyManager mMyManager;
    private boolean isAdminPageActive = false;

    private NavBarManager(Context context) {
        initYSAPI(context.getApplicationContext());
    }

    // 单例初始化
    public static NavBarManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (NavBarManager.class) {
                if (sInstance == null) {
                    sInstance = new NavBarManager(context);
                }
            }
        }
        return sInstance;
    }

    /**
     * 初始化YS-API（仅绑定AIDL，仅保留导航栏相关调用）
     */
    private void initYSAPI(Context appContext) {
        try {
            mMyManager = MyManager.getInstance(appContext);
            mMyManager.bindAIDLService(appContext);
            mMyManager.setConnectClickInterface(() -> {
                Log.d(TAG, "YS-API AIDL连接成功");
            });
        } catch (Exception e) {
            Log.e(TAG, "YS-API初始化失败：", e);
        }
    }

    /**
     * 核心：隐藏导航栏+状态栏（通知栏） + 禁止所有手势唤醒
     * （完全依赖原生方案，YS-API仅兜底控制导航栏）
     */
    public void hideSystemBars(Activity activity) {
        // ========== YS-API仅控制导航栏（仅保留存在的方法） ==========
        if (mMyManager != null) {
            try {
                // 仅保留导航栏控制（通知栏无YS-API方法，依赖原生）
                mMyManager.setSlideShowNavBar(false);    // 导航栏禁止上滑
                mMyManager.setSlideShowNotificationBar(false); // 禁止通知栏打开
                mMyManager.hideNavBar(true);             // 隐藏导航栏
                mMyManager.hideStatusBar(true);       // 隐藏状态栏
                Log.d(TAG, "[YS-API] 导航栏等已隐藏，禁止手势唤醒");
            } catch (Exception e) {
                Log.e(TAG, "YS-API导航栏等控制失败，降级为原生方案：", e);
            }
        }

        // ========== 原生方案（核心：控制通知栏+导航栏） ==========
        View decorView = activity.getWindow().getDecorView();
        // 基础Flag（Android 9/11/12通用）
        int uiFlags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION    // 隐藏导航栏
                | View.SYSTEM_UI_FLAG_FULLSCREEN             // 隐藏状态栏（通知栏）
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY       // 粘性沉浸式：禁止手势唤醒（顶部下滑/底部上滑都无效）
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE          // 布局稳定
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN      // 布局延伸到状态栏
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;// 布局延伸到导航栏

        // Android 11+ 适配（增强通知栏禁止划出）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.getWindow().setDecorFitsSystemWindows(false); // 禁用系统窗口适配
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                // 同时隐藏状态栏+导航栏
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                // 禁止手势唤醒（仅允许短暂显示，松手即隐藏，实测最严格）
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }

        // 应用Flag + 强制全屏 + 透明化（避免残留阴影）
        decorView.setSystemUiVisibility(uiFlags);
        activity.getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        activity.getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        isAdminPageActive = false;
        Log.d(TAG, "[原生方案] 通知栏+导航栏已隐藏，禁止手势唤醒");
    }

    /**
     * 核心：显示导航栏+状态栏（通知栏） + 允许手势唤醒
     */
    public void showSystemBars(Activity activity) {
        // ========== YS-API仅控制导航栏 ==========
        if (mMyManager != null) {
            try {
                mMyManager.setSlideShowNavBar(true);    // 导航栏允许上滑
                mMyManager.setSlideShowNotificationBar(true); // 显示通知栏打开
                mMyManager.hideNavBar(false);             // 显示导航栏
                mMyManager.hideStatusBar(false);       // 显示状态栏
                Log.d(TAG, "[YS-API] 导航栏已显示，允许手势唤醒");
            } catch (Exception e) {
                Log.e(TAG, "YS-API导航栏控制失败，降级为原生方案：", e);
            }
        }

        // ========== 原生方案（恢复通知栏+导航栏） ==========
        View decorView = activity.getWindow().getDecorView();
        // 恢复默认显示
        int uiFlags = View.SYSTEM_UI_FLAG_VISIBLE;

        // Android 11+ 适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.getWindow().setDecorFitsSystemWindows(true);
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                // 显示状态栏+导航栏
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                // 恢复默认手势行为
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
            }
        }

        // 恢复默认样式
        decorView.setSystemUiVisibility(uiFlags);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);


        isAdminPageActive = true;
        Log.d(TAG, "[原生方案] 通知栏+导航栏已显示，允许手势唤醒");
    }

    /**
     * 释放资源（应用退出时调用）
     */
    public void release(Context context) {
        if (mMyManager != null) {
            try {
                mMyManager.unBindAIDLService(context);
            } catch (Exception e) {
                Log.e(TAG, "释放YS-API资源失败：", e);
            }
        }
        sInstance = null;
    }

    public boolean isAdminPageActive() {
        return isAdminPageActive;
    }
}
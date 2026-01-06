package com.silan.robotpeisongcontrl;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.silan.robotpeisongcontrl.utils.NavBarManager;

import java.util.Locale;

public class BaseActivity extends AppCompatActivity {

    protected NavBarManager mNavBarManager;
    // ===== 新增：灰化逻辑相关 =====
    protected boolean isButtonLocked = false; // 按钮锁定状态
    protected View mRootView; // 页面根布局
    protected Handler mMainHandler = new Handler(Looper.getMainLooper()); // 全局Handler

    @Override
    protected void attachBaseContext(Context newBase) {
        // 原有语言设置逻辑
        Context context = updateBaseContextLocale(newBase);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        updateLanguage(); // 原有语言设置
        super.onCreate(savedInstanceState);
        // 初始化导航栏管理器
        mNavBarManager = NavBarManager.getInstance(getApplicationContext());

        // ===== 新增：延迟获取根布局（确保布局加载完成）=====
        mMainHandler.post(() -> {
            mRootView = findViewById(android.R.id.content);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 原有导航栏逻辑
        getWindow().getDecorView().postDelayed(() -> {
            if (!isAdminPage()) {
                mNavBarManager.hideSystemBars(BaseActivity.this);
            } else {
                clearImmersiveFlags();
                mNavBarManager.showSystemBars(BaseActivity.this);
            }
        }, 50);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            if (!isAdminPage() && (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                mNavBarManager.hideSystemBars(this);
            }
        });
    }

    // 原有清除沉浸式Flag逻辑
    private void clearImmersiveFlags() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 原有管理员页面逻辑
        if (isAdminPage() && isFinishing()) {
            mNavBarManager.hideSystemBars(this);
        }
    }

    // 原有管理员页面标记方法
    protected boolean isAdminPage() {
        return false;
    }

    // 原有语言设置逻辑
    private Context updateBaseContextLocale(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("personalization_prefs", Context.MODE_PRIVATE);
        String langCode = prefs.getString("selected_language", "zh");
        return setLocale(context, langCode);
    }

    private void updateLanguage() {
        SharedPreferences prefs = getSharedPreferences("personalization_prefs", Context.MODE_PRIVATE);
        String langCode = prefs.getString("selected_language", "zh");
        setLocale(this, langCode);
    }

    private Context setLocale(Context context, String langCode) {
        Locale locale = getLocaleFromCode(langCode);
        Locale.setDefault(locale);

        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            res.updateConfiguration(config, res.getDisplayMetrics());
        }
        return context;
    }

    private Locale getLocaleFromCode(String langCode) {
        switch (langCode) {
            case "zh": return Locale.SIMPLIFIED_CHINESE;
            case "zh_rTW": return Locale.TRADITIONAL_CHINESE;
            case "en": return Locale.ENGLISH;
            case "ko": return Locale.KOREAN;
            case "ja": return Locale.JAPANESE;
            default: return Locale.getDefault();
        }
    }

    // ===== 核心新增：按钮灰化锁定/解锁方法 =====
    /**
     * 锁定当前页面所有按钮（灰化+禁止点击）
     */
    protected void lockAllButtons() {
        if (isButtonLocked || mRootView == null) return;
        isButtonLocked = true;
        traverseAndLock(mRootView);
    }

    /**
     * 解锁当前页面所有按钮（恢复样式+允许点击）
     */
    protected void unlockAllButtons() {
        if (!isButtonLocked || mRootView == null) return;
        isButtonLocked = false;
        traverseAndUnlock(mRootView);
    }

    // 递归遍历View树，锁定所有Button
    private void traverseAndLock(View view) {
        if (view instanceof Button) {
            Button btn = (Button) view;
            // 保存原有状态（用于解锁恢复）
            btn.setTag(R.id.btn_origin_background, btn.getBackground());
            btn.setTag(R.id.btn_origin_alpha, btn.getAlpha());
            btn.setTag(R.id.btn_origin_clickable, btn.isClickable());
            // 灰化+禁止点击
            btn.setAlpha(0.5f);
            btn.setClickable(false);
        } else if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                traverseAndLock(viewGroup.getChildAt(i));
            }
        }
    }

    // 递归遍历View树，解锁所有Button
    private void traverseAndUnlock(View view) {
        if (view instanceof Button) {
            Button btn = (Button) view;
            // 恢复原有状态
            if (btn.getTag(R.id.btn_origin_background) != null) {
                btn.setBackground((android.graphics.drawable.Drawable) btn.getTag(R.id.btn_origin_background));
            }
            if (btn.getTag(R.id.btn_origin_alpha) != null) {
                btn.setAlpha((float) btn.getTag(R.id.btn_origin_alpha));
            }
            if (btn.getTag(R.id.btn_origin_clickable) != null) {
                btn.setClickable((boolean) btn.getTag(R.id.btn_origin_clickable));
            }
            // 清空标签，避免内存泄漏
            btn.setTag(R.id.btn_origin_background, null);
            btn.setTag(R.id.btn_origin_alpha, null);
            btn.setTag(R.id.btn_origin_clickable, null);
        } else if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                traverseAndUnlock(viewGroup.getChildAt(i));
            }
        }
    }

    // ===== 新增：生命周期兜底（防止按钮永久锁定）=====
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unlockAllButtons(); // 页面销毁时强制解锁
        mMainHandler.removeCallbacksAndMessages(null); // 清空Handler回调
    }

    // ===== 新增：Fragment适配方法（如果需要）=====
    public static void lockButtonsInFragment(BaseActivity activity) {
        if (activity != null) activity.lockAllButtons();
    }

    public static void unlockButtonsInFragment(BaseActivity activity) {
        if (activity != null) activity.unlockAllButtons();
    }
}
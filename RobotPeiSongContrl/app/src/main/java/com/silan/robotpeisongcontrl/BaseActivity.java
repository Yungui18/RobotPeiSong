package com.silan.robotpeisongcontrl;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.silan.robotpeisongcontrl.utils.NavBarManager;

import java.util.Locale;

public class BaseActivity extends AppCompatActivity {

    protected NavBarManager mNavBarManager;

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 关键修改：调用新的状态栏+导航栏控制方法
        getWindow().getDecorView().postDelayed(() -> {
            if (!isAdminPage()) {
                mNavBarManager.hideSystemBars(BaseActivity.this); // 普通页面隐藏
            } else {
                clearImmersiveFlags();
                mNavBarManager.showSystemBars(BaseActivity.this); // 管理员页面显示
            }
        }, 50);
        // 隐藏原生标题栏（原有逻辑补充）
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        // 额外保障：监听UI变化，防止手势唤醒后重新隐藏
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(visibility -> {
            if (!isAdminPage() && (visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
                mNavBarManager.hideSystemBars(this);
            }
        });
    }

    /**
     * 新增：清除沉浸式Flag（解决IMMERSIVE_STICKY粘性问题）
     */
    private void clearImmersiveFlags() {
        View decorView = getWindow().getDecorView();
        // 先清除所有沉浸式Flag
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        // 清除全屏Flag
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                // 强制显示系统栏
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 管理员页面退出时（销毁），恢复隐藏状态栏+导航栏
        if (isAdminPage() && isFinishing()) {
            mNavBarManager.hideSystemBars(this); // 隐藏状态栏+导航栏
        }
    }

    /**
     * 子类重写此方法标记是否为超级管理员页面
     * @return true=管理员页面（显示导航栏+状态栏），false=普通页面（隐藏）
     */
    protected boolean isAdminPage() {
        return false;
    }

    // 原有语言设置逻辑（无修改）
    private Context updateBaseContextLocale(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("personalization_prefs", Context.MODE_PRIVATE);
        String langCode = prefs.getString("selected_language", "zh");
        return setLocale(context, langCode);
    }

    private void updateLanguage() {
        SharedPreferences prefs = getSharedPreferences("personalization_prefs", MODE_PRIVATE);
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
}
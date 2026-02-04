package com.silan.robotpeisongcontrl.utils;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.silan.robotpeisongcontrl.R;

/**
 * 跳转主界面前置加载弹窗工具类
 */
public class LoadingDialogUtil {
    private static Dialog mLoadingDialog; // 单例弹窗，避免重复创建
    private static android.os.Handler mHandler = new android.os.Handler();
    private static Runnable mTimeoutRunnable;

    /**
     * 显示加载弹窗（不可手动关闭）
     * @param context 跳转发起页面的上下文
     * @param tip 弹窗提示文字
     */
    public static void showLoadingDialog(Context context, String tip) {
        // 清除之前的超时任务
        if (mTimeoutRunnable != null) {
            mHandler.removeCallbacks(mTimeoutRunnable);
        }

        // 避免重复显示弹窗
        if (mLoadingDialog != null && mLoadingDialog.isShowing()) {
            return;
        }

        // 创建自定义弹窗
        mLoadingDialog = new Dialog(context, R.style.LoadingDialogStyle);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null);
        TextView tvTip = dialogView.findViewById(R.id.tv_loading_tip);
        tvTip.setText(tip);

        mLoadingDialog.setContentView(dialogView);
        mLoadingDialog.setCancelable(false); // 不可手动关闭
        mLoadingDialog.setCanceledOnTouchOutside(false);

        // 避免页面销毁时显示弹窗引发异常
        if (context instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) context;
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                mLoadingDialog.show();

                // ===== 新增：5秒超时自动关闭 =====
                mTimeoutRunnable = () -> {
                    dismissLoadingDialog();
                };
                mHandler.postDelayed(mTimeoutRunnable, 5000);
            }
        }
    }

    /**
     * 关闭加载弹窗
     */
    public static void dismissLoadingDialog() {
        // 清除超时任务
        if (mTimeoutRunnable != null) {
            mHandler.removeCallbacks(mTimeoutRunnable);
            mTimeoutRunnable = null;
        }

        if (mLoadingDialog != null) {
            try {
                if (mLoadingDialog.isShowing()) {
                    mLoadingDialog.dismiss();
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                mLoadingDialog = null; // 释放资源
            }
        }
    }

    /**
     * 判断弹窗是否正在显示
     */
    public static boolean isDialogShowing() {
        return mLoadingDialog != null && mLoadingDialog.isShowing();
    }
}
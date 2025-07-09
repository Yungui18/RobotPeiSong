package com.silan.robotpeisongcontrl.utils;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

public class ExactAlarmPermissionHelper {

    public static boolean canScheduleExactAlarms(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return alarmManager.canScheduleExactAlarms();
    }

    public static void requestExactAlarmPermission(AppCompatActivity activity,
                                                   ActivityResultLauncher<Intent> launcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }

        if (canScheduleExactAlarms(activity)) {
            return;
        }

        Toast.makeText(activity, "需要精确闹钟权限来执行定时配送", Toast.LENGTH_LONG).show();

        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        launcher.launch(intent);
    }

    public static void handlePermissionResult(Context context) {
        if (canScheduleExactAlarms(context)) {
            Toast.makeText(context, "精确闹钟权限已授予", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "精确闹钟权限被拒绝，定时配送可能无法正常工作", Toast.LENGTH_LONG).show();
        }
    }
}

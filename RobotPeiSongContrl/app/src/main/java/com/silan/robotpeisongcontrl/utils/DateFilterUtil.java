package com.silan.robotpeisongcontrl.utils;

import android.util.Log;

import com.silan.robotpeisongcontrl.model.DailyMileage;
import com.silan.robotpeisongcontrl.model.DailySuccessFailure;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 日期过滤工具类：按年月筛选数据
 */
public class DateFilterUtil {
    // 日期格式（与项目中存储的格式一致：yyyy-MM-dd）
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    // 年月格式：yyyy-MM
    private static final SimpleDateFormat YEAR_MONTH_FORMAT = new SimpleDateFormat("yyyy-MM", Locale.CHINA);

    /**
     * 过滤指定年月的每日里程数据（仅保留该月数据）
     */
    public static List<DailyMileage> filterMileageByMonth(List<DailyMileage> allData, int targetYear, int targetMonth) {
        List<DailyMileage> filteredData = new ArrayList<>();
        if (allData == null || allData.isEmpty()) return filteredData;

        for (DailyMileage mileage : allData) {
            try {
                Date date = DATE_FORMAT.parse(mileage.getDate());
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                int year = calendar.get(Calendar.YEAR);
                int month = calendar.get(Calendar.MONTH) + 1; // Calendar月份从0开始，需+1

                // 匹配目标年月
                if (year == targetYear && month == targetMonth) {
                    filteredData.add(mileage);
                }
            } catch (ParseException e) {
                Log.e("DateFilterUtil", "解析里程日期失败: " + mileage.getDate(), e);
            }
        }
        return filteredData;
    }

    /**
     * 过滤指定年月的每日成败数据（仅保留该月数据）
     */
    public static List<DailySuccessFailure> filterSuccessFailureByMonth(List<DailySuccessFailure> allData, int targetYear, int targetMonth) {
        List<DailySuccessFailure> filteredData = new ArrayList<>();
        if (allData == null || allData.isEmpty()) return filteredData;

        for (DailySuccessFailure dsf : allData) {
            try {
                Date date = DATE_FORMAT.parse(dsf.getDate());
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                int year = calendar.get(Calendar.YEAR);
                int month = calendar.get(Calendar.MONTH) + 1;

                // 匹配目标年月
                if (year == targetYear && month == targetMonth) {
                    filteredData.add(dsf);
                }
            } catch (ParseException e) {
                Log.e("DateFilterUtil", "解析成败日期失败: " + dsf.getDate(), e);
            }
        }
        return filteredData;
    }

    /**
     * 获取当前年月（默认显示当前月数据）
     * @return 数组：[0]年，[1]月（1-12）
     */
    public static int[] getCurrentYearMonth() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        return new int[]{year, month};
    }
}
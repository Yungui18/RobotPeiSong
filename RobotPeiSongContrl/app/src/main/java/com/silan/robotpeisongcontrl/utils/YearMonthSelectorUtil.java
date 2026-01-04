package com.silan.robotpeisongcontrl.utils;

import android.util.Log;

import com.silan.robotpeisongcontrl.model.DailyMileage;
import com.silan.robotpeisongcontrl.model.DailySuccessFailure;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 年月选择工具类：生成年月选项、解析年月字符串（兼容所有Android版本）
 */
public class YearMonthSelectorUtil {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    /**
     * 从里程数据中提取所有存在的年份（去重）
     */
    public static List<Integer> getYearsFromMileageData(List<DailyMileage> mileageData) {
        Set<Integer> yearSet = new HashSet<>();
        for (DailyMileage data : mileageData) {
            try {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(DATE_FORMAT.parse(data.getDate()));
                yearSet.add(calendar.get(Calendar.YEAR));
            } catch (ParseException e) {
                Log.e("YearMonthUtil", "解析里程日期失败: " + data.getDate(), e);
            }
        }
        // 转换为有序列表（升序）
        List<Integer> years = new ArrayList<>(yearSet);
        years.sort(Integer::compareTo);
        return years;
    }

    /**
     * 从成败数据中提取所有存在的年份（去重）
     */
    public static List<Integer> getYearsFromSuccessFailureData(List<DailySuccessFailure> sfData) {
        Set<Integer> yearSet = new HashSet<>();
        for (DailySuccessFailure data : sfData) {
            try {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(DATE_FORMAT.parse(data.getDate()));
                yearSet.add(calendar.get(Calendar.YEAR));
            } catch (ParseException e) {
                Log.e("YearMonthUtil", "解析成败日期失败: " + data.getDate(), e);
            }
        }
        // 转换为有序列表（升序）
        List<Integer> years = new ArrayList<>(yearSet);
        years.sort(Integer::compareTo);
        return years;
    }

    /**
     * 生成月份选项（1-12月，格式：1月、2月...12月）
     */
    public static List<String> generateMonthOptions() {
        List<String> months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            months.add(i + "月");
        }
        return months;
    }

    /**
     * 解析月份字符串（如"6月"）为数字（6）
     */
    public static int parseMonth(String monthStr) {
        try {
            // 截取数字部分（如"6月"→"6"）
            return Integer.parseInt(monthStr.replace("月", ""));
        } catch (Exception e) {
            Log.e("YearMonthUtil", "解析月份失败: " + monthStr, e);
            return Calendar.getInstance().get(Calendar.MONTH) + 1; // 默认当前月
        }
    }

    /**
     * 生成指定范围的年份选项（兜底：无数据时显示近10年）
     */
    public static List<Integer> generateDefaultYears(int range) {
        List<Integer> years = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        int currentYear = calendar.get(Calendar.YEAR);
        for (int i = 0; i < range; i++) {
            years.add(currentYear - i);
        }
        years.sort(Integer::compareTo); // 升序排列
        return years;
    }
}

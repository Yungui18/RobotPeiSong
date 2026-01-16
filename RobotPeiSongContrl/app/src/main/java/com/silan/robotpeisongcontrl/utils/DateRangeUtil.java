package com.silan.robotpeisongcontrl.utils;

import android.text.TextUtils;

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
 * 日期范围校验工具类
 */
public class DateRangeUtil {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
    public static final int MAX_DAYS = 31; // 最大筛选天数

    /**
     * 校验日期范围
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate 结束日期（yyyy-MM-dd）
     * @return 0=成功，-1=结束日期早于开始，-2=超过31天，-3=格式错误，-4=日期为空
     */
    public static int checkDateRange(String startDate, String endDate) {
        if (TextUtils.isEmpty(startDate) || TextUtils.isEmpty(endDate)) {
            return -4;
        }
        try {
            Date start = DATE_FORMAT.parse(startDate);
            Date end = DATE_FORMAT.parse(endDate);

            if (end.before(start)) {
                return -1;
            }

            // 计算天数差
            long diffTime = end.getTime() - start.getTime();
            long diffDays = diffTime / (1000 * 60 * 60 * 24);

            if (diffDays > MAX_DAYS) {
                return -2;
            }
            return 0;
        } catch (ParseException e) {
            e.printStackTrace();
            return -3;
        }
    }

    /**
     * 过滤里程数据按日期范围
     * @param allData 所有里程数据
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate 结束日期（yyyy-MM-dd）
     * @return 过滤后的数据
     */
    public static List<DailyMileage> filterMileageByDateRange(List<DailyMileage> allData, String startDate, String endDate) {
        List<DailyMileage> filtered = new ArrayList<>();
        if (allData == null || allData.isEmpty()) {
            return filtered;
        }
        try {
            Date start = DATE_FORMAT.parse(startDate);
            Date end = DATE_FORMAT.parse(endDate);
            for (DailyMileage item : allData) {
                Date itemDate = DATE_FORMAT.parse(item.getDate());
                if (!itemDate.before(start) && !itemDate.after(end)) {
                    filtered.add(item);
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return filtered;
    }

    /**
     * 过滤成败数据按日期范围
     * @param allData 所有成败数据
     * @param startDate 开始日期（yyyy-MM-dd）
     * @param endDate 结束日期（yyyy-MM-dd）
     * @return 过滤后的数据
     */
    public static List<DailySuccessFailure> filterSuccessFailureByDateRange(List<DailySuccessFailure> allData, String startDate, String endDate) {
        List<DailySuccessFailure> filtered = new ArrayList<>();
        if (allData == null || allData.isEmpty()) {
            return filtered;
        }
        try {
            Date start = DATE_FORMAT.parse(startDate);
            Date end = DATE_FORMAT.parse(endDate);
            for (DailySuccessFailure item : allData) {
                Date itemDate = DATE_FORMAT.parse(item.getDate());
                if (!itemDate.before(start) && !itemDate.after(end)) {
                    filtered.add(item);
                }
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return filtered;
    }

    /**
     * 获取当前日期（yyyy-MM-dd）
     */
    public static String getCurrentDate() {
        return DATE_FORMAT.format(new Date());
    }

    /**
     * 获取N天前的日期
     * @param days 天数（如30则返回30天前）
     */
    public static String getDateBefore(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -days);
        return DATE_FORMAT.format(calendar.getTime());
    }
}

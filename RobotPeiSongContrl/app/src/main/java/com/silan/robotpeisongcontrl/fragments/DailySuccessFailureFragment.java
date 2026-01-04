package com.silan.robotpeisongcontrl.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.model.DailySuccessFailure;
import com.silan.robotpeisongcontrl.model.DeliveryFailure;
import com.silan.robotpeisongcontrl.utils.DateFilterUtil;
import com.silan.robotpeisongcontrl.utils.DeliveryFailureManager;
import com.silan.robotpeisongcontrl.utils.TaskSuccessManager;
import com.silan.robotpeisongcontrl.utils.YearMonthSelectorUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DailySuccessFailureFragment extends Fragment {
    private BarChart barChart;
    private Spinner spinnerYear;   // 年份Spinner
    private Spinner spinnerMonth;  // 月份Spinner
    private int selectedYear;      // 选中的年
    private int selectedMonth;     // 选中的月
    private List<DailySuccessFailure> allSfData; // 缓存所有成败数据

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily_success_failure, container, false);
        barChart = view.findViewById(R.id.bar_chart);
        spinnerYear = view.findViewById(R.id.spinner_year);
        spinnerMonth = view.findViewById(R.id.spinner_month);

        initChart();
        // 初始化当前年月
        int[] currentYm = DateFilterUtil.getCurrentYearMonth();
        selectedYear = currentYm[0];
        selectedMonth = currentYm[1];

        // 加载所有成败数据（用于提取年份）
        loadAllSuccessFailureData();

        return view;
    }

    // 加载所有成败数据（缓存+初始化Spinner）
    private void loadAllSuccessFailureData() {
        // 1. 统计成功数
        Map<String, Integer> successMap = new HashMap<>();
        List<TaskSuccessManager.TaskSuccess> successList = TaskSuccessManager.loadAllSuccess(getContext());
        for (TaskSuccessManager.TaskSuccess success : successList) {
            successMap.put(success.getDate(), successMap.getOrDefault(success.getDate(), 0) + 1);
        }

        // 2. 统计失败数
        Map<String, Integer> failureMap = new HashMap<>();
        List<DeliveryFailure> failureList = DeliveryFailureManager.loadAllFailures(getContext());
        for (DeliveryFailure failure : failureList) {
            String date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                    .format(new java.util.Date(failure.getTimestamp()));
            failureMap.put(date, failureMap.getOrDefault(date, 0) + 1);
        }

        // 3. 合并所有数据
        allSfData = new ArrayList<>();
        Map<String, DailySuccessFailure> tempMap = new HashMap<>();
        for (String date : successMap.keySet()) {
            tempMap.put(date, new DailySuccessFailure(date, successMap.get(date), 0));
        }
        for (String date : failureMap.keySet()) {
            if (tempMap.containsKey(date)) {
                DailySuccessFailure dsf = tempMap.get(date);
                tempMap.put(date, new DailySuccessFailure(date, dsf.getSuccessCount(), failureMap.get(date)));
            } else {
                tempMap.put(date, new DailySuccessFailure(date, 0, failureMap.get(date)));
            }
        }
        allSfData.addAll(tempMap.values());

        // 初始化年月Spinner
        initYearMonthSpinners();
        // 过滤并绘制数据
        filterAndDrawData();
    }

    // 初始化年月Spinner（基于成败数据中的年份）
    private void initYearMonthSpinners() {
        // 1. 年份Spinner：提取数据中所有年份
        List<Integer> yearList = YearMonthSelectorUtil.getYearsFromSuccessFailureData(allSfData);
        // 兜底：无数据时显示近10年
        if (yearList.isEmpty()) {
            yearList = YearMonthSelectorUtil.generateDefaultYears(10);
        }
        ArrayAdapter<Integer> yearAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                yearList
        );
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(yearAdapter);

        // 默认选中当前年
        int currentYearPos = yearList.indexOf(selectedYear);
        if (currentYearPos != -1) {
            spinnerYear.setSelection(currentYearPos);
        }

        // 2. 月份Spinner：固定1-12月
        List<String> monthList = YearMonthSelectorUtil.generateMonthOptions();
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                monthList
        );
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMonth.setAdapter(monthAdapter);
        spinnerMonth.setSelection(selectedMonth - 1);

        // 3. 年份选择事件
        spinnerYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedYear = (Integer) parent.getItemAtPosition(position);
                filterAndDrawData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedYear = Calendar.getInstance().get(Calendar.YEAR);
            }
        });

        // 4. 月份选择事件
        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String monthStr = (String) parent.getItemAtPosition(position);
                selectedMonth = YearMonthSelectorUtil.parseMonth(monthStr);
                filterAndDrawData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedMonth = Calendar.getInstance().get(Calendar.MONTH) + 1;
            }
        });
    }

    // 过滤选中年月的数据并绘制图表
    private void filterAndDrawData() {
        if (allSfData == null) allSfData = new ArrayList<>();
        // 过滤选中年月的数据
        List<DailySuccessFailure> filteredData = DateFilterUtil.filterSuccessFailureByMonth(allSfData, selectedYear, selectedMonth);
        drawChart(filteredData);
    }

    // 初始化图表（原有逻辑不变）
    private void initChart() {
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.getDescription().setEnabled(false);
        barChart.setPinchZoom(false);
        barChart.setDrawGridBackground(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(-45);
        xAxis.setGranularity(1f);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);

        barChart.getLegend().setEnabled(true);
    }

    // 绘制图表（原有逻辑不变）
    private void drawChart(List<DailySuccessFailure> dataList) {
        if (dataList.isEmpty()) {
            barChart.clear();
            // 新增：无数据提示
            barChart.setNoDataText("暂无当日成败数据");
            barChart.setNoDataTextColor(getContext() != null ? getResources().getColor(R.color.seablue, getContext().getTheme()) : android.graphics.Color.GRAY);
            barChart.invalidate();
            return;
        }

        ArrayList<BarEntry> successEntries = new ArrayList<>();
        ArrayList<BarEntry> failureEntries = new ArrayList<>();
        ArrayList<String> xLabels = new ArrayList<>();

        for (int i = 0; i < dataList.size(); i++) {
            DailySuccessFailure item = dataList.get(i);
            successEntries.add(new BarEntry(i, item.getSuccessCount()));
            failureEntries.add(new BarEntry(i, item.getFailureCount()));
            xLabels.add(item.getDate());
        }

        // 修复getResources()空指针
        BarDataSet successSet = new BarDataSet(successEntries, "成功数");
        successSet.setColor(getContext() != null ? getResources().getColor(R.color.green, getContext().getTheme()) : android.graphics.Color.GREEN);
        successSet.setValueTextSize(10f);

        BarDataSet failureSet = new BarDataSet(failureEntries, "失败数");
        failureSet.setColor(getContext() != null ? getResources().getColor(R.color.red, getContext().getTheme()) : android.graphics.Color.RED);
        failureSet.setValueTextSize(10f);

        BarData barData = new BarData(successSet, failureSet);
        barData.setBarWidth(0.4f);
        barData.groupBars(-0.4f, 0.1f, 0.05f);

        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        barChart.setData(barData);
        barChart.getXAxis().setAxisMinimum(-0.5f);
        barChart.getXAxis().setAxisMaximum(dataList.size() - 0.5f);
        barChart.invalidate();
    }

}
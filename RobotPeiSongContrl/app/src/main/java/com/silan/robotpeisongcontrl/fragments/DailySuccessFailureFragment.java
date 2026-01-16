package com.silan.robotpeisongcontrl.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.model.DailySuccessFailure;
import com.silan.robotpeisongcontrl.model.DeliveryFailure;
import com.silan.robotpeisongcontrl.utils.DateFilterUtil;
import com.silan.robotpeisongcontrl.utils.DateRangeUtil;
import com.silan.robotpeisongcontrl.utils.DeliveryFailureManager;
import com.silan.robotpeisongcontrl.utils.TaskSuccessManager;
import com.silan.robotpeisongcontrl.utils.YearMonthSelectorUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class DailySuccessFailureFragment extends Fragment {
    private BarChart barChart;
    private EditText etStartDate, etEndDate;
    private Button btnQuery;
    private List<DailySuccessFailure> allSfData; // 缓存所有成败数据

    private boolean isFragmentDestroyed = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        isFragmentDestroyed = false;

        View view = inflater.inflate(R.layout.fragment_daily_success_failure, container, false);
        barChart = view.findViewById(R.id.bar_chart);
        etStartDate = view.findViewById(R.id.et_start_date);
        etEndDate = view.findViewById(R.id.et_end_date);
        btnQuery = view.findViewById(R.id.btn_query);

        initChart();
        initDatePickers(); // 初始化日期选择器
        loadAllSuccessFailureData(); // 加载所有成败数据

        // 查询按钮点击事件
        btnQuery.setOnClickListener(v -> queryByDateRange());

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isFragmentDestroyed = true;
    }

    /**
     * 初始化日期选择器
     */
    private void initDatePickers() {
        if (isFragmentDestroyed || getContext() == null) {
            return;
        }

        // 默认开始日期为30天前，结束日期为今天
        String defaultStart = DateRangeUtil.getDateBefore(30);
        String defaultEnd = DateRangeUtil.getCurrentDate();
        etStartDate.setText(defaultStart);
        etEndDate.setText(defaultEnd);

        // 开始日期选择器
        etStartDate.setOnClickListener(v -> showDatePicker(true));
        // 结束日期选择器
        etEndDate.setOnClickListener(v -> showDatePicker(false));
    }

    /**
     * 显示日期选择器
     * @param isStart 是否为开始日期
     */
    private void showDatePicker(boolean isStart) {
        if (isFragmentDestroyed || getActivity() == null || getContext() == null) {
            return;
        }

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(isStart ? "选择开始日期" : "选择结束日期")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            if (isFragmentDestroyed || getContext() == null) {
                return;
            }

            // 转换为yyyy-MM-dd格式
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
            String selectedDate = sdf.format(selection);

            if (isStart) {
                etStartDate.setText(selectedDate);
            } else {
                etEndDate.setText(selectedDate);
            }
        });

        datePicker.show(getActivity().getSupportFragmentManager(), "DATE_PICKER");
    }

    // 加载所有成败数据
    private void loadAllSuccessFailureData() {
        if (isFragmentDestroyed || getContext() == null) {
            return;
        }

        // 统计成功数
        Map<String, Integer> successMap = new HashMap<>();
        List<TaskSuccessManager.TaskSuccess> successList = TaskSuccessManager.loadAllSuccess(getContext());
        for (TaskSuccessManager.TaskSuccess success : successList) {
            successMap.put(success.getDate(), successMap.getOrDefault(success.getDate(), 0) + 1);
        }

        // 统计失败数
        Map<String, Integer> failureMap = new HashMap<>();
        List<DeliveryFailure> failureList = DeliveryFailureManager.loadAllFailures(getContext());
        for (DeliveryFailure failure : failureList) {
            String date = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                    .format(new Date(failure.getTimestamp()));
            failureMap.put(date, failureMap.getOrDefault(date, 0) + 1);
        }

        // 合并所有数据
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

        // 首次加载默认日期范围数据
        if (!isFragmentDestroyed) {
            queryByDateRange();
        }
    }

    /**
     * 按日期范围查询数据
     */
    private void queryByDateRange() {
        if (isFragmentDestroyed || getContext() == null) {
            return;
        }

        String startDate = etStartDate.getText().toString().trim();
        String endDate = etEndDate.getText().toString().trim();

        // 校验日期范围
        int checkResult = DateRangeUtil.checkDateRange(startDate, endDate);
        switch (checkResult) {
            case -1:
                Toast.makeText(getContext(), "结束日期不能早于开始日期", Toast.LENGTH_SHORT).show();
                return;
            case -2:
                Toast.makeText(getContext(), "筛选天数最多支持31天", Toast.LENGTH_SHORT).show();
                return;
            case -3:
                Toast.makeText(getContext(), "日期格式错误（请输入yyyy-MM-dd）", Toast.LENGTH_SHORT).show();
                return;
            case -4:
                Toast.makeText(getContext(), "请选择开始和结束日期", Toast.LENGTH_SHORT).show();
                return;
        }

        // 过滤数据
        if (allSfData == null) allSfData = new ArrayList<>();
        List<DailySuccessFailure> filteredData = DateRangeUtil.filterSuccessFailureByDateRange(allSfData, startDate, endDate);
        drawChart(filteredData);
    }


    private void initChart() {
        if (isFragmentDestroyed || getContext() == null) {
            return;
        }
        // 禁用所有交互（原有逻辑保留）
        barChart.setTouchEnabled(false);
        barChart.setScaleEnabled(false);
        barChart.setScaleXEnabled(false);
        barChart.setScaleYEnabled(false);
        barChart.setDragEnabled(false);
        barChart.setDoubleTapToZoomEnabled(false);
        barChart.setHighlightPerTapEnabled(false);
        barChart.setHighlightPerDragEnabled(false);
        barChart.setPinchZoom(false);
        barChart.setLongClickable(false);

        // ========== 新增美化配置 ==========
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setExtraOffsets(10, 10, 10, 20); // 内边距

        // X轴美化
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(-45);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(12f);
        xAxis.setTextColor(getResources().getColor(R.color.gray_700, getContext().getTheme()));
        xAxis.setAxisLineColor(getResources().getColor(R.color.gray_300, getContext().getTheme()));
        xAxis.setAxisLineWidth(1f);

        // Y轴美化
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(getResources().getColor(R.color.gray_200, getContext().getTheme()));
        leftAxis.setGridLineWidth(0.5f);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextSize(12f);
        leftAxis.setTextColor(getResources().getColor(R.color.gray_700, getContext().getTheme()));
        leftAxis.setAxisLineColor(getResources().getColor(R.color.gray_300, getContext().getTheme()));
        // Y轴整数格式化（原有逻辑保留）
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
        barChart.getAxisRight().setEnabled(false);

        // 图例美化
        Legend legend = barChart.getLegend();
        legend.setEnabled(true);
        legend.setTextSize(12f);
        legend.setTextColor(getResources().getColor(R.color.gray_700, getContext().getTheme()));
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
    }

    // 绘制图表（美化版）
    private void drawChart(List<DailySuccessFailure> dataList) {
        if (isFragmentDestroyed || getContext() == null) {
            return;
        }

        if (dataList.isEmpty()) {
            barChart.clear();
            // 美化无数据提示
            barChart.setNoDataText("暂无当日成败数据");
            barChart.setNoDataTextColor(getResources().getColor(R.color.gray_500, getContext().getTheme()));
            barChart.invalidate();
            return;
        }

        ArrayList<BarEntry> successEntries = new ArrayList<>();
        ArrayList<BarEntry> failureEntries = new ArrayList<>();
        ArrayList<String> xLabels = new ArrayList<>();

        for (int i = 0; i < dataList.size(); i++) {
            DailySuccessFailure item = dataList.get(i);
            // 强制整数（模型中已为int，此处再次确认）
            int successInt = item.getSuccessCount();
            int failureInt = item.getFailureCount();

            successEntries.add(new BarEntry(i, successInt));
            failureEntries.add(new BarEntry(i, failureInt));
            xLabels.add(item.getDate());
        }

        // 成功柱子：淡绿色（柔和）
        BarDataSet successSet = new BarDataSet(successEntries, "成功数");
        successSet.setColor(getResources().getColor(R.color.green_light, getContext().getTheme()));
        successSet.setValueTextSize(11f);
        successSet.setValueTextColor(getResources().getColor(R.color.gray_800, getContext().getTheme()));
        successSet.setBarBorderWidth(0.5f);
        successSet.setBarBorderColor(getResources().getColor(R.color.green, getContext().getTheme()));
        // 数值标签强制整数（原有逻辑保留）
        successSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        // 失败柱子：淡红色（柔和）
        BarDataSet failureSet = new BarDataSet(failureEntries, "失败数");
        failureSet.setColor(getResources().getColor(R.color.red_light, getContext().getTheme()));
        failureSet.setValueTextSize(11f);
        failureSet.setValueTextColor(getResources().getColor(R.color.gray_800, getContext().getTheme()));
        failureSet.setBarBorderWidth(0.5f);
        failureSet.setBarBorderColor(getResources().getColor(R.color.red, getContext().getTheme()));
        // 数值标签强制整数（原有逻辑保留）
        failureSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        // 调整柱子宽度和分组间距
        BarData barData = new BarData(successSet, failureSet);
        barData.setBarWidth(0.35f); // 单根柱子宽度更适中
        barData.groupBars(-0.4f, 0.15f, 0.05f); // 分组间距优化

        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        barChart.setData(barData);
        barChart.getXAxis().setAxisMinimum(-0.5f);
        barChart.getXAxis().setAxisMaximum(dataList.size() - 0.5f);
        // 新增动画
        barChart.animateY(800);
        barChart.invalidate();
    }
}
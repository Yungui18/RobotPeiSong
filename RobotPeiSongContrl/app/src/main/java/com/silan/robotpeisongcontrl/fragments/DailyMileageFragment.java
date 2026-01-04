package com.silan.robotpeisongcontrl.fragments;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.model.DailyMileage;
import com.silan.robotpeisongcontrl.model.MileageResponse;
import com.silan.robotpeisongcontrl.utils.DateFilterUtil;
import com.silan.robotpeisongcontrl.utils.MileageManager;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.YearMonthSelectorUtil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import okio.ByteString;


public class DailyMileageFragment extends Fragment {
    private BarChart barChart;
    private Spinner spinnerYear;   // 年份Spinner
    private Spinner spinnerMonth;  // 月份Spinner
    private int selectedYear;      // 选中的年
    private int selectedMonth;     // 选中的月
    private List<DailyMileage> allMileageData; // 缓存所有里程数据（用于提取年份）

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily_mileage, container, false);
        barChart = view.findViewById(R.id.bar_chart);
        spinnerYear = view.findViewById(R.id.spinner_year);
        spinnerMonth = view.findViewById(R.id.spinner_month);

        initChart();
        // 初始化选中年月为当前月
        int[] currentYm = DateFilterUtil.getCurrentYearMonth();
        selectedYear = currentYm[0];
        selectedMonth = currentYm[1];

        // ========== 新增：提前初始化默认Spinner（立即显示+默认选当年） ==========
        initTemporaryDefaultSpinner();

        // 先加载所有数据（用于提取年份选项）
        loadAllMileageData();

        return view;
    }

    private void initTemporaryDefaultSpinner() {
        if (getActivity() == null) return;

        // 年份：近10年，默认选当年
        List<Integer> tempYears = YearMonthSelectorUtil.generateDefaultYears(10);
        ArrayAdapter<Integer> tempYearAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                tempYears
        );
        tempYearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(tempYearAdapter);

        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int currentYearPos = tempYears.indexOf(currentYear);
        spinnerYear.setSelection(currentYearPos != -1 ? currentYearPos : tempYears.size() - 1);

        // 月份：1-12月，默认选当月
        List<String> tempMonths = YearMonthSelectorUtil.generateMonthOptions();
        ArrayAdapter<String> tempMonthAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                tempMonths
        );
        tempMonthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMonth.setAdapter(tempMonthAdapter);
        spinnerMonth.setSelection(selectedMonth - 1);

        // 临时选择事件（数据加载后会替换）
        spinnerYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedYear = tempYears.get(position);
                if (allMileageData != null) filterAndDrawData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedMonth = YearMonthSelectorUtil.parseMonth(tempMonths.get(position));
                if (allMileageData != null) filterAndDrawData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // 加载所有里程数据（缓存+初始化年月Spinner）
    private void loadAllMileageData() {
        // 调用新的getMileageData，使用正确的MileageDataCallback
        RobotController.getMileageData(new RobotController.MileageDataCallback() {
            @Override
            public void onSuccess(MileageResponse response) {
                try {
                    // 直接使用处理好的response，无需解析
                    MileageManager.processMileageResponse(getContext(), response);

                    // 缓存所有里程数据
                    allMileageData = MileageManager.loadDailyMileage(getContext());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            initYearMonthSpinners();
                            filterAndDrawData();
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> initDefaultYearMonthSpinners());
                    }
                }
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                // 加载本地缓存
                allMileageData = MileageManager.loadDailyMileage(getContext());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (allMileageData.isEmpty()) {
                            initDefaultYearMonthSpinners();
                        } else {
                            initYearMonthSpinners();
                        }
                        filterAndDrawData();
                    });
                }
            }
        });
    }

    // 初始化年月Spinner（基于数据中的年份）
    private void initYearMonthSpinners() {
        Log.d("MileageFragment", "里程数据条数：" + (allMileageData == null ? 0 : allMileageData.size()));
        // 1. 初始化年份Spinner（提取数据中所有年份）
        List<Integer> yearList = YearMonthSelectorUtil.getYearsFromMileageData(allMileageData);
        // 兜底：无数据时显示近10年
        if (yearList.isEmpty()) {
            Log.d("MileageFragment", "无里程数据，使用默认近10年");
            yearList = YearMonthSelectorUtil.generateDefaultYears(10);
        }
        // 年份适配器（显示数字，如2022、2023...）
        ArrayAdapter<Integer> yearAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                yearList
        );
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(yearAdapter);

        // ========== 核心修改：确保默认选中当年 ==========
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int currentYearPos = yearList.indexOf(currentYear);
        if (currentYearPos != -1) {
            spinnerYear.setSelection(currentYearPos); // 有当年则选当年
        } else {
            spinnerYear.setSelection(yearList.size() - 1); // 无当年则选最新年
            selectedYear = yearList.get(yearList.size() - 1); // 同步选中值
        }

        // 2. 初始化月份Spinner（固定1-12月）
        List<String> monthList = YearMonthSelectorUtil.generateMonthOptions();
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                monthList
        );
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMonth.setAdapter(monthAdapter);

        // 默认选中当前月（如6月→索引5）
        spinnerMonth.setSelection(selectedMonth - 1);

        // 3. 年份选择事件
        spinnerYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedYear = (Integer) parent.getItemAtPosition(position);
                filterAndDrawData(); // 选中年份后过滤数据
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
                filterAndDrawData(); // 选中月份后过滤数据
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedMonth = Calendar.getInstance().get(Calendar.MONTH) + 1;
            }
        });
    }

    // 兜底：无数据时初始化默认年月Spinner（近10年+12月）
    private void initDefaultYearMonthSpinners() {
        // 年份：近10年（升序：2015-2025）
        List<Integer> defaultYears = YearMonthSelectorUtil.generateDefaultYears(10);
        ArrayAdapter<Integer> yearAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                defaultYears
        );
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerYear.setAdapter(yearAdapter);

        // ========== 核心修改：找到当年在默认列表中的位置并选中 ==========
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        int currentYearPos = defaultYears.indexOf(currentYear);
        // 找不到则选最后一个（最新年）
        spinnerYear.setSelection(currentYearPos != -1 ? currentYearPos : defaultYears.size() - 1);
        selectedYear = currentYear; // 强制赋值为当年

        // 月份：1-12月（默认选中当月）
        List<String> monthList = YearMonthSelectorUtil.generateMonthOptions();
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                monthList
        );
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMonth.setAdapter(monthAdapter);
        spinnerMonth.setSelection(selectedMonth - 1);

        // 绑定选择事件
        spinnerYear.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedYear = (Integer) parent.getItemAtPosition(position);
                filterAndDrawData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerMonth.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String monthStr = (String) parent.getItemAtPosition(position);
                selectedMonth = YearMonthSelectorUtil.parseMonth(monthStr);
                filterAndDrawData();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // 过滤选中年月的数据并绘制图表
    private void filterAndDrawData() {
        if (allMileageData == null) allMileageData = new ArrayList<>();
        // 过滤选中年月的数据（仅保留1个月）
        List<DailyMileage> filteredData = DateFilterUtil.filterMileageByMonth(allMileageData, selectedYear, selectedMonth);
        drawChart(filteredData);
    }

    // 初始化图表样式（原有逻辑不变）
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
    private void drawChart(List<DailyMileage> dataList) {if (dataList.isEmpty()) {
        barChart.clear();
        // 新增：无数据提示
        barChart.setNoDataText("暂无当日里程数据");
        barChart.setNoDataTextColor(getContext() != null ? getResources().getColor(R.color.seablue, getContext().getTheme()) : android.graphics.Color.GRAY);
        barChart.invalidate();
        return;
    }

        ArrayList<BarEntry> entries = new ArrayList<>();
        ArrayList<String> xLabels = new ArrayList<>();

        for (int i = 0; i < dataList.size(); i++) {
            DailyMileage item = dataList.get(i);
            entries.add(new BarEntry(i, (float) item.getMileage()));
            xLabels.add(item.getDate());
        }

        // 修复getResources()空指针
        BarDataSet dataSet = new BarDataSet(entries, "当日里程（米）");
        dataSet.setColor(getContext() != null ? getResources().getColor(R.color.blue, getContext().getTheme()) : android.graphics.Color.BLUE);
        dataSet.setValueTextSize(10f);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.8f);

        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        barChart.setData(barData);
        barChart.invalidate();
    }
}
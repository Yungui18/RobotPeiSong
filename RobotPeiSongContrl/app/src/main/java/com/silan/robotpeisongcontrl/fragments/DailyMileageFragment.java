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
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
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
import com.silan.robotpeisongcontrl.utils.DateRangeUtil;
import com.silan.robotpeisongcontrl.utils.MileageManager;
import com.silan.robotpeisongcontrl.utils.OkHttpUtils;
import com.silan.robotpeisongcontrl.utils.RobotController;
import com.silan.robotpeisongcontrl.utils.YearMonthSelectorUtil;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okio.ByteString;


public class DailyMileageFragment extends Fragment {
    private BarChart barChart;
    private EditText etStartDate, etEndDate;
    private Button btnQuery;
    private List<DailyMileage> allMileageData; // 缓存所有里程数据

    private boolean isDestroyed = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        isDestroyed = false; // 初始化标记
        View view = inflater.inflate(R.layout.fragment_daily_mileage, container, false);
        barChart = view.findViewById(R.id.bar_chart);
        etStartDate = view.findViewById(R.id.et_start_date);
        etEndDate = view.findViewById(R.id.et_end_date);
        btnQuery = view.findViewById(R.id.btn_query);

        initChart();
        initDatePickers(); // 初始化日期选择器
        loadAllMileageData(); // 加载所有里程数据

        // 查询按钮点击事件
        btnQuery.setOnClickListener(v -> queryByDateRange());

        return view;
    }

    // 新增：Fragment销毁时标记
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isDestroyed = true;
    }

    /**
     * 初始化日期选择器
     */
    private void initDatePickers() {
        // 先校验Fragment是否存活
        if (isDestroyed || getContext() == null) {
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
        // 先校验Fragment是否存活
        if (isDestroyed || getActivity() == null) {
            return;
        }
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(isStart ? "选择开始日期" : "选择结束日期")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
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

    /**
     * 加载所有里程数据
     */
    private void loadAllMileageData() {
        RobotController.getMileageData(new RobotController.MileageDataCallback() {
            @Override
            public void onSuccess(MileageResponse response) {
                // 核心修复1：校验Fragment是否存活+Context是否有效
                if (isDestroyed || getContext() == null) {
                    return;
                }
                try {
                    MileageManager.processMileageResponse(getContext(), response);
                    allMileageData = MileageManager.loadDailyMileage(getContext());
                    // 核心修复2：切换到主线程且校验Activity是否存在
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (!isDestroyed) {
                                queryByDateRange();
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    loadLocalData();
                }
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                // 核心修复3：失败回调也校验Fragment状态
                if (!isDestroyed) {
                    loadLocalData();
                }
            }
        });
    }

    /**
     * 加载本地缓存数据（核心修复：增加Context校验）
     */
    private void loadLocalData() {
        // 先校验Context和Fragment状态
        if (isDestroyed || getContext() == null) {
            return;
        }
        allMileageData = MileageManager.loadDailyMileage(getContext());
        // 切换主线程且校验
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (!isDestroyed) {
                    queryByDateRange();
                }
            });
        }
    }

    /**
     * 按日期范围查询数据
     */
    private void queryByDateRange() {
        // 先校验Fragment状态
        if (isDestroyed || getContext() == null) {
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
        if (allMileageData == null) allMileageData = new ArrayList<>();
        List<DailyMileage> filteredData = DateRangeUtil.filterMileageByDateRange(allMileageData, startDate, endDate);
        drawChart(filteredData);
    }

    // 初始化图表样式（美化版）
    private void initChart() {
        if (isDestroyed || getContext() == null) {
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
        barChart.setExtraOffsets(10, 10, 10, 20); // 图表内边距，避免贴边

        // X轴美化
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(-45);
        xAxis.setGranularity(1f);
        xAxis.setTextSize(12f); // 文字大小
        xAxis.setTextColor(getResources().getColor(R.color.gray_700, getContext().getTheme())); // 文字颜色
        xAxis.setAxisLineColor(getResources().getColor(R.color.gray_300, getContext().getTheme())); // 轴线颜色
        xAxis.setAxisLineWidth(1f); // 轴线宽度

        // Y轴美化
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(getResources().getColor(R.color.gray_200, getContext().getTheme())); // 网格线浅灰
        leftAxis.setGridLineWidth(0.5f); // 网格线细一点
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextSize(12f); // 文字大小
        leftAxis.setTextColor(getResources().getColor(R.color.gray_700, getContext().getTheme())); // 文字颜色
        leftAxis.setAxisLineColor(getResources().getColor(R.color.gray_300, getContext().getTheme())); // 轴线颜色
        barChart.getAxisRight().setEnabled(false);

        // 图例美化
        Legend legend = barChart.getLegend();
        legend.setEnabled(true);
        legend.setTextSize(12f);
        legend.setTextColor(getResources().getColor(R.color.gray_700, getContext().getTheme()));
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.TOP); // 图例在顶部
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.RIGHT); // 图例在右侧
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
    }

    // 绘制图表（美化版）
    private void drawChart(List<DailyMileage> dataList) {
        if (isDestroyed || getContext() == null) {
            return;
        }
        if (dataList.isEmpty()) {
            barChart.clear();
            barChart.setNoDataText("暂无当日里程数据");
            barChart.setNoDataTextColor(getResources().getColor(R.color.gray_500, getContext().getTheme()));
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

        // 美化柱子：淡蓝色（柔和）
        BarDataSet dataSet = new BarDataSet(entries, "当日里程（米）");
        dataSet.setColor(getResources().getColor(R.color.blue_light, getContext().getTheme()));
        dataSet.setValueTextSize(11f); // 数值文字大小
        dataSet.setValueTextColor(getResources().getColor(R.color.gray_800, getContext().getTheme())); // 数值颜色
        dataSet.setBarBorderWidth(0.5f); // 柱子边框
        dataSet.setBarBorderColor(getResources().getColor(R.color.blue, getContext().getTheme())); // 边框颜色

        // 调整柱子宽度（0.6更适中）
        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.6f);

        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));
        barChart.setData(barData);
        // 调整X轴范围，避免柱子贴边
        barChart.getXAxis().setAxisMinimum(-0.5f);
        barChart.getXAxis().setAxisMaximum(dataList.size() - 0.5f);
        // 新增动画，刷新更流畅
        barChart.animateY(800);
        barChart.invalidate();
    }
}
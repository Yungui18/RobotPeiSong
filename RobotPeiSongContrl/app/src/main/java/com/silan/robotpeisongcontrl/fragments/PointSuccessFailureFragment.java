package com.silan.robotpeisongcontrl.fragments;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.silan.robotpeisongcontrl.R;
import com.silan.robotpeisongcontrl.model.DeliveryFailure;
import com.silan.robotpeisongcontrl.model.PointSuccessFailure;
import com.silan.robotpeisongcontrl.utils.DeliveryFailureManager;
import com.silan.robotpeisongcontrl.utils.TaskSuccessManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PointSuccessFailureFragment extends Fragment {

    private BarChart barChart;
    private boolean isFragmentDestroyed = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        isFragmentDestroyed = false;
        View view = inflater.inflate(R.layout.fragment_point_success_failure, container, false);
        barChart = view.findViewById(R.id.bar_chart);
        initChart();
        loadPointData();
        return view;
    }

    // 销毁时标记
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isFragmentDestroyed = true;
    }

    // 初始化图
    private void initChart() {
        if (isFragmentDestroyed || getContext() == null) {
            return;
        }
        // 禁用所有交互
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

    // 加载点位成败数据
    private void loadPointData() {
        if (isFragmentDestroyed || getContext() == null) {
            return;
        }
        // 1. 统计成功数
        Map<String, Integer> successMap = new HashMap<>();
        List<TaskSuccessManager.TaskSuccess> successList = TaskSuccessManager.loadAllSuccess(getContext());
        for (TaskSuccessManager.TaskSuccess success : successList) {
            successMap.put(success.getPointName(), successMap.getOrDefault(success.getPointName(), 0) + 1);
        }

        // 2. 统计失败数
        Map<String, Integer> failureMap = new HashMap<>();
        List<DeliveryFailure> failureList = DeliveryFailureManager.loadAllFailures(getContext());
        for (DeliveryFailure failure : failureList) {
            failureMap.put(failure.getPointName(), failureMap.getOrDefault(failure.getPointName(), 0) + 1);
        }

        // 3. 合并数据
        List<PointSuccessFailure> dataList = new ArrayList<>();
        Map<String, PointSuccessFailure> tempMap = new HashMap<>();
        // 先加成功的
        for (String point : successMap.keySet()) {
            tempMap.put(point, new PointSuccessFailure(point, successMap.get(point), 0));
        }
        // 再加失败的
        for (String point : failureMap.keySet()) {
            if (tempMap.containsKey(point)) {
                PointSuccessFailure psf = tempMap.get(point);
                tempMap.put(point, new PointSuccessFailure(point,
                        psf.getSuccessCount(), failureMap.get(point)));
            } else {
                tempMap.put(point, new PointSuccessFailure(point, 0, failureMap.get(point)));
            }
        }
        dataList.addAll(tempMap.values());

        // 4. 绘制图表
        drawChart(dataList);
    }

    // 绘制图表（美化版）
    private void drawChart(List<PointSuccessFailure> dataList) {
        if (isFragmentDestroyed || getContext() == null) {
            return;
        }
        // 1. 数据为空时：清空图表+显示无数据提示
        if (dataList.isEmpty()) {
            barChart.clear();
            barChart.setNoDataText("暂无点位成败数据");
            barChart.setNoDataTextColor(getResources().getColor(R.color.gray_500, getContext().getTheme()));
            barChart.invalidate();
            return;
        }

        ArrayList<BarEntry> successEntries = new ArrayList<>();
        ArrayList<BarEntry> failureEntries = new ArrayList<>();
        ArrayList<String> xLabels = new ArrayList<>();

        for (int i = 0; i < dataList.size(); i++) {
            PointSuccessFailure item = dataList.get(i);
            // 强制整数
            int successInt = item.getSuccessCount();
            int failureInt = item.getFailureCount();

            successEntries.add(new BarEntry(i, successInt));
            failureEntries.add(new BarEntry(i, failureInt));
            xLabels.add(item.getPointName());
        }

        // 成功柱子：淡绿色
        BarDataSet successSet = new BarDataSet(successEntries, "成功数");
        successSet.setColor(getResources().getColor(R.color.green_light, getContext().getTheme()));
        successSet.setValueTextSize(11f);
        successSet.setValueTextColor(getResources().getColor(R.color.gray_800, getContext().getTheme()));
        successSet.setBarBorderWidth(0.5f);
        successSet.setBarBorderColor(getResources().getColor(R.color.green, getContext().getTheme()));
        // 数值标签强制整数
        successSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        // 失败柱子：淡红色
        BarDataSet failureSet = new BarDataSet(failureEntries, "失败数");
        failureSet.setColor(getResources().getColor(R.color.red_light, getContext().getTheme()));
        failureSet.setValueTextSize(11f);
        failureSet.setValueTextColor(getResources().getColor(R.color.gray_800, getContext().getTheme()));
        failureSet.setBarBorderWidth(0.5f);
        failureSet.setBarBorderColor(getResources().getColor(R.color.red, getContext().getTheme()));
        // 数值标签强制整数
        failureSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });

        // 调整柱子宽度和分组间距
        BarData barData = new BarData(successSet, failureSet);
        barData.setBarWidth(0.35f);
        barData.groupBars(-0.4f, 0.15f, 0.05f);

        // X轴标签
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));

        // 更新图表
        barChart.setData(barData);
        barChart.getXAxis().setAxisMinimum(-0.5f);
        barChart.getXAxis().setAxisMaximum(dataList.size() - 0.5f);
        // 新增动画
        barChart.animateY(800);
        barChart.invalidate();
    }
}

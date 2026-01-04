package com.silan.robotpeisongcontrl.fragments;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.Fragment;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_point_success_failure, container, false);
        barChart = view.findViewById(R.id.bar_chart);
        initChart();
        loadPointData();
        return view;
    }

    // 初始化图表
    private void initChart() {
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.getDescription().setEnabled(false);
        barChart.setPinchZoom(false);
        barChart.setDrawGridBackground(false);

        // X轴
        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setLabelRotationAngle(-45);
        xAxis.setGranularity(1f);

        // Y轴
        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setAxisMinimum(0f);
        barChart.getAxisRight().setEnabled(false);

        // 图例
        barChart.getLegend().setEnabled(true);
    }

    // 加载点位成败数据
    private void loadPointData() {
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
        // 合并所有点位
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

    // 绘制图表（分组柱状图）
    private void drawChart(List<PointSuccessFailure> dataList) {
        // 1. 数据为空时：清空图表+显示无数据提示，避免空指针
        if (dataList.isEmpty()) {
            barChart.clear();
            barChart.setNoDataText("暂无点位成败数据");
            barChart.setNoDataTextColor(getContext() != null ? getResources().getColor(R.color.seablue, getContext().getTheme()) : android.graphics.Color.GRAY);
            barChart.invalidate();
            return;
        }

        ArrayList<BarEntry> successEntries = new ArrayList<>();
        ArrayList<BarEntry> failureEntries = new ArrayList<>();
        ArrayList<String> xLabels = new ArrayList<>();

        for (int i = 0; i < dataList.size(); i++) {
            PointSuccessFailure item = dataList.get(i);
            successEntries.add(new BarEntry(i, item.getSuccessCount()));
            failureEntries.add(new BarEntry(i, item.getFailureCount()));
            xLabels.add(item.getPointName());
        }

        // 成功数据集（修复getResources()空指针）
        BarDataSet successSet = new BarDataSet(successEntries, "成功数");
        successSet.setColor(getContext() != null ? getResources().getColor(R.color.green, getContext().getTheme()) : android.graphics.Color.GREEN);
        successSet.setValueTextSize(10f);

        // 失败数据集（修复getResources()空指针）
        BarDataSet failureSet = new BarDataSet(failureEntries, "失败数");
        failureSet.setColor(getContext() != null ? getResources().getColor(R.color.red, getContext().getTheme()) : android.graphics.Color.RED);
        failureSet.setValueTextSize(10f);

        // 组合数据（核心修复：移除barChart.getBarData()的空指针调用）
        BarData barData = new BarData(successSet, failureSet);
        barData.setBarWidth(0.4f); // 单根柱子宽度
        // 删掉这行错误代码：barChart.getBarData().setBarWidth(0.4f);
        // 设置分组间距
        barData.groupBars(-0.4f, 0.1f, 0.05f);

        // X轴标签
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(xLabels));

        // 更新图表
        barChart.setData(barData);
        barChart.getXAxis().setAxisMinimum(-0.5f);
        barChart.getXAxis().setAxisMaximum(dataList.size() - 0.5f);
        barChart.invalidate();
    }
}

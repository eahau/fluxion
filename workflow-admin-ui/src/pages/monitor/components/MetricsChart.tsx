import ReactECharts from 'echarts-for-react';
import type { MetricsTrend } from '@/types/monitor';

interface MetricsChartProps {
  title?: string;
  trend?: MetricsTrend;
  height?: number;
}

const MetricsChart: React.FC<MetricsChartProps> = ({
  title = '请求量趋势 (24h)',
  trend,
  height = 300,
}) => {
  const points = trend?.points || [];
  const xAxis = points.map((p) => {
    const d = new Date(p.timestamp || 0);
    return `${d.getHours().toString().padStart(2, '0')}:00`;
  });
  const series = points.map((p) => p.value || 0);

  const option = {
    title: { text: title, left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: xAxis, boundaryGap: false },
    yAxis: { type: 'value' },
    series: [{ data: series, type: 'line', smooth: true, itemStyle: { color: '#6366f1' }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(99,102,241,0.25)' }, { offset: 1, color: 'rgba(99,102,241,0.02)' }] } } }],
  };

  return <ReactECharts option={option} style={{ height }} />;
};

export default MetricsChart;

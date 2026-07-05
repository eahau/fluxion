import { Card, Progress, Tag, Space, Typography, Collapse, Tooltip, Badge } from 'antd';
import {
  CheckCircleFilled,
  CloseCircleFilled,
  WarningFilled,
  ClockCircleOutlined,
  MinusCircleFilled,
} from '@ant-design/icons';
import { useState } from 'react';
import JsonEditor from '@/components/JsonEditor';
import type { TraceDetail } from '@/types/monitor';

const { Text, Paragraph } = Typography;

interface TraceTimelineProps {
  trace: TraceDetail;
}

const statusMeta: Record<string, { color: string; icon: React.ReactNode; label: string }> = {
  SUCCESS: { color: '#52c41a', icon: <CheckCircleFilled style={{ color: '#52c41a' }} />, label: '成功' },
  COMPLETED: { color: '#52c41a', icon: <CheckCircleFilled style={{ color: '#52c41a' }} />, label: '完成' },
  FAILED: { color: '#ff4d4f', icon: <CloseCircleFilled style={{ color: '#ff4d4f' }} />, label: '失败' },
  FALLBACK: { color: '#faad14', icon: <WarningFilled style={{ color: '#faad14' }} />, label: '降级' },
  SKIPPED: { color: '#d9d9d9', icon: <MinusCircleFilled style={{ color: '#d9d9d9' }} />, label: '跳过' },
  RUNNING: { color: '#6366f1', icon: <ClockCircleOutlined style={{ color: '#6366f1' }} />, label: '运行中' },
};

const TraceTimeline: React.FC<TraceTimelineProps> = ({ trace }) => {
  const [expandedNodes, setExpandedNodes] = useState<Set<number>>(new Set());
  const traces = trace.traces || [];
  const totalMs = trace.totalDurationMs || 1;

  const toggleNode = (idx: number) => {
    setExpandedNodes((prev) => {
      const next = new Set(prev);
      next.has(idx) ? next.delete(idx) : next.add(idx);
      return next;
    });
  };

  // 计算整体状态
  const hasFailed = traces.some((t: any) => t.status === 'FAILED');
  const overallStatus = hasFailed ? 'FAILED' : (traces.every((t: any) => t.status === 'SUCCESS' || t.status === 'COMPLETED') ? 'SUCCESS' : 'PARTIAL');
  const overallMeta = statusMeta[overallStatus] || statusMeta.SUCCESS;

  return (
    <div>
      {/* 顶部摘要 */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '16px 20px',
        background: hasFailed ? '#fef2f2' : '#f0fdf4',
        borderRadius: 10,
        marginBottom: 20,
        border: `1px solid ${hasFailed ? '#fecaca' : '#bbf7d0'}`,
      }}>
        <Space size={16}>
          {overallMeta.icon}
          <div>
            <Text strong style={{ fontSize: 15 }}>{trace.workflowName}</Text>
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {trace.executionId?.slice(0, 20)}...
              </Text>
            </div>
          </div>
        </Space>
        <Space size={24}>
          <div style={{ textAlign: 'center' }}>
            <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>总耗时</Text>
            <Text strong style={{ fontSize: 18 }}>{totalMs}<Text type="secondary" style={{ fontSize: 12 }}>ms</Text></Text>
          </div>
          <div style={{ textAlign: 'center' }}>
            <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>节点数</Text>
            <Text strong style={{ fontSize: 18 }}>{traces.length}</Text>
          </div>
          <div style={{ textAlign: 'center' }}>
            <Text type="secondary" style={{ fontSize: 11, display: 'block' }}>状态</Text>
            <Tag color={overallMeta.color} style={{ borderRadius: 4 }}>{overallMeta.label}</Tag>
          </div>
        </Space>
      </div>

      {/* 甘特图视图 */}
      <div style={{ marginBottom: 24 }}>
        <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: 1 }}>
          执行时间线
        </Text>
        <div style={{ marginTop: 8 }}>
          {traces.map((t: any, idx: number) => {
            const meta = statusMeta[t.status] || statusMeta.SUCCESS;
            const pct = Math.min(100, (t.durationMs / totalMs) * 100);
            // 计算偏移：简化处理，按顺序排列
            return (
              <div key={idx} style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 6 }}>
                <div style={{ width: 140, textAlign: 'right', flexShrink: 0 }}>
                  <Text style={{ fontSize: 12 }} ellipsis={{ tooltip: t.nodeName }}>{t.nodeName}</Text>
                </div>
                <div style={{ flex: 1, position: 'relative', height: 24 }}>
                  <Tooltip title={`${t.nodeName}: ${t.durationMs}ms (${meta.label})`}>
                    <div style={{
                      position: 'absolute',
                      left: 0,
                      width: `${Math.max(pct, 4)}%`,
                      height: '100%',
                      borderRadius: 4,
                      background: `${meta.color}25`,
                      border: `1px solid ${meta.color}50`,
                      display: 'flex',
                      alignItems: 'center',
                      paddingLeft: 8,
                    }}>
                      <Text style={{ fontSize: 10, color: meta.color, fontWeight: 600 }}>
                        {t.durationMs}ms
                      </Text>
                    </div>
                  </Tooltip>
                </div>
                <div style={{ width: 20, flexShrink: 0 }}>{meta.icon}</div>
              </div>
            );
          })}
        </div>
      </div>

      {/* 详细节点列表 */}
      <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: 1 }}>
        节点详情 (点击展开)
      </Text>
      <div style={{ marginTop: 8 }}>
        {traces.map((t: any, idx: number) => {
          const meta = statusMeta[t.status] || statusMeta.SUCCESS;
          const isExpanded = expandedNodes.has(idx);
          return (
            <div
              key={idx}
              style={{
                marginBottom: 8,
                borderRadius: 10,
                border: `1px solid ${isExpanded ? meta.color : '#f0f0f0'}`,
                overflow: 'hidden',
                transition: 'all 0.2s',
              }}
            >
              {/* 节点头部 */}
              <div
                onClick={() => toggleNode(idx)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 12,
                  padding: '12px 16px',
                  cursor: 'pointer',
                  background: isExpanded ? `${meta.color}08` : '#fff',
                  transition: 'background 0.2s',
                }}
              >
                {/* 连接线指示器 */}
                <div style={{
                  width: 3,
                  height: 32,
                  borderRadius: 2,
                  background: meta.color,
                  flexShrink: 0,
                }} />
                {meta.icon}
                <div style={{ flex: 1, minWidth: 0 }}>
                  <Space size={8}>
                    <Text strong style={{ fontSize: 13 }}>{t.nodeName}</Text>
                    <Tag color={meta.color} style={{ borderRadius: 4, fontSize: 11, margin: 0 }}>{meta.label}</Tag>
                  </Space>
                </div>
                <Space size={12}>
                  <Tag style={{ borderRadius: 4, margin: 0 }}>
                    <ClockCircleOutlined style={{ marginRight: 4 }} />
                    {t.durationMs}ms
                  </Tag>
                  <Progress
                    type="circle"
                    percent={Math.min(100, (t.durationMs / totalMs) * 100)}
                    size={24}
                    strokeColor={meta.color}
                    format={() => ''}
                  />
                </Space>
              </div>

              {/* 展开详情 */}
              {isExpanded && (
                <div style={{ padding: '12px 16px', borderTop: '1px solid #f0f0f0', background: '#fafafa' }}>
                  {t.sql && (
                    <div style={{ marginBottom: 12 }}>
                      <Text type="secondary" style={{ fontSize: 11 }}>SQL</Text>
                      <pre style={{
                        margin: '4px 0', padding: 8, background: '#f8fafc',
                        borderRadius: 6, fontSize: 12, border: '1px solid #e2e8f0',
                        overflow: 'auto',
                      }}>
                        {t.sql}
                      </pre>
                    </div>
                  )}
                  <div style={{ display: 'flex', gap: 16 }}>
                    <div style={{ flex: 1 }}>
                      <Text type="secondary" style={{ fontSize: 11 }}>输入</Text>
                      <div style={{ marginTop: 4 }}>
                        <JsonEditor value={t.input} readOnly height={120} />
                      </div>
                    </div>
                    <div style={{ flex: 1 }}>
                      <Text type="secondary" style={{ fontSize: 11 }}>输出</Text>
                      <div style={{ marginTop: 4 }}>
                        <JsonEditor value={t.output} readOnly height={120} />
                      </div>
                    </div>
                  </div>
                  {t.error && (
                    <div style={{
                      marginTop: 12, padding: '8px 12px',
                      background: '#fef2f2', borderRadius: 6,
                      border: '1px solid #fecaca', color: '#dc2626',
                      fontSize: 12,
                    }}>
                      <CloseCircleFilled style={{ marginRight: 6 }} />
                      {t.error}
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default TraceTimeline;

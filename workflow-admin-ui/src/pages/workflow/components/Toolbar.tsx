import { Button, Space, Input, Tag, Tooltip, Divider, Segmented, Popover } from 'antd';
import {
  SaveOutlined,
  BugOutlined,
  ArrowLeftOutlined,
  SettingOutlined,
  UndoOutlined,
  RedoOutlined,
  ThunderboltOutlined,
  PlusOutlined,
  AppstoreOutlined,
  WarningOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { history, useRequest } from '@umijs/max';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import { listTriggerFunctions, type WorkflowTrigger, type TriggerFunctionMeta } from '@/services/triggerFunctions';
import { listApps, type App } from '@/services/apps';

interface ToolbarProps {
  title: string;
  onSave: () => void;
  onDebug: () => void;
  debugActive: boolean;
  onOpenTrigger: () => void;
  onOpenMeta?: () => void;
  layoutDirection: 'TB' | 'LR';
  onLayoutChange: (dir: 'TB' | 'LR') => void;
  darkMode?: boolean;
}

function resolveTriggerSummary(
  triggers: WorkflowTrigger[],
  metas: TriggerFunctionMeta[] | undefined,
): React.ReactNode {
  if (!triggers || triggers.length === 0) {
    return (
      <Tag color="default" icon={<ThunderboltOutlined />} style={{ borderRadius: 6 }}>
        纯函数集合（无触发器）
      </Tag>
    );
  }
  const head = triggers[0];
  const meta = metas?.find((m) => m.functionRef === head.functionRef);
  const label = meta?.label || (head.functionRef ? head.functionRef.substring(head.functionRef.lastIndexOf(':') + 1) : '未指定');
  const cfg = (head.config ?? {}) as Record<string, any>;
  const tokens: { key: string; value: any; color: string }[] = [];
  if (typeof cfg.method === 'string' && cfg.method) {
    tokens.push({ key: 'method', value: cfg.method.toUpperCase(), color: '#6366f1' });
  }
  if (typeof cfg.path === 'string' && cfg.path) {
    tokens.push({ key: 'path', value: cfg.path, color: '#0891b2' });
  } else if (typeof cfg.bindKey === 'string' && cfg.bindKey) {
    tokens.push({ key: 'bindKey', value: cfg.bindKey, color: '#0891b2' });
  } else if (typeof cfg.topic === 'string' && cfg.topic) {
    tokens.push({ key: 'topic', value: cfg.topic, color: '#0891b2' });
  } else if (typeof cfg.serviceKey === 'string' && cfg.serviceKey) {
    tokens.push({ key: 'serviceKey', value: cfg.serviceKey, color: '#0891b2' });
  } else if (typeof cfg.queue === 'string' && cfg.queue) {
    tokens.push({ key: 'queue', value: cfg.queue, color: '#0891b2' });
  }
  const extraCount = Math.max(0, triggers.length - 1);
  return (
    <Space size={4} wrap>
      <Tag
        color={meta?.legacyProtocol ? 'purple' : 'cyan'}
        icon={<ThunderboltOutlined />}
        style={{ borderRadius: 6, fontWeight: 600, margin: 0 }}
      >
        {label}
      </Tag>
      {tokens.map((t) => (
        <Tag key={t.key} color={t.color} style={{ margin: 0, fontFamily: '"SF Mono", Monaco, monospace', fontSize: 12 }}>
          {t.key === 'method' ? null : `${t.key}=`}
          {typeof t.value === 'string' && t.value.length > 30 ? t.value.substring(0, 30) + '…' : t.value}
        </Tag>
      ))}
      {extraCount > 0 && (
        <Tag color="geekblue" style={{ margin: 0 }}>
          +{extraCount} 触发器
        </Tag>
      )}
      {!head.enabled && (
        <Tag color="default" style={{ margin: 0 }}>
          已禁用
        </Tag>
      )}
    </Space>
  );
}

const Toolbar: React.FC<ToolbarProps> = ({
  title,
  onSave,
  onDebug,
  debugActive,
  onOpenTrigger,
  onOpenMeta,
  layoutDirection,
  onLayoutChange,
  darkMode,
}) => {
  const { workflowMeta, setWorkflowMeta, canUndo, canRedo, undo, redo } = useWorkflowStore();
  const { data: metaList } = useRequest(
    async () => listTriggerFunctions(),
    { refreshDeps: [] },
  );
  const { data: appList, loading: appsLoading } = useRequest(async () => listApps(), { refreshDeps: [] });
  const currentApp: App | undefined = (appList ?? []).find((a: App) => a.appKey === workflowMeta.appGroup);

  const triggers = workflowMeta.triggers && workflowMeta.triggers.length > 0 ? workflowMeta.triggers : [];

  const renderTriggerPopoverContent = () => (
    <div style={{ minWidth: 280 }}>
      <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 8 }}>
        当前共 <strong style={{ color: '#000' }}>{triggers.length}</strong> 个触发器
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {triggers.length === 0 ? (
          <div style={{ fontSize: 12, color: '#8c8c8c', padding: '4px 0' }}>
            当前是「纯函数集合」，只能被其他工作流通过 SET_REF 引用，无法被外部事件触发。
          </div>
        ) : (
          triggers.map((t, i) => {
            const meta = metaList?.find((m) => m.functionRef === t.functionRef);
            const label = meta?.label || (t.functionRef ?? `#${i + 1}`);
            return (
              <div
                key={t.id ?? `trigger-${i}`}
                style={{
                  padding: '6px 10px',
                  borderRadius: 6,
                  border: '1px solid ' + (t.enabled ? '#e8e8e8' : '#f0f0f0'),
                  background: t.enabled ? '#fafafa' : '#fafafa',
                  opacity: t.enabled ? 1 : 0.6,
                }}
              >
                <div style={{ fontWeight: 600, fontSize: 13 }}>
                  {label}
                  <Tag color="purple" style={{ marginLeft: 6 }}>{t.type ?? 'API'}</Tag>
                  {!t.enabled && <Tag style={{ marginLeft: 6 }}>已禁用</Tag>}
                </div>
                <div style={{ marginTop: 4, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                  {Object.entries(t.config ?? {})
                    .filter(([, v]) => v !== undefined && v !== null && v !== '')
                    .slice(0, 3)
                    .map(([k, v]) => (
                      <Tag key={k} color="geekblue" style={{ margin: 0, fontSize: 11 }}>
                        {k}={typeof v === 'string' && v.length > 16 ? v.substring(0, 16) + '…' : String(v)}
                      </Tag>
                    ))}
                </div>
              </div>
            );
          })
        )}
      </div>
      <Divider style={{ margin: '10px 0' }} />
      <Button block type="primary" icon={triggers.length === 0 ? <PlusOutlined /> : <SettingOutlined />} onClick={onOpenTrigger}>
        {triggers.length === 0 ? '添加触发器' : '编辑触发器配置'}
      </Button>
    </div>
  );

  const renderAppPopoverContent = () => (
    <div style={{ minWidth: 300 }}>
      <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 8 }}>
        数据源（DB / Redis 等）与 <strong style={{ color: '#000' }}>应用维度</strong> 绑定，后续节点若涉及 DB / Redis 函数必须先选 App。
      </div>
      {currentApp ? (
        <div style={{ padding: 10, borderRadius: 8, border: '1px solid #e8e8e8', background: '#fafafa' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
            <AppstoreOutlined style={{ color: '#6366f1', fontSize: 16 }} />
            <strong style={{ fontSize: 14 }}>{currentApp.appName}</strong>
            <Tag color="geekblue" style={{ margin: 0 }}>{currentApp.appKey}</Tag>
            {currentApp.status === 'ACTIVE' ? (
              <Tag color="success" icon={<CheckCircleOutlined />} style={{ margin: 0 }}>正常</Tag>
            ) : (
              <Tag color="default">禁用</Tag>
            )}
          </div>
          {currentApp.owner && <div style={{ fontSize: 12, color: '#8c8c8c' }}>负责人：{currentApp.owner}</div>}
          {currentApp.description && <div style={{ fontSize: 12, color: '#595959', marginTop: 4, whiteSpace: 'pre-wrap' }}>{currentApp.description}</div>}
        </div>
      ) : (
        <div style={{
          padding: 10,
          borderRadius: 8,
          border: '1px dashed #faad14',
          background: '#fffbe6',
          fontSize: 12,
          color: '#d48806',
          display: 'flex',
          alignItems: 'center',
          gap: 6,
        }}>
          <WarningOutlined />
          <span><strong>尚未选择应用</strong>，若添加 DB / Redis 函数节点会提示必须先选择 App。</span>
        </div>
      )}
      {onOpenMeta && (
        <>
          <Divider style={{ margin: '10px 0' }} />
          <Button block type="primary" icon={<SettingOutlined />} onClick={onOpenMeta}>
            前往「函数集合配置」选择 / 编辑所属应用
          </Button>
        </>
      )}
    </div>
  );

  const renderAppTag = () => {
    if (appsLoading) {
      return (
        <Tag icon={<AppstoreOutlined />} style={{ borderRadius: 6, borderStyle: 'dashed', margin: 0 }}>
          应用加载中…
        </Tag>
      );
    }
    if (currentApp) {
      return (
        <Tag
          icon={<AppstoreOutlined />}
          color="geekblue"
          style={{ borderRadius: 6, fontWeight: 500, margin: 0 }}
        >
          App：{currentApp.appName} — {currentApp.appKey}
        </Tag>
      );
    }
    return (
      <Tag
        icon={<WarningOutlined />}
        color="warning"
        style={{ borderRadius: 6, fontWeight: 500, margin: 0, borderStyle: 'solid' }}
      >
        未选择应用（数据源未绑定）
      </Tag>
    );
  };

  return (
    <div
      className="fluxion-designer-toolbar"
      style={{
        padding: '10px 20px',
        borderBottom: '1px solid var(--fluxion-border)',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        background: 'var(--fluxion-bg-elevated)',
      }}
    >
      <Space size={12}>
        <Button
          icon={<ArrowLeftOutlined />}
          onClick={() => history.push('/workflow')}
          style={{
            borderRadius: 8,
            border: '1px solid var(--fluxion-border)',
          }}
        >
          返回
        </Button>
        <Input
          value={workflowMeta.name}
          onChange={(e) => setWorkflowMeta({ name: e.target.value })}
          style={{
            width: 240,
            borderRadius: 8,
            border: '1px solid var(--fluxion-border)',
          }}
          placeholder="函数集合 / 工作流名称"
        />
        {onOpenMeta && (
          <Popover
            content={renderAppPopoverContent()}
            title="所属应用（数据源绑定维度）"
            trigger="hover"
            placement="bottomLeft"
            overlayInnerStyle={{ padding: 12 }}
          >
            <Button
              onClick={onOpenMeta}
              style={{ borderRadius: 8, border: currentApp ? '1px solid #bae0ff' : '1px dashed #faad14', background: currentApp ? 'rgba(99,102,241,0.06)' : 'transparent' }}
            >
              {renderAppTag()}
            </Button>
          </Popover>
        )}
        <Popover
          content={renderTriggerPopoverContent()}
          title="触发器配置摘要（点击打开编辑面板）"
          trigger="hover"
          placement="bottomLeft"
          overlayInnerStyle={{ padding: 12 }}
        >
          <Button
            onClick={onOpenTrigger}
            style={{ borderRadius: 8, border: '1px dashed var(--fluxion-primary)', color: 'var(--fluxion-primary)' }}
            icon={<ThunderboltOutlined />}
          >
            {resolveTriggerSummary(triggers, metaList)}
          </Button>
        </Popover>
        {onOpenMeta && (
          <Button
            icon={<SettingOutlined />}
            onClick={onOpenMeta}
            style={{ borderRadius: 8, border: '1px solid var(--fluxion-border)' }}
          >
            函数集合配置
          </Button>
        )}
      </Space>
      <Space size={8}>
        {/* 撤销/重做 */}
        <Tooltip title="撤销 (Ctrl+Z)">
          <Button
            icon={<UndoOutlined />}
            disabled={!canUndo()}
            onClick={undo}
            size="small"
            style={{ borderRadius: 6 }}
          />
        </Tooltip>
        <Tooltip title="重做 (Ctrl+Shift+Z)">
          <Button
            icon={<RedoOutlined />}
            disabled={!canRedo()}
            onClick={redo}
            size="small"
            style={{ borderRadius: 6 }}
          />
        </Tooltip>
        <Divider type="vertical" style={{ height: 24, margin: '0 4px' }} />
        {/* 布局方向切换 */}
        <Tooltip title="整理画布布局">
          <Segmented
            value={layoutDirection}
            onChange={(v) => onLayoutChange(v as 'TB' | 'LR')}
            options={[
              { value: 'TB', label: '竖向' },
              { value: 'LR', label: '横向' },
            ]}
            size="small"
          />
        </Tooltip>
        <Divider type="vertical" style={{ height: 24, margin: '0 4px' }} />
        <Button
          icon={<BugOutlined />}
          type={debugActive ? 'primary' : 'default'}
          onClick={onDebug}
          style={{
            borderRadius: 8,
            ...(debugActive ? { background: 'var(--fluxion-primary)', borderColor: 'var(--fluxion-primary)' } : {}),
          }}
        >
          调试
        </Button>
        <Button
          icon={<SaveOutlined />}
          type="primary"
          onClick={onSave}
          style={{
            borderRadius: 8,
            background: 'linear-gradient(135deg, var(--fluxion-primary), var(--fluxion-primary-hover))',
            border: 'none',
            boxShadow: '0 2px 8px rgba(99, 102, 241, 0.3)',
          }}
        >
          保存
        </Button>
      </Space>
    </div>
  );
};

export default Toolbar;

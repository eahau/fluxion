import { Button, Space, Input, Select, Tooltip, Divider, Segmented } from 'antd';
import {
  SaveOutlined,
  BugOutlined,
  ArrowLeftOutlined,
  SettingOutlined,
  UndoOutlined,
  RedoOutlined,
} from '@ant-design/icons';
import { history } from '@umijs/max';
import { useWorkflowStore } from '@/stores/useWorkflowStore';
import { getProtocolConfig } from '@/constants/protocol';

interface ToolbarProps {
  title: string;
  onSave: () => void;
  onDebug: () => void;
  debugActive: boolean;
  onOpenMeta: () => void;
  layoutDirection: 'TB' | 'LR';
  onLayoutChange: (dir: 'TB' | 'LR') => void;
  darkMode?: boolean;
}

const Toolbar: React.FC<ToolbarProps> = ({ title, onSave, onDebug, debugActive, onOpenMeta, layoutDirection, onLayoutChange, darkMode }) => {
  const { workflowMeta, setWorkflowMeta, canUndo, canRedo, undo, redo } = useWorkflowStore();
  const protocolCfg = getProtocolConfig(workflowMeta.protocol);

  /** 渲染协议方法控件：有预定义选项 → Select；null → Input（Dubbo 自由文本） */
  const renderMethodControl = () => {
    if (!protocolCfg.methodVisible) return null;

    // 有预定义选项：HTTP / gRPC → Select 下拉
    if (protocolCfg.methodOptions) {
      return (
        <Select
          value={workflowMeta.method}
          onChange={(v) => setWorkflowMeta({ method: v })}
          style={{ width: protocolCfg.methodOptions.some((o) => o.label.length > 6) ? 160 : 120 }}
          popupMatchSelectWidth={false}
          placeholder="方法"
          options={protocolCfg.methodOptions.map((opt) => ({
            value: opt.value,
            label: <span style={{ color: opt.color, fontWeight: 600 }}>{opt.label}</span>,
          }))}
        />
      );
    }

    // 无预定义选项：Dubbo → 自由文本 Input
    return (
      <Input
        value={workflowMeta.method || ''}
        onChange={(e) => setWorkflowMeta({ method: e.target.value || undefined })}
        style={{
          width: 140,
          borderRadius: 8,
          border: '1px solid var(--fluxion-border)',
          fontFamily: '"SF Mono", Monaco, monospace',
          fontSize: 13,
        }}
        placeholder="方法名 (如 getUser)"
      />
    );
  };

  return (
    <div className="fluxion-designer-toolbar" style={{ 
      padding: '10px 20px', 
      borderBottom: '1px solid var(--fluxion-border)', 
      display: 'flex', 
      justifyContent: 'space-between', 
      alignItems: 'center',
      background: 'var(--fluxion-bg-elevated)',
    }}>
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
          placeholder="工作流名称"
        />
        {renderMethodControl()}
        <Input
          value={workflowMeta.path}
          onChange={(e) => setWorkflowMeta({ path: e.target.value })}
          style={{ 
            width: 280,
            borderRadius: 8,
            border: '1px solid var(--fluxion-border)',
            fontFamily: '"SF Mono", Monaco, monospace',
            fontSize: 13,
          }}
          placeholder={protocolCfg.bindPlaceholder}
        />
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
          icon={<SettingOutlined />} 
          onClick={onOpenMeta}
          style={{ borderRadius: 8 }}
        >
          配置
        </Button>
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

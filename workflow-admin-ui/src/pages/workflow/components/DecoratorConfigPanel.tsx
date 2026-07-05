import { useEffect, useMemo, useState } from 'react';
import { Alert, Checkbox, Collapse, Form, Input, InputNumber, Select, Space, Spin, Switch } from 'antd';
import { listDecorators } from '@/services/decorator';
import type { DecoratorDefinition } from '@/types/api';

interface Props {
  nodeId: string;
  decorators: string[];
  decoratorParams: Record<string, any>;
  onChange: (decorators: string[], params: Record<string, any>) => void;
}

interface ParamField {
  name: string;
  label: string;
  type: string;
  default?: any;
  minimum?: number;
  options?: string[];
}

const DecoratorConfigPanel: React.FC<Props> = ({ decorators, decoratorParams, onChange }) => {
  const [definitions, setDefinitions] = useState<DecoratorDefinition[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setLoading(true);
    listDecorators({ silent: true })
      .then(setDefinitions)
      .catch((err) => {
        console.error('Failed to load decorators', err);
      })
      .finally(() => setLoading(false));
  }, []);

  const { decoratorMap, groups, mutexMap } = useMemo(() => {
    const map = new Map<string, DecoratorDefinition>();
    definitions.forEach((d) => d.name && map.set(d.name, d));

    const groupSet = new Set<string>();
    definitions.forEach((d) => groupSet.add(d.group || '其他'));

    const mutex = new Map<string, string[]>();
    // TODO: 若后端支持互斥配置，可在此解析；目前按无互斥处理
    return { decoratorMap: map, groups: Array.from(groupSet), mutexMap: mutex };
  }, [definitions]);

  const paramFields = useMemo(() => {
    const map = new Map<string, ParamField[]>();
    definitions.forEach((def) => {
      if (!def.name) return;
      const schema = def.paramSchema as Record<string, any> | undefined;
      const properties = schema?.properties as Record<string, any> | undefined;
      if (!properties) {
        map.set(def.name, []);
        return;
      }
      const fields: ParamField[] = Object.entries(properties).map(([name, prop]: [string, any]) => ({
        name,
        label: prop.title || prop.description || name,
        type: prop.type || 'string',
        default: prop.default,
        minimum: prop.minimum,
        options: prop.enum,
      }));
      map.set(def.name, fields);
    });
    return map;
  }, [definitions]);

  const conflicts = useMemo(() => {
    const set = new Set<string>();
    decorators.forEach((name) => {
      mutexMap.get(name)?.forEach((m) => {
        if (decorators.includes(m)) {
          set.add(`${name} 与 ${m} 互斥`);
        }
      });
    });
    return Array.from(set);
  }, [decorators, mutexMap]);

  const toggleDecorator = (def: DecoratorDefinition, checked: boolean) => {
    const name = def.name;
    let next = checked ? [...decorators, name] : decorators.filter((d) => d !== name);
    if (checked) {
      // 填入默认值
      const defaults: Record<string, any> = {};
      paramFields.get(name)?.forEach((p) => {
        if (p.default !== undefined && decoratorParams[name]?.[p.name] === undefined) {
          defaults[p.name] = p.default;
        }
      });
      if (Object.keys(defaults).length > 0) {
        onChange(next, {
          ...decoratorParams,
          [name]: { ...(decoratorParams[name] || {}), ...defaults },
        });
        return;
      }
    }
    onChange(next, decoratorParams);
  };

  const updateParam = (decorator: string, param: string, value: any) => {
    onChange(decorators, {
      ...decoratorParams,
      [decorator]: { ...(decoratorParams[decorator] || {}), [param]: value },
    });
  };

  const renderParamInput = (defName: string, field: ParamField) => {
    const value = decoratorParams[defName]?.[field.name] ?? field.default;

    if (field.options && field.options.length > 0) {
      return (
        <Select
          value={value}
          onChange={(v) => updateParam(defName, field.name, v)}
          options={field.options.map((o) => ({ value: o, label: o }))}
          style={{ width: '100%' }}
        />
      );
    }

    switch (field.type) {
      case 'number':
      case 'integer':
        return (
          <InputNumber
            value={value}
            onChange={(v) => updateParam(defName, field.name, v)}
            style={{ width: '100%' }}
            min={field.minimum}
            step={field.type === 'integer' ? 1 : 0.1}
          />
        );
      case 'boolean':
        return <Switch checked={!!value} onChange={(v) => updateParam(defName, field.name, v)} />;
      case 'string':
      default:
        return (
          <Input
            value={value}
            onChange={(e) => updateParam(defName, field.name, e.target.value)}
            placeholder={`请输入 ${field.label}`}
          />
        );
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 24 }}>
        <Spin tip="加载装饰器列表..." />
      </div>
    );
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }}>
      {conflicts.length > 0 && (
        <Alert type="warning" message="存在互斥装饰器" description={conflicts.join('；')} showIcon />
      )}
      {groups.length === 0 && <Alert type="info" message="当前后端未注册任何装饰器" showIcon />}
      {groups.map((group) => (
        <Collapse key={group} defaultActiveKey={[group]} ghost>
          <Collapse.Panel header={group} key={group}>
            <Space direction="vertical" style={{ width: '100%' }}>
              {definitions
                .filter((d) => (d.group || '其他') === group)
                .map((def) => {
                  const checked = decorators.includes(def.name);
                  return (
                    <div key={def.name} style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 8 }}>
                      <Checkbox checked={checked} onChange={(e) => toggleDecorator(def, e.target.checked)}>
                        {def.label || def.name}
                      </Checkbox>
                      {checked && (
                        <>
                          {def.description && (
                            <div style={{ color: '#888', fontSize: 12, marginTop: 4 }}>{def.description}</div>
                          )}
                          {paramFields.get(def.name)?.length > 0 && (
                            <Form layout="vertical" size="small" style={{ marginTop: 6 }}>
                              {paramFields.get(def.name)!.map((field) => (
                                <Form.Item key={field.name} label={field.label} style={{ marginBottom: 6 }}>
                                  {renderParamInput(def.name, field)}
                                </Form.Item>
                              ))}
                            </Form>
                          )}
                        </>
                      )}
                    </div>
                  );
                })}
            </Space>
          </Collapse.Panel>
        </Collapse>
      ))}
    </Space>
  );
};

export default DecoratorConfigPanel;

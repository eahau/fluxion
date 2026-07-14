// features/function-test/ui/FunctionTestPanel.tsx — Dispatcher
//
// 本文件是函数测试的统一入口（Dispatcher / Facade），不再承载任何业务逻辑。
// 职责：根据 functionId / domain / paramSchema 特征，把渲染分发给对应的垂直切片 feature：
//   - condition-function-test：builtin:filter / builtin:conditionBranch
//   - db-function-test：domain=db 或含 sql 字段
//   - redis-function-test：含 redis 命令 Schema
//   - generic-function-test：兜底（Script、External HTTP、其他自定义）
//
// 依赖约束：这里允许 import 其他 feature（因为 Dispatcher 本质上就是跨 feature 的组合层），
// 但它不实现任何业务逻辑，仅做"类型判断 → 组件分发"。
// —— 新增的其他函数测试种类请新建独立 feature 并在此加入分发规则。
//
// 容错保障：所有子 feature 用 React.lazy 做动态 import，
// 单个子 feature 模块初始化 throw / No matching export / 语法错，只影响它自己，
// 不会把整个 editor 路由 chunk 一起挂死（避免"函数详情一直转圈"）。

import React, { Suspense, useMemo, useState, useEffect } from 'react';
import { Alert, Select, Spin, Empty, Tag, Space, Typography, message } from 'antd';
import type { FunctionDefinition } from '@/types/function';
import { isConditionFunction } from '@/utils/conditionSchema';
import { hasSqlField, isRedisCommandSchema, parseSchema } from '@/shared/lib/json-schema';
import ComponentErrorBoundary from '@/components/error-boundaries/ComponentErrorBoundary';
import { useRequest } from '@umijs/max';
import { listApps } from '@/services/apps';
import type { App } from '@/services/apps';

const LazyDbTestPanel = React.lazy(
  () => import('@/features/db-function-test/ui/DbTestPanel'),
);
const LazyRedisTestPanel = React.lazy(
  () => import('@/features/redis-function-test/ui/RedisTestPanel'),
);
const LazyConditionTestPanel = React.lazy(
  () => import('@/features/condition-function-test/ui/ConditionTestPanel'),
);
const LazyGenericTestPanel = React.lazy(
  () => import('@/features/generic-function-test/ui/GenericTestPanel'),
);

/**
 * 将 listApps 返回的各种可能结构统一归一化为 App[]。
 *
 * 经验教训（#642630）：不同调用链 / SDK 版本 / 后端历史兼容包装可能导致
 * 响应不是裸数组，而是被包装在 { list } / { data } / { items } / { records }
 * 或分页结构 { content, list: [...] }。此处做兜底归一化，避免渲染空。
 */
function normalizeAppList(raw: any): App[] {
  if (raw == null) return [];
  if (Array.isArray(raw)) return raw as App[];
  if (typeof raw !== 'object') return [];

  const candidates: Array<keyof typeof raw> = [
    'list', 'data', 'items', 'records', 'rows',
    'content', 'result', 'apps', 'appList',
  ];
  for (const k of candidates) {
    const v = raw[k];
    if (Array.isArray(v)) return v as App[];
  }
  // 兜底：如果对象本身就是单个 App（理论不会发生，兼容）
  if ((raw as App).id != null && (raw as App).appKey != null) {
    return [raw as App];
  }
  console.warn('[FunctionTestPanel] normalizeAppList 无法识别结构，返回空：', raw);
  return [];
}

export interface FunctionTestPanelProps {
  functionId: string;
  functionDefinition?: FunctionDefinition | null;
  mode?: 'function' | 'node';
  /** 节点测试时，若入参 schema 已由上游确定，传入后条件测试面板将锁定选择 */
  fixedInputSchema?: Record<string, any>;
  onExecuteTest?: (payload: { inputs: any; appId?: number | string | null; appKey?: string | null }) => Promise<any>;
  /** 节点当前配置的参数（如 filter 的 rules），用于预填充测试面板 */
  nodeParams?: Record<string, any>;
  /** paramValidate 的锁定校验 Schema（只读展示），测试输入基于此 schema 生成 */
  lockedSchema?: Record<string, any>;
  /** 外部传入的 appGroup（如从 workflow 上下文获取），作为默认值 */
  defaultAppGroup?: string | null;
}

type TestKind = 'condition' | 'db' | 'redis' | 'generic';

const DATA_SOURCE_PREFIXES = [
  'db:', 'dbExecute', 'dbQuery', 'dbUpdate', 'builtin:db',
  'mysql:', 'postgres:', 'postgresql:', 'oracle:', 'sqlserver:', 'clickhouse:', 'h2:', 'jdbc:',
  'redis:', 'builtin:redis', 'redisson:', 'lettuce:',
  'kafka:', 'builtin:kafka', 'rabbitmq:', 'rocketmq:', 'mq:', 'builtin:mq',
  'dubbo:', 'grpc:', 'builtin:dubbo', 'builtin:grpc',
];

function resolveKind(
  functionId: string,
  functionDefinition: FunctionDefinition | null | undefined,
): TestKind {
  if (isConditionFunction(functionId)) return 'condition';

  const rawId = functionId?.startsWith('builtin:') ? functionId.slice('builtin:'.length) : functionId;

  if (rawId === 'dbExecute' || rawId.startsWith('db')) return 'db';
  if (rawId === 'httpCall' || rawId === 'httpRequest' || rawId.startsWith('http')) return 'generic';
  if (rawId === 'redisCommand' || rawId === 'redis' || rawId.startsWith('redis')) return 'redis';

  const rawConfig = (functionDefinition?.config ?? {}) as Record<string, any>;
  const domain = (rawConfig.domain ?? rawConfig.category) as string | undefined;
  const rawParamSchema = rawConfig.paramSchema ?? rawConfig.inputSchema;
  const paramSchema = parseSchema(rawParamSchema) as Record<string, any> | null;

  if (domain === 'db' || hasSqlField(paramSchema as any)) return 'db';
  if (domain === 'redis' || isRedisCommandSchema(paramSchema as any)) return 'redis';
  return 'generic';
}

/** 判断函数是否依赖数据源（DB/Redis/MQ/Dubbo/gRPC 等），需要先选 App 才能测试 */
function isDataSourceDependent(
  functionId: string,
  functionDefinition: FunctionDefinition | null | undefined,
  kind: TestKind,
): boolean {
  if (kind === 'db' || kind === 'redis') return true;

  const rawId = functionId?.toLowerCase() ?? '';
  if (DATA_SOURCE_PREFIXES.some((p) => rawId.startsWith(p.toLowerCase()))) return true;

  const rawConfig = (functionDefinition?.config ?? {}) as Record<string, any>;
  const domain = (rawConfig.domain ?? rawConfig.category) as string | undefined;
  if (domain === 'db' || domain === 'redis' || domain === 'mq' || domain === 'kafka'
    || domain === 'rabbitmq' || domain === 'rocketmq' || domain === 'dubbo' || domain === 'grpc') {
    return true;
  }
  const hasResourceRef = rawConfig.resourceRef != null || rawConfig.resourceName != null;
  return hasResourceRef;
}

function withBoundaryAndSuspense(
  componentName: string,
  Comp: React.LazyExoticComponent<React.ComponentType<any>>,
  props: any,
): React.ReactElement {
  return (
    <ComponentErrorBoundary componentName={componentName}>
      <Suspense
        fallback={
          <div style={{ minHeight: 180, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Spin />
          </div>
        }
      >
        <Comp {...props} />
      </Suspense>
    </ComponentErrorBoundary>
  );
}

const FunctionTestPanelInner: React.FC<FunctionTestPanelProps> = ({
  functionId,
  functionDefinition,
  fixedInputSchema,
  onExecuteTest,
  nodeParams,
  lockedSchema,
  defaultAppGroup,
}) => {
  const kind = useMemo<TestKind>(
    () => resolveKind(functionId, functionDefinition),
    [functionId, functionDefinition],
  );
  const needsApp = useMemo<boolean>(
    () => isDataSourceDependent(functionId, functionDefinition, kind),
    [functionId, functionDefinition, kind],
  );

  const { data, loading: appsLoading, error: appsError } = useRequest(
    () => listApps(),
    {
      formatResult: (res) => normalizeAppList(res),
      refreshDeps: [needsApp],
    },
  );
  const appList = useMemo<App[]>(() => (data as App[]) ?? [], [data]);

  useEffect(() => {
    if (appsError) {
      console.error('[FunctionTestPanel] 加载应用列表失败：', appsError);
      message.error('加载应用列表失败，请刷新页面重试');
    }
  }, [appsError]);

  const appOptions = useMemo(() => {
    return appList
      .filter((a) => {
        if (a == null) return false;
        if (!a.status) return true;
        const s = String(a.status).toUpperCase();
        if (s === 'DISABLED' || s === 'INACTIVE' || s === '0') return false;
        return true;
      })
      .map((a) => ({
        value: a.appKey,
        label: `${a.appName} — ${a.appKey}${a.owner ? `（负责人：${a.owner}）` : ''}`,
      }));
  }, [appList]);

  const [selectedAppKey, setSelectedAppKey] = useState<string | undefined | null>(defaultAppGroup ?? null);

  useEffect(() => {
    if (defaultAppGroup && !selectedAppKey) {
      setSelectedAppKey(defaultAppGroup);
    }
  }, [defaultAppGroup]);

  const selectedAppInfo = useMemo(
    () => appList.find((a) => a.appKey === selectedAppKey),
    [appList, selectedAppKey],
  );

  const canExecute = !needsApp || (needsApp && !!selectedAppKey);

  const wrappedOnExecuteTest = useMemo(() => {
    if (!onExecuteTest) return undefined;
    return async (payload: { inputs: any }) => {
      if (needsApp && !selectedAppKey) {
        throw new Error('请先选择所属应用（数据源与应用绑定）');
      }
      return onExecuteTest({
        ...payload,
        appId: selectedAppInfo?.id ?? null,
        appKey: selectedAppKey ?? null,
      });
    };
  }, [onExecuteTest, needsApp, selectedAppKey, selectedAppInfo]);

  const sharedProps: any = {
    functionId,
    functionDefinition,
    fixedInputSchema,
    onExecuteTest: wrappedOnExecuteTest,
    nodeParams,
    lockedSchema,
    appId: selectedAppInfo?.id,
    appGroup: selectedAppKey,
    _canExecute: canExecute,
  };

  const renderAppRequirementBanner = () => {
    if (!needsApp) return null;
    return (
      <div style={{ marginBottom: 12 }}>
        <Alert
          type={canExecute ? 'success' : 'warning'}
          showIcon
          style={{ borderRadius: 8, marginBottom: 8 }}
          message={
            <Space size={[8, 8]} style={{ width: '100%', flexWrap: 'wrap' }}>
              <span>
                <strong>数据源依赖函数检测：</strong>执行测试需要先选择所属应用（DB / Redis / MQ 等资源与应用绑定）
              </span>
              <Select
                loading={appsLoading}
                showSearch
                allowClear
                style={{ minWidth: 320 }}
                placeholder={
                  appsLoading
                    ? '加载应用列表中…'
                    : appOptions.length === 0
                    ? '尚未创建应用，请先前往应用管理创建'
                    : '请选择所属应用（数据源绑定维度）'
                }
                value={selectedAppKey}
                onChange={(v) => setSelectedAppKey(v ?? null)}
                options={appOptions}
                filterOption={(input, option) =>
                  (option?.label ?? '').toString().toLowerCase().includes(input.toLowerCase())
                }
                notFoundContent={
                  appsLoading ? <Spin size="small" /> : (
                    <Empty
                      description={<span>暂无应用，请先前往 <a href="#/app" target="_blank" rel="noreferrer">应用管理</a> 创建</span>}
                      image={Empty.PRESENTED_IMAGE_SIMPLE}
                    />
                  )
                }
              />
              {selectedAppInfo && (
                <Tag color="geekblue" style={{ margin: 0 }}>
                  当前应用：{selectedAppInfo.appName}（{selectedAppInfo.appKey}）
                </Tag>
              )}
              {!canExecute && (
                <Typography.Text type="warning" style={{ fontSize: 12 }}>
                  ⚠️ 未选择应用，测试按钮已禁用
                </Typography.Text>
              )}
            </Space>
          }
        />
      </div>
    );
  };

  const panel = (() => {
    switch (kind) {
      case 'condition':
        return withBoundaryAndSuspense(
          `条件/过滤函数测试（${functionId || '未命名'}）`,
          LazyConditionTestPanel,
          sharedProps,
        );
      case 'db':
        return withBoundaryAndSuspense(
          `DB 函数测试（${functionId || '未命名'}）`,
          LazyDbTestPanel,
          sharedProps,
        );
      case 'redis':
        return withBoundaryAndSuspense(
          `Redis 函数测试（${functionId || '未命名'}）`,
          LazyRedisTestPanel,
          sharedProps,
        );
      case 'generic':
      default:
        return withBoundaryAndSuspense(
          `通用函数测试（${functionId || '未命名'}）`,
          LazyGenericTestPanel,
          sharedProps,
        );
    }
  })();

  return (
    <div>
      {renderAppRequirementBanner()}
      {panel}
    </div>
  );
};

const FunctionTestPanel: React.FC<FunctionTestPanelProps> = (props) => (
  <ComponentErrorBoundary componentName={`函数测试面板（${props.functionId || '未命名'}）`}>
    <FunctionTestPanelInner {...props} />
  </ComponentErrorBoundary>
);

export default FunctionTestPanel;

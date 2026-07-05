export const PROTOCOL_OPTIONS = ['HTTP', 'HTTPS', 'GRPC', 'DUBBO', 'KAFKA'] as const;

export type WorkflowProtocol = (typeof PROTOCOL_OPTIONS)[number];

// ── 方法选项定义（集中管理，避免散落各处）──

export interface MethodOption {
  value: string;
  label: string;
  color: string;
}

/** 全量 HTTP 方法 */
export const HTTP_METHOD_OPTIONS: MethodOption[] = [
  { value: 'GET',     label: 'GET',     color: '#16a34a' },
  { value: 'POST',    label: 'POST',    color: '#2563eb' },
  { value: 'PUT',     label: 'PUT',     color: '#d97706' },
  { value: 'DELETE',  label: 'DELETE',  color: '#ef4444' },
  { value: 'PATCH',   label: 'PATCH',   color: '#7c3aed' },
  { value: 'HEAD',    label: 'HEAD',    color: '#0891b2' },
  { value: 'OPTIONS', label: 'OPTIONS', color: '#64748b' },
  { value: 'TRACE',   label: 'TRACE',   color: '#a16207' },
];

/** gRPC 调用类型 */
export const GRPC_METHOD_OPTIONS: MethodOption[] = [
  { value: 'UNARY',            label: 'Unary',            color: '#16a34a' },
  { value: 'SERVER_STREAMING', label: 'Server Stream',    color: '#2563eb' },
  { value: 'CLIENT_STREAMING', label: 'Client Stream',    color: '#d97706' },
  { value: 'BIDI_STREAMING',   label: 'Bidi Stream',      color: '#7c3aed' },
];

/** 所有方法值 → 颜色的映射（HTTP + gRPC），供列表/详情/卡片等全局复用 */
export const METHOD_COLOR_MAP: Record<string, string> = Object.fromEntries(
  [...HTTP_METHOD_OPTIONS, ...GRPC_METHOD_OPTIONS].map((o) => [o.value, o.color]),
);

// ── 协议配置 ──

export interface ProtocolConfig {
  label: string;
  methodVisible: boolean;
  methodRequired: boolean;
  /** 预定义方法选项列表；为 null 时表示自由文本输入（如 Dubbo 方法名） */
  methodOptions: MethodOption[] | null;
  bindLabel: string;
  bindPlaceholder: string;
  bindRequired: boolean;
}

export function getProtocolConfig(protocol?: string): ProtocolConfig {
  switch (protocol) {
    case 'HTTP':
    case 'HTTPS':
      return {
        label: protocol,
        methodVisible: true,
        methodRequired: true,
        methodOptions: HTTP_METHOD_OPTIONS,
        bindLabel: '请求路径',
        bindPlaceholder: '/api/example',
        bindRequired: true,
      };
    case 'GRPC':
      return {
        label: 'gRPC',
        methodVisible: true,
        methodRequired: false,
        methodOptions: GRPC_METHOD_OPTIONS,
        bindLabel: '服务键 (serviceKey)',
        bindPlaceholder: 'com.example.UserService/GetUser',
        bindRequired: true,
      };
    case 'DUBBO':
      return {
        label: 'Dubbo',
        methodVisible: true,
        methodRequired: false,
        methodOptions: null, // Dubbo 方法名是自由文本
        bindLabel: '服务键 (serviceKey)',
        bindPlaceholder: 'com.example.UserService',
        bindRequired: true,
      };
    case 'KAFKA':
      return {
        label: 'Kafka',
        methodVisible: false,
        methodRequired: false,
        methodOptions: null,
        bindLabel: '绑定键 (bindKey)',
        bindPlaceholder: 'order.created',
        bindRequired: true,
      };
    default:
      return {
        label: protocol || '未知',
        methodVisible: false,
        methodRequired: false,
        methodOptions: null,
        bindLabel: '绑定键',
        bindPlaceholder: '请输入绑定键',
        bindRequired: false,
      };
  }
}

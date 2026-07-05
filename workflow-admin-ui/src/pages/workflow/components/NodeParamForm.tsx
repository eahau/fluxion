import { useMemo, useCallback } from 'react';
import { getLocale } from 'umi';
import Form from '@rjsf/antd';
import validator from '@rjsf/validator-ajv8';
import { NODE_PARAM_SCHEMAS } from '@/constants/nodeParamSchemas';
import JsonEditor from '@/components/JsonEditor';
import { resolveDynamicWidgets, resolveRedisCommandUiSchema } from '@/utils/schemaUiResolver';
import DbTableSelect from './widgets/DbTableSelect';
import DbColumnsSelect from './widgets/DbColumnsSelect';
import DbColumnsKeyValue from './widgets/DbColumnsKeyValue';
import DbSqlEditor from './widgets/DbSqlEditor';
import JsonCodeEditor from './widgets/JsonCodeEditor';
import FieldAutoComplete from './widgets/FieldAutoComplete';
import BindingInput from './widgets/BindingInput';
import ConditionConfigEditor from './ConditionConfigEditor';
import SearchableSelectWidget from './widgets/SearchableSelectWidget';
import JexlExpressionEditor, { ExpressionEditor } from './widgets/JexlExpressionEditor';

/**
 * 根据当前 locale 将 schema 中的 x-i18n 描述替换到 description。
 * x-i18n 结构：{ "zh": { "description": "..." }, "en": { "description": "..." } }
 * 若 locale 以匹配语言开头（如 zh-CN → zh），则用对应 description 覆盖。
 */
function resolveI18nSchema(schema: Record<string, any>, locale: string): Record<string, any> {
  if (!schema || typeof schema !== 'object') return schema;
  const lang = locale.split('-')[0];
  const result = { ...schema };
  const i18n = result['x-i18n'];
  if (i18n && typeof i18n === 'object') {
    const localized = i18n[lang] || i18n[locale];
    if (localized?.description) {
      result.description = localized.description;
    }
  }
  if (result.properties && typeof result.properties === 'object') {
    const newProps: Record<string, any> = {};
    for (const [key, prop] of Object.entries(result.properties)) {
      newProps[key] = resolveI18nSchema(prop as Record<string, any>, locale);
    }
    result.properties = newProps;
  }
  if (result.items && typeof result.items === 'object') {
    result.items = resolveI18nSchema(result.items, locale);
  }
  return result;
}

interface NodeParamFormProps {
  nodeType: string;
  functionRef?: string;
  /** 从函数定义获取的入参 Schema（优先级高于硬编码 NODE_PARAM_SCHEMAS） */
  paramSchema?: Record<string, any>;
  /** 从函数定义获取的出参 Schema（只读展示） */
  outputSchema?: Record<string, any>;
  /** 上游节点可用字段列表（用于 field 自动补全） */
  upstreamFields?: string[];
  /** 上游节点字段 → 类型映射（用于操作符按类型智能过滤） */
  upstreamFieldTypes?: Record<string, string>;
  /** 工作流入参字段列表（用于 $ref 绑定补全） */
  inputSchemaFields?: string[];
  value: any;
  onChange: (value: any) => void;
}

const UI_SCHEMA = {
  script: {
    'ui:widget': 'textarea',
    'ui:options': {
      rows: 8,
    },
  },
  statement: {
    'ui:widget': 'textarea',
  },
  expression: {
    'ui:widget': 'jexlExpression',
  },
  template: {
    'ui:widget': 'textarea',
  },
  bodyTemplate: {
    'ui:widget': 'textarea',
    'ui:options': {
      rows: 6,
    },
  },
  condition: {
    'ui:widget': 'jexlExpression',
  },
  config: {
    'ui:widget': 'jsonCode',
  },
  params: {
    'ui:widget': 'jsonCode',
  },
  where: {
    'ui:widget': 'jsonCode',
  },
  data: {
    'ui:widget': 'jsonCode',
  },
  headers: {
    'ui:widget': 'jsonCode',
  },
  // CONDITION_BRANCH 数组子项的 uiSchema
  conditions: {
    items: {
      condition: {
        'ui:widget': 'textarea',
        'ui:options': {
          rows: 2,
        },
      },
    },
  },
  // Redis 命令选择器：178 个命令必须支持搜索（@rjsf/antd 默认 SelectWidget 无 showSearch）
  command: {
    'ui:widget': 'searchableSelect',
  },
  // PIPELINE 子项的 command 也要支持搜索
  commands: {
    items: {
      command: {
        'ui:widget': 'searchableSelect',
      },
    },
  },
};

/** dbExecute 字段顺序（x-widget 已由 schema 驱动，此处仅控制展示顺序） */
const DB_EXECUTE_FIELD_ORDER = ['sql', 'params', 'dataSource', 'resultType', 'limit', 'compensateSql', 'compensateFunctionRef'];
const DB_EXECUTE_FUNCTION_REFS = new Set(['builtin:dbExecute']);

const widgets = {
  dbTableSelect: DbTableSelect,
  dbColumnsSelect: DbColumnsSelect,
  dbColumnsKeyValue: DbColumnsKeyValue,
  dbSqlEditor: DbSqlEditor,
  jsonCode: JsonCodeEditor,
  fieldAutoComplete: FieldAutoComplete,
  binding: BindingInput,
  searchableSelect: SearchableSelectWidget,
  jexlExpression: JexlExpressionEditor,
  expressionEditor: ExpressionEditor,
};

/**
 * 将本地 NODE_PARAM_SCHEMAS 中的 enum / x-widget 补全到 API schema。
 * API 返回的 paramSchema 可能缺少前端渲染所需的 enum（如下拉选项列表）或 x-widget，
 * 此函数以本地 schema 为兜底，确保关键渲染元数据不丢失。
 */
function mergeLocalSchema(apiSchema: Record<string, any>, functionRef?: string): Record<string, any> {
  if (!functionRef) return apiSchema;
  const localSchema = NODE_PARAM_SCHEMAS[functionRef];
  if (!localSchema?.properties) return apiSchema;

  const result = { ...apiSchema };
  const apiProps = { ...(result.properties || {}) };

  for (const [key, localProp] of Object.entries(localSchema.properties) as [string, any][]) {
    const apiProp = apiProps[key] as Record<string, any> | undefined;
    if (!apiProp) continue;
    // 补全 enum：API 缺失时从本地补全
    if (localProp.enum && !apiProp.enum) {
      apiProps[key] = { ...apiProp, enum: localProp.enum };
    }
    // 补全 x-widget：API 缺失时从本地补全
    if (localProp['x-widget'] && !apiProp['x-widget']) {
      apiProps[key] = { ...(apiProps[key] as Record<string, any>), 'x-widget': localProp['x-widget'] };
    }
  }

  result.properties = apiProps;
  return result;
}

const NodeParamForm: React.FC<NodeParamFormProps> = ({ nodeType, functionRef, paramSchema, outputSchema, upstreamFields, upstreamFieldTypes, inputSchemaFields, value, onChange }) => {
  const locale = getLocale();
  const schema = useMemo(() => {
    let raw: Record<string, any>;
    // 1. 优先使用函数定义中的 paramSchema（来自 API）
    if (paramSchema && paramSchema.properties) {
      raw = mergeLocalSchema(paramSchema, functionRef);
    }
    // 2. 按函数引用匹配硬编码 schema（如 builtin:dbExecute）
    else if (functionRef && NODE_PARAM_SCHEMAS[functionRef]) {
      raw = NODE_PARAM_SCHEMAS[functionRef];
    }
    // 3. 按节点类型匹配，最后降级为 CUSTOM
    else {
      raw = NODE_PARAM_SCHEMAS[nodeType] || NODE_PARAM_SCHEMAS.CUSTOM;
    }
    // 根据当前 locale 替换 x-i18n 描述
    return resolveI18nSchema(raw, locale);
  }, [nodeType, functionRef, paramSchema, locale]);

  const dynamicWidgets = useMemo(() => resolveDynamicWidgets(schema), [schema]);

  // Redis 命令动态字段显隐：raw 模式优先，否则根据 x-commands 元数据 + 当前选中的命令决定
  const redisHiddenFields = useMemo(
    () => resolveRedisCommandUiSchema(schema, value),
    [schema, value],
  );

  const uiSchema = useMemo(() => {
    const base = { ...UI_SCHEMA, ...dynamicWidgets, ...redisHiddenFields };
    if (functionRef && DB_EXECUTE_FUNCTION_REFS.has(functionRef)) {
      return {
        ...base,
        'ui:order': DB_EXECUTE_FIELD_ORDER,
      };
    }
    return base;
  }, [functionRef, dynamicWidgets, redisHiddenFields]);

  // 所有 hooks 必须在任何 early return 之前调用，否则节点类型切换时会触发
  // "Rendered more hooks than during the previous render"
  const handleFormChange = useCallback(
    (partial: Record<string, any>) => {
      onChange({ ...(value || {}), ...partial });
    },
    [onChange, value],
  );

  const handleRjsfChange = useCallback(
    (e: any) => onChange(e.formData || {}),
    [onChange],
  );

  const formContext = useMemo(
    () => ({
      ...value,
      upstreamFields: upstreamFields || [],
      upstreamFieldTypes: upstreamFieldTypes || {},
      inputSchemaFields: inputSchemaFields || [],
      onFormChange: handleFormChange,
    }),
    [value, upstreamFields, upstreamFieldTypes, inputSchemaFields, handleFormChange],
  );

  if (schema === NODE_PARAM_SCHEMAS.CUSTOM) {
    return (
      <div>
        <div style={{ marginBottom: 8, fontWeight: 500 }}>自定义参数</div>
        <JsonEditor
          value={value?.params}
          onChange={(v) => onChange({ ...value, params: v })}
          height={160}
        />
        <div style={{ marginBottom: 8, fontWeight: 500, marginTop: 16 }}>自定义配置</div>
        <JsonEditor
          value={value?.config}
          onChange={(v) => onChange({ ...value, config: v })}
          height={160}
        />
      </div>
    );
  }

  // 条件分支 / 过滤 → 使用自定义紧凑编辑器
  if (nodeType === 'CONDITION_BRANCH' || nodeType === 'FILTER') {
    return (
      <ConditionConfigEditor
        mode={nodeType === 'CONDITION_BRANCH' ? 'branch' : 'filter'}
        value={value || {}}
        onChange={onChange}
        upstreamFields={upstreamFields}
        fieldTypes={upstreamFieldTypes}
      />
    );
  }

  return (
    <div className="node-param-form">
      <Form
        schema={schema}
        uiSchema={uiSchema}
        validator={validator}
        widgets={widgets}
        formData={value || {}}
        formContext={formContext}
        onChange={handleRjsfChange}
        children={<></>}
      />
      {outputSchema && outputSchema.properties && (
        <div style={{ marginTop: 16, padding: '8px 12px', background: '#f6f8fa', borderRadius: 6, border: '1px solid #e8e8e8' }}>
          <div style={{ fontWeight: 600, marginBottom: 8, color: '#595959' }}>出参 Schema</div>
          <pre style={{ fontSize: 12, margin: 0, whiteSpace: 'pre-wrap', color: '#8c8c8c' }}>
            {JSON.stringify(outputSchema, null, 2)}
          </pre>
        </div>
      )}
    </div>
  );
};

export default NodeParamForm;

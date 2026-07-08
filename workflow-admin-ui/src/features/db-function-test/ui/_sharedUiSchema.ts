// features/db-function-test/ui/_sharedUiSchema.ts
// DB Feature 自用：RJSF 基础 UI Schema 与动态 widget 解析

import JsonCodeEditor from '@/pages/workflow/components/widgets/JsonCodeEditor';
import { resolveDynamicWidgets as baseResolve } from '@/utils/schemaUiResolver';

export const BASE_FALLBACK_UI_SCHEMA: Record<string, any> = {
  params: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  data: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  where: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  config: { 'ui:widget': 'jsonCode', 'ui:options': { autoHeight: true, minHeight: 80, maxHeight: 240 } },
  script: { 'ui:widget': 'textarea', 'ui:options': { rows: 4 } },
  raw: { 'ui:widget': 'redisRawEditor', 'ui:options': { autoHeight: true, minHeight: 60, maxHeight: 180 } },
};

export function resolveDynamicWidgets(schema: Record<string, any>): Record<string, any> {
  return baseResolve(schema);
}

// 此文件还会被 Redis/Generic feature 使用时，可直接 import 此文件
export { JsonCodeEditor };

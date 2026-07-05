/**
 * 将 JSON Schema properties 展平为点号路径列表
 * @param properties JSON Schema 的 properties 对象
 * @param prefix 当前路径前缀
 * @param result 收集结果的数组
 * @param depth 当前递归深度
 * @param maxDepth 最大递归深度（默认 3）
 */
export function flattenSchemaFields(
  properties: Record<string, any>,
  prefix: string,
  result: string[],
  depth = 0,
  maxDepth = 3,
) {
  if (depth > maxDepth) return;
  for (const [key, schema] of Object.entries(properties)) {
    const path = prefix ? `${prefix}.${key}` : key;
    result.push(path);
    if (schema?.type === 'object' && schema?.properties) {
      flattenSchemaFields(schema.properties, path, result, depth + 1, maxDepth);
    }
    // array 类型：深入 items 提取嵌套字段路径
    if (schema?.type === 'array' && schema?.items) {
      if (schema.items.type === 'object' && schema.items.properties) {
        flattenSchemaFields(schema.items.properties, path, result, depth + 1, maxDepth);
      }
    }
  }
}

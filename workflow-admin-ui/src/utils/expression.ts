/**
 * 表达式工具
 * 提供 JSONPath / SpEL 的解析、提取变量、语法高亮辅助函数。
 */

const JSONPATH_VAR_REGEX = /\$\.(?:[a-zA-Z_][a-zA-Z0-9_]*|\[\d+\]|\['[^']+'\]|\["[^"]+"\])*/g;
const SPEL_VAR_REGEX = /(?<![a-zA-Z0-9_])([a-zA-Z_][a-zA-Z0-9_]*)(?=\s*\.?)/g;

export interface ExpressionParseResult {
  type: 'jsonpath' | 'spel' | 'unknown';
  variables: string[];
}

export function detectExpressionType(expr: string): ExpressionParseResult['type'] {
  const trimmed = (expr || '').trim();
  if (trimmed.startsWith('$.') || trimmed.startsWith('$[')) return 'jsonpath';
  if (/^[\w\s+\-*/%<>=!&|():.\[\]'"]+$/.test(trimmed) && /[a-zA-Z_]/.test(trimmed)) return 'spel';
  return 'unknown';
}

export function parseExpression(expr: string): ExpressionParseResult {
  const type = detectExpressionType(expr);
  const variables: string[] = [];

  if (type === 'jsonpath') {
    const matches = expr.match(JSONPATH_VAR_REGEX) || [];
    matches.forEach((m) => {
      if (!variables.includes(m)) variables.push(m);
    });
  } else if (type === 'spel') {
    const matches = expr.match(SPEL_VAR_REGEX) || [];
    const keywords = new Set([
      'true',
      'false',
      'null',
      'and',
      'or',
      'not',
      'eq',
      'ne',
      'lt',
      'le',
      'gt',
      'ge',
      'matches',
      'contains',
      'size',
      'T',
      'new',
      'return',
      'if',
      'else',
    ]);
    matches.forEach((m) => {
      if (!keywords.has(m) && !variables.includes(m)) variables.push(m);
    });
  }

  return { type, variables };
}

export function evaluateJsonPath(context: any, path: string): any {
  if (!path || !path.startsWith('$')) return undefined;
  const tokens = path
    .slice(1)
    .split(/\.|\[(\d+)\]|\['([^']+)'\]|\["([^"]+)"\]/)
    .filter(Boolean);

  let current = context;
  for (const token of tokens) {
    if (current == null) return undefined;
    current = current[token];
  }
  return current;
}

export function extractExpressionsFromObject(obj: any): string[] {
  const results: string[] = [];
  if (typeof obj === 'string') {
    const type = detectExpressionType(obj);
    if (type !== 'unknown') results.push(obj);
  } else if (Array.isArray(obj)) {
    obj.forEach((item) => results.push(...extractExpressionsFromObject(item)));
  } else if (obj && typeof obj === 'object') {
    Object.values(obj).forEach((v) => results.push(...extractExpressionsFromObject(v)));
  }
  return results;
}

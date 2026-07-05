import { useEffect, useMemo, useRef, useState, useCallback } from 'react';
import { Collapse, Spin, Table, Tag, Space, Typography, message, Button, Card, List, Popconfirm, Empty, Tooltip, Select } from 'antd';
import { useClickDebounce } from '@/utils/useClickDebounce';
import { PlayCircleOutlined, HistoryOutlined, DeleteOutlined, ClearOutlined, ThunderboltOutlined } from '@ant-design/icons';
import CodeMirror from '@uiw/react-codemirror';
import { sql } from '@codemirror/lang-sql';
import { EditorView } from '@codemirror/view';
import type { WidgetProps } from '@rjsf/utils';
import { getTablesWithColumns, previewSql } from '@/services/schema';
import { testFunction } from '@/services/function';
import JsonEditor from '@/components/JsonEditor';
import {
  getSqlHistory,
  saveSqlHistory,
  removeSqlHistory,
  clearSqlHistory,
  formatHistoryTime,
  type SqlHistoryItem,
} from '@/utils/sqlHistory';

interface ColumnInfo {
  name: string;
  dataType?: string;
  nullable: boolean;
  autoIncrement: boolean;
  comment?: string;
}

/** camelCase → snake_case（如 userId → user_id） */
function camelToSnake(str: string): string {
  return str.replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`);
}

type SqlOpType = 'SELECT' | 'INSERT' | 'UPDATE' | 'DELETE';

/**
 * 智能匹配上游字段与表列名。
 * 返回 Map<表列名, { upstreamField, paramName }>，paramName 为 SQL 中的 :paramName。
 */
function matchUpstreamToColumns(
  upstreamFields: string[],
  columns: ColumnInfo[],
): Map<string, { upstreamField: string; paramName: string }> {
  const matches = new Map<string, { upstreamField: string; paramName: string }>();
  const colSet = new Set(columns.map((c) => c.name));

  for (const field of upstreamFields) {
    // 1. 完全匹配（如 status === status）
    if (colSet.has(field)) {
      matches.set(field, { upstreamField: field, paramName: field });
      continue;
    }
    // 2. camelCase → snake_case（如 userId → user_id）
    const snake = camelToSnake(field);
    if (colSet.has(snake)) {
      matches.set(snake, { upstreamField: field, paramName: field });
      continue;
    }
    // 3. 后缀匹配（如上游 userId，列 id）
    const fieldLower = field.toLowerCase();
    for (const col of colSet) {
      if (matches.has(col)) continue;
      if (fieldLower.endsWith(col.toLowerCase()) && col.length >= 2) {
        matches.set(col, { upstreamField: field, paramName: field });
        break;
      }
    }
  }
  return matches;
}

/**
 * 根据表名、列信息、上游匹配参数和操作类型生成 SQL 模板。
 */
function generateSqlTemplate(
  op: SqlOpType,
  tableName: string,
  columns: ColumnInfo[],
  matched: Map<string, { upstreamField: string; paramName: string }>,
): string {
  const pk = columns.find((c) => !c.nullable && (c.autoIncrement || c.name === 'id'));
  const whereKey = pk ? pk.name : columns[0]?.name;

  switch (op) {
    case 'SELECT': {
      if (matched.size > 0) {
        const where = Array.from(matched.entries())
          .map(([col, m]) => `${col} = :${m.paramName}`)
          .join(' AND ');
        return `SELECT * FROM ${tableName} WHERE ${where}`;
      }
      return `SELECT * FROM ${tableName}`;
    }
    case 'INSERT': {
      const cols = columns.filter((c) => !c.autoIncrement);
      const names = cols.map((c) => c.name).join(', ');
      const params = cols.map((c) => {
        const m = matched.get(c.name);
        return m ? `:${m.paramName}` : `:${c.name}`;
      }).join(', ');
      return `INSERT INTO ${tableName} (${names}) VALUES (${params})`;
    }
    case 'UPDATE': {
      const setCols = columns.filter((c) => !c.autoIncrement && !pk?.name?.includes(c.name));
      const setClause = setCols.map((c) => {
        const m = matched.get(c.name);
        return `${c.name} = ${m ? `:${m.paramName}` : `:${c.name}`}`;
      }).join(', ');
      const whereCol = whereKey || 'id';
      const whereParam = matched.get(whereCol)?.paramName || whereCol;
      return `UPDATE ${tableName} SET ${setClause || 'col = :col'} WHERE ${whereCol} = :${whereParam}`;
    }
    case 'DELETE': {
      const whereCol2 = whereKey || 'id';
      const whereParam2 = matched.get(whereCol2)?.paramName || whereCol2;
      return `DELETE FROM ${tableName} WHERE ${whereCol2} = :${whereParam2}`;
    }
    default:
      return '';
  }
}

/**
 * 手写 SQL 编辑器 Widget（用于 @rjsf）。
 * 基于 CodeMirror 6 提供 SQL 语法高亮、表/列自动补全，
 * 附带当前数据库表结构参考面板，以及 SQL 执行预览功能。
 *
 * 函数测试模式下（通过 ui:options.functionId 指定），预览会调用对应函数的 test 接口，
 * 从而真实执行 builtin:dbExecute 等函数逻辑，而非直接走 sql-preview。
 */
const MIN_LINES = 3;
const MAX_LINES = 25;
const LINE_HEIGHT = 18;

const DbSqlEditor: React.FC<WidgetProps> = ({ value, onChange, disabled, readonly, formContext, options }) => {
  const sqlValue = typeof value === 'string' ? value : '';
  const editorViewRef = useRef<EditorView | null>(null);
  const [tables, setTables] = useState<string[]>([]);
  const [tablesLoading, setTablesLoading] = useState(false);
  const [columnsMap, setColumnsMap] = useState<Record<string, ColumnInfo[]>>({});
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewColumns, setPreviewColumns] = useState<string[]>([]);
  const [previewRows, setPreviewRows] = useState<Record<string, any>[]>([]);
  const [functionTestResult, setFunctionTestResult] = useState<any>(null);
  const [sqlHistory, setSqlHistory] = useState<SqlHistoryItem[]>([]);
  const [showHistory, setShowHistory] = useState(false);
  // ── SQL 模板生成器状态 ──
  const [showGenerator, setShowGenerator] = useState(false);
  const [genTable, setGenTable] = useState<string | undefined>(undefined);
  const [genOp, setGenOp] = useState<SqlOpType>('SELECT');

  const functionId = (options as any)?.functionId as string | undefined;
  const allParams = (formContext as any)?.params as Record<string, any> | undefined;
  const dataSource = (formContext as any)?.dataSource as string | undefined;
  const upstreamFields: string[] = (formContext as any)?.upstreamFields || [];
  const isFunctionTestMode = !!functionId;
  // 紧凑模式：隐藏执行按钮、历史、生成器、表结构参考与结果面板，仅保留编辑器
  const compactMode = (options as any)?.compact === true;

  // 将已加载的表结构转换为 CodeMirror SQL 自动补全 schema
  const sqlSchema = useMemo(() => {
    const schema: Record<string, string[]> = {};
    tables.forEach((tableName) => {
      schema[tableName] = (columnsMap[tableName] || []).map((c) => c.name);
    });
    return schema;
  }, [tables, columnsMap]);

  const [darkMode, setDarkMode] = useState(false);

  useEffect(() => {
    setDarkMode(localStorage.getItem('fluxion-dark-mode') === 'true');
  }, []);

  const heightOption = (options as any)?.height;
  const minHeightOption = (options as any)?.minHeight ?? 80;
  const maxHeightOption = (options as any)?.maxHeight ?? 450;
  const autoHeight = (options as any)?.autoHeight ?? false;

  const editorHeight = useMemo(() => {
    if (!autoHeight) return heightOption ?? '160px';
    const lines = sqlValue.split('\n').length;
    const desired = Math.max(MIN_LINES, Math.min(lines, MAX_LINES)) * LINE_HEIGHT + 16;
    const min = typeof minHeightOption === 'number' ? minHeightOption : parseInt(minHeightOption, 10) || 80;
    const max = typeof maxHeightOption === 'number' ? maxHeightOption : parseInt(maxHeightOption, 10) || 450;
    return `${Math.min(Math.max(desired, min), max)}px`;
  }, [autoHeight, heightOption, minHeightOption, maxHeightOption, sqlValue]);

  const extensions = useMemo(
    () => [
      sql({ schema: sqlSchema, upperCaseKeywords: true }),
      EditorView.editable.of(!disabled && !readonly),
      darkMode ? EditorView.theme({ '&': { backgroundColor: '#1e293b' } }) : [],
    ],
    [sqlSchema, disabled, readonly, darkMode],
  );

  useEffect(() => {
    setTablesLoading(true);
    getTablesWithColumns(dataSource, { silent: true })
      .then((tablesWithColumns) => {
        const tableNames = tablesWithColumns.map((t) => t.name);
        setTables(tableNames);
        const map: Record<string, ColumnInfo[]> = {};
        tablesWithColumns.forEach((t) => {
          map[t.name] = t.columns.map((c) => ({
            name: c.name,
            dataType: c.dataType,
            nullable: c.nullable,
            autoIncrement: c.autoIncrement,
            comment: c.comment,
          }));
        });
        setColumnsMap(map);
      })
      .catch((e) => console.error('加载表结构失败:', e))
      .finally(() => setTablesLoading(false));
  }, [dataSource]);

  // ── SQL 模板生成器：智能匹配 + 生成 SQL ──
  const genColumns = useMemo(() => (genTable ? columnsMap[genTable] || [] : []), [genTable, columnsMap]);
  const genMatched = useMemo(
    () => (genTable ? matchUpstreamToColumns(upstreamFields, genColumns) : new Map()),
    [genTable, upstreamFields, genColumns],
  );
  const genPreview = useMemo(
    () => (genTable ? generateSqlTemplate(genOp, genTable, genColumns, genMatched) : ''),
    [genTable, genOp, genColumns, genMatched],
  );

  const handleApplyGenerated = useClickDebounce(useCallback(() => {
    if (!genPreview) return;
    onChange(genPreview);
    // 自动生成 params 绑定对象：{ paramName: { $ref: upstreamField } }
    if (genMatched.size > 0 && !isFunctionTestMode) {
      const paramsObj: Record<string, any> = {};
      genMatched.forEach(({ upstreamField, paramName }) => {
        paramsObj[paramName] = { $ref: upstreamField };
      });
      // 通过 onFormChange 回写兄弟字段 params
      const onFormChange = (formContext as any)?.onFormChange as ((partial: Record<string, any>) => void) | undefined;
      if (onFormChange) {
        onFormChange({ params: paramsObj });
        message.success(
          `已生成 SQL 并自动填充 ${genMatched.size} 个参数绑定（$ref 引用）`,
          4,
        );
      } else {
        message.success(
          `已生成 SQL 并识别 ${genMatched.size} 个参数绑定：${Array.from(genMatched.keys()).join(', ')}`,
          4,
        );
      }
    } else {
      message.success('已生成 SQL 模板');
    }
    setShowGenerator(false);
  }, [genPreview, genMatched, isFunctionTestMode, onChange, formContext]));

  // 加载 SQL 历史记录
  useEffect(() => {
    setSqlHistory(getSqlHistory(functionId));
  }, [functionId]);

  const insertText = (text: string) => {
    if (disabled || readonly) return;
    const view = editorViewRef.current;
    if (!view) {
      onChange(sqlValue ? `${sqlValue} ${text}` : text);
      return;
    }
    const { state } = view;
    const selection = state.selection.main;
    const before = state.doc.toString().slice(0, selection.from);
    const after = state.doc.toString().slice(selection.to);
    const prefix = before.length > 0 && !/\s$/.test(before) ? ' ' : '';
    const suffix = after.length > 0 && /^\S/.test(after) ? ' ' : '';
    const insert = `${prefix}${text}${suffix}`;
    const nextCursor = selection.from + insert.length;
    view.dispatch({
      changes: { from: selection.from, to: selection.to, insert },
      selection: { anchor: nextCursor },
    });
    onChange(view.state.doc.toString());
    view.focus();
  };

  const handlePreview = useClickDebounce(async () => {
    if (!sqlValue.trim()) {
      message.warning('请先输入 SQL');
      return;
    }
    setPreviewLoading(true);
    setFunctionTestResult(null);
    try {
      if (isFunctionTestMode) {
        // 函数测试模式：走真实函数 test 接口
        const inputs = (formContext as any) || {};
        const res = await testFunction(functionId, inputs, { silent: true });
        setFunctionTestResult(res);
        if (res?.success) {
          message.success('函数执行成功');
          const output = res.output;
          const summary = Array.isArray(output) ? `返回 ${output.length} 行` : undefined;
          saveSqlHistory(sqlValue, 'success', summary, functionId);
        } else {
          message.error(res?.error || '函数执行失败');
          saveSqlHistory(sqlValue, 'error', res?.error, functionId);
        }
      } else {
        // 工作流设计器模式：直接 SQL 预览
        const result = await previewSql(sqlValue, allParams, dataSource);
        setPreviewColumns(result.columns);
        setPreviewRows(result.rows);
        message.success(`预览成功，返回 ${result.rows.length} 行`);
        saveSqlHistory(sqlValue, 'success', `返回 ${result.rows.length} 行`);
      }
      // 刷新历史记录
      setSqlHistory(getSqlHistory(functionId));
    } catch (err: any) {
      console.error(isFunctionTestMode ? '函数测试失败:' : 'SQL 预览失败:', err);
      message.error(err?.message || (isFunctionTestMode ? '函数测试失败' : 'SQL 预览失败'));
      saveSqlHistory(sqlValue, 'error', err?.message || '执行失败', functionId);
      setSqlHistory(getSqlHistory(functionId));
    } finally {
      setPreviewLoading(false);
    }
  });

  // 从历史记录中选择 SQL
  const handleHistorySelect = (item: SqlHistoryItem) => {
    onChange(item.sql);
    setShowHistory(false);
  };

  // 删除历史记录
  const handleHistoryRemove = (index: number) => {
    removeSqlHistory(index, functionId);
    setSqlHistory(getSqlHistory(functionId));
  };

  // 清空历史记录
  const handleHistoryClear = () => {
    clearSqlHistory(functionId);
    setSqlHistory([]);
  };

  const columnTableColumns = [
    {
      title: '列名',
      dataIndex: 'name',
      key: 'name',
      width: 120,
      render: (name: string, record: ColumnInfo & { tableName: string }) => (
        <Button type="link" size="small" style={{ padding: 0 }} onClick={() => insertText(`${record.tableName}.${name}`)}>
          {name}
        </Button>
      ),
    },
    {
      title: '类型',
      dataIndex: 'dataType',
      key: 'dataType',
      width: 120,
      render: (text: string) => {
        if (!text) return '';
        return (
          <Tooltip title={text} placement="topLeft">
            <span style={{ 
              display: 'block', 
              overflow: 'hidden', 
              textOverflow: 'ellipsis', 
              whiteSpace: 'nowrap',
              cursor: 'pointer'
            }}>
              {text}
            </span>
          </Tooltip>
        );
      },
    },
    {
      title: '属性',
      key: 'flags',
      width: 100,
      render: (_: any, record: ColumnInfo) => (
        <Space size={0}>
          {!record.nullable && <Tag color="red">NOT NULL</Tag>}
          {record.autoIncrement && <Tag color="#6366f1">AUTO</Tag>}
        </Space>
      ),
    },
    {
      title: '注释',
      dataIndex: 'comment',
      key: 'comment',
      render: (text: string) => {
        if (!text) return '';
        return (
          <Tooltip title={text} placement="topLeft">
            <span style={{ 
              display: 'block', 
              overflow: 'hidden', 
              textOverflow: 'ellipsis', 
              whiteSpace: 'nowrap',
              cursor: 'pointer',
              maxWidth: 200
            }}>
              {text}
            </span>
          </Tooltip>
        );
      },
    },
  ];

  const collapseItems = tables.map((tableName) => ({
    key: tableName,
    label: (
      <span>
        <Button type="link" size="small" style={{ padding: 0, marginRight: 8 }} onClick={(e) => { e.stopPropagation(); insertText(tableName); }}>
          {tableName}
        </Button>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          点击表名/列名插入 SQL
        </Typography.Text>
      </span>
    ),
    children: (
      <Table
        rowKey="name"
        size="small"
        columns={columnTableColumns}
        dataSource={(columnsMap[tableName] || []).map((c) => ({ ...c, tableName }))}
        pagination={false}
        locale={{ emptyText: '暂无列信息' }}
      />
    ),
  }));

  const previewTableColumns = previewColumns.map((col) => ({
    title: col,
    dataIndex: col,
    key: col,
    ellipsis: true,
    render: (value: any) => {
      const text = value != null ? String(value) : '';
      if (text.length > 50) {
        return (
          <Tooltip title={text} placement="topLeft">
            <span style={{ cursor: 'pointer' }}>{text.slice(0, 50)}...</span>
          </Tooltip>
        );
      }
      return text;
    },
  }));

  // 从 SQL 中提取表名（简单匹配 FROM/JOIN 后的表名）
  const extractTableName = (sql: string): string | null => {
    const match = sql.match(/\b(?:FROM|JOIN)\s+([a-zA-Z_][a-zA-Z0-9_]*)/i);
    return match ? match[1] : null;
  };

  // 构建字段名到描述的映射
  const columnCommentMap = useMemo(() => {
    const tableName = extractTableName(sqlValue);
    if (!tableName) return {};
    const columns = columnsMap[tableName] || [];
    const map: Record<string, string> = {};
    columns.forEach((col) => {
      if (col.comment) {
        map[col.name] = col.comment;
      }
    });
    return map;
  }, [sqlValue, columnsMap]);

  const functionTestOutputColumns = useMemo(() => {
    const output = functionTestResult?.output;
    if (Array.isArray(output) && output.length > 0 && typeof output[0] === 'object') {
      return Object.keys(output[0]).map((col) => {
        const comment = columnCommentMap[col];
        return {
          title: comment ? (
            <Tooltip title={comment} placement="top">
              <span style={{ cursor: 'help', borderBottom: '1px dashed #999' }}>
                {col}
              </span>
            </Tooltip>
          ) : col,
          dataIndex: col,
          key: col,
          ellipsis: true,
          render: (value: any) => {
            const text = value != null ? String(value) : '';
            if (text.length > 50) {
              return (
                <Tooltip title={text} placement="topLeft">
                  <span style={{ cursor: 'pointer' }}>{text.slice(0, 50)}...</span>
                </Tooltip>
              );
            }
            return text;
          },
        };
      });
    }
    return [];
  }, [functionTestResult, columnCommentMap]);

  const renderFunctionTestResult = () => {
    if (!functionTestResult) return null;
    const { success, output, error } = functionTestResult;
    return (
      <Card
        size="small"
        title={
          <Space>
            <Tag color={success ? 'success' : 'error'}>{success ? '执行成功' : '执行失败'}</Tag>
            {success && Array.isArray(output) && <span>返回 {output.length} 行</span>}
          </Space>
        }
        styles={{ body: { padding: 0 } }}
      >
        {success ? (
          Array.isArray(output) && functionTestOutputColumns.length > 0 ? (
            <Table
              rowKey={(record, index) => `${index}`}
              size="small"
              columns={functionTestOutputColumns}
              dataSource={output}
              pagination={{ pageSize: 10, hideOnSinglePage: true }}
              scroll={{ x: 'max-content' }}
            />
          ) : (
            <div style={{ padding: 12 }}>
              <JsonEditor value={output} readOnly height={160} />
            </div>
          )
        ) : (
          <div style={{ padding: 12, color: '#cf1322' }}>{error || '未知错误'}</div>
        )}
      </Card>
    );
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={compactMode ? 8 : 'middle'}>
      <CodeMirror
        value={sqlValue}
        height={editorHeight}
        extensions={extensions}
        onChange={(newValue) => onChange(newValue)}
        onCreateEditor={(view) => { editorViewRef.current = view; }}
        placeholder="在此输入 SQL，支持 :paramName 命名参数，例如：SELECT * FROM user WHERE id = :id"
        style={{ border: '1px solid #d9d9d9', borderRadius: 6 }}
      />
      {!compactMode && <Space size="small" wrap>
        <Button
          type="primary"
          icon={<PlayCircleOutlined />}
          loading={previewLoading}
          onClick={handlePreview}
          disabled={disabled || readonly}
          size="small"
        >
          {isFunctionTestMode ? '执行函数测试' : '执行预览（仅 SELECT）'}
        </Button>
        {!isFunctionTestMode && tables.length > 0 && (
          <Button
            icon={<ThunderboltOutlined />}
            onClick={() => setShowGenerator(!showGenerator)}
            size="small"
          >
            SQL 生成
          </Button>
        )}
        <Button
          icon={<HistoryOutlined />}
          onClick={() => setShowHistory(!showHistory)}
          size="small"
        >
          历史记录 {sqlHistory.length > 0 && `(${sqlHistory.length})`}
        </Button>
        {showHistory && sqlHistory.length > 0 && (
          <Popconfirm
            title="确定清空所有历史记录吗？"
            onConfirm={handleHistoryClear}
            okText="确定"
            cancelText="取消"
          >
            <Button
              icon={<ClearOutlined />}
              size="small"
              danger
            >
              清空
            </Button>
          </Popconfirm>
        )}
      </Space>}
      {/* SQL 模板生成器面板 */}
      {!compactMode && showGenerator && (
        <Card
          size="small"
          title={
            <Space>
              <ThunderboltOutlined />
              <span>SQL 模板生成器</span>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                （根据上游节点 schema 智能生成参数化 SQL）
              </Typography.Text>
            </Space>
          }
          extra={
            <Button type="text" size="small" onClick={() => setShowGenerator(false)}>
              关闭
            </Button>
          }
          styles={{ body: { padding: 12 } }}
        >
          <Space direction="vertical" style={{ width: '100%' }} size={8}>
            {/* 表选择 + 操作类型 */}
            <Space size={8}>
              <Select
                showSearch
                placeholder="选择目标表"
                value={genTable}
                onChange={(v) => setGenTable(v)}
                style={{ width: 160 }}
                size="small"
                options={tables.map((t) => ({ value: t, label: t }))}
                filterOption={(input, option) =>
                  (option?.label as string)?.toLowerCase().includes(input.toLowerCase()) ?? false
                }
              />
              <Select
                value={genOp}
                onChange={(v) => setGenOp(v)}
                style={{ width: 100 }}
                size="small"
                options={[
                  { value: 'SELECT', label: 'SELECT' },
                  { value: 'INSERT', label: 'INSERT' },
                  { value: 'UPDATE', label: 'UPDATE' },
                  { value: 'DELETE', label: 'DELETE' },
                ]}
              />
            </Space>
            {/* 上游字段匹配结果 */}
            {genTable && upstreamFields.length > 0 && (
              <div>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  上游字段智能匹配（{genMatched.size}/{upstreamFields.length}）：
                </Typography.Text>
                <div style={{ marginTop: 4 }}>
                  {genMatched.size > 0 ? (
                    <Space size={[4, 4]} wrap>
                      {Array.from(genMatched.entries()).map(([col, { upstreamField }]) => (
                        <Tag key={col} color="#6366f1">
                          {upstreamField} → {col}
                        </Tag>
                      ))}
                    </Space>
                  ) : (
                    <Typography.Text type="warning" style={{ fontSize: 12 }}>
                      未找到匹配的字段，请检查上游输出与表列名是否对应
                    </Typography.Text>
                  )}
                </div>
              </div>
            )}
            {genTable && upstreamFields.length === 0 && (
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                当前节点无上游字段，生成的 SQL 将不包含参数绑定
              </Typography.Text>
            )}
            {/* 生成预览 + 应用按钮 */}
            {genPreview && (
              <>
                <div
                  style={{
                    padding: '8px 12px',
                    background: '#f6f8fa',
                    borderRadius: 4,
                    fontFamily: 'monospace',
                    fontSize: 12,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all',
                    border: '1px solid #e8e8e8',
                  }}
                >
                  {genPreview}
                </div>
                <Button
                  type="primary"
                  size="small"
                  icon={<ThunderboltOutlined />}
                  onClick={handleApplyGenerated}
                  disabled={disabled || readonly}
                >
                  应用到编辑器
                </Button>
              </>
            )}
          </Space>
        </Card>
      )}
      {/* SQL 历史记录面板 */}
      {!compactMode && showHistory && (
        <Card
          size="small"
          title={
            <Space>
              <HistoryOutlined />
              <span>SQL 执行历史</span>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                （点击可快速填充）
              </Typography.Text>
            </Space>
          }
          extra={
            <Button
              type="text"
              size="small"
              onClick={() => setShowHistory(false)}
            >
              关闭
            </Button>
          }
          styles={{ body: { padding: 0, maxHeight: 300, overflow: 'auto' } }}
        >
          {sqlHistory.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="暂无历史记录"
              style={{ padding: 16 }}
            />
          ) : (
            <List
              size="small"
              dataSource={sqlHistory}
              renderItem={(item, index) => (
                <List.Item
                  style={{
                    padding: '8px 12px',
                    cursor: 'pointer',
                    backgroundColor: index % 2 === 0 ? '#fafafa' : undefined,
                  }}
                  onClick={() => handleHistorySelect(item)}
                  actions={[
                    <Button
                      key="delete"
                      type="text"
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleHistoryRemove(index);
                      }}
                    />,
                  ]}
                >
                  <List.Item.Meta
                    title={
                      <Space>
                        <Tag
                          color={item.status === 'success' ? 'success' : 'error'}
                          style={{ margin: 0 }}
                        >
                          {item.status === 'success' ? '成功' : '失败'}
                        </Tag>
                        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                          {formatHistoryTime(item.timestamp)}
                        </Typography.Text>
                        {item.summary && (
                          <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                            {item.summary}
                          </Typography.Text>
                        )}
                      </Space>
                    }
                    description={
                      <Typography.Paragraph
                        ellipsis={{ rows: 2 }}
                        style={{ margin: 0, fontSize: 12, fontFamily: 'monospace' }}
                      >
                        {item.sql}
                      </Typography.Paragraph>
                    }
                  />
                </List.Item>
              )}
            />
          )}
        </Card>
      )}
      {!compactMode && !isFunctionTestMode && previewColumns.length > 0 && (
        <Card size="small" title="预览结果" styles={{ body: { padding: 0 } }}>
          <Table
            rowKey={(record, index) => `${index}`}
            size="small"
            columns={previewTableColumns}
            dataSource={previewRows}
            pagination={{ pageSize: 10, hideOnSinglePage: true }}
            scroll={{ x: 'max-content' }}
          />
        </Card>
      )}
      {!compactMode && isFunctionTestMode && renderFunctionTestResult()}
      {!compactMode && <div>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          数据库表结构参考（点击表名或列名自动插入 SQL）：
        </Typography.Text>
        <Spin spinning={tablesLoading}>
          <Collapse
            size="small"
            style={{ marginTop: 8, maxHeight: 320, overflow: 'auto' }}
            items={collapseItems}
          />
        </Spin>
      </div>}
    </Space>
  );
};

export default DbSqlEditor;

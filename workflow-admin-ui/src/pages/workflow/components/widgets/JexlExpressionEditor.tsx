import { useEffect, useMemo, useCallback, useState } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { StreamLanguage, StringStream, type StreamParser } from '@codemirror/language';
import { autocompletion, CompletionContext, CompletionResult } from '@codemirror/autocomplete';
import { EditorView } from '@codemirror/view';
import type { WidgetProps } from '@rjsf/utils';
import { Tag, Space, Tooltip } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';

interface ExprModeState {
  /** 当前是否处于字符串中（引号字符） */
  inString: string | null;
}

/**
 * AviatorScript 轻量语法高亮模式（完全向后兼容 JEXL 语法）
 *
 * 支持的语法：
 * - 字符串：'...' / "..." / `...`
 * - 数字：整数、小数、十六进制 0x 前缀
 * - 注释：# 单行注释（Aviator 支持） 与  // 单行注释
 * - 关键字：AviatorScript 全部关键字（let/const/return/if/else/for/while/break/continue + 兼容 JEXL 的 in/and/or/not）
 * - 操作符：== != >= <= > < =~ !~ && || ! + - * / % ?: ?? -> (lambda)
 * - 函数调用：标识符后紧跟 ( 标为 functionName
 * - 正则：/pattern/flag 简单匹配
 */
const aviatorMode: StreamParser<ExprModeState> = {
  startState() {
    return { inString: null };
  },
  token(stream: StringStream, state: ExprModeState) {
    // 字符串跨行支持
    if (state.inString) {
      while (!stream.eol()) {
        if (stream.next() === state.inString) {
          const pos = stream.pos - 2;
          if (pos >= 0 && stream.string.charAt(pos) === '\\') continue;
          state.inString = null;
          break;
        }
      }
      return 'string';
    }

    if (stream.eatSpace()) return null;

    const ch = stream.peek();

    // 单行注释：# （AviatorScript 原生）或 //
    if (ch === '#') {
      // 注意：SpEL 兼容 #variable，后面紧跟字母才算变量，否则是注释
      stream.next();
      const after = stream.peek();
      if (after && /[A-Za-z_$]/.test(after)) {
        // 这是 #variable（SpEL 兼容），回退为 variableName
        return 'variableName';
      }
      stream.skipToEnd();
      return 'lineComment';
    }
    if (ch === '/' && stream.string.charAt(stream.pos + 1) === '/') {
      stream.skipToEnd();
      return 'lineComment';
    }

    // 字符串开始
    if (ch === "'" || ch === '"' || ch === '`') {
      state.inString = ch;
      stream.next();
      return 'string';
    }

    // 数字（含 0x 十六进制）
    if (stream.match(/^0x[\da-fA-F]+|^\d+(\.\d+)?/)) {
      return 'number';
    }

    // 操作符（匹配顺序：先长后短）
    // 新增 AviatorScript 特有：lambda 箭头 -> 、elvis ?? 、elvis 长格式 ?:
    if (stream.match(/^(->|==|!=|>=|<=|=~|!~|&&|\|\||\?\?|\?:)/)) {
      return 'operator';
    }
    if (stream.match(/^[+\-*/%><=!&|^~?:]/)) {
      return 'operator';
    }

    // 关键字 / 标识符 / 函数调用
    if (stream.match(/^[a-zA-Z_$][\w$]*/)) {
      const word = stream.current().toLowerCase();
      const isKeyword = AVIATOR_KEYWORDS.includes(word);
      const ahead = stream.string.slice(stream.pos).trimStart();
      if (ahead.startsWith('(') && !isKeyword) {
        return 'functionName';
      }
      return isKeyword ? 'keyword' : 'variableName';
    }

    stream.next();
    return null;
  },
};

/**
 * AviatorScript 关键字（兼容 JEXL 旧关键字 in/and/or/not）。
 * 用于语法高亮着色 + 自动补全。
 */
const AVIATOR_KEYWORDS = [
  // 布尔/空字面量
  'true', 'false', 'nil', 'null',
  // 控制流
  'if', 'else', 'elsif', 'elif', 'while', 'for', 'in',
  'break', 'continue', 'return', 'let', 'const',
  // 函数/模块相关
  'fn', 'lambda', 'end', 'seq', 'map', 'include', 'reduce',
  // 逻辑关键字（JEXL 兼容）
  'and', 'or', 'not',
  // 字符串/数学内置前缀
  'string', 'math', 'date', 'seq', 'tuple',
];

/**
 * AviatorScript 常用内置函数提示（按功能分组，detail 说明用途）。
 *
 * 与后端 MockEngine / ExpressionEvaluator 实际可用函数严格对齐：
 *   - string.* : 所有字符串内置方法
 *   - math.* : 所有数学内置方法
 *   - seq.* : 集合/序列操作内置方法
 *   - fn.* : Mock 模板注入的 fn 工具方法（fn.now / fn.uuidShort / ...）
 *   - date.* : 日期时间常用函数
 */
const AVIATOR_FUNCTIONS = [
  // ========== string.* ==========
  { label: 'string.length(s)',    detail: '[Aviator] 字符串长度' },
  { label: 'string.substring(s,start,end?)', detail: '[Aviator] 截取子串' },
  { label: 'string.contains(s,sub)', detail: '[Aviator] 是否包含子串' },
  { label: 'string.startsWith(s,prefix)', detail: '[Aviator] 前缀判断' },
  { label: 'string.endsWith(s,suffix)', detail: '[Aviator] 后缀判断' },
  { label: 'string.index(s,sub)',  detail: '[Aviator] 子串首次位置' },
  { label: 'string.lastIndex(s,sub)', detail: '[Aviator] 子串最后位置' },
  { label: 'string.upper(s)',      detail: '[Aviator] 转大写' },
  { label: 'string.lower(s)',      detail: '[Aviator] 转小写' },
  { label: 'string.trim(s)',       detail: '[Aviator] 去首尾空格' },
  { label: 'string.replace(s,old,new)', detail: '[Aviator] 字符串替换' },
  { label: 'string.split(s,regex)', detail: '[Aviator] 正则分割为数组' },
  { label: 'string.matches(s,regex)', detail: '[Aviator] 正则匹配' },
  { label: 'string.reverse(s)',    detail: '[Aviator] 反转字符串' },
  { label: 'string.concat(a,b)',   detail: '[Aviator] 拼接字符串' },
  { label: 'string.empty(s)',      detail: '[Aviator] 是否空字符串/空集合' },

  // ========== math.* ==========
  { label: 'math.abs(n)',          detail: '[Aviator] 绝对值' },
  { label: 'math.ceil(n)',         detail: '[Aviator] 向上取整' },
  { label: 'math.floor(n)',        detail: '[Aviator] 向下取整' },
  { label: 'math.round(n)',        detail: '[Aviator] 四舍五入' },
  { label: 'math.max(a,b)',        detail: '[Aviator] 最大值' },
  { label: 'math.min(a,b)',        detail: '[Aviator] 最小值' },
  { label: 'math.pow(a,b)',        detail: '[Aviator] a 的 b 次方' },
  { label: 'math.sqrt(n)',         detail: '[Aviator] 平方根' },
  { label: 'math.log(n)',          detail: '[Aviator] 自然对数' },
  { label: 'math.log10(n)',        detail: '[Aviator] 常用对数' },
  { label: 'math.sin(n)',          detail: '[Aviator] 正弦（弧度）' },
  { label: 'math.cos(n)',          detail: '[Aviator] 余弦（弧度）' },
  { label: 'math.tan(n)',          detail: '[Aviator] 正切（弧度）' },
  { label: 'math.random()',        detail: '[Aviator] 0-1 随机浮点数' },

  // ========== seq.* (集合/序列操作) ==========
  { label: 'seq.map(col,fn)',      detail: '[Aviator] 元素映射' },
  { label: 'seq.filter(col,fn)',   detail: '[Aviator] 过滤元素' },
  { label: 'seq.reduce(col,init,fn)', detail: '[Aviator] 聚合归约' },
  { label: 'seq.include(col,fn)',  detail: '[Aviator] 是否存在匹配元素' },
  { label: 'seq.every(col,fn)',    detail: '[Aviator] 所有元素是否匹配' },
  { label: 'seq.not_any(col,fn)',  detail: '[Aviator] 无元素匹配' },
  { label: 'seq.sort(col,fn?)',    detail: '[Aviator] 排序' },
  { label: 'seq.reverse(col)',     detail: '[Aviator] 反转集合' },
  { label: 'seq.take(col,n)',      detail: '[Aviator] 取前 n 个元素' },
  { label: 'seq.drop(col,n)',      detail: '[Aviator] 丢弃前 n 个元素' },
  { label: 'seq.sum(col)',         detail: '[Aviator] 元素求和' },
  { label: 'seq.avg(col)',         detail: '[Aviator] 元素均值' },
  { label: 'seq.min(col)',         detail: '[Aviator] 最小元素' },
  { label: 'seq.max(col)',         detail: '[Aviator] 最大元素' },
  { label: 'seq.count(col,fn?)',   detail: '[Aviator] 元素计数' },
  { label: 'seq.distinct(col)',    detail: '[Aviator] 去重' },
  { label: 'seq.join(col,sep?)',   detail: '[Aviator] 拼接为字符串' },
  { label: 'seq.set(col)',         detail: '[Aviator] 转 Set 去重' },
  { label: 'seq.first(col,fn?)',   detail: '[Aviator] 首个匹配元素' },
  { label: 'seq.last(col,fn?)',    detail: '[Aviator] 最后匹配元素' },

  // ========== fn.* (Mock 模板注入的业务工具方法) ==========
  { label: 'fn.now()',             detail: '[Mock] 当前毫秒时间戳 Long（直接写 JSON 数字）' },
  { label: 'fn.timestamp()',       detail: '[Mock] 当前秒级时间戳 Long' },
  { label: 'fn.uuidShort()',       detail: '[Mock] 32 位 UUID（去横杠）' },
  { label: 'fn.uuid()',            detail: '[Mock] 完整 36 位 UUID' },
  { label: 'fn.randInt(start,end)', detail: '[Mock] [start,end) 区间随机整数' },

  // ========== date.* (常用日期时间函数) ==========
  { label: 'date.now()',           detail: '[Aviator] 当前时间 Date 对象' },
  { label: 'date.format(date,fmt)', detail: '[Aviator] 格式化为字符串' },
  { label: 'date.parse(str,fmt)',  detail: '[Aviator] 字符串转 Date' },

  // ========== JEXL 旧内置函数兼容 (保留提示避免误导，但标注 [deprecated]) ==========
  { label: 'empty',                detail: '[deprecated-JEXL→Aviator] 请改用 string.empty / count(col) == 0' },
  { label: 'size',                 detail: '[deprecated-JEXL→Aviator] 请改用 string.length(s) / count(x) / seq.count(col)' },
  { label: 'length',               detail: '[deprecated-JEXL→Aviator] 请改用 string.length(s) / count(x)' },
  { label: 'substring',            detail: '[deprecated-JEXL→Aviator] 请改用 string.substring(s,start,end)' },
  { label: 'contains',             detail: '[deprecated-JEXL→Aviator] 请改用 string.contains(s,sub)' },
  { label: 'startsWith',           detail: '[deprecated-JEXL→Aviator] 请改用 string.startsWith(s,prefix)' },
  { label: 'endsWith',             detail: '[deprecated-JEXL→Aviator] 请改用 string.endsWith(s,suffix)' },
  { label: 'matches',              detail: '[deprecated-JEXL→Aviator] 请改用 string.matches(s,regex)' },
  { label: 'toUpperCase',          detail: '[deprecated-JEXL→Aviator] 请改用 string.upcase(s) / s.toUpperCase()' },
  { label: 'toLowerCase',          detail: '[deprecated-JEXL→Aviator] 请改用 string.downcase(s) / s.toLowerCase()' },
  { label: 'abs',                  detail: '[deprecated-JEXL→Aviator] 请改用 math.abs' },
  { label: 'now',                  detail: '[deprecated-JEXL→Aviator] 请改用 fn.now（毫秒Long）/ date.now（Date对象）' },
];

/**
 * 构造 AviatorScript 自动补全源。
 *
 * 提示项来源（按优先级）：
 * 1. 上游字段（formContext.upstreamFields）
 * 2. AviatorScript 关键字（含 JEXL 兼容关键字）
 * 3. 常用内置函数（按 string. / math. / seq. / fn. / date. 分组标注）
 */
function buildAviatorCompletions(upstreamFields: string[] = []) {
  return function aviatorCompletions(context: CompletionContext): CompletionResult | null {
    const word = context.matchBefore(/[a-zA-Z_$][\w$.\[\]]*/);
    if (!word || word.from === word.to) return null;

    const text = word.text;
    const lower = text.toLowerCase();

    const options: { label: string; type?: string; detail?: string }[] = [];

    // 1) 上游字段
    upstreamFields.forEach((field) => {
      if (field.toLowerCase().includes(lower) || lower.includes(field.toLowerCase())) {
        options.push({ label: field, type: 'variable', detail: '上游字段' });
      }
    });

    // 2) 关键字
    AVIATOR_KEYWORDS.filter((kw) => kw.startsWith(lower)).forEach((kw) => {
      options.push({ label: kw, type: 'keyword' });
    });

    // 3) 函数
    AVIATOR_FUNCTIONS.filter((fn) => fn.label.toLowerCase().startsWith(lower)).forEach((fn) => {
      options.push({ label: fn.label, type: 'function', detail: fn.detail });
    });

    if (options.length === 0) return null;
    return {
      from: word.from,
      options,
      validFor: /^[\w$.\[\]]*$/,
    };
  };
}

const MIN_LINES = 2;
const MAX_LINES = 20;
const LINE_HEIGHT = 18;

/**
 * 表达式编辑器（AviatorScript 语法）。
 *
 * 与旧版 JEXL 编辑器的对应关系：
 *   - 关键字扩展为 AviatorScript 全量关键字（向后兼容 JEXL 的 in/and/or/not）
 *   - 内置函数表切换为 Aviator 的 string./math./seq./fn. 分组（JEXL 旧函数保留 [deprecated] 提示）
 *   - StreamParser 新增 // 注释 / lambda 箭头 -> 着色
 *   - 编辑器右上角增加 "AviatorScript" 紫色徽章 + Hover 差异速览
 *
 * 作为 @rjsf widget 使用方式保持不变：`'ui:widget': 'jexlExpressionEditor'`
 * 新代码建议使用：`'ui:widget': 'expressionEditor'`（alias 已在底部导出）
 */
const JexlExpressionEditor: React.FC<WidgetProps> = ({
  value,
  onChange,
  disabled,
  readonly,
  placeholder,
  options,
  formContext,
}) => {
  const textValue = typeof value === 'string' ? value : '';
  const [darkMode, setDarkMode] = useState(false);

  useEffect(() => {
    setDarkMode(localStorage.getItem('fluxion-dark-mode') === 'true');
  }, []);

  const upstreamFields: string[] = (formContext as any)?.upstreamFields || [];

  const heightOption = (options as any)?.height;
  const minLines = Math.max(1, Math.min(MAX_LINES, (options as any)?.minLines ?? MIN_LINES));
  const maxLines = Math.max(minLines, Math.min(MAX_LINES, (options as any)?.maxLines ?? MAX_LINES));

  const calcHeight = useCallback((content: string) => {
    if (heightOption) return heightOption;
    const lines = Math.max(minLines, Math.min(maxLines, content.split('\n').length));
    return lines * LINE_HEIGHT + 16;
  }, [heightOption, minLines, maxLines]);

  const [height, setHeight] = useState(() => calcHeight(textValue));

  useEffect(() => {
    setHeight(calcHeight(textValue));
  }, [textValue, calcHeight]);

  const extensions = useMemo(
    () => [
      StreamLanguage.define(aviatorMode),
      autocompletion({ override: [buildAviatorCompletions(upstreamFields)] }),
      EditorView.lineWrapping,
    ],
    [upstreamFields],
  );

  return (
    <div style={{ position: 'relative' }}>
      {/* 引擎徽章：右上角静态悬浮，不参与输入交互 */}
      <div style={{
        position: 'absolute',
        top: 4,
        right: 8,
        zIndex: 10,
        pointerEvents: 'none',
      }}>
        <Tooltip
          placement="topRight"
          title={
            <div style={{ fontSize: 12, lineHeight: 1.6, maxWidth: 300 }}>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>AviatorScript 语法</div>
              <div>• 逻辑：<code>&amp;&amp; / || / !</code>（兼容 <code>and / or / not</code>）</div>
              <div>• 空值合并：<code>a ?? b</code>（a 为 null 返回 b）</div>
              <div>• 三元：<code>cond ? a : b</code></div>
              <div>• 字符串：<code>string.contains(s,sub) / .upper / .lower</code></div>
              <div>• 集合：<code>seq.filter(c, lambda(x) -&gt; x&gt;0 end)</code></div>
              <div style={{ marginTop: 4, color: '#91caff' }}>JEXL 旧语法 100% 兼容运行</div>
            </div>
          }
        >
          <Tag
            color="purple"
            icon={<ThunderboltOutlined />}
            style={{ fontSize: 10, margin: 0, padding: '0 6px', opacity: 0.9 }}
          >
            AviatorScript
          </Tag>
        </Tooltip>
      </div>

      <CodeMirror
        value={textValue}
        height={`${height}px`}
        placeholder={placeholder ?? '输入表达式（AviatorScript），如 status == \'APPROVED\' && math.floor(price) > 100'}
        theme={darkMode ? 'dark' : 'light'}
        extensions={extensions}
        readOnly={disabled || readonly}
        onChange={(v) => onChange?.(v || undefined)}
        basicSetup={{
          lineNumbers: false,
          highlightActiveLine: false,
          highlightActiveLineGutter: false,
          foldGutter: false,
          autocompletion: true,
        }}
      />
    </div>
  );
};

/** 新推荐 widget key：`'ui:widget': 'expressionEditor'`（与引擎无关的语义化命名） */
export const ExpressionEditor = JexlExpressionEditor;

export default JexlExpressionEditor;

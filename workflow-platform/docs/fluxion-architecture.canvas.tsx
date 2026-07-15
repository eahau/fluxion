import {
  Stack, Row, Grid, Divider, Text, H1, H2,
  Table, Tag, Card, CardHeader, CardBody, Pill, Callout
} from 'qoder/canvas';
import { useHostTheme } from 'qoder/canvas';

// ─── 共用 SVG 工具 ────────────────────────────────────────────────────────────
function mkHelpers(t: any) {
  const bg = t.bg.elevated, bgP = t.bg.panel, txtP = t.text.primary;
  const txtS = t.text.secondary, txtT = t.text.tertiary;
  const sq = t.stroke.quaternary;
  return {
    bg, bgP, txtP, txtS, txtT, sq,
    box:   (x:number,y:number,w:number,h:number,fill:string,bd:string) =>
             <rect x={x} y={y} width={w} height={h} rx="5" fill={fill} stroke={bd} strokeWidth="1.2"/>,
    lbl:   (x:number,y:number,s:string,c:string,sz=10,fw="500") =>
             <text x={x} y={y} textAnchor="middle" fill={c} fontSize={sz} fontWeight={fw}>{s}</text>,
    sub:   (x:number,y:number,s:string) =>
             <text x={x} y={y} textAnchor="middle" fill={txtT} fontSize={8}>{s}</text>,
    arr:   (x1:number,y1:number,x2:number,y2:number,c:string,id="ah") =>
             <line x1={x1} y1={y1} x2={x2} y2={y2} stroke={c} strokeWidth="1.5" markerEnd={`url(#${id})`}/>,
    dash:  (x1:number,y1:number,x2:number,y2:number,c:string,id="ahd") =>
             <line x1={x1} y1={y1} x2={x2} y2={y2} stroke={c} strokeWidth="1" strokeDasharray="5,3" markerEnd={`url(#${id})`}/>,
    defs:  (colors: Record<string,string>) => (
      <defs>
        {Object.entries(colors).map(([id, fill]) => (
          <marker key={id} id={id} markerWidth="8" markerHeight="6" refX="7" refY="3" orient="auto">
            <polygon points="0 0,8 3,0 6" fill={fill}/>
          </marker>
        ))}
      </defs>
    ),
  };
}

// ─── Admin Backend SVG ────────────────────────────────────────────────────────
function AdminBackendSvg({ tokens: t }: { tokens: any }) {
  const h = mkHelpers(t);
  const cAdmin = t.chart.warmPink, cAdminBg = t.status.dangerBg;
  const cCache = t.chart.teal,    cCacheBg  = t.status.successBg;
  const cSync  = t.chart.goldenYellow, cSyncBg = t.status.warningBg;
  const cMeta  = t.chart.purple,  cMetaBg   = t.fill.tertiary;

  return (
    <svg viewBox="0 0 900 300" width="100%" style={{ display:'block' }}>
      {h.defs({ ah: h.txtS, ahd: cAdmin, ahc: cCache, ahm: cMeta })}

      {/* ── Admin Frontend ── */}
      {h.box(30, 20, 140, 50, cAdminBg, cAdmin)}
      {h.lbl(100, 40, "Admin Frontend", cAdmin, 9, "700")}
      {h.sub(100, 54, "React · JWT 认证")}
      {h.sub(100, 64, "工作流 CRUD / 函数管理 / 执行日志")}

      {/* ── Admin API ── */}
      {h.box(220, 20, 190, 50, cAdminBg, cAdmin)}
      {h.lbl(315, 40, "Admin API (fluxion-admin)", cAdmin, 9, "700")}
      {h.sub(315, 54, "Spring Boot · RBAC 权限")}
      {h.sub(315, 64, "POST /workflows  GET /executions  POST /debug/step")}

      {h.arr(170, 45, 220, 45, cAdmin)}
      <text x="196" y="39" fill={cAdmin} fontSize="8" textAnchor="middle">REST</text>

      {/* ── MetaWorkflow 自举说明 ── */}
      <rect x="460" y="12" width="210" height="66" rx="5" fill={cMetaBg} stroke={cMeta} strokeWidth="1.2" strokeDasharray="5,3"/>
      {h.lbl(565, 28, "Admin API = META WorkflowDef 自举", cMeta, 9, "700")}
      {h.sub(565, 42, "每个 Admin API 端点对应一条 category=META")}
      {h.sub(565, 54, "is_protected=1 的 WorkflowDefinition")}
      {h.sub(565, 66, "MetaWorkflowGuard 保护：禁止删除 / 修改路由")}
      {h.arr(410, 45, 460, 45, cMeta, "ahm")}

      {/* ── MetaWorkflowLoader 三级启动 ── */}
      {h.box(30, 110, 210, 80, cAdminBg, cAdmin)}
      {h.lbl(135, 126, "MetaWorkflowLoader", cAdmin, 9, "700")}
      {h.sub(135, 140, "三级降级启动 (Bootstrap Fallback)")}
      <text x="48" y="158" fill={h.txtT} fontSize="8">Level 1: DB 加载所有 META WorkflowDef</text>
      <text x="48" y="170" fill={h.txtT} fontSize="8">Level 2: classpath:/meta-workflows/*.yaml</text>
      <text x="48" y="182" fill={h.txtT} fontSize="8">Level 3: 硬编码最小 REST 接口（紧急兜底）</text>

      {h.arr(135, 70, 135, 110, cAdmin)}

      {/* ── WorkflowDefinitionCache ── */}
      {h.box(290, 110, 200, 56, cCacheBg, cCache)}
      {h.lbl(390, 128, "WorkflowDefinitionCache", cCache, 9, "700")}
      {h.sub(390, 142, "Caffeine 本地缓存 (L1)")}
      {h.sub(390, 154, "cache.put() ← ConfigSyncManager 更新")}
      {h.sub(390, 163, "WorkflowRouter.execute() 读取（热加载透明）")}

      {h.arr(240, 145, 290, 145, cCache, "ahc")}

      {/* ── ConfigSyncManager ── */}
      {h.box(550, 110, 200, 56, cSyncBg, cSync)}
      {h.lbl(650, 128, "ConfigSyncManager", cSync, 9, "700")}
      {h.sub(650, 142, "L1 DB 轮询 (30s / poll-interval)")}
      {h.sub(650, 154, "L2 Apollo 推送 (毫秒级延迟)")}
      {h.sub(650, 163, "L3 Nacos 推送 (服务发现一体)")}

      {h.arr(490, 138, 550, 138, cSync)}
      <text x="521" y="132" fill={cSync} fontSize="8" textAnchor="middle">更新</text>

      {/* ── DB / Config Center ── */}
      {h.box(290, 220, 200, 40, h.bgP, h.sq)}
      {h.lbl(390, 237, "wf_definition (MySQL)", h.txtS, 9, "600")}
      {h.sub(390, 252, "updated_at 变更 → L1 轮询感知")}

      {h.box(550, 220, 200, 40, h.bgP, h.sq)}
      {h.lbl(650, 237, "Apollo / Nacos", h.txtS, 9, "600")}
      {h.sub(650, 252, "配置推送 → L2/L3 监听器回调")}

      {h.arr(390, 166, 390, 220, h.txtS)}
      {h.arr(650, 166, 650, 220, h.txtS)}

      {/* Footer annotation */}
      <text x="450" y="292" textAnchor="middle" fill={h.txtT} fontSize="8">
        管理后台本身通过元工作流自举，ConfigSyncManager 驱动全平台热加载，WorkflowDefinitionCache 屏蔽变更细节
      </text>
    </svg>
  );
}

// ─── Workflow Process Flow SVG ────────────────────────────────────────────────
function ProcessFlowSvg({ tokens: t }: { tokens: any }) {
  const h = mkHelpers(t);
  const cOk  = t.chart.teal,   cOkBg  = t.status.successBg;
  const cErr = t.chart.red,    cErrBg = t.status.dangerBg;
  const cWrn = t.chart.goldenYellow, cWrnBg = t.status.warningBg;
  const cFp  = t.chart.green,  cFpBg  = t.status.successBg;
  const cSaga = t.chart.brightOrange, cSagaBg = t.status.warningBg;
  const cDec = t.chart.blue;

  // diamond shape helper
  const diamond = (cx:number,cy:number,rx:number,ry:number,fill:string,bd:string) =>
    <polygon
      points={`${cx},${cy-ry} ${cx+rx},${cy} ${cx},${cy+ry} ${cx-rx},${cy}`}
      fill={fill} stroke={bd} strokeWidth="1.2"/>;

  return (
    <svg viewBox="0 0 960 820" width="100%" style={{ display:'block' }}>
      {h.defs({ ah2: h.txtS, aherr: cErr, ahok: cOk, ahwrn: cWrn, ahsaga: cSaga })}

      {/* ── Happy Path (center column) ── */}
      {/* 1. External Request */}
      {h.box(340, 10, 240, 32, t.status.infoBg, t.chart.blue)}
      {h.lbl(460, 29, "外部请求  External Request", t.chart.blue, 9, "700")}
      {h.arr(460, 42, 460, 62, h.txtS, "ah2")}

      {/* 2. Adapter */}
      {h.box(340, 62, 240, 36, t.status.infoBg, t.chart.blue)}
      {h.lbl(460, 77, "Adapter Layer", t.chart.blue, 10, "700")}
      {h.sub(460, 91, "toUnifiedRequest() → WorkflowRouter")}
      {h.arr(460, 98, 460, 118, h.txtS, "ah2")}

      {/* 3. WorkflowRouter: lookup definition + choose executor */}
      {h.box(340, 118, 240, 44, t.fill.tertiary, h.sq)}
      {h.lbl(460, 134, "WorkflowRouter.execute()", h.txtS, 9, "700")}
      {h.sub(460, 147, "cache.get(workflowId) → WorkflowDefinition")}
      {h.sub(460, 158, "saga_enabled? → SagaExecutor : WorkflowEngine")}
      {h.arr(460, 162, 460, 182, h.txtS, "ah2")}

      {/* 4. Schema Validate */}
      {diamond(460, 194, 70, 22, cWrnBg, cWrn)}
      {h.lbl(460, 191, "SchemaValidator", cWrn, 8.5, "700")}
      {h.lbl(460, 203, "validate(inputSchema)", cWrn, 8, "400")}
      {/* PASS arrow */}
      {h.arr(460, 216, 460, 236, cOk, "ahok")}
      <text x="468" y="230" fill={cOk} fontSize="8">PASS</text>
      {/* FAIL arrow right */}
      {h.arr(530, 194, 640, 194, cErr, "aherr")}
      <text x="572" y="188" fill={cErr} fontSize="8">FAIL</text>
      {h.box(640, 178, 180, 32, cErrBg, cErr)}
      {h.lbl(730, 192, "InvalidParamException", cErr, 8.5, "700")}
      {h.sub(730, 205, "WF-400-001 → HTTP 400")}

      {/* 5. Init State */}
      {h.box(340, 236, 240, 32, cFpBg, cFp)}
      {h.lbl(460, 250, "ImmutableExecutionState.start()", cFp, 9, "700")}
      {h.sub(460, 263, "锁定版本快照，生成 executionId / traceId")}
      {h.arr(460, 268, 460, 288, h.txtS, "ah2")}

      {/* 6. Node Loop box */}
      <rect x="290" y="288" width="340" height="330" rx="6"
            fill={cFpBg} stroke={cFp} strokeWidth="1" strokeDasharray="6,3"/>
      {h.lbl(460, 306, "节点执行循环  Node Execution Loop", cFp, 9, "700")}

      {/* 6a. buildNodeInput */}
      {h.box(320, 316, 280, 28, h.bg, cFp)}
      {h.lbl(460, 327, "buildNodeInput(node, directInput, state)", cFp, 8.5, "600")}
      {h.sub(460, 340, "declaredDeps 按 dependsOn 提取 → NodeInput")}
      {h.arr(460, 344, 460, 362, h.txtS, "ah2")}

      {/* 6b. FunctionRegistry + Decorators */}
      {h.box(320, 362, 280, 28, h.bg, cFp)}
      {h.lbl(460, 374, "FunctionRegistry.resolve() + Decorator.decorate()", cFp, 8.5, "600")}
      {h.sub(460, 387, "custom > builtin > script > external  |  metrics→trace→rateLimit→…")}
      {h.arr(460, 390, 460, 408, h.txtS, "ah2")}

      {/* 6c. fn.apply */}
      {h.box(320, 408, 280, 28, h.bg, cFp)}
      {h.lbl(460, 421, "withTimeout( fn.apply(nodeInput) )", cFp, 8.5, "700")}
      {h.sub(460, 434, "纯函数调用，FunctionResult<O> 返回")}
      {h.arr(460, 436, 460, 454, h.txtS, "ah2")}

      {/* 6d. Result decision */}
      {diamond(460, 466, 80, 24, cWrnBg, cWrn)}
      {h.lbl(460, 463, "ErrorStrategy", cWrn, 8.5, "700")}
      {h.lbl(460, 475, "/ Result", cWrn, 8.5, "400")}

      {/* SUCCESS arrow down */}
      {h.arr(460, 490, 460, 510, cOk, "ahok")}
      <text x="468" y="504" fill={cOk} fontSize="8">SUCCESS / SKIP</text>

      {/* FALLBACK arrow right (inside loop) */}
      {h.dash(540, 466, 600, 466, cWrn, "ahwrn")}
      <text x="558" y="460" fill={cWrn} fontSize="8">FALLBACK</text>
      {h.box(600, 452, 110, 28, cWrnBg, cWrn)}
      {h.lbl(655, 463, "fn.fallback(input,ex)", cWrn, 8, "600")}
      {h.sub(655, 474, "降级输出继续执行")}
      {h.dash(655, 480, 560, 522, cWrn, "ahwrn")}

      {/* RETRY arrow left (outside loop) */}
      {h.dash(380, 466, 300, 466, cWrn, "ahwrn")}
      <text x="330" y="460" fill={cWrn} fontSize="8">RETRY</text>
      {h.box(180, 452, 120, 36, cWrnBg, cWrn)}
      {h.lbl(240, 466, "RetryScheduler", cWrn, 8.5, "700")}
      {h.sub(240, 478, "HashedWheelTimer")}
      {h.sub(240, 488, "指数退避，非阻塞")}
      {h.dash(240, 452, 380, 420, cWrn, "ahwrn")}

      {/* FAIL arrow far right */}
      {h.arr(540, 478, 640, 500, cErr, "aherr")}
      <text x="580" y="484" fill={cErr} fontSize="8">FAIL</text>
      {h.box(640, 490, 185, 32, cErrBg, cErr)}
      {h.lbl(732, 504, "WorkflowNodeException", cErr, 8.5, "700")}
      {h.sub(732, 516, "WF-500-001 → 中止工作流")}

      {/* 6e. withNodeOutput */}
      {h.box(320, 510, 280, 28, h.bg, cFp)}
      {h.lbl(460, 523, "state = state.withNodeOutput(nodeId, output)", cFp, 8.5, "600")}
      {h.sub(460, 536, "产生新的不可变快照，旧快照不受影响")}
      {h.arr(460, 538, 460, 556, h.txtS, "ah2")}

      {/* 6f. resolveNextNode */}
      {diamond(460, 568, 80, 22, h.bgP, h.sq)}
      {h.lbl(460, 565, "resolveNextNode()", h.txtS, 8.5, "600")}
      {h.lbl(460, 577, "条件分支/顺序/DAG", h.txtS, 8, "400")}

      {/* Has next → loop back */}
      {h.dash(380, 568, 310, 568, h.txtS, "ah2")}
      <text x="336" y="562" fill={h.txtT} fontSize="8">有下一节点</text>
      <path d="M310,568 L310,322 L320,322" fill="none" stroke={h.txtS} strokeWidth="1" strokeDasharray="4,3"/>

      {/* No next → exit loop */}
      {h.arr(460, 590, 460, 640, cOk, "ahok")}
      <text x="468" y="620" fill={cOk} fontSize="8">流程结束</text>

      {/* 7. OutputSchema validate (devMode) */}
      {h.box(340, 640, 240, 32, h.bgP, h.sq)}
      {h.lbl(460, 654, "OutputSchema 校验 (devMode)", h.txtS, 9, "600")}
      {h.sub(460, 667, "schemaValidator.validateStrict(outputSchema, data)")}
      {h.arr(460, 672, 460, 692, h.txtS, "ah2")}

      {/* 8. EngineResult */}
      {h.box(340, 692, 240, 32, cOkBg, cOk)}
      {h.lbl(460, 706, "EngineResult.success(data, finalState)", cOk, 9, "700")}
      {h.sub(460, 720, "data + executionId + trace + finalState 快照")}
      {h.arr(460, 724, 460, 744, h.txtS, "ah2")}

      {/* 9. Adapter Response */}
      {h.box(340, 744, 240, 32, t.status.infoBg, t.chart.blue)}
      {h.lbl(460, 758, "Adapter → HTTP Response / RPC Reply", t.chart.blue, 9, "700")}
      {h.sub(460, 772, "ApiResponse.ok(data) → 200  |  errorMsg → 5xx")}

      {/* ── Saga Compensation Path (bottom) ── */}
      <line x1="60" y1="790" x2="700" y2="790" stroke={cSaga} strokeWidth="1" strokeDasharray="6,3"/>
      <text x="360" y="800" fill={cSaga} fontSize="8" fontWeight="700" textAnchor="middle">── Saga 补偿路径 ──</text>
      {h.box(30, 800, 220, 18, cSagaBg, cSaga)}
      {h.lbl(140, 813, "sideEffects → CompensationEntry 入栈", cSaga, 8, "600")}
      {h.arr(250, 808, 340, 808, cSaga, "ahsaga")}
      {h.box(340, 800, 220, 18, cSagaBg, cSaga)}
      {h.lbl(450, 813, "FAIL触发 → SagaExecutor.compensate()", cSaga, 8, "600")}
      {h.arr(560, 808, 640, 808, cSaga, "ahsaga")}
      {h.box(640, 800, 180, 18, cSagaBg, cSaga)}
      {h.lbl(730, 813, "逆序 compensateFn.apply() 回滚", cSaga, 8, "600")}
    </svg>
  );
}

// ─── Module Dependency SVG ────────────────────────────────────────────────────
function ModuleDependencySvg({ tokens: t }: { tokens: any }) {
  const h = mkHelpers(t);
  const c0 = t.chart.teal,   c0bg = t.status.successBg;   // core
  const c1 = t.chart.blue,   c1bg = t.status.infoBg;       // spi
  const c2 = t.chart.cyan,   c2bg = t.status.infoBg;       // adapters
  const c3 = t.chart.warmPink, c3bg = t.status.dangerBg;   // admin
  const c4 = t.chart.purple,  c4bg = t.fill.tertiary;      // gateway
  const c5 = t.chart.green,   c5bg = t.status.successBg;   // functions
  const c6 = t.chart.goldenYellow, c6bg = t.status.warningBg; // config
  const c7 = t.chart.red,     c7bg = t.status.dangerBg;    // runtime / sidecar

  return (
    <svg viewBox="0 0 1100 660" width="100%" style={{ display:'block' }}>
      {h.defs({ ah: h.txtS })}

      {/* Legend */}
      {h.box(10, 10, 185, 172, h.bgP, h.sq)}
      {h.lbl(102, 26, "图例 Legend", h.txtS, 9, "600")}
      {h.box(20, 34, 60, 16, c0bg, c0)}{h.lbl(85, 45, "零依赖核心", h.txtT, 8)}
      {h.box(20, 56, 60, 16, c1bg, c1)}{h.lbl(85, 67, "SPI 接口层", h.txtT, 8)}
      {h.box(20, 78, 60, 16, c2bg, c2)}{h.lbl(88, 89, "协议适配实现", h.txtT, 8)}
      {h.box(20, 100, 60, 16, c5bg, c5)}{h.lbl(85, 111, "函数实现", h.txtT, 8)}
      {h.box(20, 122, 60, 16, c6bg, c6)}{h.lbl(85, 133, "配置中心", h.txtT, 8)}
      {h.box(20, 144, 60, 16, c3bg, c3)}{h.lbl(85, 155, "管理后台", h.txtT, 8)}
      {h.box(20, 166, 60, 16, c7bg, c7)}{h.lbl(85, 177, "运行面 / sidecar", h.txtT, 8)}

      {/* fluxion-core (no deps) */}
      {h.box(250, 10, 210, 52, c0bg, c0)}
      {h.lbl(355, 28, "fluxion-core", c0, 11, "700")}
      {h.sub(355, 42, "Java 21 + Kotlin · 零框架依赖")}
      {h.sub(355, 54, "Engine / FP Abstractions / Retry / Saga")}

      {/* fluxion-adapter-spi */}
      {h.box(250, 90, 210, 46, c1bg, c1)}
      {h.lbl(355, 108, "fluxion-adapter-spi", c1, 10, "700")}
      {h.sub(355, 122, "UnifiedRequest / EngineResult")}
      {h.sub(355, 133, "WorkflowRouter（适配器 SPI 边界）")}
      {h.arr(355, 62, 355, 90, c0)}
      <text x="363" y="80" fill={c0} fontSize="8">depends on</text>

      {/* fluxion-adapter-http 能力域分组 */}
      <rect x="30" y="170" width="170" height="110" rx="5" fill="none" stroke={c2} strokeWidth="1" strokeDasharray="5,3"/>
      <text x="115" y="183" textAnchor="middle" fill={c2} fontSize="9" fontWeight="600">fluxion-adapter-http</text>

      {h.box(40, 190, 150, 35, c2bg, c2)}
      {h.lbl(115, 202, "http-core", c2, 8, "700")}
      {h.sub(115, 214, "HttpRouteDefinition /")}
      {h.sub(115, 224, "RouteConfigStore SPI")}

      {h.box(40, 235, 150, 35, c2bg, c2)}
      {h.lbl(115, 247, "http-springmvc-boot", c2, 8, "700")}
      {h.sub(115, 259, "Spring MVC + Apollo/Nacos")}
      {h.sub(115, 269, "统一自动装配")}

      {/* fluxion-adapter-rpc 能力域分组 */}
      <rect x="215" y="170" width="330" height="110" rx="5" fill="none" stroke={c2} strokeWidth="1" strokeDasharray="5,3"/>
      <text x="380" y="183" textAnchor="middle" fill={c2} fontSize="9" fontWeight="600">fluxion-adapter-rpc</text>

      {h.box(225, 190, 90, 40, c2bg, c2)}
      {h.lbl(270, 205, "rpc-dubbo", c2, 8, "700")}
      {h.sub(270, 217, "Apache Dubbo")}
      {h.sub(270, 228, "零 Spring")}

      {h.box(325, 190, 90, 40, c2bg, c2)}
      {h.lbl(370, 205, "rpc-grpc", c2, 8, "700")}
      {h.sub(370, 217, "gRPC")}
      {h.sub(370, 228, "零 Spring")}

      {h.box(425, 190, 110, 40, c2bg, c2)}
      {h.lbl(480, 205, "rpc-spring-boot", c2, 8, "700")}
      {h.sub(480, 217, "@DubboService / @GrpcService")}
      {h.sub(480, 228, "Spring Boot 装配")}

      {/* fluxion-adapter-mq 能力域分组 */}
      <rect x="560" y="170" width="155" height="110" rx="5" fill="none" stroke={c2} strokeWidth="1" strokeDasharray="5,3"/>
      <text x="637" y="183" textAnchor="middle" fill={c2} fontSize="9" fontWeight="600">fluxion-adapter-mq</text>

      {h.box(570, 190, 135, 40, c2bg, c2)}
      {h.lbl(637, 205, "mq-kafka", c2, 8, "700")}
      {h.sub(637, 217, "KafkaProducer / Consumer")}
      {h.sub(637, 228, "零 Spring")}

      {h.box(570, 240, 135, 40, c2bg, c2)}
      {h.lbl(637, 255, "mq-spring-boot", c2, 8, "700")}
      {h.sub(637, 267, "SmartLifecycle 启动")}
      {h.sub(637, 278, "Spring Boot 装配")}

      {/* SPI → adapters */}
      {h.arr(250, 120, 115, 190, c1)}
      {h.arr(300, 136, 270, 190, c1)}
      {h.arr(350, 136, 370, 190, c1)}
      {h.arr(400, 136, 480, 190, c1)}
      {h.arr(420, 120, 637, 210, c1)}

      {/* fluxion-admin */}
      {h.box(190, 360, 240, 24, c3bg, c3)}
      {h.lbl(310, 376, "fluxion-admin", c3, 10, "700")}
      {h.sub(310, 385, "Kotlin + Spring Boot · Admin API / MetaWorkflowLoader / ConfigSyncManager")}

      {/* adapters → admin */}
      {h.arr(115, 270, 230, 360, c2)}
      {h.arr(270, 230, 260, 360, c2)}
      {h.arr(370, 230, 290, 360, c2)}
      {h.arr(480, 230, 320, 360, c2)}
      {h.arr(637, 280, 390, 360, c2)}

      {/* ── Right column: function implementations ── */}

      {/* fluxion-script 能力域分组 */}
      <rect x="750" y="10" width="220" height="95" rx="5" fill="none" stroke={c5} strokeWidth="1" strokeDasharray="5,3"/>
      <text x="860" y="23" textAnchor="middle" fill={c5} fontSize="9" fontWeight="600">fluxion-script</text>

      {h.box(760, 28, 200, 32, c5bg, c5)}
      {h.lbl(860, 44, "script-core", c5, 8, "700")}
      {h.sub(860, 54, "Groovy / GraalVM JS")}
      {h.sub(860, 64, "零 Spring")}
      {h.arr(460, 44, 760, 44, c0)}

      {h.box(760, 65, 200, 32, c5bg, c5)}
      {h.lbl(860, 81, "script-spring-boot", c5, 8, "700")}
      {h.sub(860, 91, "ScriptEngineAutoConfiguration")}
      {h.sub(860, 101, "Spring Boot 装配")}

      {/* fluxion-builtin-functions 能力域分组 */}
      <rect x="750" y="115" width="220" height="95" rx="5" fill="none" stroke={c5} strokeWidth="1" strokeDasharray="5,3"/>
      <text x="860" y="128" textAnchor="middle" fill={c5} fontSize="9" fontWeight="600">fluxion-builtin-functions</text>

      {h.box(760, 133, 200, 32, c5bg, c5)}
      {h.lbl(860, 149, "builtin-functions-core", c5, 8, "700")}
      {h.sub(860, 159, "HTTP / JSON / MQ / DB")}
      {h.sub(860, 169, "零 Spring")}
      {h.arr(460, 149, 760, 149, c0)}

      {h.box(760, 170, 200, 32, c5bg, c5)}
      {h.lbl(860, 186, "builtin-functions-spring-boot", c5, 8, "700")}
      {h.sub(860, 196, "BuiltinAutoConfiguration")}
      {h.sub(860, 206, "Spring Boot 装配")}

      {/* ── fluxion-config 能力域分组 ── */}
      <rect x="750" y="220" width="220" height="270" rx="5" fill="none" stroke={c6} strokeWidth="1" strokeDasharray="5,3"/>
      <text x="860" y="235" textAnchor="middle" fill={c6} fontSize="9" fontWeight="600">fluxion-config 能力域</text>

      {h.box(760, 240, 200, 36, c6bg, c6)}
      {h.lbl(860, 256, "fluxion-config-core", c6, 9, "700")}
      {h.sub(860, 266, "公共抽象 / SnapshotParser")}
      {h.arr(460, 260, 760, 260, c1)}

      {h.box(760, 285, 200, 36, c6bg, c6)}
      {h.lbl(860, 301, "fluxion-config-http", c6, 9, "700")}
      {h.sub(860, 311, "Admin → Worker HTTP 推送")}
      {h.arr(460, 305, 760, 305, c1)}

      {h.box(760, 330, 200, 36, c6bg, c6)}
      {h.lbl(860, 346, "fluxion-config-apollo", c6, 9, "700")}
      {h.sub(860, 356, "Apollo 配置中心实现")}
      {h.arr(460, 350, 760, 350, c1)}

      {h.box(760, 375, 200, 36, c6bg, c6)}
      {h.lbl(860, 391, "fluxion-config-nacos", c6, 9, "700")}
      {h.sub(860, 401, "Nacos 配置中心实现")}
      {h.arr(460, 395, 760, 395, c1)}

      {h.box(760, 420, 200, 36, c6bg, c6)}
      {h.lbl(860, 436, "fluxion-config-spring-boot", c6, 9, "700")}
      {h.sub(860, 446, "自动装配 Apollo/Nacos/HTTP")}
      {h.arr(460, 440, 760, 440, c1)}

      {/* admin → config center (admin uses config publisher) */}
      <line x1="430" y1="372" x2="750" y2="330" stroke={c6} strokeWidth="1" strokeDasharray="5,3"/>
      <text x="585" y="390" fill={c6} fontSize="7" textAnchor="middle">Admin 通过 config-publisher 推送</text>

      {/* ── fluxion-runtime 能力域分组（运行面 / sidecar）── */}
      <rect x="750" y="500" width="220" height="110" rx="5" fill="none" stroke={c7} strokeWidth="1" strokeDasharray="5,3"/>
      <text x="860" y="515" textAnchor="middle" fill={c7} fontSize="9" fontWeight="600">fluxion-runtime 能力域</text>

      {h.box(760, 520, 200, 28, c7bg, c7)}
      {h.lbl(860, 534, "fluxion-runtime-core", c7, 8, "700")}
      {h.sub(860, 544, "RuntimeWorkflowRouter（零 Spring）")}
      {h.arr(440, 136, 860, 534, c1)}

      {h.box(760, 553, 200, 28, c7bg, c7)}
      {h.lbl(860, 567, "fluxion-runtime-spring-boot", c7, 8, "700")}
      {h.sub(860, 577, "ConfigBackedDefinitionProvider 自动装配")}
      {h.arr(860, 456, 860, 553, c6)}

      {h.box(760, 586, 200, 28, c7bg, c7)}
      {h.lbl(860, 600, "fluxion-runtime", c7, 8, "700")}
      {h.sub(860, 610, "可执行 sidecar 应用 · 动态路由执行入口")}

      {/* fluxion-gateway (separate process) */}
      {h.box(760, 620, 200, 24, c4bg, c4)}
      {h.lbl(860, 636, "fluxion-gateway", c4, 9, "700")}
      {h.sub(860, 645, "Python / Go · 独立进程 gRPC 服务")}

      {/* Java 8 legacy app → runtime */}
      {h.box(30, 600, 180, 28, h.bgP, h.sq)}
      {h.lbl(120, 614, "Java 8 Legacy App", h.txtS, 8.5, "600")}
      {h.sub(120, 624, "HTTP / RPC / MQ 远程调用")}
      {h.arr(210, 614, 760, 600, c7)}
      <text x="485" y="600" fill={c7} fontSize="8" textAnchor="middle">WorkflowRouteRegistry 动态路由</text>
    </svg>
  );
}

// ─── Architecture Flow SVG (original, preserved) ─────────────────────────────
function ArchitectureFlowSvg({ tokens: t }: { tokens: any }) {
  const h = mkHelpers(t);
  const cAdapter=t.chart.blue, cAdapterBg=t.status.infoBg;
  const cEngine=t.chart.teal,  cEngineBg=t.status.successBg;
  const cFp=t.chart.green,     cFpBg=t.status.successBg;
  const cRegistry=t.chart.goldenYellow, cRegistryBg=t.status.warningBg;
  const cImpl=t.chart.cyan,    cImplBg=t.status.infoBg;
  const cPolyglot=t.chart.purple, cPolyglotBg=t.fill.tertiary;
  const cMeta=t.chart.warmPink, cMetaBg=t.status.dangerBg;
  const cSaga=t.chart.brightOrange, cSagaBg=t.status.warningBg;

  return (
    <svg viewBox="0 0 900 1010" width="100%" style={{ display:'block' }}>
      {h.defs({ ah: h.txtS, ahd: cPolyglot })}

      {h.box(60,12,810,52,h.bgP,h.sq)}
      {h.lbl(465,30,"外部调用层",h.txtT,9,"600")}
      {h.box(80,38,120,22,cAdapterBg,cAdapter)}{h.lbl(140,53,"HTTP Request",cAdapter,9,"600")}
      {h.box(220,38,120,22,cAdapterBg,cAdapter)}{h.lbl(280,53,"Dubbo RPC",cAdapter,9,"600")}
      {h.box(360,38,120,22,cAdapterBg,cAdapter)}{h.lbl(420,53,"gRPC Call",cAdapter,9,"600")}
      {h.box(500,38,120,22,cAdapterBg,cAdapter)}{h.lbl(560,53,"Kafka Message",cAdapter,9,"600")}
      {h.box(640,38,110,22,cAdapterBg,cAdapter)}{h.lbl(695,53,"MQ / Admin API",cAdapter,9,"600")}
      {h.arr(140,60,140,90,h.txtS)}{h.arr(280,60,280,90,h.txtS)}{h.arr(420,60,420,90,h.txtS)}
      {h.arr(560,60,560,90,h.txtS)}{h.arr(695,60,695,90,h.txtS)}

      {h.box(60,90,810,70,cAdapterBg,cAdapter)}
      {h.lbl(465,108,"协议适配层  Adapter Layer",cAdapter,10,"600")}
      {h.box(75,112,152,38,h.bg,cAdapter)}{h.lbl(151,128,"SpringMvcDynamicAdapter",h.txtP,8.5,"600")}{h.sub(151,143,"配置中心 → registerMapping")}
      {h.box(240,112,130,38,h.bg,cAdapter)}{h.lbl(305,128,"DubboWorkflowServiceImpl",h.txtP,8.5,"600")}{h.sub(305,143,"DubboWorkflowApi 实现")}
      {h.box(385,112,130,38,h.bg,cAdapter)}{h.lbl(450,128,"GrpcWorkflowServiceImpl",h.txtP,8.5,"600")}{h.sub(450,143,"WorkflowServiceImplBase")}
      {h.box(530,112,140,38,h.bg,cAdapter)}{h.lbl(600,128,"KafkaWorkflowConsumer",h.txtP,8.5,"600")}{h.sub(600,143,"SmartLifecycle 启停控制")}
      {h.box(685,112,170,38,cMetaBg,cMeta)}{h.lbl(770,128,"MetaWorkflow 自举",cMeta,8.5,"600")}{h.sub(770,143,"Admin API = META WorkflowDef")}
      {h.arr(151,150,380,180,h.txtS)}{h.arr(305,150,400,180,h.txtS)}{h.arr(450,150,440,180,h.txtS)}{h.arr(600,150,480,180,h.txtS)}{h.arr(770,150,540,180,cMeta)}

      {h.box(60,175,810,58,h.bgP,h.sq)}
      {h.box(270,185,230,38,h.bg,cEngine)}{h.lbl(385,200,"WorkflowRouter",cEngine,10,"700")}{h.sub(385,215,"UnifiedRequest → WorkflowDefinition → Engine")}
      {h.box(520,185,170,38,cEngineBg,cEngine)}{h.lbl(605,200,"WorkflowDefinitionCache",cEngine,9,"600")}{h.sub(605,215,"Caffeine L1 + L2/L3 推送热加载")}
      {h.box(705,185,150,38,cEngineBg,cEngine)}{h.lbl(780,200,"ConfigSyncManager",cEngine,9,"600")}{h.sub(780,215,"L1 DB轮询 / L2 Apollo / L3 Nacos")}
      {h.arr(500,204,520,204,cEngine)}{h.arr(690,204,705,204,cEngine)}
      {h.arr(325,223,230,265,h.txtS)}{h.arr(385,223,420,265,h.txtS)}{h.arr(445,223,610,265,cSaga)}

      {h.box(60,255,810,95,cEngineBg,cEngine)}
      {h.lbl(465,273,"执行引擎层  Execution Engine",cEngine,10,"600")}
      {h.box(75,278,230,62,h.bg,cEngine)}{h.lbl(190,295,"WorkflowEngine",cEngine,10,"700")}{h.sub(190,308,"线性流 · Java")}{h.sub(190,320,"ImmutableState 快照驱动")}{h.sub(190,332,"withTimeout / ExpressionEvaluator")}
      {h.box(320,278,230,62,h.bg,cEngine)}{h.lbl(435,295,"DagExecutor",cEngine,10,"700")}{h.sub(435,308,"并行 DAG · Kotlin Coroutines")}{h.sub(435,320,"topologicalSort (Kahn)")}{h.sub(435,332,"awaitFirst (coroutines select)")}
      {h.box(565,278,230,62,cSagaBg,cSaga)}{h.lbl(680,295,"SagaExecutor",cSaga,10,"700")}{h.sub(680,308,"补偿事务 · 逆序回滚")}{h.sub(680,320,"FunctionResult.sideEffects 驱动")}{h.sub(680,332,"CompensationEntry 补偿栈")}
      {h.arr(190,340,260,375,h.txtS)}{h.arr(435,340,435,375,h.txtS)}{h.arr(680,340,600,375,h.txtS)}

      {h.box(60,365,810,110,cFpBg,cFp)}
      {h.lbl(465,383,"函数式核心抽象  FP Core",cFp,10,"700")}
      <text x="465" y="396" textAnchor="middle" fill={cFp} fontSize="8" fontStyle="italic">「一切皆函数」的工程化实现 — 调用栈类比是直觉入口，函数组合才是本质</text>
      {h.box(75,402,175,62,h.bg,cFp)}{h.lbl(162,418,"WorkflowFunction<O>",cFp,9,"700")}{h.sub(162,430,"纯函数 · 引用透明")}{h.sub(162,442,"apply(NodeInput)→FunctionResult")}{h.sub(162,454,"andThen() / fallback()")}
      {h.box(262,402,155,62,h.bg,cFp)}{h.lbl(339,418,"NodeInput",cFp,9,"700")}{h.sub(339,430,"CPU 栈帧形参类比")}{h.sub(339,442,"directInput+declaredDeps")}{h.sub(339,454,"+workflowInput+nodeParams")}
      {h.box(429,402,165,62,h.bg,cFp)}{h.lbl(511,418,"FunctionResult<O>",cFp,9,"700")}{h.sub(511,430,"IO Monad 类比")}{h.sub(511,442,"output+status+sideEffects")}{h.sub(511,454,"副作用显式 → Saga 感知")}
      {h.box(606,402,250,62,h.bg,cFp)}{h.lbl(731,418,"ImmutableExecutionState",cFp,9,"700")}{h.sub(731,430,"State Monad · Append-Only")}{h.sub(731,442,"withNodeOutput()→新实例")}{h.sub(731,454,"mergeNodeOutputs() DAG合并")}
      {h.arr(435,464,435,496,h.txtS)}

      {h.box(60,486,810,80,cRegistryBg,cRegistry)}
      {h.lbl(465,503,"函数注册 + 装饰器链",cRegistry,10,"600")}
      {h.box(75,510,330,46,h.bg,cRegistry)}{h.lbl(240,526,"FunctionRegistry 多版本共存",cRegistry,9,"700")}{h.sub(240,538,"custom > builtin > script > external")}{h.sub(240,550,"ACTIVE + RETIRING · inFlight 计数 · purgeRetiring")}
      {h.box(420,510,430,46,h.bg,cRegistry)}{h.lbl(635,526,"NodeDecorator 装饰器链",cRegistry,9,"700")}{h.sub(635,538,"metrics→trace→rateLimit→cache→async")}{h.sub(635,550,"RetryScheduler(HashedWheelTimer) 非阻塞重试")}
      {h.arr(240,556,240,588,h.txtS)}{h.arr(635,556,635,588,h.txtS)}

      {h.box(60,578,810,70,cImplBg,cImpl)}
      {h.lbl(465,595,"函数实现层  Function Implementations",cImpl,10,"600")}
      {h.box(75,604,118,36,h.bg,cImpl)}{h.lbl(134,618,"DB 函数组",cImpl,9,"600")}{h.sub(134,631,"Query/Insert/Update/Delete")}
      {h.box(203,604,100,36,h.bg,cImpl)}{h.lbl(253,618,"HTTP 函数组",cImpl,9,"600")}{h.sub(253,631,"httpCall")}
      {h.box(313,604,110,36,h.bg,cImpl)}{h.lbl(368,618,"Redis 函数",cImpl,9,"600")}{h.sub(368,631,"builtin:redisCommand SPI")}
      {h.box(433,604,115,36,h.bg,cImpl)}{h.lbl(490,618,"Transform",cImpl,9,"600")}{h.sub(490,631,"JMESPath/JSONata")}
      {h.box(558,604,105,36,h.bg,cImpl)}{h.lbl(610,618,"脚本函数组",cImpl,9,"600")}{h.sub(610,631,"Groovy/GraalVM JS")}
      {h.box(673,604,180,36,h.bg,cImpl)}{h.lbl(763,618,"横切函数组",cImpl,9,"600")}{h.sub(763,631,"responseWrapper/mqPublish")}
      {h.dash(610,648,610,688,cPolyglot)}

      <line x1="60" y1="672" x2="870" y2="672" stroke={cPolyglot} strokeWidth="1.5" strokeDasharray="8,4"/>
      <rect x="330" y="661" width="220" height="20" rx="3" fill={cPolyglotBg} stroke={cPolyglot} strokeWidth="1"/>
      <text x="440" y="675" textAnchor="middle" fill={cPolyglot} fontSize="9" fontWeight="700">── 跨进程边界 Function Gateway ──</text>

      {h.box(60,682,810,80,cPolyglotBg,cPolyglot)}
      {h.lbl(465,700,"多语言接入层  Polyglot",cPolyglot,10,"600")}
      {h.box(200,706,200,46,h.bg,cPolyglot)}{h.lbl(300,722,"FunctionGatewayServer",cPolyglot,9,"700")}{h.sub(300,734,"InvokeFunction/Stream")}{h.sub(300,746,"sideEffects 必须回传")}
      {h.box(430,706,150,46,h.bg,cPolyglot)}{h.lbl(505,722,"Python Runtime",cPolyglot,9,"700")}{h.sub(505,734,"Port 9001")}{h.sub(505,746,"WorkflowFunction SDK")}
      {h.box(600,706,140,46,h.bg,cPolyglot)}{h.lbl(670,722,"Go Runtime",cPolyglot,9,"700")}{h.sub(670,734,"Port 9002")}{h.sub(670,746,"gRPC stub")}
      {h.dash(300,706,300,672,cPolyglot)}{h.dash(400,729,430,729,cPolyglot)}{h.dash(580,729,600,729,cPolyglot)}

      <text x="465" y="1002" textAnchor="middle" fill={h.txtT} fontSize="8">
        数据流：外部请求 → UnifiedRequest → WorkflowDefinition → Engine → FunctionRegistry → WorkflowFunction → FunctionResult → EngineResult
      </text>
    </svg>
  );
}

// ─── FP Philosophy Table ──────────────────────────────────────────────────────
function PhilosophySection() {
  return (
    <Card>
      <CardHeader title="函数式设计原则" />
      <CardBody>
        <Table
          headers={['FP 设计原则','引擎实现方式','工程类比']}
          rows={[
            ['纯函数','apply(NodeInput) → FunctionResult — 返回值是唯一输出通道，无副作用','相同输入→确定性输出'],
            ['不可变状态','ImmutableExecutionState.withNodeOutput() → 新实例，旧快照永久保留','State Monad · Append-Only'],
            ['显式副作用','sideEffects 字段显式声明，Saga 补偿感知','IO Monad'],
            ['函数组合','andThen(after) 链式管道，节点输出即下一节点输入','UNIX 管道'],
            ['引用透明','相同 NodeInput → 确定性输出，天然可重放、可调试','FP 核心性质'],
            ['显式依赖','dependsOn:["n1"] 编译期依赖图，可静态分析','Dataflow 编程'],
          ]}
          rowTone={['success','success','success','success','success','success']}
        />
      </CardBody>
    </Card>
  );
}

// ─── Main Export ───────────────────────────────────────────────────────────────
export default function WorkflowV2ArchitectureFull() {
  const { tokens } = useHostTheme();

  return (
    <Stack gap={20} style={{ padding: 24 }}>
      <Stack gap={8}>
        <H1>函数式工作流平台 — 架构全景（含管理后台 + 执行流程）</H1>
        <Row gap={8} wrap>
          <Pill tone="success">一切皆函数 (FP)</Pill>
          <Pill tone="info">不可变执行状态</Pill>
          <Pill tone="primary">显式副作用 / Saga</Pill>
          <Pill tone="warning">时间轮重试 / DAG 并行</Pill>
          <Pill tone="danger">MetaWorkflow 自举</Pill>
        </Row>
      </Stack>

      <Divider />

      <H2>管理后台架构  Admin Backend</H2>
      <Callout tone="info">
        Admin API 本身由 category=META 的 WorkflowDefinition 驱动（元工作流自举）。
        ConfigSyncManager 通过三级热加载（DB轮询 / Apollo / Nacos）驱动 WorkflowDefinitionCache 刷新，
        WorkflowRouter 对热加载完全透明。
      </Callout>
      <Card variant="borderless">
        <CardBody style={{ padding: 0 }}>
          <AdminBackendSvg tokens={tokens} />
        </CardBody>
      </Card>

      <Divider />

      <H2>平台整体分层架构  Full Architecture Layers</H2>
      <Card variant="borderless">
        <CardBody style={{ padding: 0 }}>
          <ArchitectureFlowSvg tokens={tokens} />
        </CardBody>
      </Card>

      <Divider />

      <H2>工作流执行流程  Execution Process Flow</H2>
      <Callout tone="success">
        单次工作流执行的完整生命周期：从外部请求到 EngineResult，包含错误策略（SKIP/FALLBACK/RETRY/FAIL）、
        时间轮非阻塞重试、Saga 补偿路径三条关键分支。
      </Callout>
      <Card variant="borderless">
        <CardBody style={{ padding: 0 }}>
          <ProcessFlowSvg tokens={tokens} />
        </CardBody>
      </Card>

      <Divider />

      <H2>模块依赖关系  Module Dependency</H2>
      <Card variant="borderless">
        <CardBody style={{ padding: 0 }}>
          <ModuleDependencySvg tokens={tokens} />
        </CardBody>
      </Card>

      <Divider />

      <PhilosophySection />

      <Divider />

      <H2>核心类型速查</H2>
      <Grid columns={4} gap={12}>
        <Card>
          <CardHeader title="WorkflowFunction&lt;O&gt;" />
          <CardBody>
            <Stack gap={4}>
              <Tag tone="success">FunctionalInterface · 纯函数</Tag>
              <Text size="small" tone="secondary">apply(NodeInput) → FunctionResult&lt;O&gt;</Text>
              <Text size="small" tone="secondary">andThen(after) 函数组合</Text>
              <Text size="small" tone="secondary">fallback(input, ex) 降级处理</Text>
              <Text size="small" tone="secondary">meta() 元信息 (name/inputSchema)</Text>
            </Stack>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="ImmutableExecutionState" />
          <CardBody>
            <Stack gap={4}>
              <Tag tone="success">State Monad · Append-Only</Tag>
              <Text size="small" tone="secondary">start(def, rawInput) 版本锁定初始化</Text>
              <Text size="small" tone="secondary">withNodeOutput(id, out) → 新实例</Text>
              <Text size="small" tone="secondary">mergeNodeOutputs(map) DAG 原子合并</Text>
              <Text size="small" tone="secondary">天然无锁，DAG 并发读写安全</Text>
            </Stack>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="FunctionRegistry" />
          <CardBody>
            <Stack gap={4}>
              <Tag tone="warning">多版本共存 · 热发布安全</Tag>
              <Text size="small" tone="secondary">register(name, version, meta, fn)</Text>
              <Text size="small" tone="secondary">ACTIVE + RETIRING 双版本槽</Text>
              <Text size="small" tone="secondary">InFlightTrackingFunction 自动计数</Text>
              <Text size="small" tone="secondary">purgeRetiring() 无在途时清理旧版</Text>
            </Stack>
          </CardBody>
        </Card>
        <Card>
          <CardHeader title="RetryScheduler (时间轮)" />
          <CardBody>
            <Stack gap={4}>
              <Tag tone="warning">HashedWheelTimer · 非阻塞</Tag>
              <Text size="small" tone="secondary">O(1) 注册/取消 (vs 红黑树 O log n)</Text>
              <Text size="small" tone="secondary">schedule(supplier, delayMs) → Future</Text>
              <Text size="small" tone="secondary">时间轮线程只推进槽位，不执行业务</Text>
              <Text size="small" tone="secondary">retryWorker 线程池执行实际函数调用</Text>
            </Stack>
          </CardBody>
        </Card>
      </Grid>

      <Divider />

      <H2>后续演进方向 Phase 2~5</H2>
      <Card>
        <CardBody>
          <Grid columns={4} gap={12}>
            <Card>
              <CardHeader title="Phase 2 · 结构共享" />
              <CardBody>
                <Stack gap={4}>
                  <Tag tone="info">Clojure 持久化数据结构</Tag>
                  <Text size="small" tone="secondary">HAMT / 持久化 Map</Text>
                  <Text size="small" tone="secondary">ExecutionTrace 持久化</Text>
                  <Text size="small" tone="secondary">精确重放 / 断点恢复</Text>
                </Stack>
              </CardBody>
            </Card>
            <Card>
              <CardHeader title="Phase 3 · Actor & Supervisor" />
              <CardBody>
                <Stack gap={4}>
                  <Tag tone="info">Erlang Actor 模型</Tag>
                  <Text size="small" tone="secondary">FunctionActor 封装调用</Text>
                  <Text size="small" tone="secondary">节点级故障隔离</Text>
                  <Text size="small" tone="secondary">Restart / Compensate / Terminate</Text>
                </Stack>
              </CardBody>
            </Card>
            <Card>
              <CardHeader title="Phase 4 · Pattern Matching" />
              <CardBody>
                <Stack gap={4}>
                  <Tag tone="info">Scala 模式匹配</Tag>
                  <Text size="small" tone="secondary">match / when DSL</Text>
                  <Text size="small" tone="secondary">ADT 密封类型分支</Text>
                  <Text size="small" tone="secondary">编译期穷尽性检查</Text>
                </Stack>
              </CardBody>
            </Card>
            <Card>
              <CardHeader title="Phase 5 · Data-as-Code" />
              <CardBody>
                <Stack gap={4}>
                  <Tag tone="info">Clojure 数据即代码</Tag>
                  <Text size="small" tone="secondary">Workflow DSL 纯数据化</Text>
                  <Text size="small" tone="secondary">宏节点展开</Text>
                  <Text size="small" tone="secondary">元工作流自举生成</Text>
                </Stack>
              </CardBody>
            </Card>
          </Grid>
        </CardBody>
      </Card>

      <Divider />

      <Card>
        <CardHeader title="模块依赖层次（构建顺序）" />
        <CardBody>
          <Table
            headers={['模块','语言','核心内容','依赖']}
            rows={[
              ['fluxion-core','Java 21 + Kotlin','Engine / FP Abstractions / RetryScheduler / Saga','零依赖'],
              ['fluxion-adapter-spi','Java','UnifiedRequest / EngineResult / WorkflowRouter','fluxion-core'],
              ['fluxion-adapter-http:core','Kotlin','HttpRouteDefinition / RouteConfigStore SPI','fluxion-adapter-spi'],
              ['fluxion-adapter-http:springmvc','Kotlin','动态 registerMapping / WorkflowHttpHandler','http-core + Spring MVC'],
              ['fluxion-adapter-http:springmvc-nacos','Kotlin','NacosRouteConfigStore（可选引入）','http-springmvc + Nacos'],
              ['fluxion-adapter-http:springmvc-apollo','Kotlin','ApolloRouteConfigStore（可选引入）','http-springmvc + Apollo'],
              ['fluxion-adapter-http:springmvc-boot','Kotlin','统一自动装配 Apollo/Nacos/Spring MVC','http-springmvc + Spring Boot'],
              ['fluxion-adapter-rpc:dubbo','Kotlin','GenericService 泛化暴露','fluxion-adapter-spi + Dubbo'],
              ['adapter-grpc','Kotlin','WorkflowGatewayImplBase','spi + gRPC'],
              ['adapter-kafka','Kotlin','KafkaListenerEndpointRegistry 动态订阅','spi + Spring Kafka'],
              ['decorator-impl-core','Java','5 装饰器实现 + MicrometerMetrics（零 Spring Boot）','fluxion-core'],
              ['decorator-impl-spring-boot','Java','DecoratorImplAutoConfiguration 装配桥接','decorator-impl-core'],
              ['fluxion-redis-core','Java','RedisClientAdapter SPI + builtin:redisCommand（零 Spring Boot）','fluxion-core'],
              ['fluxion-redis-spring-boot','Java','RedisWorkflowAutoConfiguration 装配桥接','redis-core'],
              ['fluxion-redis-lettuce','Java','LettuceRedisAdapter（120+ 命令 + 真 Pipeline）','redis-core + Lettuce'],
              ['fluxion-redis-redisson','Java','RedissonRedisAdapter（类型化 API + 原生分布式锁）','redis-core + Redisson'],
              ['fluxion-script:core','Kotlin','Groovy / GraalVM JS 脚本函数（零 Spring）','fluxion-core + Groovy/JS'],
              ['fluxion-script:spring-boot','Kotlin','ScriptEngineAutoConfiguration 装配','script-core + Spring Boot'],
              ['fluxion-schema:core','Kotlin','SchemaManager · ExternalSchemaRegistry SPI · InMemorySchemaRegistry','fluxion-core'],
              ['fluxion-schema:json','Kotlin','JSON Schema 解析与校验','schema-core'],
              ['fluxion-schema:protobuf','Kotlin','Protobuf Schema 支持','schema-core'],
              ['fluxion-schema:avro','Kotlin','Avro Schema 支持','schema-core'],
              ['fluxion-schema:registry-confluent','Kotlin','Confluent Schema Registry 适配','schema-core'],
              ['fluxion-schema:registry-aws-glue','Kotlin','AWS Glue Schema Registry 适配','schema-core'],
              ['fluxion-schema:registry-azure','Kotlin','Azure Schema Registry 适配','schema-core'],
              ['fluxion-schema:registry-apicurio','Kotlin','Apicurio Schema Registry 适配','schema-core'],
              ['fluxion-schema:spring-boot','Kotlin','FluxionSchemaAutoConfiguration 装配','schema-core + Spring Boot'],
              ['fluxion-registry:core','Kotlin','InstanceRegistry · InstanceInfo（零 Spring）','fluxion-core'],
              ['fluxion-registry:spring-boot','Kotlin','SpringCloudInstanceRegistry 自动装配','registry-core + Spring Cloud Commons'],
              ['fluxion-discovery:core','Kotlin','InstanceDiscovery SPI（零 Spring）','fluxion-registry:core'],
              ['fluxion-discovery:spring-boot','Kotlin','SpringCloudInstanceDiscovery 自动装配','discovery-core + Spring Cloud DiscoveryClient'],
              ['fluxion-builtin-functions:core','Kotlin','HTTP / JSON / MQ / DB 内置函数（零 Spring）','fluxion-core + OkHttp/Jackson'],
              ['fluxion-builtin-functions:spring-boot','Kotlin','BuiltinAutoConfiguration 装配','builtin-functions-core + Spring Boot'],
              ['fluxion-config-core','Kotlin','Subscriber/Publisher 公共抽象 / HttpPushClient / SnapshotParser','fluxion-adapter-spi'],
              ['fluxion-config-http','Kotlin','HTTP 配置中心（Admin → Worker 推送）','fluxion-config-core'],
              ['fluxion-config-apollo','Kotlin','Apollo 配置中心实现','fluxion-config-core + Apollo'],
              ['fluxion-config-nacos','Kotlin','Nacos 配置中心实现','fluxion-config-core + Nacos'],
              ['fluxion-runtime-core','Kotlin','RuntimeWorkflowRouter / ExecutionSnapshotStore SPI（零 Spring）','fluxion-adapter-spi'],
              ['fluxion-runtime-spring-boot','Kotlin','自动装配 ConfigBackedDefinitionProvider / LoggingExecutionSnapshotStore','fluxion-runtime-core + fluxion-config-spring-boot'],
              ['fluxion-runtime','Kotlin + Spring Boot','可执行 sidecar 应用 · 动态路由执行入口','fluxion-runtime-spring-boot + 全部函数实现'],
              ['fluxion-gateway','Python/Go','FunctionGatewayServer gRPC Servicer','function_gateway.proto'],
              ['fluxion-admin','Kotlin + Spring Boot','Admin API / MetaWorkflowLoader / ConfigSyncManager','全部模块'],
            ]}
          />
        </CardBody>
      </Card>

      <Text tone="tertiary" size="small" style={{ textAlign: 'center' }}>
        fluxion-design.md · docs/fluxion-architecture.canvas.tsx
      </Text>
    </Stack>
  );
}

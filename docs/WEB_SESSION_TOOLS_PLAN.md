# Web 会话工具扩展规划（保留 `visit_web` 原样）

## 目标
- 保留现有 `visit_web` 行为与接口，不做破坏性改动。
- 新增会话型网页工具链：先 `start_web` / `stop_web`，再逐步补齐网页操作能力。
- 在 Kotlin 侧先开放稳定接口；`examples` 侧提供 Playwright 风格封装。

## 约束与原则
- `visit_web` 保持原样（兼容历史调用与工作流）。
- 新工具使用独立命名，不覆盖旧语义。
- 先做 MVP（可用优先），再扩展高级能力。
- 按 `docs/DEFAULT_TOOLS_ARCH.md` 的 checklist 全链路更新：Prompt/注册/实现/JS 封装/类型/示例/文档/产物同步。

## 阶段划分

### 阶段 1：会话骨架（必须）
1. 在 `SystemToolPromptsInternal.kt` 增加工具定义：
   - `start_web(url?, headers?, user_agent?, session_name?)`
   - `stop_web(session_id, close_all?)`
2. 在 `ToolRegistration.kt` 注册新工具（并保留 `visit_web` 现有注册）。
3. 新增 `WebSessionManager`（建议）：
   - 管理 `session_id -> WebView/悬浮窗状态`。
   - 管理主线程调用、会话生命周期、资源释放。
   - 支持多会话（至少串行安全）。
4. `start_web` 行为：
   - 创建（或复用）悬浮窗 WebView。
   - 可选初始化 URL 与请求头。
   - 返回 `session_id`、当前 URL、状态信息。
5. `stop_web` 行为：
   - 按 `session_id` 清理会话与 WebView。
   - 支持 `close_all` 一键收拢。

### 阶段 2：网页操作 MVP（建议同一批落地）
新增一组基于 `session_id` 的工具：
- `web_navigate(session_id, url, headers?)`
- `web_eval(session_id, script, timeout_ms?)`
- `web_click(session_id, selector, index?)`
- `web_fill(session_id, selector, value)`
- `web_wait_for(session_id, selector?, timeout_ms?)`
- `web_snapshot(session_id, include_links?, include_images?)`

MVP 目标：
- 能完成“打开页面 -> 查元素 -> 点击/输入 -> 读取信息 -> 关闭会话”的闭环。

### 阶段 3：脚本侧封装（Playwright 风格）
在 `examples` 增加封装包（TS 为源）：
- `examples/web.ts`

封装 API 建议：
- `start(options)`
- `goto(url, options?)`
- `click(selector, options?)`
- `fill(selector, value)`
- `evaluate(script)`
- `snapshot(options?)`
- `close()`

然后：
- 生成 `examples/web.js`
- 通过 `sync_example_packages.py` 同步到 `app/src/main/assets/packages/`

## 代码触点清单（按架构文档）
1. Prompt/Schema
   - `app/src/main/java/com/ai/assistance/operit/core/config/SystemToolPromptsInternal.kt`
2. 注册层
   - `app/src/main/java/com/ai/assistance/operit/core/tools/ToolRegistration.kt`
3. Kotlin 实现层
   - `app/src/main/java/com/ai/assistance/operit/core/tools/defaultTool/standard/`（新增会话工具类）
   - 必要时 `ToolGetter.kt` 接入
4. JS 封装层
   - `app/src/main/java/com/ai/assistance/operit/core/tools/javascript/JsTools.kt`
5. Types/Examples
   - `examples/types/*.d.ts`
   - `examples/*.ts`
6. 文档
   - `docs/`（本规划 + 使用说明）
7. 产物同步
   - `examples/*.js`
   - `app/src/main/assets/packages/*.js`

## 建议返回格式（便于 Agent 调用）
- `start_web`：
  - `{"session_id":"...","status":"started","url":"..."}`
- `stop_web`：
  - `{"session_id":"...","status":"stopped"}`
- 通用失败：
  - `{"error":"...","session_id":"..."}`

## 风险与注意事项
- WebView 必须在主线程操作，避免并发崩溃。
- 会话泄漏风险：异常路径也要确保 `destroy/cleanup`。
- 选择器稳定性：`web_click/web_fill` 需明确找不到元素时的错误语义。
- 与 `visit_web` 共存时要注意缓存与窗口状态隔离。

## 验收标准（MVP）
- `visit_web` 旧流程可用、行为不变。
- `start_web -> web_navigate -> web_eval -> stop_web` 全链路可跑通。
- 异常路径（无 session、超时、JS 错误）有明确错误返回。
- `examples/web.ts` 可演示完整闭环调用。

## 执行顺序建议
1. 先落地阶段 1（仅 `start_web/stop_web` + 会话管理）。
2. 再补阶段 2 中最核心的 `web_navigate/web_eval/web_click/web_fill`。
3. 最后做 `examples` 封装与文档完善。

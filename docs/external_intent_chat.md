# External Intent API: `EXTERNAL_CHAT`

本文档描述一个**独立于工作流系统**的外部交互接口：外部应用通过发送广播 Intent（`com.ai.assistance.operit.EXTERNAL_CHAT`）向 Operit 发起一次“发送消息给 AI”的请求，并通过另一个广播接收执行结果。

该接口的实现位于：

- `app/src/main/java/com/ai/assistance/operit/integrations/intent/ExternalChatReceiver.kt`

Manifest 注册：

- `AndroidManifest.xml` -> `.integrations.intent.ExternalChatReceiver`

---

## 1. Action

- **请求 Action**：`com.ai.assistance.operit.EXTERNAL_CHAT`
- **默认回传 Action**：`com.ai.assistance.operit.EXTERNAL_CHAT_RESULT`

你也可以通过 `reply_action` 指定自定义回传 action。

---

## 2. 请求参数（Intent extras）

| extra key | 类型 | 必填 | 默认值 | 说明 |
|---|---:|:---:|---:|---|
| `message` | `String` | 是 | - | 要发送给 AI 的文本 |
| `request_id` | `String` | 否 | - | 业务侧请求 ID（原样回传，便于关联请求/响应） |
| `group` | `String` | 否 | - | 当 `create_new_chat=true` 时，用于新对话分组 |
| `create_new_chat` | `Boolean` | 否 | `false` | 是否强制创建新对话再发送消息 |
| `chat_id` | `String` | 否 | - | 指定发送到某个对话（仅当 `create_new_chat=false` 时生效） |
| `create_if_none` | `Boolean` | 否 | `true` | 当未指定 `chat_id` 且当前没有对话时，是否允许自动创建对话（设为 `false` 则会报错返回） |
| `show_floating` | `Boolean` | 否 | `false` | 是否启动/显示悬浮窗服务（会触发绑定并启动 `FloatingChatService`） |
| `auto_exit_after_ms` | `Long` | 否 | `-1` | 当 `show_floating=true` 时：自动退出/关闭悬浮窗的超时（毫秒） |
| `stop_after` | `Boolean` | 否 | `false` | 本次请求结束后是否停止对话服务（会 stop `FloatingChatService`） |
| `reply_action` | `String` | 否 | `com.ai.assistance.operit.EXTERNAL_CHAT_RESULT` | 指定回传广播 action |
| `reply_package` | `String` | 否 | - | 若指定，则回传广播会设置 `intent.setPackage(reply_package)`，用于避免结果被其他 App 接收 |

---

## 3. 回传参数（Intent extras）

Operit 在处理完成后会发送一条广播（action 为 `reply_action` 或默认 action），携带如下 extras：

| extra key | 类型 | 说明 |
|---|---:|---|
| `request_id` | `String` | 若请求里携带，则原样回传 |
| `success` | `Boolean` | 是否成功 |
| `chat_id` | `String` | 发生交互的对话 ID（如果可用） |
| `ai_response` | `String` | AI 回复文本（如果可用） |
| `error` | `String` | 错误信息（失败时） |

---

## 4. 行为与优先级规则（简化版）

- 若 `message` 为空，直接失败回传。
- 若 `show_floating=true`：
  - 会尝试启动/连接 `FloatingChatService`。
  - 可通过 `auto_exit_after_ms` 设置自动退出。
- 若 `create_new_chat=true`：
  - 会新建对话（可选 `group`）。
  - 发送消息时不会使用 `chat_id`（忽略 `chat_id`）。
- 若 `create_new_chat=false` 且 `chat_id` 不为空：
  - 会先切换到指定对话并发送。
- 若 `create_new_chat=false` 且 `chat_id` 为空：
  - 默认 `create_if_none=true`：允许在没有当前对话时自动创建。
  - `create_if_none=false`：如果当前没有对话则失败回传。
- 若 `stop_after=true`：处理完会停止 `FloatingChatService`。

---

## 5. adb 示例

### 5.1 创建新对话 + 分组 + 发送消息 + 显示悬浮窗

```bash
adb shell am broadcast \
  -a com.ai.assistance.operit.EXTERNAL_CHAT \
  --es request_id "req-001" \
  --es message "你好，帮我总结一下这段话" \
  --es group "workflow" \
  --ez create_new_chat true \
  --ez show_floating true \
  --el auto_exit_after_ms 10000
```

### 5.2 发到指定 chat_id（不新建）

```bash
adb shell am broadcast \
  -a com.ai.assistance.operit.EXTERNAL_CHAT \
  --es request_id "req-002" \
  --es chat_id "YOUR_CHAT_ID" \
  --es message "继续刚才的话题"
```

### 5.3 不允许自动创建对话（create_if_none=false）

如果当前没有对话且不允许创建，会返回失败：

```bash
adb shell am broadcast \
  -a com.ai.assistance.operit.EXTERNAL_CHAT \
  --es request_id "req-003" \
  --es message "测试" \
  --ez create_if_none false
```

---

## 6. 安全建议

该 Receiver 是 `exported=true`，**任何应用都可以发送该广播**。

- 如果你担心被其他 App 滥用，建议：
  - 通过 `reply_package` 限制回传目标。
  - 后续为该 Receiver 增加自定义 permission（例如 signature 级别），并在 Manifest 中声明 `android:permission`。

---

## 7. 工作流示范：Trigger 输出 JSON + Extract(JSON) 提取参数

工作流侧（示范模板）采用的是：

- **触发器节点输出 JSON**：触发 extras 会被打包成 JSON 字符串，作为 TriggerNode 的输出（下游可以 `NodeReference(triggerId)` 拿到）。
- **提取节点 ExtractNode(JSON)**：下游用 `ExtractNode(mode=JSON)` 从 JSON 中提取字段，例如提取 `message`。

示例（概念）：

- TriggerNode 输出：`{"message":"hello","foo":"bar"}`
- ExtractNode(JSON) 参数：
  - `source = NodeReference(triggerId)`
  - `expression = "message"`
  - 输出即为 `hello`

说明：JSON 提取表达式支持 `a.b.c` 以及数组下标 `arr[0].name` 这类简单路径。

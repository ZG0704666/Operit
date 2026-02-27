/* METADATA
{
  name: "all_about_myself"
  display_name: {
    zh: "Operit配置编辑器"
    en: "Operit Config Editor"
  }
  description: {
    zh: '''软件设置直改工具包：提供一组可直接读取与修改 Operit 设置的工具，覆盖 MCP、Skill、Sandbox Package、功能模型绑定、模型参数、上下文总结与 TTS/STT 语音服务配置。'''
    en: '''Direct software-settings toolkit: a collection of tools for reading and directly modifying Operit settings, covering MCP, Skill, Sandbox Package, function-model bindings, model parameters, context-summary settings, and TTS/STT speech-service configuration.'''
  }

  enabledByDefault: true

  "category": "Chat",
  tools: [
    {
      name: "all_about_myself"
      description: {
        zh: '''配置排查手册。

【触发条件】
- 用户提到 MCP/Skill 安装失败、无法启动、工具不出现、导入失败、重名冲突、配置文件怎么改
- 用户提到沙盒包（Package）开关、内置包列表、导入删除路径、包启用状态异常
- 用户让你排查 Operit 的插件配置路径、部署目录、开关状态、环境变量
- 用户提到功能模型绑定、模型配置新增/删除/修改、模型连接测试
- 用户提到 TTS/STT 语音服务不会配置、参数太多不会填、语音播报/语音识别不可用
- 问题核心是“配置和部署链路”，而不是普通问答

【MCP：安装与排查】
0) 市场来源：
- OPR MCP 市场（issues）：https://github.com/AAswordman/OperitMCPMarket/issues
- 该市场内容较少，可在其他可信来源继续搜集 MCP，再按本手册部署。
1) 配置目录：/sdcard/Download/Operit/mcp_plugins/
- 主配置：mcp_config.json
- 运行状态缓存：server_status.json（非实时快照，仅用于状态记录与工具缓存，不作为排查判定依据）
2) 两侧路径要严格区分：
- Android 侧是源目录（用户导入/存放目录），不是最终运行目录
- Linux 侧是最终运行目录：~/mcp_plugins/<pluginId最后一段>
- MCP 真正启动时，执行目录与命令都以 Linux 侧为准
3) 本地部署实际行为（代码逻辑）：
- 创建目标目录
- 将 Android 侧插件目录复制到 Linux 侧目录
- 执行自动分析出的安装/构建命令（会跳过启动命令）
4) 命令型插件判定：
- 对于 command 为 npx/uvx/uv 的命令型插件，系统按“已部署”处理，仅做最小目录准备。
5) 系统启动行为（必须理解）：
- 本地插件：系统会读取 mcp_config.json 中该插件的 command/args/env，使用 cwd=~/mcp_plugins/<shortName> 启动进程；可用性以“工具可调用/服务可响应”为准
- 远程插件：系统按 endpoint + connectionType（可选 bearerToken/headers）发起连接并校验连通
6) command 的实际执行位置（必须按此理解）：
- 本地插件启动时，系统在 Linux 终端环境中启动进程；执行工作目录固定是 ~/mcp_plugins/<shortName>
- mcp_config.json 里填写的 command 就是在这个 Linux 工作目录上下文中被执行，不会在 Android 源目录执行
- args 里的相对路径，统一按该 cwd 解析
6.1) node 命令示例（按插件ID）：
- 例：pluginId=owner/my-plugin，则 shortName=my-plugin，cwd=~/mcp_plugins/my-plugin
- 如果入口文件在 ~/mcp_plugins/my-plugin/dist/index.js，则写：
  "command": "node"
  "args": ["dist/index.js"]
- 这里 args 必须按 cwd 写相对路径，不要写 /sdcard/... 的 Android 路径
7) mcp_config.json 字段规范：
- 顶层必须有 mcpServers（对象）
- mcpServers 的 key 是 serverId（通常与插件ID一致，避免随意改名）
- pluginId（即 mcpServers 的 key）只允许 a-zA-Z_ 和空格
- 每个 server 常用字段：
  - command（必填，启动命令）
  - args（可选，字符串数组）
  - env（可选，键值对）
  - autoApprove（可选，数组）
  - disabled（可选，true=禁用）
- MCP 的环境变量必须写在 mcpServers.<id>.env；`read_environment_variable` / `write_environment_variable` 不会读写这里。
8) mcp_config.json 路径写法规则：
- 不要把 Android 绝对路径写进本地插件启动参数（例如 /sdcard/...）
- 本地插件命令应按 Linux 运行目录编写，优先相对路径（因为 cwd 已固定到 ~/mcp_plugins/<shortName>）
- 如果必须写绝对路径，也应是 Linux 侧路径，不应写 Android 侧路径
9) 启用开关：
- 本地插件使用 mcpServers.<id>.disabled
- 远程插件使用 pluginMetadata.<id>.disabled
10) MCP 排查顺序（按顺序执行）：
- 检查开关是否启用
- 检查本地部署目录是否存在且非空
- 检查 mcp_config.json 的 command/args/env 字段是否完整
- 检查 args 是否误写 Android 路径
- 检查 env 中所需 key/token
- 不要把 server_status.json 的 active 当作唯一依据；优先看工具是否可拉取、可调用
- 检查终端依赖（node/pnpm/python/uv）与 MCP 服务状态

【Skill：安装与排查】
0) 市场来源：
- OPR Skill 市场（issues）：https://github.com/AAswordman/OperitSkillMarket/issues
- 也可从其他可信来源获取 skill，下载后解压到 skills 目录。
1) 目录：/sdcard/Download/Operit/skills/
2) 识别规则：每个 Skill 必须是一个文件夹，且包含 SKILL.md（skill.md 也可）。
3) 添加方式（按这个做）：
- 先从可信来源下载 Skill（zip 或仓库源码均可）
- 把下载内容解压后，直接放到 /sdcard/Download/Operit/skills/
- 最终目录结构必须是 /sdcard/Download/Operit/skills/<skill_name>/，且该目录内有 SKILL.md
4) 元数据解析：
- 优先读取 frontmatter 中的 name/description
- 若缺失，回退读取文件前40行里的 name:/description:
5) AI 可见性：
- 列表开关关闭后，Skill 仍在本地，但 AI 不会使用
6) Skill 排查顺序：
- 先确认路径和 SKILL.md
- 再确认是否重名冲突
- 再确认开关是否开启
- 最后检查 SKILL.md 内容是否完整（步骤/约束/输出）

【Sandbox Package：安装与排查】
1) 沙盒包目录（外部）：
- Android/data/com.ai.assistance.operit/files/packages
2) 内置包：
- 内置包来自应用内置资源，不在上述外部目录；删除内置包文件不是常规操作，通常只做开关管理。
3) 导入与删除：
- 导入：把 `.js` 或 `.toolpkg` 放入/导入到外部 packages 目录。
- 删除：外部包可直接按文件路径删除；删除前先确认是否仍被依赖。
4) 开关管理（优先使用工具）：
- 先调用 list_sandbox_packages 获取“内置+外部”包列表与当前 enabled 状态。
- 再调用 set_sandbox_package_enabled(package_name, enabled) 执行启停。
5) 制作包文档：
- https://github.com/AAswordman/Operit/blob/main/docs/SCRIPT_DEV_GUIDE.md

【Package 兼容模型（重要）】
- AI 看到的 package 是统一抽象，底层可能来自 MCP、Skill、Sandbox Package 任意一种。
- 可用包列表中的条目，不保证同类型；可能是三种类型混合出现。
- `use_package` 是三兼容入口：对 MCP/Skill/Sandbox Package 都可统一调用。
- `ping_mcp` 工具是 `use_package` 的直通封装，用于快速探测指定包是否可被加载。

【功能模型与模型配置】
1) 模型配置（Model Config）：
- 每个模型配置是一套完整连接参数（provider / endpoint / api key / model_name / 各能力开关）。
- 可以新增、删除、修改；删除默认配置 `default` 是禁止的。
2) 功能模型（Function Model）：
- 每个功能类型（如 CHAT/SUMMARY/TRANSLATION 等）会绑定到一个模型配置。
- 当一个配置里 `model_name` 写了多个模型（逗号分隔），还要指定 `model_index` 选择第几个。
3) 关键工具（优先使用）：
- `list_model_configs`：查看全部模型配置 + 当前功能绑定。
- `create_model_config`：新增模型配置（可带初始 provider/endpoint/key/model_name）。
- `update_model_config`：修改已有配置（按 config_id）。
- `delete_model_config`：删除配置（默认配置不可删）。
- `list_function_model_configs`：仅列出功能 -> 配置绑定关系（轻量）。
- `get_function_model_config`：查看某个功能当前绑定的单个配置详情。
- `set_function_model_config`：为功能指定配置与模型索引。
- `test_model_config_connection`：按设置页同等逻辑测试配置连通与多模态能力。
4) 配置修改后：
- 若该配置被某些功能使用，系统会刷新对应功能服务；绑定变更也会刷新目标功能服务。
5) 用户抱怨“输出总被截断”时：
- 可先用 `get_function_model_config` 查看对应功能绑定配置里的 `max_tokens` 参数。
- 对 DeepSeek 来说，默认常见是 4096；可将 `max_tokens_enabled` 打开并把 `max_tokens` 设到 8192 再测试。
- 对其他模型，先联网确认该模型可支持的输出上限后再设置。

【上下文总结模块（Context Summary）】
1) 用途：
- 控制会话上下文窗口与“何时触发总结”，用于降低上下文膨胀导致的丢信息、跑偏或响应不稳定。
- 只有当前对话功能模型（CHAT）所绑定配置里的上下文总结参数，会在实际对话中生效。
2) 核心机制说明：
- 软件内有一个 max 开关，即 `enable_max_context_mode`。
- 开启时使用 `max_context_length`，关闭时使用 `context_length`，目标是为用户节约 token，并按场景动态决定使用长度。
- `enable_summary` 控制是否自动总结；当上下文超过 `summary_token_threshold * 当前可用上下文长度` 时，会触发总结。
3) 建议排查顺序：
- 先用 `get_context_summary_config` 看当前功能绑定配置的上下文总结参数。
- 再用 `set_context_summary_config` 设置上下文总结参数（可传入参数覆盖默认值）。
- 若仍异常，再结合具体模型能力与业务负载做细调。

【TTS/STT 语音服务配置】
1) 配置入口：
- 在软件设置里的 Speech Services（语音服务）页面配置。
- 该页面会自动保存；改完后语音服务实例会自动重建。
2) TTS（文本转语音）可选引擎：
- `SIMPLE_TTS`：系统 TTS，基本无需填网络参数。
- `HTTP_TTS`：需重点填写 `url_template`、`headers`、`http_method`、`content_type`、`request_body`。
- `SILICONFLOW_TTS`：需填写 `api_key`、`model_name`、`voice_id`。
- `OPENAI_TTS`：需填写 `url_template`、`api_key`、`model_name`、`voice_id`。
3) STT（语音转文本）可选引擎：
- `SHERPA_NCNN`：本地识别，通常无需 API Key。
- `OPENAI_STT`：需填写 `endpoint_url`、`api_key`、`model_name`。
- `DEEPGRAM_STT`：需填写 `endpoint_url`、`api_key`、`model_name`。
4) 最常见填错点（优先检查）：
- `HTTP_TTS` 的 `headers` 不是合法 JSON（必须是对象）。
- `HTTP_TTS` 的模板没放 `{text}` 占位符（GET 通常在 URL，POST 通常在 body）。
- TTS/STT 的 endpoint 路径写错（比如把 chat/completions 写成 audio 接口）。
- `model_name` 填了不存在的模型或与接口不匹配。
- 改完配置后没有重新测试语音播报或语音识别。
5) HTTP_TTS 占位符说明（按代码逻辑）：
- 必填占位符：`{text}`。
  - 当 `http_method=GET` 时，`{text}` 必须在 `url_template` 里。
  - 当 `http_method=POST` 时，`{text}` 必须在 `request_body` 里。
- 可选占位符：`{rate}`、`{pitch}`、`{voice}`。
- 当前对外可稳定使用的占位符只有：`{text}`、`{rate}`、`{pitch}`、`{voice}`。
6) 可直接参考的最小模板：
- HTTP TTS（GET）：
  - `url_template`: `https://example.com/tts?text={text}`
  - `headers`: `{}`
  - `http_method`: `GET`
  - `content_type`: `application/json`
- HTTP TTS（POST）：
  - `url_template`: `https://example.com/tts`
  - `headers`: `{"Authorization":"Bearer <API_KEY>"}`
  - `http_method`: `POST`
  - `content_type`: `application/json`
  - `request_body`: `{"text":"{text}"}`
- OpenAI STT（默认常见）：
  - `endpoint_url`: `https://api.openai.com/v1/audio/transcriptions`
  - `model_name`: `whisper-1`
7) 排查顺序（建议）：
- 先确认选中的引擎类型是否正确（TTS 与 STT 分开看）。
- 再检查 endpoint / key / model 三件套是否完整。
- 再检查 HTTP 模板字段（headers JSON、method、body、占位符）。
- 最后做一次真实语音测试（播报 + 识别），看是否能端到端通过。
8) 对应工具（可直接用）：
- `get_speech_services_config`：获取当前 TTS/STT 配置快照（含引擎类型与关键字段）。
- `set_speech_services_config`：按字段修改 TTS/STT 配置（支持只改部分字段）。

【多模态输入规则】
1) 能力开关含义：
- 模型配置里的“支持 Tool Call / 识图 / 音频 / 视频”等开关，只是软件侧能力标识，不等于模型真实能力。
- 实际配置时，必须依据模型真实支持情况来开关，不能乱开。
2) 软件识图主链路：
- 当“对话功能模型”的模型配置开启识图且模型真实支持时：聊天附图会直接发给该模型识别。
- 当对话模型不支持识图时：软件会尝试走 OCR，或走“识图功能模型”进行识图中转。
3) 用户有识图需求时的可行条件：
- 条件 A：对话模型支持识图。
- 条件 B：对话模型不支持识图，但识图功能模型支持识图。
- 若识图功能模型也不支持识图：最终回退到 OCR。

【绘图输出说明】
- 绘图通过工具包实现。
- 软件内置了一些绘图包，可调用 list_sandbox_packages 查看。
- 通常只需启用其中一个可用绘图包即可，无需全部开启。

【执行原则】
- 严格按用户明确指示执行，不自行定义“问题”或追加未被要求的目标。
- 任何会改动用户配置的操作（开关、导入/删除、写环境变量、模型配置增删改、重启）都必须先得到用户明确确认。
- 用户没有明确要求执行某个工具时，不主动调用写入类工具。
- 回答优先基于本手册的路径与规则，避免泛化推断。'''
        en: '''Configuration troubleshooting guide.

[Trigger conditions]
- The user mentions MCP/Skill install failure, startup failure, tools not appearing, import failure, duplicate name conflicts, or config editing
- The user mentions sandbox package toggles, built-in package listing, import/delete paths, or package enable-state issues
- The user asks to troubleshoot plugin config paths, deploy directories, enable switches, or environment variables
- The user mentions function model bindings, adding/deleting/updating model configs, or testing model connectivity
- The user says TTS/STT setup is confusing, too many fields to fill, or speech playback/recognition is not working
- The core issue is configuration/deployment flow rather than normal Q&A

[MCP: install and troubleshooting]
0) Market source:
- OPR MCP market (issues): https://github.com/AAswordman/OperitMCPMarket/issues
- This market is still small; you can continue collecting MCPs from other trusted sources, then deploy with this guide.
1) Config directory: /sdcard/Download/Operit/mcp_plugins/
- Main config: mcp_config.json
- Runtime status cache: server_status.json (non-realtime snapshot for status/tool cache only; not a troubleshooting source of truth)
2) Keep Android-side and Linux-side paths strictly separated:
- Android side is the source/import location, not the final runtime location
- Linux runtime directory is: ~/mcp_plugins/<last-segment-of-pluginId>
- Actual MCP startup always runs from the Linux side
3) Real deployment behavior (from implementation):
- Create target directory
- Copy plugin files from Android side to Linux side
- Execute auto-generated install/build commands (startup commands are skipped)
4) Command-based plugin handling:
- For command-based plugins using npx/uvx/uv, the system treats them as deployed and only performs minimal directory preparation.
5) System startup behavior (critical):
- Local plugins: the app reads command/args/env from mcp_config.json and starts the process with cwd=~/mcp_plugins/<shortName>; availability should be judged by real tool call/response
- Remote plugins: the app connects using endpoint + connectionType (optional bearerToken/headers) and verifies connectivity
6) Actual execution location of command (must be understood this way):
- For local plugins, the process starts in the Linux terminal environment with fixed working directory: ~/mcp_plugins/<shortName>
- The command from mcp_config.json is executed in that Linux working-directory context, not in the Android source directory
- Any relative paths in args are resolved against that cwd
6.1) node command example (by plugin ID):
- Example: pluginId=owner/my-plugin, so shortName=my-plugin and cwd=~/mcp_plugins/my-plugin
- If the entry file is ~/mcp_plugins/my-plugin/dist/index.js, write:
  "command": "node"
  "args": ["dist/index.js"]
- args must be relative to cwd; do not use Android paths such as /sdcard/...
7) mcp_config.json field rules:
- Top level must contain mcpServers (object)
- Each key in mcpServers is a serverId (normally keep it aligned with plugin ID)
- pluginId (the mcpServers key) only allows letters a-zA-Z, underscore (_), and spaces
- Common server fields:
  - command (required, startup command)
  - args (optional, string array)
  - env (optional, key-value map)
  - autoApprove (optional, array)
  - disabled (optional, true means disabled)
- MCP environment variables must be configured in mcpServers.<id>.env; `read_environment_variable` / `write_environment_variable` do not read or write these MCP env entries.
8) Path-writing rules in mcp_config.json:
- Do not put Android absolute paths (for example /sdcard/...) in local plugin startup args
- Write local plugin command/args for Linux runtime; prefer relative paths because cwd is fixed to ~/mcp_plugins/<shortName>
- If an absolute path is required, it must be a Linux path, not an Android path
9) Enable switch:
- Local plugin uses mcpServers.<id>.disabled
- Remote plugin uses pluginMetadata.<id>.disabled
10) MCP troubleshooting order:
- Check enable switch
- Check local deployed directory exists and is non-empty
- Check whether command/args/env in mcp_config.json are complete
- Check whether args incorrectly use Android paths
- Check required key/token in env
- Do not treat `active` in server_status.json as the single source of truth; prioritize whether tools can be fetched/called successfully
- Check terminal dependencies (node/pnpm/python/uv) and MCP service status

[Skill: install and troubleshooting]
0) Market source:
- OPR Skill market (issues): https://github.com/AAswordman/OperitSkillMarket/issues
- You can also get skills from other trusted sources, then download and extract them into the skills directory.
1) Directory: /sdcard/Download/Operit/skills/
2) Recognition rule: each Skill must be a folder containing SKILL.md (skill.md is also accepted).
3) How to add a skill (use this workflow):
- Download the skill from a trusted source (zip or repository source code).
- Extract it, then place the folder directly under /sdcard/Download/Operit/skills/
- Final structure must be /sdcard/Download/Operit/skills/<skill_name>/ and that folder must contain SKILL.md
4) Metadata parsing:
- Prefer frontmatter name/description
- Fallback to name:/description: in the first 40 lines
5) AI visibility:
- If the list switch is off, the Skill remains local but is hidden from AI usage
6) Skill troubleshooting order:
- Verify path and SKILL.md
- Check duplicate-name conflict
- Check enable switch
- Check whether SKILL.md instructions are complete (steps/constraints/outputs)

[Sandbox Package: install and troubleshooting]
1) External sandbox packages directory:
- Android/data/com.ai.assistance.operit/files/packages
2) Built-in packages:
- Built-in packages come from app bundled assets, not from the external directory above; usually manage via enable/disable instead of file deletion.
3) Import and delete:
- Import: place/import `.js` or `.toolpkg` into the external packages directory.
- Delete: for external packages, delete by file path after confirming dependency impact.
4) Toggle management (prefer tools):
- Call list_sandbox_packages first to get built-in + external package list and current enabled state.
- Then call set_sandbox_package_enabled(package_name, enabled) to apply changes.
5) Package authoring guide:
- https://github.com/AAswordman/Operit/blob/main/docs/SCRIPT_DEV_GUIDE.md

[Package compatibility model (important)]
- The package list seen by AI is a unified abstraction, and each item may come from MCP, Skill, or Sandbox Package.
- Available package entries are not guaranteed to be a single type; mixed types are expected.
- `use_package` is tri-compatible and can be called uniformly for MCP/Skill/Sandbox Package.
- `ping_mcp` is a thin wrapper over `use_package` for quick package availability probing.

[Function model and model config]
1) Model config:
- Each model config is one full connection profile (provider / endpoint / api key / model_name / capability switches).
- Configs can be created, updated, and deleted; deleting the default `default` config is forbidden.
2) Function model binding:
- Each function type (for example CHAT/SUMMARY/TRANSLATION) is bound to one model config.
- If `model_name` contains multiple models (comma-separated), `model_index` selects which one to use.
3) Key tools (prefer these):
- `list_model_configs`: list all model configs and current function bindings.
- `create_model_config`: create a new model config (with optional initial provider/endpoint/key/model_name).
- `update_model_config`: update an existing config by config_id.
- `delete_model_config`: delete a config (default cannot be deleted).
- `list_function_model_configs`: list function -> config bindings only (lightweight).
- `get_function_model_config`: get one bound config detail by function type.
- `set_function_model_config`: assign config and model index for a function.
- `test_model_config_connection`: run settings-UI-equivalent connectivity/capability checks for a config.
4) After config changes:
- If a config is used by functions, corresponding function services are refreshed; binding updates also refresh target function service.
5) When users complain about truncated outputs:
- Use `get_function_model_config` to inspect `max_tokens` in the bound config for that function.
- For DeepSeek, a common default is 4096; enable `max_tokens_enabled` and try setting `max_tokens` to 8192.
- For other models, verify the supported output-token limit online before changing values.

[Context summary module]
1) Purpose:
- Controls context window behavior and summary-trigger conditions to reduce context bloat, drift, and unstable long-chat responses.
- Only context-summary settings on the config currently bound to CHAT are effective in real conversation.
2) Core mechanism:
- There is a max switch in the app: `enable_max_context_mode`.
- When enabled, it uses `max_context_length`; when disabled, it uses `context_length`, so context length can be chosen dynamically to save tokens.
- `enable_summary` controls automatic summarization. When context exceeds `summary_token_threshold * current available context length`, summarization is triggered.
3) Recommended troubleshooting flow:
- Use `get_context_summary_config` to inspect current context-summary fields for a function.
- Use `set_context_summary_config` to set context-summary fields (you can override default values via params).
- If issues remain, fine-tune based on real model capability and workload.

[TTS/STT speech-service configuration]
1) Where to configure:
- Configure it on the Speech Services page in app settings.
- The page auto-saves; after changes, speech-service instances are rebuilt automatically.
2) TTS (text-to-speech) engines:
- `SIMPLE_TTS`: system TTS, usually no network fields required.
- `HTTP_TTS`: mainly fill `url_template`, `headers`, `http_method`, `content_type`, `request_body`.
- `SILICONFLOW_TTS`: fill `api_key`, `model_name`, `voice_id`.
- `OPENAI_TTS`: fill `url_template`, `api_key`, `model_name`, `voice_id`.
3) STT (speech-to-text) engines:
- `SHERPA_NCNN`: local recognition, usually no API key required.
- `OPENAI_STT`: fill `endpoint_url`, `api_key`, `model_name`.
- `DEEPGRAM_STT`: fill `endpoint_url`, `api_key`, `model_name`.
4) Most common mistakes (check first):
- `headers` in `HTTP_TTS` is not valid JSON (must be an object).
- Missing `{text}` placeholder in HTTP TTS template (typically in URL for GET, in body for POST).
- Wrong endpoint path for TTS/STT (for example using chat/completions instead of audio endpoints).
- `model_name` does not exist or does not match the API.
- No real retest after saving config.
5) HTTP_TTS placeholder rules (from implementation):
- Required placeholder: `{text}`.
  - When `http_method=GET`, `{text}` must appear in `url_template`.
  - When `http_method=POST`, `{text}` must appear in `request_body`.
- Optional placeholders: `{rate}`, `{pitch}`, `{voice}`.
- Only these placeholders are stably supported for external configuration: `{text}`, `{rate}`, `{pitch}`, `{voice}`.
6) Minimal templates you can copy:
- HTTP TTS (GET):
  - `url_template`: `https://example.com/tts?text={text}`
  - `headers`: `{}`
  - `http_method`: `GET`
  - `content_type`: `application/json`
- HTTP TTS (POST):
  - `url_template`: `https://example.com/tts`
  - `headers`: `{"Authorization":"Bearer <API_KEY>"}`
  - `http_method`: `POST`
  - `content_type`: `application/json`
  - `request_body`: `{"text":"{text}"}`
- OpenAI STT (common default):
  - `endpoint_url`: `https://api.openai.com/v1/audio/transcriptions`
  - `model_name`: `whisper-1`
7) Recommended troubleshooting order:
- Confirm the selected engine type first (TTS and STT separately).
- Check endpoint/key/model as a bundle.
- Then verify HTTP template fields (headers JSON, method, body, placeholder).
- Finally run real voice tests (playback + recognition) end to end.
8) Related tools:
- `get_speech_services_config`: fetch current TTS/STT config snapshot (engine types + key fields).
- `set_speech_services_config`: update TTS/STT config fields (partial update supported).

[Multimodal input rules]
1) Meaning of capability switches:
- Switches like Tool Call / image / audio / video in model config are software-side capability markers, not proof of real model capability.
- Configure these switches according to actual model support; do not enable blindly.
2) Image understanding main path in app:
- If the chat-function model config enables image understanding and the model truly supports it, attached images are sent directly to that chat model.
- If the chat model does not support image understanding, the app will try OCR, or use the dedicated image-recognition function model as a relay.
3) Valid conditions when users need image understanding:
- Condition A: chat model supports image understanding.
- Condition B: chat model does not support it, but image-recognition function model supports it.
- If the image-recognition model also does not support it, the final fallback is OCR.

[Drawing output note]
- Drawing is implemented via package tools.
- The app includes several built-in drawing packages; use list_sandbox_packages to inspect them.
- Usually enabling one available drawing package is enough; no need to enable all of them.

[Execution principles]
- Follow the user's explicit instructions strictly; do not define your own "problem" or add unrequested goals.
- Any configuration-changing action (toggle/import/delete/write env/model config CRUD/restart) requires explicit user confirmation first.
- If the user did not explicitly ask to execute a specific tool, do not proactively run write-type tools.
- Answer with concrete paths/rules from this guide and avoid generic assumptions.'''
      }
      parameters: []
      advice: true
    },
    {
      name: "how_make_skill"
      description: {
        zh: '''返回如何制作 skill 的双语说明。'''
        en: '''Return a bilingual guide for creating a skill.'''
      }
      parameters: []
    },
    {
      name: "list_sandbox_packages"
      description: {
        zh: '''获取沙盒包列表（内置+外部）及当前启用状态、管理路径。'''
        en: '''Get sandbox package list (built-in + external), current enabled states, and management paths.'''
      }
      parameters: []
    },
    {
      name: "set_sandbox_package_enabled"
      description: {
        zh: '''设置沙盒包开关状态。'''
        en: '''Set sandbox package enabled state.'''
      }
      parameters: [
        {
          name: "package_name"
          description: {
            zh: "沙盒包名称"
            en: "Sandbox package name"
          }
          type: string
          required: true
        },
        {
          name: "enabled"
          description: {
            zh: "是否启用（true/false）"
            en: "Enable state (true/false)"
          }
          type: boolean
          required: true
        }
      ]
    },
    {
      name: "read_environment_variable"
      description: {
        zh: '''读取指定环境变量当前值（仅用于沙盒包脚本环境变量排查，不用于 MCP 配置）。'''
        en: '''Read current value of a specified environment variable (sandbox-package script env troubleshooting only, not MCP config env).'''
      }
      parameters: [
        {
          name: "key"
          description: {
            zh: "环境变量名"
            en: "Environment variable key"
          }
          type: string
          required: true
        }
      ]
    },
    {
      name: "write_environment_variable"
      description: {
        zh: '''写入指定环境变量；value 为空时会清除该变量（仅用于沙盒包脚本环境变量，不用于 MCP 配置 env）。'''
        en: '''Write a specified environment variable; empty value clears it (sandbox-package script env only, not MCP config env).'''
      }
      parameters: [
        {
          name: "key"
          description: {
            zh: "环境变量名"
            en: "Environment variable key"
          }
          type: string
          required: true
        },
        {
          name: "value"
          description: {
            zh: "变量值；为空时清除该变量"
            en: "Variable value; empty clears the variable"
          }
          type: string
          required: false
        }
      ]
    },
    {
      name: "restart_mcp_with_logs"
      description: {
        zh: '''触发一次 MCP 重启流程，返回每个插件的启动日志与状态摘要。'''
        en: '''Trigger one MCP restart flow and return per-plugin startup logs with status summary.'''
      }
      parameters: [
        {
          name: "timeout_ms"
          description: {
            zh: "可选，最大等待时长（毫秒）"
            en: "Optional max wait time in milliseconds"
          }
          type: integer
          required: false
        }
      ]
    },
    {
      name: "get_speech_services_config"
      description: {
        zh: '''获取当前 TTS/STT 语音服务配置快照。'''
        en: '''Get current TTS/STT speech services config snapshot.'''
      }
      parameters: []
    },
    {
      name: "set_speech_services_config"
      description: {
        zh: '''按字段更新 TTS/STT 语音服务配置（支持部分字段更新）。'''
        en: '''Update TTS/STT speech services config by fields (partial update supported).'''
      }
      parameters: [
        {
          name: "tts_service_type"
          description: {
            zh: "可选，SIMPLE_TTS/HTTP_TTS/SILICONFLOW_TTS/OPENAI_TTS"
            en: "Optional, SIMPLE_TTS/HTTP_TTS/SILICONFLOW_TTS/OPENAI_TTS"
          }
          type: string
          required: false
        },
        {
          name: "tts_url_template"
          description: {
            zh: "可选，TTS URL 模板。仅支持 `{text}`、`{rate}`、`{pitch}`、`{voice}`"
            en: "Optional TTS URL template. Supports only `{text}`, `{rate}`, `{pitch}`, `{voice}`"
          }
          type: string
          required: false
        },
        {
          name: "tts_api_key"
          description: {
            zh: "可选，TTS API Key"
            en: "Optional TTS API key"
          }
          type: string
          required: false
        },
        {
          name: "tts_headers"
          description: {
            zh: "可选，TTS headers 的 JSON 对象字符串"
            en: "Optional JSON object string for TTS headers"
          }
          type: string
          required: false
        },
        {
          name: "tts_http_method"
          description: {
            zh: "可选，GET/POST"
            en: "Optional, GET/POST"
          }
          type: string
          required: false
        },
        {
          name: "tts_request_body"
          description: {
            zh: "可选，TTS POST body 模板。仅支持 `{text}`、`{rate}`、`{pitch}`、`{voice}`"
            en: "Optional TTS POST body template. Supports only `{text}`, `{rate}`, `{pitch}`, `{voice}`"
          }
          type: string
          required: false
        },
        {
          name: "tts_content_type"
          description: {
            zh: "可选，TTS Content-Type"
            en: "Optional TTS content type"
          }
          type: string
          required: false
        },
        {
          name: "tts_voice_id"
          description: {
            zh: "可选，TTS 音色 ID"
            en: "Optional TTS voice id"
          }
          type: string
          required: false
        },
        {
          name: "tts_model_name"
          description: {
            zh: "可选，TTS 模型名"
            en: "Optional TTS model name"
          }
          type: string
          required: false
        },
        {
          name: "tts_cleaner_regexs"
          description: {
            zh: "可选，TTS 清理正则列表 JSON 数组字符串"
            en: "Optional JSON array string for TTS cleaner regex list"
          }
          type: string
          required: false
        },
        {
          name: "tts_speech_rate"
          description: {
            zh: "可选，TTS 语速"
            en: "Optional TTS speech rate"
          }
          type: number
          required: false
        },
        {
          name: "tts_pitch"
          description: {
            zh: "可选，TTS 音调"
            en: "Optional TTS pitch"
          }
          type: number
          required: false
        },
        {
          name: "stt_service_type"
          description: {
            zh: "可选，SHERPA_NCNN/OPENAI_STT/DEEPGRAM_STT"
            en: "Optional, SHERPA_NCNN/OPENAI_STT/DEEPGRAM_STT"
          }
          type: string
          required: false
        },
        {
          name: "stt_endpoint_url"
          description: {
            zh: "可选，STT endpoint URL"
            en: "Optional STT endpoint URL"
          }
          type: string
          required: false
        },
        {
          name: "stt_api_key"
          description: {
            zh: "可选，STT API Key"
            en: "Optional STT API key"
          }
          type: string
          required: false
        },
        {
          name: "stt_model_name"
          description: {
            zh: "可选，STT 模型名"
            en: "Optional STT model name"
          }
          type: string
          required: false
        }
      ]
    },
    {
      name: "list_model_configs"
      description: {
        zh: '''列出全部模型配置及功能模型绑定关系。'''
        en: '''List all model configs and function-model bindings.'''
      }
      parameters: []
    },
    {
      name: "create_model_config"
      description: {
        zh: '''新增模型配置（可带初始化字段）。'''
        en: '''Create a model config (optional initialization fields).'''
      }
      parameters: [
        {
          name: "name"
          description: {
            zh: "可选，配置名称"
            en: "Optional config name"
          }
          type: string
          required: false
        },
        {
          name: "api_provider_type"
          description: {
            zh: "可选，提供商枚举名"
            en: "Optional provider enum name"
          }
          type: string
          required: false
        },
        {
          name: "api_endpoint"
          description: {
            zh: "可选，API端点"
            en: "Optional API endpoint"
          }
          type: string
          required: false
        },
        {
          name: "api_key"
          description: {
            zh: "可选，API Key"
            en: "Optional API key"
          }
          type: string
          required: false
        },
        {
          name: "model_name"
          description: {
            zh: "可选，模型名（多个可逗号分隔）"
            en: "Optional model name (comma-separated for multiple models)"
          }
          type: string
          required: false
        },
        {
          name: "max_tokens_enabled"
          description: {
            zh: "可选，是否启用 max_tokens 参数"
            en: "Optional switch for max_tokens"
          }
          type: boolean
          required: false
        },
        {
          name: "max_tokens"
          description: {
            zh: "可选，max_tokens 数值"
            en: "Optional max_tokens value"
          }
          type: integer
          required: false
        },
        {
          name: "temperature_enabled"
          description: {
            zh: "可选，是否启用 temperature 参数"
            en: "Optional switch for temperature"
          }
          type: boolean
          required: false
        },
        {
          name: "temperature"
          description: {
            zh: "可选，temperature 数值"
            en: "Optional temperature value"
          }
          type: number
          required: false
        },
        {
          name: "top_p_enabled"
          description: {
            zh: "可选，是否启用 top_p 参数"
            en: "Optional switch for top_p"
          }
          type: boolean
          required: false
        },
        {
          name: "top_p"
          description: {
            zh: "可选，top_p 数值"
            en: "Optional top_p value"
          }
          type: number
          required: false
        },
        {
          name: "top_k_enabled"
          description: {
            zh: "可选，是否启用 top_k 参数"
            en: "Optional switch for top_k"
          }
          type: boolean
          required: false
        },
        {
          name: "top_k"
          description: {
            zh: "可选，top_k 数值"
            en: "Optional top_k value"
          }
          type: integer
          required: false
        },
        {
          name: "presence_penalty_enabled"
          description: {
            zh: "可选，是否启用 presence_penalty 参数"
            en: "Optional switch for presence_penalty"
          }
          type: boolean
          required: false
        },
        {
          name: "presence_penalty"
          description: {
            zh: "可选，presence_penalty 数值"
            en: "Optional presence_penalty value"
          }
          type: number
          required: false
        },
        {
          name: "frequency_penalty_enabled"
          description: {
            zh: "可选，是否启用 frequency_penalty 参数"
            en: "Optional switch for frequency_penalty"
          }
          type: boolean
          required: false
        },
        {
          name: "frequency_penalty"
          description: {
            zh: "可选，frequency_penalty 数值"
            en: "Optional frequency_penalty value"
          }
          type: number
          required: false
        },
        {
          name: "repetition_penalty_enabled"
          description: {
            zh: "可选，是否启用 repetition_penalty 参数"
            en: "Optional switch for repetition_penalty"
          }
          type: boolean
          required: false
        },
        {
          name: "repetition_penalty"
          description: {
            zh: "可选，repetition_penalty 数值"
            en: "Optional repetition_penalty value"
          }
          type: number
          required: false
        },
        {
          name: "custom_parameters"
          description: {
            zh: "可选，自定义参数 JSON 字符串"
            en: "Optional custom parameters JSON string"
          }
          type: string
          required: false
        }
      ]
    },
    {
      name: "update_model_config"
      description: {
        zh: '''按 config_id 修改模型配置。'''
        en: '''Update model config by config_id.'''
      }
      parameters: [
        {
          name: "config_id"
          description: {
            zh: "目标配置ID"
            en: "Target config id"
          }
          type: string
          required: true
        },
        {
          name: "name"
          description: {
            zh: "可选，配置名称"
            en: "Optional config name"
          }
          type: string
          required: false
        },
        {
          name: "api_provider_type"
          description: {
            zh: "可选，提供商枚举名"
            en: "Optional provider enum name"
          }
          type: string
          required: false
        },
        {
          name: "api_endpoint"
          description: {
            zh: "可选，API端点"
            en: "Optional API endpoint"
          }
          type: string
          required: false
        },
        {
          name: "api_key"
          description: {
            zh: "可选，API Key"
            en: "Optional API key"
          }
          type: string
          required: false
        },
        {
          name: "model_name"
          description: {
            zh: "可选，模型名（多个可逗号分隔）"
            en: "Optional model name (comma-separated for multiple models)"
          }
          type: string
          required: false
        },
        {
          name: "max_tokens_enabled"
          description: {
            zh: "可选，是否启用 max_tokens 参数"
            en: "Optional switch for max_tokens"
          }
          type: boolean
          required: false
        },
        {
          name: "max_tokens"
          description: {
            zh: "可选，max_tokens 数值"
            en: "Optional max_tokens value"
          }
          type: integer
          required: false
        },
        {
          name: "temperature_enabled"
          description: {
            zh: "可选，是否启用 temperature 参数"
            en: "Optional switch for temperature"
          }
          type: boolean
          required: false
        },
        {
          name: "temperature"
          description: {
            zh: "可选，temperature 数值"
            en: "Optional temperature value"
          }
          type: number
          required: false
        },
        {
          name: "top_p_enabled"
          description: {
            zh: "可选，是否启用 top_p 参数"
            en: "Optional switch for top_p"
          }
          type: boolean
          required: false
        },
        {
          name: "top_p"
          description: {
            zh: "可选，top_p 数值"
            en: "Optional top_p value"
          }
          type: number
          required: false
        },
        {
          name: "top_k_enabled"
          description: {
            zh: "可选，是否启用 top_k 参数"
            en: "Optional switch for top_k"
          }
          type: boolean
          required: false
        },
        {
          name: "top_k"
          description: {
            zh: "可选，top_k 数值"
            en: "Optional top_k value"
          }
          type: integer
          required: false
        },
        {
          name: "presence_penalty_enabled"
          description: {
            zh: "可选，是否启用 presence_penalty 参数"
            en: "Optional switch for presence_penalty"
          }
          type: boolean
          required: false
        },
        {
          name: "presence_penalty"
          description: {
            zh: "可选，presence_penalty 数值"
            en: "Optional presence_penalty value"
          }
          type: number
          required: false
        },
        {
          name: "frequency_penalty_enabled"
          description: {
            zh: "可选，是否启用 frequency_penalty 参数"
            en: "Optional switch for frequency_penalty"
          }
          type: boolean
          required: false
        },
        {
          name: "frequency_penalty"
          description: {
            zh: "可选，frequency_penalty 数值"
            en: "Optional frequency_penalty value"
          }
          type: number
          required: false
        },
        {
          name: "repetition_penalty_enabled"
          description: {
            zh: "可选，是否启用 repetition_penalty 参数"
            en: "Optional switch for repetition_penalty"
          }
          type: boolean
          required: false
        },
        {
          name: "repetition_penalty"
          description: {
            zh: "可选，repetition_penalty 数值"
            en: "Optional repetition_penalty value"
          }
          type: number
          required: false
        },
        {
          name: "custom_parameters"
          description: {
            zh: "可选，自定义参数 JSON 字符串"
            en: "Optional custom parameters JSON string"
          }
          type: string
          required: false
        },
        {
          name: "enable_tool_call"
          description: {
            zh: "可选，是否开启Tool Call"
            en: "Optional tool-call switch"
          }
          type: boolean
          required: false
        },
        {
          name: "strict_tool_call"
          description: {
            zh: "可选，严格Tool Call模式"
            en: "Optional strict tool-call mode"
          }
          type: boolean
          required: false
        }
      ]
    },
    {
      name: "delete_model_config"
      description: {
        zh: '''按 config_id 删除模型配置（默认配置不可删）。'''
        en: '''Delete model config by config_id (default cannot be deleted).'''
      }
      parameters: [
        {
          name: "config_id"
          description: {
            zh: "目标配置ID"
            en: "Target config id"
          }
          type: string
          required: true
        }
      ]
    },
    {
      name: "list_function_model_configs"
      description: {
        zh: '''仅列出功能模型绑定关系（功能 -> 配置 + 模型索引）。'''
        en: '''List function model bindings only (function -> config + model index).'''
      }
      parameters: []
    },
    {
      name: "get_function_model_config"
      description: {
        zh: '''查看某个功能当前绑定的单个模型配置详情。'''
        en: '''Get one function's currently bound model config detail.'''
      }
      parameters: [
        {
          name: "function_type"
          description: {
            zh: "功能类型枚举名"
            en: "Function type enum name"
          }
          type: string
          required: true
        }
      ]
    },
    {
      name: "get_context_summary_config"
      description: {
        zh: '''获取某个功能当前绑定模型配置中的上下文总结参数。'''
        en: '''Get context-summary settings from the model config bound to a function.'''
      }
      parameters: [
        {
          name: "function_type"
          description: {
            zh: "可选，功能类型；默认 CHAT"
            en: "Optional function type; default CHAT"
          }
          type: string
          required: false
        }
      ]
    },
    {
      name: "set_context_summary_config"
      description: {
        zh: '''为某个功能绑定配置设置上下文总结参数（可选覆盖默认值）。'''
        en: '''Set context-summary settings for a function binding (optional overrides supported).'''
      }
      parameters: [
        {
          name: "function_type"
          description: {
            zh: "可选，功能类型；默认 CHAT"
            en: "Optional function type; default CHAT"
          }
          type: string
          required: false
        },
        {
          name: "context_length"
          description: {
            zh: "可选，基础上下文长度"
            en: "Optional base context length"
          }
          type: number
          required: false
        },
        {
          name: "max_context_length"
          description: {
            zh: "可选，最大上下文长度"
            en: "Optional max context length"
          }
          type: number
          required: false
        },
        {
          name: "enable_max_context_mode"
          description: {
            zh: "可选，是否启用最大上下文模式"
            en: "Optional max-context-mode switch"
          }
          type: boolean
          required: false
        },
        {
          name: "summary_token_threshold"
          description: {
            zh: "可选，总结触发 token 阈值（0~1）"
            en: "Optional token-ratio threshold for summary trigger (0~1)"
          }
          type: number
          required: false
        },
        {
          name: "enable_summary"
          description: {
            zh: "可选，是否启用上下文总结"
            en: "Optional context-summary switch"
          }
          type: boolean
          required: false
        },
        {
          name: "enable_summary_by_message_count"
          description: {
            zh: "可选，是否启用按消息条数触发总结"
            en: "Optional message-count summary trigger switch"
          }
          type: boolean
          required: false
        },
        {
          name: "summary_message_count_threshold"
          description: {
            zh: "可选，按消息条数触发总结阈值"
            en: "Optional message-count threshold for summary trigger"
          }
          type: integer
          required: false
        }
      ]
    },
    {
      name: "set_function_model_config"
      description: {
        zh: '''为功能指定模型配置与模型索引。'''
        en: '''Set model config and model index for a function.'''
      }
      parameters: [
        {
          name: "function_type"
          description: {
            zh: "功能类型枚举名"
            en: "Function type enum name"
          }
          type: string
          required: true
        },
        {
          name: "config_id"
          description: {
            zh: "模型配置ID"
            en: "Model config id"
          }
          type: string
          required: true
        },
        {
          name: "model_index"
          description: {
            zh: "可选，模型索引"
            en: "Optional model index"
          }
          type: integer
          required: false
        }
      ]
    },
    {
      name: "test_model_config_connection"
      description: {
        zh: '''按设置页同等逻辑测试某个模型配置。'''
        en: '''Run settings-UI-equivalent tests for one model config.'''
      }
      parameters: [
        {
          name: "config_id"
          description: {
            zh: "目标配置ID"
            en: "Target config id"
          }
          type: string
          required: true
        },
        {
          name: "model_index"
          description: {
            zh: "可选，模型索引"
            en: "Optional model index"
          }
          type: integer
          required: false
        }
      ]
    },
    {
      name: "ping_mcp"
      description: {
        zh: '''直通 use_package 的探测工具：用于快速测试某个 package 是否可被加载（MCP/Skill/Sandbox 三兼容）。'''
        en: '''A pass-through probe for use_package: quickly test whether a package can be loaded (tri-compatible across MCP/Skill/Sandbox).'''
      }
      parameters: [
        {
          name: "package_name"
          description: {
            zh: "要探测的包名"
            en: "Package name to probe"
          }
          type: string
          required: true
        }
      ]
    }
  ]
}*/
var __rest = (this && this.__rest) || function (s, e) {
    var t = {};
    for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0)
        t[p] = s[p];
    if (s != null && typeof Object.getOwnPropertySymbols === "function")
        for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
            if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i]))
                t[p[i]] = s[p[i]];
        }
    return t;
};
async function all_about_myself(params) {
    try {
        const { query } = params !== null && params !== void 0 ? params : {};
        complete({
            success: true,
            message: "配置排查手册已加载（MCP/Skill/Sandbox Package/功能模型与模型配置/TTS-STT语音服务），将按配置链路执行排查。",
            data: {
                query: query !== null && query !== void 0 ? query : ""
            }
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function how_make_skill() {
    var _a;
    try {
        const locale = ((_a = getLang()) !== null && _a !== void 0 ? _a : "").toLowerCase();
        const lang = locale.startsWith("zh") ? "zh" : locale.startsWith("en") ? "en" : "both";
        const zh = `如何制作 skill（简版）
1. 先创建目录：/sdcard/Download/Operit/skills/<skill_name>/
2. 必备文件：SKILL.md
3. 在 SKILL.md 顶部用 Markdown 元数据（frontmatter）写 name、description，例如：
---
name: your_skill_name
description: 用一句话说明这个 skill 做什么
---
4. 元数据后再写正文：适用场景、执行步骤、约束边界、期望输出
5. 可选内容：scripts/、templates/、examples/、assets/；在 SKILL.md 里用相对路径引用
6. 实践建议：优先下载现成 skill，直接解压过来，并确保目录下有 SKILL.md。`;
        const en = `How to make a skill (quick guide)
1. Create a directory: /sdcard/Download/Operit/skills/<skill_name>/
2. Required file: SKILL.md
3. At the top of SKILL.md, use Markdown metadata (frontmatter) for name and description, for example:
---
name: your_skill_name
description: one-line summary of what this skill does
---
4. After metadata, write the main sections: use cases, workflow steps, constraints, expected outputs
5. Optional content: scripts/, templates/, examples/, assets/; reference them from SKILL.md using relative paths
6. Practical tip: download an existing skill, extract it directly, and ensure the directory contains SKILL.md`;
        const message = lang === "zh" ? zh : lang === "en" ? en : `${zh}\n\n---\n\n${en}`;
        complete({
            success: true,
            message,
            data: {
                lang,
                zh,
                en
            }
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function list_sandbox_packages() {
    try {
        const result = await Tools.SoftwareSettings.listSandboxPackages();
        const payload = extract_string_result(result);
        complete({
            success: true,
            message: payload || "Sandbox package list fetched.",
            data: {
                raw: payload
            }
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function set_sandbox_package_enabled(params) {
    var _a, _b;
    try {
        const packageName = (_a = params === null || params === void 0 ? void 0 : params.package_name) !== null && _a !== void 0 ? _a : "";
        const enabled = (_b = params === null || params === void 0 ? void 0 : params.enabled) !== null && _b !== void 0 ? _b : false;
        const result = await Tools.SoftwareSettings.setSandboxPackageEnabled(packageName, enabled);
        const payload = extract_string_result(result);
        complete({
            success: true,
            message: payload || "Sandbox package switch updated.",
            data: {
                raw: payload
            }
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
function extract_string_result(result) {
    if (typeof result === "string")
        return result;
    if (!result || typeof result !== "object" || Array.isArray(result))
        return "";
    const record = result;
    if (typeof record.value === "string")
        return record.value;
    if (record.value !== undefined && record.value !== null)
        return String(record.value);
    if (typeof record.data === "string")
        return record.data;
    if (record.data !== undefined && record.data !== null)
        return String(record.data);
    return "";
}
function parse_environment_variable_payload(raw) {
    if (!raw)
        return null;
    try {
        const parsed = JSON.parse(raw);
        if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
            return null;
        }
        const record = parsed;
        return {
            exists: typeof record.exists === "boolean" ? record.exists : undefined,
            value: typeof record.value === "string" || record.value === null ? record.value : undefined
        };
    }
    catch (_a) {
        return null;
    }
}
async function read_environment_variable(params) {
    var _a, _b;
    try {
        const key = ((_a = params === null || params === void 0 ? void 0 : params.key) !== null && _a !== void 0 ? _a : "").trim();
        if (!key) {
            complete({
                success: false,
                message: "Missing required parameter: key"
            });
            return;
        }
        const result = await Tools.SoftwareSettings.readEnvironmentVariable(key);
        const raw = extract_string_result(result);
        const parsed = parse_environment_variable_payload(raw);
        complete({
            success: true,
            message: (parsed === null || parsed === void 0 ? void 0 : parsed.exists) ? `Environment variable read: ${key}` : `Environment variable not set: ${key}`,
            data: {
                key,
                value: (_b = parsed === null || parsed === void 0 ? void 0 : parsed.value) !== null && _b !== void 0 ? _b : null,
                exists: !!(parsed === null || parsed === void 0 ? void 0 : parsed.exists),
                raw
            }
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function write_environment_variable(params) {
    var _a, _b, _c;
    try {
        const key = ((_a = params === null || params === void 0 ? void 0 : params.key) !== null && _a !== void 0 ? _a : "").trim();
        if (!key) {
            complete({
                success: false,
                message: "Missing required parameter: key"
            });
            return;
        }
        const value = (_b = params === null || params === void 0 ? void 0 : params.value) !== null && _b !== void 0 ? _b : "";
        const result = await Tools.SoftwareSettings.writeEnvironmentVariable(key, String(value));
        const raw = extract_string_result(result);
        const parsed = parse_environment_variable_payload(raw);
        const cleared = String(value).trim() === "";
        complete({
            success: true,
            message: cleared ? `Environment variable cleared: ${key}` : `Environment variable written: ${key}`,
            data: {
                key,
                requestedValue: String(value),
                value: (_c = parsed === null || parsed === void 0 ? void 0 : parsed.value) !== null && _c !== void 0 ? _c : null,
                exists: !!(parsed === null || parsed === void 0 ? void 0 : parsed.exists),
                raw
            }
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function restart_mcp_with_logs(params) {
    try {
        const timeoutMs = params === null || params === void 0 ? void 0 : params.timeout_ms;
        const result = await Tools.SoftwareSettings.restartMcpWithLogs(timeoutMs);
        const logs = extract_string_result(result);
        complete({
            success: true,
            message: logs || "MCP restart completed.",
            data: {
                logs
            }
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function get_speech_services_config() {
    try {
        const result = await Tools.SoftwareSettings.getSpeechServicesConfig();
        const parsed = result;
        complete({
            success: true,
            message: "Speech services config fetched.",
            data: {
                parsed
            }
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function set_speech_services_config(params) {
    try {
        const updates = Object.assign({}, (params !== null && params !== void 0 ? params : {}));
        const result = await Tools.SoftwareSettings.setSpeechServicesConfig(updates);
        const parsed = result;
        complete({
            success: true,
            message: "Speech services config updated.",
            data: {
                updates,
                parsed
            }
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function list_model_configs() {
    try {
        const result = await Tools.SoftwareSettings.listModelConfigs();
        complete({
            success: true,
            message: "Model configs listed.",
            data: result
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function create_model_config(params) {
    try {
        const options = Object.assign({}, (params !== null && params !== void 0 ? params : {}));
        const result = await Tools.SoftwareSettings.createModelConfig(options);
        complete({
            success: true,
            message: "Model config created.",
            data: result
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function update_model_config(params) {
    var _a;
    try {
        const configId = ((_a = params === null || params === void 0 ? void 0 : params.config_id) !== null && _a !== void 0 ? _a : "").trim();
        if (!configId) {
            complete({
                success: false,
                message: "Missing required parameter: config_id"
            });
            return;
        }
        const _b = params !== null && params !== void 0 ? params : {}, { config_id: _ignoredConfigId } = _b, updates = __rest(_b, ["config_id"]);
        const result = await Tools.SoftwareSettings.updateModelConfig(configId, updates);
        complete({
            success: true,
            message: "Model config updated.",
            data: result
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function delete_model_config(params) {
    var _a;
    try {
        const configId = ((_a = params === null || params === void 0 ? void 0 : params.config_id) !== null && _a !== void 0 ? _a : "").trim();
        if (!configId) {
            complete({
                success: false,
                message: "Missing required parameter: config_id"
            });
            return;
        }
        const result = await Tools.SoftwareSettings.deleteModelConfig(configId);
        complete({
            success: true,
            message: "Model config deleted.",
            data: result
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function list_function_model_configs() {
    try {
        const result = await Tools.SoftwareSettings.listFunctionModelConfigs();
        complete({
            success: true,
            message: "Function model bindings listed.",
            data: result
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function get_function_model_config(params) {
    var _a, _b;
    try {
        const functionType = ((_a = params === null || params === void 0 ? void 0 : params.function_type) !== null && _a !== void 0 ? _a : "").trim();
        if (!functionType) {
            complete({
                success: false,
                message: "Missing required parameter: function_type"
            });
            return;
        }
        const result = await Tools.SoftwareSettings.getFunctionModelConfig(functionType);
        const config = (_b = result === null || result === void 0 ? void 0 : result.config) !== null && _b !== void 0 ? _b : {};
        const { contextLength: _contextLength, maxContextLength: _maxContextLength, enableMaxContextMode: _enableMaxContextMode, summaryTokenThreshold: _summaryTokenThreshold, enableSummary: _enableSummary, enableSummaryByMessageCount: _enableSummaryByMessageCount, summaryMessageCountThreshold: _summaryMessageCountThreshold } = config, configWithoutContextSummary = __rest(config, ["contextLength", "maxContextLength", "enableMaxContextMode", "summaryTokenThreshold", "enableSummary", "enableSummaryByMessageCount", "summaryMessageCountThreshold"]);
        const filteredResult = Object.assign(Object.assign({}, result), { config: configWithoutContextSummary });
        complete({
            success: true,
            message: "Function model config fetched.",
            data: filteredResult
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
function get_error_message(error) {
    return error instanceof Error ? error.message : "Unknown error";
}
function pick_context_summary_fields(config) {
    return {
        context_length: config === null || config === void 0 ? void 0 : config.contextLength,
        max_context_length: config === null || config === void 0 ? void 0 : config.maxContextLength,
        enable_max_context_mode: config === null || config === void 0 ? void 0 : config.enableMaxContextMode,
        summary_token_threshold: config === null || config === void 0 ? void 0 : config.summaryTokenThreshold,
        enable_summary: config === null || config === void 0 ? void 0 : config.enableSummary,
        enable_summary_by_message_count: config === null || config === void 0 ? void 0 : config.enableSummaryByMessageCount,
        summary_message_count_threshold: config === null || config === void 0 ? void 0 : config.summaryMessageCountThreshold
    };
}
async function get_context_summary_config(params) {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    try {
        const functionType = ((_a = params === null || params === void 0 ? void 0 : params.function_type) !== null && _a !== void 0 ? _a : "CHAT").trim().toUpperCase();
        if (!functionType) {
            complete({
                success: false,
                message: "Missing required parameter: function_type"
            });
            return;
        }
        const result = (await Tools.SoftwareSettings.getFunctionModelConfig(functionType));
        const config = result.config;
        if (!config) {
            complete({
                success: false,
                message: `No bound config found for function_type: ${functionType}`,
                data: result
            });
            return;
        }
        complete({
            success: true,
            message: `Context summary config fetched for ${functionType}.`,
            data: {
                function_type: functionType,
                config_id: (_c = (_b = result.configId) !== null && _b !== void 0 ? _b : config.id) !== null && _c !== void 0 ? _c : "",
                config_name: (_e = (_d = result.configName) !== null && _d !== void 0 ? _d : config.name) !== null && _e !== void 0 ? _e : "",
                model_index: (_f = result.modelIndex) !== null && _f !== void 0 ? _f : 0,
                actual_model_index: (_g = result.actualModelIndex) !== null && _g !== void 0 ? _g : 0,
                selected_model: (_h = result.selectedModel) !== null && _h !== void 0 ? _h : "",
                context_summary: pick_context_summary_fields(config),
                raw: result
            }
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function set_context_summary_config(params) {
    var _a, _b, _c, _d;
    try {
        const functionType = ((_a = params === null || params === void 0 ? void 0 : params.function_type) !== null && _a !== void 0 ? _a : "CHAT").trim().toUpperCase();
        if (!functionType) {
            complete({
                success: false,
                message: "Missing required parameter: function_type"
            });
            return;
        }
        const binding = (await Tools.SoftwareSettings.getFunctionModelConfig(functionType));
        const configId = ((_d = (_b = binding.configId) !== null && _b !== void 0 ? _b : (_c = binding.config) === null || _c === void 0 ? void 0 : _c.id) !== null && _d !== void 0 ? _d : "").trim();
        if (!configId) {
            complete({
                success: false,
                message: `No bound config found for function_type: ${functionType}`,
                data: binding
            });
            return;
        }
        const defaultUpdates = {
            context_length: 48,
            max_context_length: 128,
            enable_max_context_mode: true,
            summary_token_threshold: 0.7,
            enable_summary: true,
            enable_summary_by_message_count: true,
            summary_message_count_threshold: 16
        };
        const updates = Object.assign(Object.assign(Object.assign(Object.assign(Object.assign(Object.assign(Object.assign(Object.assign({}, defaultUpdates), ((params === null || params === void 0 ? void 0 : params.context_length) === undefined ? {} : { context_length: params.context_length })), ((params === null || params === void 0 ? void 0 : params.max_context_length) === undefined
            ? {}
            : { max_context_length: params.max_context_length })), ((params === null || params === void 0 ? void 0 : params.enable_max_context_mode) === undefined
            ? {}
            : { enable_max_context_mode: params.enable_max_context_mode })), ((params === null || params === void 0 ? void 0 : params.summary_token_threshold) === undefined
            ? {}
            : { summary_token_threshold: params.summary_token_threshold })), ((params === null || params === void 0 ? void 0 : params.enable_summary) === undefined ? {} : { enable_summary: params.enable_summary })), ((params === null || params === void 0 ? void 0 : params.enable_summary_by_message_count) === undefined
            ? {}
            : { enable_summary_by_message_count: params.enable_summary_by_message_count })), ((params === null || params === void 0 ? void 0 : params.summary_message_count_threshold) === undefined
            ? {}
            : { summary_message_count_threshold: params.summary_message_count_threshold }));
        const updateResult = (await Tools.SoftwareSettings.updateModelConfig(configId, updates));
        complete({
            success: true,
            message: `Context summary config updated for ${functionType}.`,
            data: {
                function_type: functionType,
                config_id: configId,
                applied_updates: updates,
                before: pick_context_summary_fields(binding.config),
                after: pick_context_summary_fields(updateResult.config),
                update_result: updateResult
            }
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function set_function_model_config(params) {
    var _a, _b;
    try {
        const functionType = ((_a = params === null || params === void 0 ? void 0 : params.function_type) !== null && _a !== void 0 ? _a : "").trim();
        const configId = ((_b = params === null || params === void 0 ? void 0 : params.config_id) !== null && _b !== void 0 ? _b : "").trim();
        if (!functionType) {
            complete({
                success: false,
                message: "Missing required parameter: function_type"
            });
            return;
        }
        if (!configId) {
            complete({
                success: false,
                message: "Missing required parameter: config_id"
            });
            return;
        }
        const result = await Tools.SoftwareSettings.setFunctionModelConfig(functionType, configId, params === null || params === void 0 ? void 0 : params.model_index);
        complete({
            success: true,
            message: "Function model binding updated.",
            data: result
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function test_model_config_connection(params) {
    var _a;
    try {
        const configId = ((_a = params === null || params === void 0 ? void 0 : params.config_id) !== null && _a !== void 0 ? _a : "").trim();
        if (!configId) {
            complete({
                success: false,
                message: "Missing required parameter: config_id"
            });
            return;
        }
        const result = await Tools.SoftwareSettings.testModelConfigConnection(configId, params === null || params === void 0 ? void 0 : params.model_index);
        const success = !!(result === null || result === void 0 ? void 0 : result.success);
        complete({
            success,
            message: success ? "Model config connection tests passed." : "Model config connection tests have failures.",
            data: result
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
async function ping_mcp(params) {
    var _a;
    try {
        const packageName = ((_a = params === null || params === void 0 ? void 0 : params.package_name) !== null && _a !== void 0 ? _a : "").trim();
        if (!packageName) {
            complete({
                success: false,
                message: "Missing required parameter: package_name"
            });
            return;
        }
        const result = await toolCall("use_package", { package_name: packageName });
        complete({
            success: true,
            message: `Package probe finished: ${packageName}`,
            data: result
        });
    }
    catch (error) {
        complete({
            success: false,
            message: get_error_message(error)
        });
    }
}
exports.all_about_myself = all_about_myself;
exports.how_make_skill = how_make_skill;
exports.list_sandbox_packages = list_sandbox_packages;
exports.set_sandbox_package_enabled = set_sandbox_package_enabled;
exports.read_environment_variable = read_environment_variable;
exports.write_environment_variable = write_environment_variable;
exports.restart_mcp_with_logs = restart_mcp_with_logs;
exports.get_speech_services_config = get_speech_services_config;
exports.set_speech_services_config = set_speech_services_config;
exports.list_model_configs = list_model_configs;
exports.create_model_config = create_model_config;
exports.update_model_config = update_model_config;
exports.delete_model_config = delete_model_config;
exports.list_function_model_configs = list_function_model_configs;
exports.get_function_model_config = get_function_model_config;
exports.get_context_summary_config = get_context_summary_config;
exports.set_context_summary_config = set_context_summary_config;
exports.set_function_model_config = set_function_model_config;
exports.test_model_config_connection = test_model_config_connection;
exports.ping_mcp = ping_mcp;

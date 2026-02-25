/* METADATA
{
  name: "all_about_myself"
  display_name: {
    zh: "Operit配置编辑器"
    en: "Operit Config Editor"
  }
  description: {
    zh: '''配置排查手册：用于处理 MCP、Skill、Sandbox Package，以及功能模型与模型配置的安装、部署、启动、导入、开关与测试。'''
    en: '''Configuration troubleshooting guide for MCP, Skill, Sandbox Package, and function/model-configuration setup, deployment, startup, import, toggles, and testing.'''
  }

  enabledByDefault: true

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
- 问题核心是“配置和部署链路”，而不是普通问答

【MCP：安装与排查】
0) 市场来源：
- OPR MCP 市场（issues）：https://github.com/AAswordman/OperitMCPMarket/issues
- 该市场内容较少，可在其他可信来源继续搜集 MCP，再按本手册部署。
1) 配置目录：/sdcard/Download/Operit/mcp_plugins/
- 主配置：mcp_config.json
- 状态缓存：server_status.json（内部状态文件，通常不需要手改）
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
- 本地插件：系统会读取 mcp_config.json 中该插件的 command/args/env，使用 cwd=~/mcp_plugins/<shortName> 启动进程，并校验是否 active
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
- `list_function_model_configs`：查看功能 -> 配置绑定关系。
- `set_function_model_config`：为功能指定配置与模型索引。
- `test_model_config_connection`：按设置页同等逻辑测试配置连通与多模态能力。
4) 配置修改后：
- 若该配置被某些功能使用，系统会刷新对应功能服务；绑定变更也会刷新目标功能服务。

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
- 优先做“配置定位 + 精确修改 + 可执行排查步骤”。
- 用户问 MCP/Skill 问题时，优先基于上述路径与规则回答，不要泛泛而谈。
- 用户问沙盒包管理时，优先调用 list_sandbox_packages 与 set_sandbox_package_enabled，不要凭猜测回答包状态。
- 需要触发一次 MCP 全量重启并采集每个插件日志时，调用 restart_mcp_with_logs 工具（可选 timeout_ms）。
- 用户问模型配置或功能模型绑定时，优先调用 list/create/update/delete_model_config、list/set_function_model_config、test_model_config_connection。
- 用户问“怎么制作 skill”时，调用 how_make_skill 工具。'''
        en: '''Configuration troubleshooting guide.

[Trigger conditions]
- The user mentions MCP/Skill install failure, startup failure, tools not appearing, import failure, duplicate name conflicts, or config editing
- The user mentions sandbox package toggles, built-in package listing, import/delete paths, or package enable-state issues
- The user asks to troubleshoot plugin config paths, deploy directories, enable switches, or environment variables
- The user mentions function model bindings, adding/deleting/updating model configs, or testing model connectivity
- The core issue is configuration/deployment flow rather than normal Q&A

[MCP: install and troubleshooting]
0) Market source:
- OPR MCP market (issues): https://github.com/AAswordman/OperitMCPMarket/issues
- This market is still small; you can continue collecting MCPs from other trusted sources, then deploy with this guide.
1) Config directory: /sdcard/Download/Operit/mcp_plugins/
- Main config: mcp_config.json
- Status cache: server_status.json (internal state file; usually should not be edited manually)
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
- Local plugins: the app reads command/args/env from mcp_config.json, starts the process with cwd=~/mcp_plugins/<shortName>, and verifies active status
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
- `list_function_model_configs`: list current function -> config bindings.
- `set_function_model_config`: assign config and model index for a function.
- `test_model_config_connection`: run settings-UI-equivalent connectivity/capability checks for a config.
4) After config changes:
- If a config is used by functions, corresponding function services are refreshed; binding updates also refresh target function service.

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
- Focus on config diagnosis + precise edits + executable troubleshooting steps.
- For MCP/Skill issues, answer with concrete paths/rules above instead of generic guidance.
- For sandbox package management, prefer list_sandbox_packages and set_sandbox_package_enabled instead of guessing state.
- When a full MCP restart with per-plugin startup logs is required, call restart_mcp_with_logs (optional timeout_ms).
- For model config and function bindings, prefer list/create/update/delete_model_config, list/set_function_model_config, and test_model_config_connection.
- If user asks how to create a skill, call how_make_skill.'''
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
      advice: true
    },
    {
      name: "list_sandbox_packages"
      description: {
        zh: '''获取沙盒包列表（内置+外部）及当前启用状态、管理路径。'''
        en: '''Get sandbox package list (built-in + external), current enabled states, and management paths.'''
      }
      parameters: []
      advice: true
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
      advice: true
    },
    {
      name: "read_environment_variable"
      description: {
        zh: '''读取指定环境变量当前值（用于配置排查）。'''
        en: '''Read current value of a specified environment variable (for configuration troubleshooting).'''
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
      advice: true
    },
    {
      name: "write_environment_variable"
      description: {
        zh: '''写入指定环境变量；value 为空时会清除该变量。'''
        en: '''Write a specified environment variable; empty value clears the variable.'''
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
      advice: true
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
      advice: true
    },
    {
      name: "list_model_configs"
      description: {
        zh: '''列出全部模型配置及功能模型绑定关系。'''
        en: '''List all model configs and function-model bindings.'''
      }
      parameters: []
      advice: true
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
        }
      ]
      advice: true
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
      advice: true
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
      advice: true
    },
    {
      name: "list_function_model_configs"
      description: {
        zh: '''列出功能模型绑定关系（功能 -> 配置 + 模型索引）。'''
        en: '''List function model bindings (function -> config + model index).'''
      }
      parameters: []
      advice: true
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
      advice: true
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
      advice: true
    }
  ]
}
*/

async function all_about_myself(params: { query?: string }) {
  try {
    const { query } = params ?? {};
    complete({
      success: true,
      message: "配置排查手册已加载（MCP/Skill/Sandbox Package/功能模型与模型配置），将按配置链路执行排查。",
      data: {
        query: query ?? ""
      }
    });
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
    });
  }
}

async function how_make_skill() {
  try {
    const locale = (getLang() ?? "").toLowerCase();
    const lang: "zh" | "en" | "both" = locale.startsWith("zh") ? "zh" : locale.startsWith("en") ? "en" : "both";

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
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
    });
  }
}

async function list_sandbox_packages() {
  try {
    const result = await Tools.SoftwareSettings.listSandboxPackages();
    const payload = result?.value ?? "";
    complete({
      success: true,
      message: payload || "Sandbox package list fetched.",
      data: {
        raw: payload
      }
    });
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
    });
  }
}

async function set_sandbox_package_enabled(params?: { package_name?: string; enabled?: boolean | string | number }) {
  try {
    const packageName = params?.package_name ?? "";
    const enabled = params?.enabled ?? false;
    const result = await Tools.SoftwareSettings.setSandboxPackageEnabled(packageName, enabled);
    const payload = result?.value ?? "";
    complete({
      success: true,
      message: payload || "Sandbox package switch updated.",
      data: {
        raw: payload
      }
    });
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
    });
  }
}

async function read_environment_variable(params?: { key?: string }) {
  try {
    const key = (params?.key ?? "").trim();
    if (!key) {
      complete({
        success: false,
        message: "Missing required parameter: key"
      });
      return;
    }

    const result = await Tools.SoftwareSettings.readEnvironmentVariable(key);
    const raw = result?.value ?? "";
    let parsed: any = null;
    try {
      parsed = raw ? JSON.parse(raw) : null;
    } catch {
      parsed = null;
    }

    complete({
      success: true,
      message: parsed?.exists ? `Environment variable read: ${key}` : `Environment variable not set: ${key}`,
      data: {
        key,
        value: parsed?.value ?? null,
        exists: !!parsed?.exists,
        raw
      }
    });
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
    });
  }
}

async function write_environment_variable(params?: { key?: string; value?: string }) {
  try {
    const key = (params?.key ?? "").trim();
    if (!key) {
      complete({
        success: false,
        message: "Missing required parameter: key"
      });
      return;
    }

    const value = params?.value ?? "";
    const result = await Tools.SoftwareSettings.writeEnvironmentVariable(key, String(value));
    const raw = result?.value ?? "";
    let parsed: any = null;
    try {
      parsed = raw ? JSON.parse(raw) : null;
    } catch {
      parsed = null;
    }
    const cleared = String(value).trim() === "";

    complete({
      success: true,
      message: cleared ? `Environment variable cleared: ${key}` : `Environment variable written: ${key}`,
      data: {
        key,
        requestedValue: String(value),
        value: parsed?.value ?? null,
        exists: !!parsed?.exists,
        raw
      }
    });
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
    });
  }
}

async function restart_mcp_with_logs(params?: { timeout_ms?: number | string }) {
  try {
    const timeoutMs = params?.timeout_ms;
    const result = await Tools.SoftwareSettings.restartMcpWithLogs(timeoutMs);
    const logs = result?.value ?? "";
    complete({
      success: true,
      message: logs || "MCP restart completed.",
      data: {
        logs
      }
    });
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
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
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
    });
  }
}

async function create_model_config(params?: {
  name?: string;
  api_provider_type?: string;
  api_endpoint?: string;
  api_key?: string;
  model_name?: string;
}) {
  try {
    const options = { ...(params ?? {}) };
    const result = await Tools.SoftwareSettings.createModelConfig(options);
    complete({
      success: true,
      message: "Model config created.",
      data: result
    });
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
    });
  }
}

async function update_model_config(params?: {
  config_id?: string;
  name?: string;
  api_provider_type?: string;
  api_endpoint?: string;
  api_key?: string;
  model_name?: string;
  enable_direct_image_processing?: boolean;
  enable_direct_audio_processing?: boolean;
  enable_direct_video_processing?: boolean;
  enable_google_search?: boolean;
  enable_tool_call?: boolean;
  strict_tool_call?: boolean;
  mnn_forward_type?: number;
  mnn_thread_count?: number;
  llama_thread_count?: number;
  llama_context_size?: number;
  request_limit_per_minute?: number;
  max_concurrent_requests?: number;
}) {
  try {
    const configId = (params?.config_id ?? "").trim();
    if (!configId) {
      complete({
        success: false,
        message: "Missing required parameter: config_id"
      });
      return;
    }

    const updates = { ...(params ?? {}) };
    delete (updates as any).config_id;
    const result = await Tools.SoftwareSettings.updateModelConfig(configId, updates);
    complete({
      success: true,
      message: "Model config updated.",
      data: result
    });
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
    });
  }
}

async function delete_model_config(params?: { config_id?: string }) {
  try {
    const configId = (params?.config_id ?? "").trim();
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
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
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
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
    });
  }
}

async function set_function_model_config(params?: {
  function_type?: string;
  config_id?: string;
  model_index?: number | string;
}) {
  try {
    const functionType = (params?.function_type ?? "").trim();
    const configId = (params?.config_id ?? "").trim();
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

    const result = await Tools.SoftwareSettings.setFunctionModelConfig(
      functionType,
      configId,
      params?.model_index
    );
    complete({
      success: true,
      message: "Function model binding updated.",
      data: result
    });
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
    });
  }
}

async function test_model_config_connection(params?: { config_id?: string; model_index?: number | string }) {
  try {
    const configId = (params?.config_id ?? "").trim();
    if (!configId) {
      complete({
        success: false,
        message: "Missing required parameter: config_id"
      });
      return;
    }

    const result = await Tools.SoftwareSettings.testModelConfigConnection(configId, params?.model_index);
    const success = !!result?.success;
    complete({
      success,
      message: success ? "Model config connection tests passed." : "Model config connection tests have failures.",
      data: result
    });
  } catch (error: any) {
    complete({
      success: false,
      message: error?.message ?? "Unknown error"
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
exports.list_model_configs = list_model_configs;
exports.create_model_config = create_model_config;
exports.update_model_config = update_model_config;
exports.delete_model_config = delete_model_config;
exports.list_function_model_configs = list_function_model_configs;
exports.set_function_model_config = set_function_model_config;
exports.test_model_config_connection = test_model_config_connection;

/* METADATA
{
    "name": "linux_ssh",
    "display_name": {
        "zh": "Linux SSH",
        "en": "Linux SSH"
    },
    "description": {
        "zh": "基于 terminal 集成能力提供 Linux SSH 连接、tmux 长任务与远程文件操作。",
        "en": "Linux SSH tools powered by terminal integration, including tmux long jobs and remote file operations."
    },
    "enabledByDefault": true,
    "category": "System",
    "env": [
        {
            "name": "LINUX_SSH_HOST",
            "description": { "zh": "SSH 主机地址", "en": "SSH host" },
            "required": false
        },
        {
            "name": "LINUX_SSH_PORT",
            "description": { "zh": "SSH 端口，默认 22", "en": "SSH port, default 22" },
            "required": false
        },
        {
            "name": "LINUX_SSH_USERNAME",
            "description": { "zh": "SSH 用户名", "en": "SSH username" },
            "required": false
        },
        {
            "name": "LINUX_SSH_PASSWORD",
            "description": { "zh": "SSH 密码（可选，使用密钥时可留空）", "en": "SSH password (optional when key auth is used)" },
            "required": false
        },
        {
            "name": "LINUX_SSH_PRIVATE_KEY_PATH",
            "description": { "zh": "SSH 私钥路径（可选）", "en": "SSH private key path (optional)" },
            "required": false
        },
        {
            "name": "LINUX_SSH_TMUX_SESSION",
            "description": { "zh": "默认 tmux 会话名，默认 operit_ai", "en": "Default tmux session name, default operit_ai" },
            "required": false
        },
        {
            "name": "LINUX_SSH_LOCAL_TERMINAL_SESSION",
            "description": { "zh": "本地 terminal 会话名，默认 linux_ssh_default_session", "en": "Local terminal session name, default linux_ssh_default_session" },
            "required": false
        },
        {
            "name": "LINUX_SSH_TIMEOUT_MS",
            "description": { "zh": "默认命令超时毫秒，默认 20000", "en": "Default timeout in ms, default 20000" },
            "required": false
        }
    ],
    "tools": [
        {
            "name": "usage_advice",
            "description": {
                "zh": "Linux SSH 使用建议：\\n- 长任务优先用 tmux（linux_ssh_tmux_run），不要直接前台阻塞。\\n- 先用 linux_ssh_configure 配置连接参数，后续工具不再接收认证参数。\\n- 修改远程文件前优先先读后改，避免覆盖错误。\\n- 避免破坏性命令（如 rm -rf /、强制重置），除非得到明确授权。",
                "en": "Linux SSH usage advice:\\n- Prefer tmux for long-running tasks (linux_ssh_tmux_run).\\n- Configure SSH fields with linux_ssh_configure first; other tools no longer accept auth fields.\\n- Read-before-write when editing remote files.\\n- Avoid destructive commands unless explicitly authorized."
            },
            "parameters": [],
            "advice": true
        },
        {
            "name": "linux_ssh_configure",
            "description": {
                "zh": "设置并持久化 Linux SSH 连接参数，可选测试连通性。",
                "en": "Set and persist Linux SSH connection parameters, with optional connectivity test."
            },
            "parameters": [
                { "name": "host", "description": { "zh": "主机地址", "en": "Host" }, "type": "string", "required": false },
                { "name": "port", "description": { "zh": "端口，默认 22", "en": "Port, default 22" }, "type": "number", "required": false },
                { "name": "username", "description": { "zh": "用户名", "en": "Username" }, "type": "string", "required": false },
                { "name": "password", "description": { "zh": "密码（可选）", "en": "Password (optional)" }, "type": "string", "required": false },
                { "name": "private_key_path", "description": { "zh": "私钥路径（可选）", "en": "Private key path (optional)" }, "type": "string", "required": false },
                { "name": "tmux_session_name", "description": { "zh": "默认 tmux 会话名（可选）", "en": "Default tmux session name (optional)" }, "type": "string", "required": false },
                { "name": "local_session_name", "description": { "zh": "本地 terminal 会话名（可选）", "en": "Local terminal session name (optional)" }, "type": "string", "required": false },
                { "name": "timeout_ms", "description": { "zh": "默认超时毫秒（可选）", "en": "Default timeout ms (optional)" }, "type": "number", "required": false },
                { "name": "test_connection", "description": { "zh": "是否立即测试连接", "en": "Whether to test connection immediately" }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "linux_ssh_test_connection",
            "description": { "zh": "测试 SSH 连接并返回远端用户和主机信息。", "en": "Test SSH connection and return remote user/host info." },
            "parameters": [
                { "name": "timeout_ms", "description": { "zh": "超时毫秒（可选）", "en": "Timeout ms (optional)" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "linux_ssh_exec",
            "description": { "zh": "通过 SSH 在远程 Linux 执行一条命令。", "en": "Execute one command on remote Linux through SSH." },
            "parameters": [
                { "name": "command", "description": { "zh": "要执行的命令", "en": "Command to execute" }, "type": "string", "required": true },
                { "name": "timeout_ms", "description": { "zh": "超时毫秒（可选）", "en": "Timeout ms (optional)" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "linux_ssh_ensure_tmux",
            "description": { "zh": "在远程 Linux 自动安装并校验 tmux。", "en": "Auto install and verify tmux on remote Linux." },
            "parameters": []
        },
        {
            "name": "linux_ssh_tmux_run",
            "description": { "zh": "在远程 tmux 中启动长任务（断线不影响任务）。", "en": "Start a long-running task in remote tmux (survives disconnects)." },
            "parameters": [
                { "name": "command", "description": { "zh": "要执行的长任务命令", "en": "Long-running command" }, "type": "string", "required": true },
                { "name": "workdir", "description": { "zh": "远程工作目录（可选）", "en": "Remote working directory (optional)" }, "type": "string", "required": false },
                { "name": "tmux_session_name", "description": { "zh": "tmux 会话名（可选）", "en": "tmux session name (optional)" }, "type": "string", "required": false },
                { "name": "window_name", "description": { "zh": "tmux 窗口名（可选）", "en": "tmux window name (optional)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "linux_ssh_tmux_capture",
            "description": { "zh": "抓取远程 tmux 会话窗口输出。", "en": "Capture output from remote tmux session/window." },
            "parameters": [
                { "name": "tmux_session_name", "description": { "zh": "tmux 会话名（可选）", "en": "tmux session name (optional)" }, "type": "string", "required": false },
                { "name": "window_name", "description": { "zh": "tmux 窗口名（可选）", "en": "tmux window name (optional)" }, "type": "string", "required": false },
                { "name": "max_lines", "description": { "zh": "最多抓取行数，默认 200", "en": "Max lines, default 200" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "linux_ssh_tmux_list_windows",
            "description": { "zh": "列出远程 tmux 会话中的窗口列表。", "en": "List windows in a remote tmux session." },
            "parameters": [
                { "name": "tmux_session_name", "description": { "zh": "tmux 会话名（可选）", "en": "tmux session name (optional)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "linux_ssh_terminal_open",
            "description": { "zh": "打开一个可持续交互的 SSH 终端会话。", "en": "Open a persistent interactive SSH terminal session." },
            "parameters": [
                { "name": "local_session_name", "description": { "zh": "本地 terminal 会话名（可选）", "en": "Local terminal session name (optional)" }, "type": "string", "required": false },
                { "name": "timeout_ms", "description": { "zh": "打开动作超时毫秒（可选，默认 3000）", "en": "Open timeout in ms (optional, default 3000)" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "linux_ssh_terminal_input",
            "description": { "zh": "向交互 SSH 终端输入文本或控制键。", "en": "Write text/control keys into interactive SSH terminal." },
            "parameters": [
                { "name": "input", "description": { "zh": "文本输入（可选）", "en": "Input text (optional)" }, "type": "string", "required": false },
                { "name": "control", "description": { "zh": "控制键（如 enter/tab/ctrl）", "en": "Control key (e.g. enter/tab/ctrl)" }, "type": "string", "required": false },
                { "name": "local_session_name", "description": { "zh": "本地 terminal 会话名（可选）", "en": "Local terminal session name (optional)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "linux_ssh_terminal_screen",
            "description": { "zh": "获取交互 SSH 终端当前可见屏幕。", "en": "Get current visible screen of interactive SSH terminal." },
            "parameters": [
                { "name": "local_session_name", "description": { "zh": "本地 terminal 会话名（可选）", "en": "Local terminal session name (optional)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "linux_ssh_terminal_close",
            "description": { "zh": "关闭交互 SSH 终端会话。", "en": "Close interactive SSH terminal session." },
            "parameters": [
                { "name": "local_session_name", "description": { "zh": "本地 terminal 会话名（可选）", "en": "Local terminal session name (optional)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "linux_ssh_ls",
            "description": { "zh": "列出远程目录。", "en": "List remote directory." },
            "parameters": [
                { "name": "path", "description": { "zh": "目录路径，默认 ~", "en": "Directory path, default ~" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "linux_ssh_read",
            "description": { "zh": "读取远程文件（支持按行范围）。", "en": "Read remote file (supports line range)." },
            "parameters": [
                { "name": "path", "description": { "zh": "文件路径", "en": "File path" }, "type": "string", "required": true },
                { "name": "line_start", "description": { "zh": "起始行（可选）", "en": "Start line (optional)" }, "type": "number", "required": false },
                { "name": "line_end", "description": { "zh": "结束行（可选）", "en": "End line (optional)" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "linux_ssh_write",
            "description": { "zh": "写入远程文件（默认覆盖，可选追加）。", "en": "Write remote file (overwrite by default, append optional)." },
            "parameters": [
                { "name": "path", "description": { "zh": "文件路径", "en": "File path" }, "type": "string", "required": true },
                { "name": "content", "description": { "zh": "写入内容", "en": "Content to write" }, "type": "string", "required": true },
                { "name": "append", "description": { "zh": "是否追加，默认 false", "en": "Append mode, default false" }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "linux_ssh_edit",
            "description": { "zh": "按精确字符串替换远程文件内容。", "en": "Edit remote file by exact string replacement." },
            "parameters": [
                { "name": "path", "description": { "zh": "文件路径", "en": "File path" }, "type": "string", "required": true },
                { "name": "old_text", "description": { "zh": "旧文本", "en": "Old text" }, "type": "string", "required": true },
                { "name": "new_text", "description": { "zh": "新文本", "en": "New text" }, "type": "string", "required": true },
                { "name": "expected_replacements", "description": { "zh": "期望替换次数，默认 1", "en": "Expected replacements, default 1" }, "type": "number", "required": false }
            ]
        }
    ]
}
*/

const linuxSshTools = (function () {
    const PACKAGE_VERSION = "0.1.0";
    const DEFAULT_PORT = 22;
    const DEFAULT_TIMEOUT_MS = 20_000;
    const DEFAULT_CONNECT_OPEN_TIMEOUT_MS = 3_000;
    const DEFAULT_LOCAL_SESSION_NAME = "linux_ssh_default_session";
    const DEFAULT_TMUX_SESSION_NAME = "operit_ai";

    const ENV_KEYS = {
        host: "LINUX_SSH_HOST",
        port: "LINUX_SSH_PORT",
        username: "LINUX_SSH_USERNAME",
        password: "LINUX_SSH_PASSWORD",
        privateKeyPath: "LINUX_SSH_PRIVATE_KEY_PATH",
        tmuxSessionName: "LINUX_SSH_TMUX_SESSION",
        localSessionName: "LINUX_SSH_LOCAL_TERMINAL_SESSION",
        timeoutMs: "LINUX_SSH_TIMEOUT_MS"
    };

    function asText(value) {
        return String(value == null ? "" : value);
    }

    function firstNonBlank(...values) {
        for (let i = 0; i < values.length; i += 1) {
            const value = values[i];
            if (typeof value === "string" && value.trim()) {
                return value.trim();
            }
        }
        return "";
    }

    function parsePositiveInt(value, fallbackValue) {
        const raw = asText(value).trim();
        if (!raw) {
            return fallbackValue;
        }
        const parsed = Number(raw);
        if (!Number.isFinite(parsed) || parsed <= 0) {
            return fallbackValue;
        }
        return Math.floor(parsed);
    }

    function parseOptionalPositiveInt(value, fieldName) {
        const raw = asText(value).trim();
        if (!raw) {
            return undefined;
        }
        const parsed = Number(raw);
        if (!Number.isFinite(parsed) || parsed <= 0) {
            throw new Error(`Invalid ${fieldName}: expected positive integer`);
        }
        return Math.floor(parsed);
    }

    function parseBoolean(value, fallbackValue) {
        const raw = asText(value).trim().toLowerCase();
        if (!raw) {
            return fallbackValue;
        }
        if (raw === "true" || raw === "1" || raw === "yes" || raw === "on") {
            return true;
        }
        if (raw === "false" || raw === "0" || raw === "no" || raw === "off") {
            return false;
        }
        return fallbackValue;
    }

    function readEnv(name) {
        if (typeof getEnv !== "function") {
            return "";
        }
        const value = getEnv(name);
        return value == null ? "" : asText(value).trim();
    }

    function shellQuote(value) {
        return `'${asText(value).replace(/'/g, `'"'"'`)}'`;
    }

    function maskConfig(config) {
        return {
            host: config.host,
            port: config.port,
            username: config.username,
            hasPassword: !!config.password,
            privateKeyPath: config.privateKeyPath || "",
            tmuxSessionName: config.tmuxSessionName,
            localSessionName: config.localSessionName,
            timeoutMs: config.timeoutMs
        };
    }

    function extractStringResult(result) {
        if (typeof result === "string") {
            return result;
        }
        if (!result || typeof result !== "object" || Array.isArray(result)) {
            return "";
        }
        if (typeof result.value === "string") {
            return result.value;
        }
        if (result.value != null) {
            return String(result.value);
        }
        if (typeof result.data === "string") {
            return result.data;
        }
        if (result.data != null) {
            return String(result.data);
        }
        return "";
    }

    async function writeEnvVar(key, value) {
        try {
            await Tools.SoftwareSettings.writeEnvironmentVariable(key, asText(value));
            return true;
        } catch (_error) {
            return false;
        }
    }

    async function persistProvidedConfig(params) {
        if (!params || typeof params !== "object") {
            return [];
        }
        const entries = [
            ["host", ENV_KEYS.host],
            ["port", ENV_KEYS.port],
            ["username", ENV_KEYS.username],
            ["password", ENV_KEYS.password],
            ["private_key_path", ENV_KEYS.privateKeyPath],
            ["tmux_session_name", ENV_KEYS.tmuxSessionName],
            ["local_session_name", ENV_KEYS.localSessionName],
            ["timeout_ms", ENV_KEYS.timeoutMs]
        ];

        const persisted: string[] = [];
        for (let i = 0; i < entries.length; i += 1) {
            const paramKey = entries[i][0];
            const envKey = entries[i][1];
            if (params[paramKey] !== undefined && params[paramKey] !== null) {
                const ok = await writeEnvVar(envKey, params[paramKey]);
                if (ok) {
                    persisted.push(envKey);
                }
            }
        }
        return persisted;
    }

    async function resolveSshConfig(params, options) {
        const opts = options || {};
        if (opts.persistIfProvided !== false) {
            await persistProvidedConfig(params);
        }

        const allowParamConnection = opts.allowParamConnection !== false;
        const allowParamAuth = opts.allowParamAuth !== false;

        const host = firstNonBlank(
            allowParamConnection && params ? asText(params.host) : "",
            readEnv(ENV_KEYS.host)
        );
        const username = firstNonBlank(
            allowParamConnection && params ? asText(params.username) : "",
            readEnv(ENV_KEYS.username)
        );
        const password = firstNonBlank(
            allowParamAuth && params ? asText(params.password) : "",
            readEnv(ENV_KEYS.password)
        );
        const privateKeyPath = firstNonBlank(
            allowParamAuth && params ? asText(params.private_key_path) : "",
            readEnv(ENV_KEYS.privateKeyPath)
        );

        const portRaw = firstNonBlank(
            allowParamConnection && params ? asText(params.port) : "",
            readEnv(ENV_KEYS.port),
            String(DEFAULT_PORT)
        );
        const timeoutRaw = firstNonBlank(params && asText(params.timeout_ms), readEnv(ENV_KEYS.timeoutMs), String(DEFAULT_TIMEOUT_MS));
        const tmuxSessionName = firstNonBlank(params && asText(params.tmux_session_name), readEnv(ENV_KEYS.tmuxSessionName), DEFAULT_TMUX_SESSION_NAME);
        const localSessionName = firstNonBlank(params && asText(params.local_session_name), readEnv(ENV_KEYS.localSessionName), DEFAULT_LOCAL_SESSION_NAME);

        const port = parsePositiveInt(portRaw, DEFAULT_PORT);
        const timeoutMs = parsePositiveInt(timeoutRaw, DEFAULT_TIMEOUT_MS);

        if (!host) {
            throw new Error("Missing SSH host. Configure with linux_ssh_configure or set LINUX_SSH_HOST");
        }
        if (!username) {
            throw new Error("Missing SSH username. Configure with linux_ssh_configure or set LINUX_SSH_USERNAME");
        }

        if (opts.requireAuth !== false) {
            if (!password && !privateKeyPath) {
                throw new Error("Missing SSH auth. Configure password/private_key_path via linux_ssh_configure");
            }
        }

        return {
            host,
            port,
            username,
            password,
            privateKeyPath,
            tmuxSessionName,
            localSessionName,
            timeoutMs
        };
    }

    async function createLocalTerminalSession(localSessionName) {
        return await Tools.System.terminal.create(localSessionName || DEFAULT_LOCAL_SESSION_NAME);
    }

    async function runLocalCommand(sessionName, command, timeoutMs) {
        const session = await createLocalTerminalSession(sessionName);
        const effectiveTimeout = parsePositiveInt(timeoutMs, DEFAULT_TIMEOUT_MS);
        const result = await Tools.System.terminal.exec(session.sessionId, command, effectiveTimeout);
        return {
            sessionId: result.sessionId || session.sessionId,
            exitCode: Number(result.exitCode || 0),
            timedOut: !!result.timedOut,
            output: asText(result.output),
            command: asText(result.command || command)
        };
    }

    async function ensureLocalCommand(sessionName, commandName, installScript) {
        const check = await runLocalCommand(
            sessionName,
            `if command -v ${commandName} >/dev/null 2>&1; then echo '__FOUND__'; else echo '__MISSING__'; fi`,
            DEFAULT_TIMEOUT_MS
        );
        if (check.output.includes("__FOUND__")) {
            return { success: true, installed: false, output: check.output };
        }

        const install = await runLocalCommand(sessionName, installScript, 180_000);
        const verify = await runLocalCommand(
            sessionName,
            `if command -v ${commandName} >/dev/null 2>&1; then echo '__FOUND__'; else echo '__MISSING__'; fi`,
            DEFAULT_TIMEOUT_MS
        );

        if (!verify.output.includes("__FOUND__")) {
            throw new Error(
                `Failed to install ${commandName}.\nInstall output:\n${install.output}\nVerify output:\n${verify.output}`
            );
        }
        return { success: true, installed: true, output: install.output };
    }

    async function ensureLocalSshDependencies(config) {
        const sessionName = config.localSessionName;

        await ensureLocalCommand(
            sessionName,
            "ssh",
            [
                "if command -v apt-get >/dev/null 2>&1; then",
                "  (sudo -n apt-get update && sudo -n apt-get install -y openssh-client) || (apt-get update && apt-get install -y openssh-client)",
                "elif command -v dnf >/dev/null 2>&1; then",
                "  (sudo -n dnf install -y openssh-clients) || dnf install -y openssh-clients",
                "elif command -v yum >/dev/null 2>&1; then",
                "  (sudo -n yum install -y openssh-clients) || yum install -y openssh-clients",
                "elif command -v pacman >/dev/null 2>&1; then",
                "  (sudo -n pacman -Sy --noconfirm openssh) || pacman -Sy --noconfirm openssh",
                "else",
                "  echo '__NO_PACKAGE_MANAGER__'",
                "fi"
            ].join("\n")
        );

        if (config.password && !config.privateKeyPath) {
            await ensureLocalCommand(
                sessionName,
                "sshpass",
                [
                    "if command -v apt-get >/dev/null 2>&1; then",
                    "  (sudo -n apt-get update && sudo -n apt-get install -y sshpass) || (apt-get update && apt-get install -y sshpass)",
                    "elif command -v dnf >/dev/null 2>&1; then",
                    "  (sudo -n dnf install -y sshpass) || dnf install -y sshpass",
                    "elif command -v yum >/dev/null 2>&1; then",
                    "  (sudo -n yum install -y sshpass) || yum install -y sshpass",
                    "elif command -v pacman >/dev/null 2>&1; then",
                    "  (sudo -n pacman -Sy --noconfirm sshpass) || pacman -Sy --noconfirm sshpass",
                    "else",
                    "  echo '__NO_PACKAGE_MANAGER__'",
                    "fi"
                ].join("\n")
            );
        }
    }

    function buildSshOptions(config) {
        const connectTimeoutSeconds = Math.max(5, Math.floor(config.timeoutMs / 1000));
        const options = [
            "-o StrictHostKeyChecking=no",
            "-o UserKnownHostsFile=/dev/null",
            "-o LogLevel=ERROR",
            "-o ServerAliveInterval=30",
            "-o ServerAliveCountMax=120",
            `-o ConnectTimeout=${connectTimeoutSeconds}`
        ];

        if (config.password && !config.privateKeyPath) {
            options.push("-o PreferredAuthentications=password", "-o PubkeyAuthentication=no");
        }

        return options.join(" ");
    }

    function buildSshCommand(config, remoteCommand, interactive) {
        const authPrefix = (config.password && !config.privateKeyPath)
            ? `SSHPASS=${shellQuote(config.password)} sshpass -e `
            : "";
        const keyPart = config.privateKeyPath ? ` -i ${shellQuote(config.privateKeyPath)}` : "";
        const target = `${config.username}@${config.host}`;
        const options = buildSshOptions(config);
        const tty = interactive ? " -tt" : "";
        const base = `${authPrefix}ssh${tty}${keyPart} ${options} -p ${config.port} ${shellQuote(target)}`;
        if (remoteCommand === undefined || remoteCommand === null) {
            return base;
        }
        return `${base} ${shellQuote(remoteCommand)}`;
    }

    async function runRemoteCommand(config, remoteCommand, timeoutMs) {
        await ensureLocalSshDependencies(config);
        const command = buildSshCommand(config, remoteCommand, false);
        const result = await runLocalCommand(config.localSessionName, command, timeoutMs || config.timeoutMs);
        return result;
    }

    function extractBlock(output, beginToken, endToken) {
        const start = output.indexOf(beginToken);
        if (start < 0) {
            return "";
        }
        const from = start + beginToken.length;
        const end = output.indexOf(endToken, from);
        if (end < 0) {
            return output.slice(from).trim();
        }
        return output.slice(from, end).trim();
    }

    function hasExactMarkerLine(output, marker) {
        return asText(output)
            .split(/\r?\n/)
            .some((line) => asText(line).trim() === marker);
    }

    function decodeBase64Utf8(value) {
        const raw = asText(value).replace(/\s+/g, "");
        if (!raw) {
            return "";
        }
        try {
            if (typeof atob === "function") {
                const binary = atob(raw);
                if (typeof TextDecoder !== "undefined") {
                    const bytes = new Uint8Array(binary.length);
                    for (let i = 0; i < binary.length; i += 1) {
                        bytes[i] = binary.charCodeAt(i);
                    }
                    return new TextDecoder("utf-8").decode(bytes);
                }
                return binary;
            }
        } catch (_error) {
            // Ignore decode errors and fallback to raw text.
        }
        return raw;
    }

    async function ensureRemoteTmux(config) {
        const installScript = [
            "if command -v tmux >/dev/null 2>&1; then",
            "  echo '__TMUX_READY__'",
            "  exit 0",
            "fi",
            "if command -v apt-get >/dev/null 2>&1; then",
            "  (sudo -n apt-get update && sudo -n apt-get install -y tmux) || (apt-get update && apt-get install -y tmux)",
            "elif command -v dnf >/dev/null 2>&1; then",
            "  (sudo -n dnf install -y tmux) || dnf install -y tmux",
            "elif command -v yum >/dev/null 2>&1; then",
            "  (sudo -n yum install -y tmux) || yum install -y tmux",
            "elif command -v pacman >/dev/null 2>&1; then",
            "  (sudo -n pacman -Sy --noconfirm tmux) || pacman -Sy --noconfirm tmux",
            "else",
            "  echo '__NO_PACKAGE_MANAGER__'",
            "fi",
            "if command -v tmux >/dev/null 2>&1; then",
            "  echo '__TMUX_READY__'",
            "  exit 0",
            "fi",
            "echo '__TMUX_INSTALL_FAILED__'",
            "exit 7"
        ].join("\n");

        const result = await runRemoteCommand(config, installScript, 240_000);
        const success = result.exitCode === 0 && result.output.includes("__TMUX_READY__");
        return {
            success,
            exitCode: result.exitCode,
            timedOut: result.timedOut,
            output: result.output
        };
    }

    async function readRemoteFileContent(config, path, lineStart, lineEnd) {
        const escapedPath = shellQuote(path);
        let readCmd = `cat ${escapedPath}`;

        if (lineStart !== undefined || lineEnd !== undefined) {
            const start = lineStart === undefined ? 1 : lineStart;
            const end = lineEnd === undefined ? "$" : String(lineEnd);
            readCmd = `sed -n '${start},${end}p' ${escapedPath}`;
        }

        const script = [
            `if [ ! -f ${escapedPath} ]; then`,
            "  echo '__OPERIT_FILE_NOT_FOUND__'",
            "  exit 4",
            "fi",
            "printf '__OPERIT_BEGIN__\\n'",
            readCmd,
            "printf '\\n__OPERIT_END__\\n'"
        ].join("\n");

        const result = await runRemoteCommand(config, script, config.timeoutMs);
        if (result.exitCode !== 0 || result.timedOut) {
            throw new Error(`Failed to read remote file: ${result.output}`);
        }

        const content = extractBlock(result.output, "__OPERIT_BEGIN__", "__OPERIT_END__");
        return {
            output: result.output,
            content
        };
    }

    async function writeRemoteFileContent(config, path, content, appendMode) {
        const escapedPath = shellQuote(path);
        const redirectOperator = appendMode ? ">>" : ">";

        const script = [
            `mkdir -p \"$(dirname -- ${escapedPath})\"`,
            `cat ${redirectOperator} ${escapedPath} <<'__OPERIT_EOF__'`,
            asText(content),
            "__OPERIT_EOF__"
        ].join("\n");

        const result = await runRemoteCommand(config, script, config.timeoutMs);
        if (result.exitCode !== 0 || result.timedOut) {
            throw new Error(`Failed to write remote file: ${result.output}`);
        }

        return {
            output: result.output,
            exitCode: result.exitCode,
            timedOut: result.timedOut
        };
    }

    async function linux_ssh_configure(params) {
        try {
            const config = await resolveSshConfig(params, {
                persistIfProvided: true,
                requireAuth: false,
                allowParamConnection: true,
                allowParamAuth: true
            });
            const testConnection = parseBoolean(params && params.test_connection, false);

            let connection: unknown = null;
            if (testConnection) {
                connection = await linux_ssh_test_connection(params);
            }

            return {
                success: true,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                testConnection,
                connection
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_test_connection(params) {
        try {
            const config = await resolveSshConfig(params, {
                persistIfProvided: false,
                requireAuth: true,
                allowParamConnection: false,
                allowParamAuth: false
            });
            const timeoutMs = parsePositiveInt(params && params.timeout_ms, config.timeoutMs);
            const command = [
                "printf '__OPERIT_CONNECT_BEGIN__\\n'",
                "echo \"user=$(whoami)\"",
                "echo \"host=$(hostname)\"",
                "if command -v tmux >/dev/null 2>&1; then echo 'tmux=present'; else echo 'tmux=missing'; fi",
                "printf '__OPERIT_CONNECT_END__\\n'"
            ].join("\n");

            const result = await runRemoteCommand(config, command, timeoutMs);
            const success = result.exitCode === 0 && !result.timedOut;
            const block = extractBlock(result.output, "__OPERIT_CONNECT_BEGIN__", "__OPERIT_CONNECT_END__");

            return {
                success,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                timeoutMs,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: block || result.output,
                rawOutput: result.output,
                error: success ? "" : `SSH connection failed, exitCode=${result.exitCode}`
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_exec(params) {
        try {
            const command = asText(params && params.command).trim();
            if (!command) {
                throw new Error("Missing required parameter: command");
            }

            const config = await resolveSshConfig(params, {
                persistIfProvided: false,
                requireAuth: true,
                allowParamConnection: false,
                allowParamAuth: false
            });
            const timeoutMs = parsePositiveInt(params && params.timeout_ms, config.timeoutMs);
            const result = await runRemoteCommand(config, command, timeoutMs);
            const success = result.exitCode === 0 && !result.timedOut;

            return {
                success,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                command,
                timeoutMs,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: result.output,
                sessionId: result.sessionId,
                error: success ? "" : `Remote command failed, exitCode=${result.exitCode}`
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_ensure_tmux(params) {
        try {
            const config = await resolveSshConfig(params, {
                persistIfProvided: false,
                requireAuth: true,
                allowParamConnection: false,
                allowParamAuth: false
            });
            const tmuxResult = await ensureRemoteTmux(config);

            return {
                success: !!tmuxResult.success,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                exitCode: tmuxResult.exitCode,
                timedOut: tmuxResult.timedOut,
                output: tmuxResult.output,
                error: tmuxResult.success ? "" : "Failed to install or verify tmux on remote host"
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_tmux_run(params) {
        try {
            const command = asText(params && params.command).trim();
            if (!command) {
                throw new Error("Missing required parameter: command");
            }

            const config = await resolveSshConfig(params, {
                persistIfProvided: false,
                requireAuth: true,
                allowParamConnection: false,
                allowParamAuth: false
            });
            const tmuxSessionName = firstNonBlank(params && asText(params.tmux_session_name), config.tmuxSessionName);
            const windowName = firstNonBlank(params && asText(params.window_name), `job_${Date.now()}`);
            const workdir = asText(params && params.workdir).trim();

            const tmuxReady = await ensureRemoteTmux(config);
            if (!tmuxReady.success) {
                throw new Error(`tmux setup failed: ${tmuxReady.output}`);
            }

            const runLine = workdir ? `cd ${shellQuote(workdir)} && ${command}` : command;
            const targetWindow = `${tmuxSessionName}:${windowName}`;

            const script = [
                `tmux has-session -t ${shellQuote(tmuxSessionName)} 2>/dev/null || tmux new-session -d -s ${shellQuote(tmuxSessionName)} -n main`,
                `tmux new-window -d -t ${shellQuote(tmuxSessionName)} -n ${shellQuote(windowName)}`,
                `tmux send-keys -t ${shellQuote(targetWindow)} ${shellQuote(runLine)} C-m`,
                "printf '__OPERIT_TMUX_RUN_OK__\\n'",
                `echo "session=${tmuxSessionName}"`,
                `echo "window=${windowName}"`
            ].join("\n");

            const result = await runRemoteCommand(config, script, config.timeoutMs);
            const success = result.exitCode === 0 && !result.timedOut && result.output.includes("__OPERIT_TMUX_RUN_OK__");

            return {
                success,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                tmuxSessionName,
                windowName,
                command,
                workdir,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: result.output,
                error: success ? "" : `tmux run failed, exitCode=${result.exitCode}`
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_tmux_capture(params) {
        try {
            const config = await resolveSshConfig(params, {
                persistIfProvided: false,
                requireAuth: true,
                allowParamConnection: false,
                allowParamAuth: false
            });
            const tmuxSessionName = firstNonBlank(params && asText(params.tmux_session_name), config.tmuxSessionName);
            const windowName = asText(params && params.window_name).trim();
            const maxLines = parsePositiveInt(params && params.max_lines, 200);

            const tmuxReady = await ensureRemoteTmux(config);
            if (!tmuxReady.success) {
                throw new Error(`tmux setup failed: ${tmuxReady.output}`);
            }

            const target = windowName ? `${tmuxSessionName}:${windowName}` : tmuxSessionName;
            const script = [
                `tmux has-session -t ${shellQuote(tmuxSessionName)} 2>/dev/null || { echo '__OPERIT_TMUX_NOT_FOUND__'; exit 4; }`,
                `printf '__OPERIT_TMUX_CAPTURE_B64_BEGIN__\\n'`,
                `tmux capture-pane -t ${shellQuote(target)} -p -S -${maxLines} | base64 | tr -d '\\n'`,
                `printf '\\n__OPERIT_TMUX_CAPTURE_B64_END__\\n'`
            ].join("; ");

            const result = await runRemoteCommand(config, script, config.timeoutMs);
            const success = result.exitCode === 0 && !result.timedOut;
            const encoded = extractBlock(result.output, "__OPERIT_TMUX_CAPTURE_B64_BEGIN__", "__OPERIT_TMUX_CAPTURE_B64_END__");
            const legacyContent = extractBlock(result.output, "__OPERIT_TMUX_CAPTURE_BEGIN__", "__OPERIT_TMUX_CAPTURE_END__");
            const decodedContent = decodeBase64Utf8(encoded);
            const content = decodedContent || legacyContent;

            return {
                success,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                tmuxSessionName,
                windowName,
                maxLines,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: content || result.output,
                rawOutput: result.output,
                error: success ? "" : `tmux capture failed, exitCode=${result.exitCode}`
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_tmux_list_windows(params) {
        try {
            const config = await resolveSshConfig(params, {
                persistIfProvided: false,
                requireAuth: true,
                allowParamConnection: false,
                allowParamAuth: false
            });
            const tmuxSessionName = firstNonBlank(params && asText(params.tmux_session_name), config.tmuxSessionName);

            const tmuxReady = await ensureRemoteTmux(config);
            if (!tmuxReady.success) {
                throw new Error(`tmux setup failed: ${tmuxReady.output}`);
            }

            const script = [
                `tmux has-session -t ${shellQuote(tmuxSessionName)} 2>/dev/null || { echo '__OPERIT_TMUX_NOT_FOUND__'; exit 4; }`,
                "printf '__OPERIT_TMUX_WINDOWS_BEGIN__\\n'",
                `tmux list-windows -t ${shellQuote(tmuxSessionName)} -F '#{window_index}:#{window_name}'`,
                "printf '__OPERIT_TMUX_WINDOWS_END__\\n'"
            ].join("; ");

            const result = await runRemoteCommand(config, script, config.timeoutMs);
            const notFound = hasExactMarkerLine(result.output, "__OPERIT_TMUX_NOT_FOUND__");
            const success = result.exitCode === 0 && !result.timedOut && !notFound;
            const block = extractBlock(result.output, "__OPERIT_TMUX_WINDOWS_BEGIN__", "__OPERIT_TMUX_WINDOWS_END__");
            const windows = block
                .split(/\r?\n/)
                .map((line) => asText(line).trim())
                .filter((line) => !!line && /^\d+:.+/.test(line))
                .map((line) => {
                    const firstColon = line.indexOf(":");
                    const index = firstColon >= 0 ? line.slice(0, firstColon).trim() : "";
                    const name = firstColon >= 0 ? line.slice(firstColon + 1).trim() : line;
                    return {
                        index,
                        name,
                        key: index ? `${index}:${name}` : name,
                        label: index ? `#${index} ${name}` : name
                    };
                });

            return {
                success,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                tmuxSessionName,
                sessionExists: !notFound,
                windows,
                count: windows.length,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: block || result.output,
                rawOutput: result.output,
                error: notFound
                    ? `tmux session not found: ${tmuxSessionName}`
                    : (success ? "" : `tmux list windows failed, exitCode=${result.exitCode}`)
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_terminal_open(params) {
        try {
            const config = await resolveSshConfig(params, {
                persistIfProvided: false,
                requireAuth: true,
                allowParamConnection: false,
                allowParamAuth: false
            });
            await ensureLocalSshDependencies(config);

            const session = await createLocalTerminalSession(config.localSessionName);
            const command = buildSshCommand(config, undefined, true);
            const timeoutMs = parsePositiveInt(params && params.timeout_ms, DEFAULT_CONNECT_OPEN_TIMEOUT_MS);

            const openResult = await Tools.System.terminal.exec(session.sessionId, command, timeoutMs);

            return {
                success: true,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                sessionId: openResult.sessionId || session.sessionId,
                timeoutMs,
                timedOut: !!openResult.timedOut,
                exitCode: Number(openResult.exitCode || 0),
                output: asText(openResult.output),
                hint: "Use linux_ssh_terminal_input and linux_ssh_terminal_screen for interactive operations."
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_terminal_input(params) {
        try {
            const input = params && params.input;
            const control = params && params.control;
            if (input === undefined && control === undefined) {
                throw new Error("At least one of input/control is required");
            }

            const localSessionName = firstNonBlank(
                params && asText(params.local_session_name),
                readEnv(ENV_KEYS.localSessionName),
                DEFAULT_LOCAL_SESSION_NAME
            );
            const session = await createLocalTerminalSession(localSessionName);
            const result = await Tools.System.terminal.input(session.sessionId, {
                input: input === undefined ? undefined : asText(input),
                control: control === undefined ? undefined : asText(control)
            });

            return {
                success: true,
                packageVersion: PACKAGE_VERSION,
                localSessionName,
                sessionId: session.sessionId,
                result: extractStringResult(result)
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_terminal_screen(params) {
        try {
            const localSessionName = firstNonBlank(
                params && asText(params.local_session_name),
                readEnv(ENV_KEYS.localSessionName),
                DEFAULT_LOCAL_SESSION_NAME
            );
            const session = await createLocalTerminalSession(localSessionName);
            const result = await Tools.System.terminal.screen(session.sessionId);
            return {
                success: true,
                packageVersion: PACKAGE_VERSION,
                localSessionName,
                sessionId: result.sessionId || session.sessionId,
                rows: Number(result.rows || 0),
                cols: Number(result.cols || 0),
                content: asText(result.content)
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_terminal_close(params) {
        try {
            const localSessionName = firstNonBlank(
                params && asText(params.local_session_name),
                readEnv(ENV_KEYS.localSessionName),
                DEFAULT_LOCAL_SESSION_NAME
            );
            const session = await createLocalTerminalSession(localSessionName);
            await Tools.System.terminal.input(session.sessionId, { input: "exit" });
            await Tools.System.terminal.input(session.sessionId, { control: "enter" });
            const closeResult = await Tools.System.terminal.close(session.sessionId);
            return {
                success: true,
                packageVersion: PACKAGE_VERSION,
                localSessionName,
                sessionId: session.sessionId,
                closeMessage: extractStringResult(closeResult)
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_ls(params) {
        try {
            const config = await resolveSshConfig(params, {
                persistIfProvided: false,
                requireAuth: true,
                allowParamConnection: false,
                allowParamAuth: false
            });
            const path = firstNonBlank(params && asText(params.path), "~");
            const command = `ls -la ${shellQuote(path)}`;
            const result = await runRemoteCommand(config, command, config.timeoutMs);
            const success = result.exitCode === 0 && !result.timedOut;

            return {
                success,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                path,
                exitCode: result.exitCode,
                timedOut: result.timedOut,
                output: result.output,
                error: success ? "" : `ls failed, exitCode=${result.exitCode}`
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_read(params) {
        try {
            const path = asText(params && params.path).trim();
            if (!path) {
                throw new Error("Missing required parameter: path");
            }

            const config = await resolveSshConfig(params, {
                persistIfProvided: false,
                requireAuth: true,
                allowParamConnection: false,
                allowParamAuth: false
            });
            const lineStart = parseOptionalPositiveInt(params && params.line_start, "line_start");
            const lineEnd = parseOptionalPositiveInt(params && params.line_end, "line_end");
            if (lineStart !== undefined && lineEnd !== undefined && lineEnd < lineStart) {
                throw new Error("line_end must be greater than or equal to line_start");
            }

            const readResult = await readRemoteFileContent(config, path, lineStart, lineEnd);
            return {
                success: true,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                path,
                lineStart: lineStart === undefined ? null : lineStart,
                lineEnd: lineEnd === undefined ? null : lineEnd,
                content: readResult.content,
                rawOutput: readResult.output
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_write(params) {
        try {
            const path = asText(params && params.path).trim();
            if (!path) {
                throw new Error("Missing required parameter: path");
            }
            if (!params || params.content === undefined || params.content === null) {
                throw new Error("Missing required parameter: content");
            }

            const config = await resolveSshConfig(params, {
                persistIfProvided: false,
                requireAuth: true,
                allowParamConnection: false,
                allowParamAuth: false
            });
            const content = asText(params.content);
            const append = parseBoolean(params && params.append, false);
            const writeResult = await writeRemoteFileContent(config, path, content, append);

            return {
                success: true,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                path,
                append,
                contentLength: content.length,
                exitCode: writeResult.exitCode,
                timedOut: writeResult.timedOut,
                output: writeResult.output
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    async function linux_ssh_edit(params) {
        try {
            const path = asText(params && params.path).trim();
            if (!path) {
                throw new Error("Missing required parameter: path");
            }
            const oldText = asText(params && params.old_text);
            if (!oldText) {
                throw new Error("Missing required parameter: old_text");
            }
            if (!params || params.new_text === undefined || params.new_text === null) {
                throw new Error("Missing required parameter: new_text");
            }

            const config = await resolveSshConfig(params, {
                persistIfProvided: false,
                requireAuth: true,
                allowParamConnection: false,
                allowParamAuth: false
            });
            const newText = asText(params.new_text);
            const expectedReplacements = parsePositiveInt(params && params.expected_replacements, 1);

            const readResult = await readRemoteFileContent(config, path, undefined, undefined);
            const source = readResult.content;
            const parts = source.split(oldText);
            const replacements = parts.length - 1;

            if (replacements !== expectedReplacements) {
                throw new Error(
                    `Replacement count mismatch: expected ${expectedReplacements}, actual ${replacements}`
                );
            }

            const updated = parts.join(newText);
            await writeRemoteFileContent(config, path, updated, false);

            return {
                success: true,
                packageVersion: PACKAGE_VERSION,
                config: maskConfig(config),
                path,
                replacements,
                expectedReplacements,
                beforeLength: source.length,
                afterLength: updated.length
            };
        } catch (error) {
            return {
                success: false,
                packageVersion: PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }

    return {
        linux_ssh_configure,
        linux_ssh_test_connection,
        linux_ssh_exec,
        linux_ssh_ensure_tmux,
        linux_ssh_tmux_run,
        linux_ssh_tmux_capture,
        linux_ssh_tmux_list_windows,
        linux_ssh_terminal_open,
        linux_ssh_terminal_input,
        linux_ssh_terminal_screen,
        linux_ssh_terminal_close,
        linux_ssh_ls,
        linux_ssh_read,
        linux_ssh_write,
        linux_ssh_edit
    };
})();

exports.linux_ssh_configure = linuxSshTools.linux_ssh_configure;
exports.linux_ssh_test_connection = linuxSshTools.linux_ssh_test_connection;
exports.linux_ssh_exec = linuxSshTools.linux_ssh_exec;
exports.linux_ssh_ensure_tmux = linuxSshTools.linux_ssh_ensure_tmux;
exports.linux_ssh_tmux_run = linuxSshTools.linux_ssh_tmux_run;
exports.linux_ssh_tmux_capture = linuxSshTools.linux_ssh_tmux_capture;
exports.linux_ssh_tmux_list_windows = linuxSshTools.linux_ssh_tmux_list_windows;
exports.linux_ssh_terminal_open = linuxSshTools.linux_ssh_terminal_open;
exports.linux_ssh_terminal_input = linuxSshTools.linux_ssh_terminal_input;
exports.linux_ssh_terminal_screen = linuxSshTools.linux_ssh_terminal_screen;
exports.linux_ssh_terminal_close = linuxSshTools.linux_ssh_terminal_close;
exports.linux_ssh_ls = linuxSshTools.linux_ssh_ls;
exports.linux_ssh_read = linuxSshTools.linux_ssh_read;
exports.linux_ssh_write = linuxSshTools.linux_ssh_write;
exports.linux_ssh_edit = linuxSshTools.linux_ssh_edit;

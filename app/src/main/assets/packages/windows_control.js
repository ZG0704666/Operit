/* METADATA
{
    "name": "windows_control",
    "description": {
        "zh": "通过 HTTP 调用 Operit PC Agent 控制 Windows 电脑，支持执行 PowerShell/CMD 命令并返回输出。",
        "en": "Control a Windows PC through Operit PC Agent over HTTP, execute PowerShell/CMD commands, and return output."
    },
    "enabledByDefault": false,
    "env": [
        {
            "name": "WINDOWS_AGENT_BASE_URL",
            "description": { "zh": "Operit PC Agent 地址，例如 http://192.168.1.8:58321", "en": "Operit PC Agent URL, e.g. http://192.168.1.8:58321" },
            "required": true
        },
        {
            "name": "WINDOWS_AGENT_TOKEN",
            "description": { "zh": "Agent API 令牌（必填）", "en": "Agent API token (required)" },
            "required": true
        },
        {
            "name": "WINDOWS_AGENT_DEFAULT_SHELL",
            "description": { "zh": "默认 shell：powershell/pwsh/cmd，默认 powershell", "en": "Default shell: powershell/pwsh/cmd, default powershell" },
            "required": false
        },
        {
            "name": "WINDOWS_AGENT_TIMEOUT_MS",
            "description": { "zh": "默认命令超时毫秒数，默认 30000", "en": "Default command timeout in ms, default 30000" },
            "required": false
        }
    ],
    "tools": [
        {
            "name": "usage_advice",
            "description": {
                "zh": "Windows 控制建议：\\n- 查找优先使用快速工具（如 rg），其次使用 PowerShell 原生命令。\\n- 修改文件优先使用文件工具（read/edit/write），采用小步、可追踪修改。\\n- 不做破坏性操作（如 git reset --hard、强制回滚），除非你明确授权。\\n- 涉及权限风险或越界操作时，先申请授权再执行。",
                "en": "Windows control advice:\\n- Prefer fast search tools first (e.g., rg), then PowerShell native commands.\\n- For file changes, prefer file tools (read/edit/write) with small, traceable steps.\\n- Do not perform destructive operations (e.g., git reset --hard or forced rollback) unless explicitly authorized.\\n- For permission-risk or out-of-bound actions, request authorization before execution."
            },
            "parameters": [],
            "advice": true
        },
        {
            "name": "windows_exec",
            "description": {
                "zh": "通过 Operit PC Agent 的 HTTP 接口在 Windows 上执行命令。",
                "en": "Execute commands on Windows via Operit PC Agent HTTP API."
            },
            "parameters": [
                {
                    "name": "command",
                    "description": { "zh": "要执行的命令内容", "en": "Command content to execute" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "shell",
                    "description": { "zh": "shell：powershell/pwsh/cmd，默认读取环境变量或 powershell", "en": "Shell: powershell/pwsh/cmd, default from env or powershell" },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次命令超时毫秒数（可选）", "en": "Timeout for this command in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "windows_test_connection",
            "description": {
                "zh": "测试 Agent HTTP 连通性，并执行 whoami 预设验证命令通道。",
                "en": "Test Agent HTTP connectivity and run whoami preset to verify command channel."
            },
            "parameters": [
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次测试超时毫秒数（可选）", "en": "Timeout for this test in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "read",
            "description": {
                "zh": "读取 Windows 文件。支持整文件读取，也支持按 offset/length 分段读取。",
                "en": "Read a file on Windows. Supports full read and segmented read by offset/length."
            },
            "parameters": [
                {
                    "name": "path",
                    "description": { "zh": "文件路径（绝对路径或相对 Agent 工作目录）", "en": "File path (absolute path or relative to agent working directory)" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "encoding",
                    "description": { "zh": "文本编码：utf8/utf16le/ascii/latin1，默认 utf8", "en": "Text encoding: utf8/utf16le/ascii/latin1, default utf8" },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "offset",
                    "description": { "zh": "分段读取起始字节偏移（可选）", "en": "Start byte offset for segmented read (optional)" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "length",
                    "description": { "zh": "分段读取长度（字节，可选）", "en": "Segment length in bytes (optional)" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次操作超时毫秒数（可选）", "en": "Timeout for this operation in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "edit",
            "description": {
                "zh": "读取文件后执行精确字符串替换，再覆盖写回。默认要求只替换 1 处。",
                "en": "Read file, perform exact string replacement, and overwrite the file. Default expects exactly 1 replacement."
            },
            "parameters": [
                {
                    "name": "path",
                    "description": { "zh": "文件路径", "en": "File path" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "old_text",
                    "description": { "zh": "待替换的原始文本（精确匹配）", "en": "Original text to replace (exact match)" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "new_text",
                    "description": { "zh": "替换后的文本", "en": "Replacement text" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "expected_replacements",
                    "description": { "zh": "期望替换次数，默认 1", "en": "Expected number of replacements, default 1" },
                    "type": "number",
                    "required": false
                },
                {
                    "name": "encoding",
                    "description": { "zh": "文本编码，默认 utf8", "en": "Text encoding, default utf8" },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次操作超时毫秒数（可选）", "en": "Timeout for this operation in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        },
        {
            "name": "write",
            "description": {
                "zh": "覆盖写入文件内容（会替换原文件全部内容）。",
                "en": "Overwrite file content (replaces the entire file)."
            },
            "parameters": [
                {
                    "name": "path",
                    "description": { "zh": "文件路径", "en": "File path" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "content",
                    "description": { "zh": "写入内容", "en": "Content to write" },
                    "type": "string",
                    "required": true
                },
                {
                    "name": "encoding",
                    "description": { "zh": "文本编码，默认 utf8", "en": "Text encoding, default utf8" },
                    "type": "string",
                    "required": false
                },
                {
                    "name": "timeout_ms",
                    "description": { "zh": "本次操作超时毫秒数（可选）", "en": "Timeout for this operation in ms (optional)" },
                    "type": "number",
                    "required": false
                }
            ]
        }
    ]
}
*/
const windowsControl = (function () {
    const WINDOWS_CONTROL_PACKAGE_VERSION = "0.1.0";
    const ENV_KEYS = {
        baseUrl: "WINDOWS_AGENT_BASE_URL",
        token: "WINDOWS_AGENT_TOKEN",
        defaultShell: "WINDOWS_AGENT_DEFAULT_SHELL",
        timeoutMs: "WINDOWS_AGENT_TIMEOUT_MS"
    };
    function asText(value) {
        return String(value == null ? "" : value);
    }
    function buildVersionMismatchMessage(remoteVersion) {
        return [
            `Version mismatch: package=${WINDOWS_CONTROL_PACKAGE_VERSION}, agent=${remoteVersion || "unknown"}.`,
            "请前往 Windows 一键配置，重新上传最新 operit-pc-agent.zip 到电脑并运行，然后再粘贴最新配置。"
        ].join(" ");
    }
    function readEnv(name) {
        if (typeof getEnv === "function") {
            const value = getEnv(name);
            if (value !== undefined && value !== null) {
                return asText(value).trim();
            }
        }
        return "";
    }
    function normalizeShell(value, fallback) {
        const raw = asText(value || fallback).trim().toLowerCase();
        if (raw === "pwsh" || raw === "cmd") {
            return raw;
        }
        return "powershell";
    }
    function parseTimeout(value, fallback) {
        const raw = asText(value).trim();
        if (!raw) {
            return fallback;
        }
        const parsed = Number(raw);
        if (!Number.isFinite(parsed) || parsed < 1000 || parsed > 600000) {
            throw new Error("Invalid timeout_ms, expected 1000..600000");
        }
        return Math.floor(parsed);
    }
    function parseOptionalNonNegativeInt(value, fieldName) {
        const raw = asText(value).trim();
        if (!raw) {
            return undefined;
        }
        const parsed = Number(raw);
        if (!Number.isFinite(parsed) || parsed < 0) {
            throw new Error(`Invalid ${fieldName}, expected non-negative integer`);
        }
        return Math.floor(parsed);
    }
    function parseExpectedReplacements(value) {
        const raw = asText(value).trim();
        if (!raw) {
            return 1;
        }
        const parsed = Number(raw);
        if (!Number.isFinite(parsed) || parsed < 1) {
            throw new Error("Invalid expected_replacements, expected integer >= 1");
        }
        return Math.floor(parsed);
    }
    function normalizeBaseUrl(rawValue) {
        let base = asText(rawValue).trim();
        if (!base) {
            throw new Error("Missing env: WINDOWS_AGENT_BASE_URL");
        }
        if (!/^https?:\/\//i.test(base)) {
            base = `http://${base}`;
        }
        base = base.replace(/\/+$/, "");
        return base;
    }
    function parseJson(content) {
        const text = asText(content).trim();
        if (!text) {
            return {};
        }
        try {
            return JSON.parse(text);
        }
        catch (error) {
            throw new Error(`Agent response is not valid JSON: ${error && error.message ? error.message : String(error)}`);
        }
    }
    function resolveAgentConfig() {
        const baseUrl = normalizeBaseUrl(readEnv(ENV_KEYS.baseUrl));
        const token = readEnv(ENV_KEYS.token);
        if (!token) {
            throw new Error("Missing env: WINDOWS_AGENT_TOKEN");
        }
        const defaultShell = normalizeShell(readEnv(ENV_KEYS.defaultShell), "powershell");
        const timeoutMs = parseTimeout(readEnv(ENV_KEYS.timeoutMs), 30000);
        return {
            baseUrl,
            token,
            defaultShell,
            timeoutMs
        };
    }
    async function httpRequest(config, path, method, body, timeoutMs) {
        const response = await Tools.Net.http({
            url: `${config.baseUrl}${path}`,
            method,
            headers: {
                Accept: "application/json"
            },
            body: body || undefined,
            connect_timeout: Math.min(timeoutMs, 10000),
            read_timeout: timeoutMs + 5000,
            validateStatus: false
        });
        return {
            statusCode: response.statusCode,
            content: response.content,
            url: response.url
        };
    }
    async function ensureVersionCompatible(config, timeoutMs) {
        const healthResponse = await httpRequest(config, "/api/health", "GET", null, timeoutMs);
        const health = parseJson(healthResponse.content);
        if (healthResponse.statusCode >= 400 || !health.ok) {
            throw new Error(`Health check failed: HTTP ${healthResponse.statusCode}`);
        }
        const remoteVersion = asText(health.version || health.agentVersion).trim();
        if (!remoteVersion) {
            throw new Error("Agent version is missing. 请前往 Windows 一键配置重新部署电脑端。");
        }
        if (remoteVersion !== WINDOWS_CONTROL_PACKAGE_VERSION) {
            throw new Error(buildVersionMismatchMessage(remoteVersion));
        }
        return {
            health,
            remoteVersion
        };
    }
    async function postCommand(config, payload, timeoutMs) {
        const requestBody = Object.assign(Object.assign({}, payload), { token: config.token || undefined, timeoutMs });
        const response = await httpRequest(config, "/api/command/execute", "POST", requestBody, timeoutMs);
        const data = parseJson(response.content);
        if (response.statusCode >= 400) {
            const message = data && data.error ? asText(data.error) : `HTTP ${response.statusCode}`;
            throw new Error(`Agent command request failed: ${message}`);
        }
        return data;
    }
    async function postTextFileApi(config, path, payload, timeoutMs) {
        const requestBody = Object.assign(Object.assign({}, payload), { token: config.token || undefined, timeoutMs });
        const response = await httpRequest(config, path, "POST", requestBody, timeoutMs);
        const data = parseJson(response.content);
        if (response.statusCode >= 400 || !data.ok) {
            const message = data && data.error ? asText(data.error) : `HTTP ${response.statusCode}`;
            throw new Error(`Agent text file request failed: ${message}`);
        }
        return data;
    }
    async function windows_exec(params) {
        try {
            const config = resolveAgentConfig();
            const command = asText(params && params.command).trim();
            if (!command) {
                throw new Error("Missing required parameter: command");
            }
            const shell = normalizeShell(params && params.shell, config.defaultShell);
            const timeoutMs = parseTimeout(params && params.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const data = await postCommand(config, { command, shell }, timeoutMs);
            return {
                success: !!data.ok,
                agentBaseUrl: config.baseUrl,
                shell,
                command,
                exitCode: data.exitCode,
                timedOut: !!data.timedOut,
                durationMs: data.durationMs,
                output: asText(data.stdout),
                stderr: asText(data.stderr),
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                agentVersion: versionCheck.remoteVersion,
                error: data.ok ? "" : asText(data.error || data.stderr || "Command failed")
            };
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function windows_test_connection(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params && params.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const commandData = await postCommand(config, { preset: "whoami" }, timeoutMs);
            return {
                success: !!commandData.ok,
                agentBaseUrl: config.baseUrl,
                shell: "cmd",
                command: "preset:whoami",
                exitCode: commandData.exitCode,
                timedOut: !!commandData.timedOut,
                durationMs: commandData.durationMs,
                output: asText(commandData.stdout),
                stderr: asText(commandData.stderr),
                health: versionCheck.health,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                agentVersion: versionCheck.remoteVersion,
                error: commandData.ok ? "" : asText(commandData.error || commandData.stderr || "Command channel failed")
            };
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function read(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params && params.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const path = asText(params && params.path).trim();
            if (!path) {
                throw new Error("Missing required parameter: path");
            }
            const encoding = asText(params && params.encoding).trim();
            const offset = parseOptionalNonNegativeInt(params && params.offset, "offset");
            const length = parseOptionalNonNegativeInt(params && params.length, "length");
            const useSegment = offset !== undefined || length !== undefined;
            const data = useSegment
                ? await postTextFileApi(config, "/api/file/read_segment", {
                    path,
                    encoding: encoding || undefined,
                    offset: offset === undefined ? 0 : offset,
                    length: length === undefined ? undefined : length
                }, timeoutMs)
                : await postTextFileApi(config, "/api/file/read", {
                    path,
                    encoding: encoding || undefined
                }, timeoutMs);
            const result = {
                success: true,
                agentBaseUrl: config.baseUrl,
                path: asText(data.path),
                encoding: asText(data.encoding),
                content: asText(data.content),
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                agentVersion: versionCheck.remoteVersion
            };
            if (data.offset !== undefined) {
                result.offset = Number(data.offset);
            }
            if (data.length !== undefined) {
                result.length = Number(data.length);
            }
            if (data.totalBytes !== undefined) {
                result.totalBytes = Number(data.totalBytes);
            }
            if (data.eof !== undefined) {
                result.eof = !!data.eof;
            }
            return result;
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function write(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params && params.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
            const path = asText(params && params.path).trim();
            if (!path) {
                throw new Error("Missing required parameter: path");
            }
            if (!params || params.content === undefined || params.content === null) {
                throw new Error("Missing required parameter: content");
            }
            const content = asText(params.content);
            const encoding = asText(params && params.encoding).trim();
            const data = await postTextFileApi(config, "/api/file/write", {
                path,
                content,
                encoding: encoding || undefined
            }, timeoutMs);
            return {
                success: true,
                agentBaseUrl: config.baseUrl,
                path: asText(data.path),
                encoding: asText(data.encoding),
                sizeBytes: Number(data.sizeBytes),
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                agentVersion: versionCheck.remoteVersion
            };
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    async function edit(params) {
        try {
            const config = resolveAgentConfig();
            const timeoutMs = parseTimeout(params && params.timeout_ms, config.timeoutMs);
            const versionCheck = await ensureVersionCompatible(config, timeoutMs);
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
            const newText = asText(params.new_text);
            const expectedReplacements = parseExpectedReplacements(params && params.expected_replacements);
            const encoding = asText(params && params.encoding).trim();
            const data = await postTextFileApi(config, "/api/file/edit", {
                path,
                old_text: oldText,
                new_text: newText,
                expected_replacements: expectedReplacements,
                encoding: encoding || undefined
            }, timeoutMs);
            return {
                success: true,
                agentBaseUrl: config.baseUrl,
                path: asText(data.path),
                encoding: asText(data.encoding),
                sizeBytes: Number(data.sizeBytes),
                replacements: Number(data.replacements),
                expectedReplacements: Number(data.expectedReplacements),
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                agentVersion: versionCheck.remoteVersion
            };
        }
        catch (error) {
            return {
                success: false,
                packageVersion: WINDOWS_CONTROL_PACKAGE_VERSION,
                error: error && error.message ? error.message : String(error)
            };
        }
    }
    return {
        windows_exec,
        windows_test_connection,
        windows_check_connection: windows_test_connection,
        read,
        edit,
        write
    };
})();
exports.windows_exec = windowsControl.windows_exec;
exports.windows_test_connection = windowsControl.windows_test_connection;
exports.windows_check_connection = windowsControl.windows_check_connection;
exports.read = windowsControl.read;
exports.edit = windowsControl.edit;
exports.write = windowsControl.write;

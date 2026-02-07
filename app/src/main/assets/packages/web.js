/* METADATA
{
    "name": "web",
    "description": {
        "zh": "Playwright 风格的网页会话封装。提供 start/goto/click/fill/evaluate/snapshot/close 等接口，底层基于 start_web 与 web_* 工具。",
        "en": "Playwright-style web session wrapper. Exposes start/goto/click/fill/evaluate/snapshot/close using start_web and web_* tools."
    },
    "enabledByDefault": true,
    "tools": [
        {
            "name": "start",
            "description": { "zh": "启动网页会话并打开悬浮浏览窗口。", "en": "Start a web session and open a floating browser window." },
            "parameters": [
                { "name": "url", "description": { "zh": "可选，初始 URL", "en": "Optional initial URL." }, "type": "string", "required": false },
                { "name": "headers", "description": { "zh": "可选，请求头对象", "en": "Optional request headers object." }, "type": "object", "required": false },
                { "name": "user_agent", "description": { "zh": "可选，自定义 User-Agent", "en": "Optional custom User-Agent." }, "type": "string", "required": false },
                { "name": "session_name", "description": { "zh": "可选，会话名称", "en": "Optional session name." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "goto",
            "description": { "zh": "让会话跳转到指定 URL。", "en": "Navigate session to target URL." },
            "parameters": [
                { "name": "url", "description": { "zh": "目标 URL", "en": "Target URL." }, "type": "string", "required": true },
                { "name": "session_id", "description": { "zh": "可选，不传则使用 Kotlin 侧当前活动会话", "en": "Optional. Uses active Kotlin-side session when omitted." }, "type": "string", "required": false },
                { "name": "headers", "description": { "zh": "可选，请求头对象", "en": "Optional request headers object." }, "type": "object", "required": false }
            ]
        },
        {
            "name": "click",
            "description": { "zh": "按 CSS 选择器点击元素。", "en": "Click element by CSS selector." },
            "parameters": [
                { "name": "selector", "description": { "zh": "CSS 选择器", "en": "CSS selector." }, "type": "string", "required": true },
                { "name": "session_id", "description": { "zh": "可选，不传则使用 Kotlin 侧当前活动会话", "en": "Optional. Uses active Kotlin-side session when omitted." }, "type": "string", "required": false },
                { "name": "index", "description": { "zh": "可选，匹配元素中的索引", "en": "Optional index among matched elements." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "fill",
            "description": { "zh": "按 CSS 选择器填写输入框。", "en": "Fill input by CSS selector." },
            "parameters": [
                { "name": "selector", "description": { "zh": "CSS 选择器", "en": "CSS selector." }, "type": "string", "required": true },
                { "name": "value", "description": { "zh": "写入值", "en": "Value to set." }, "type": "string", "required": true },
                { "name": "session_id", "description": { "zh": "可选，不传则使用 Kotlin 侧当前活动会话", "en": "Optional. Uses active Kotlin-side session when omitted." }, "type": "string", "required": false }
            ]
        },
        {
            "name": "evaluate",
            "description": { "zh": "在网页中执行 JavaScript。", "en": "Evaluate JavaScript in current page." },
            "parameters": [
                { "name": "script", "description": { "zh": "JavaScript 脚本", "en": "JavaScript source." }, "type": "string", "required": true },
                { "name": "session_id", "description": { "zh": "可选，不传则使用 Kotlin 侧当前活动会话", "en": "Optional. Uses active Kotlin-side session when omitted." }, "type": "string", "required": false },
                { "name": "timeout_ms", "description": { "zh": "可选，执行超时", "en": "Optional execution timeout." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "wait_for",
            "description": { "zh": "等待页面就绪或元素出现。", "en": "Wait for page ready or selector appearance." },
            "parameters": [
                { "name": "selector", "description": { "zh": "可选，CSS 选择器", "en": "Optional CSS selector." }, "type": "string", "required": false },
                { "name": "session_id", "description": { "zh": "可选，不传则使用 Kotlin 侧当前活动会话", "en": "Optional. Uses active Kotlin-side session when omitted." }, "type": "string", "required": false },
                { "name": "timeout_ms", "description": { "zh": "可选，等待超时", "en": "Optional wait timeout." }, "type": "number", "required": false }
            ]
        },
        {
            "name": "snapshot",
            "description": { "zh": "抓取当前网页文本快照。", "en": "Capture current page text snapshot." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "可选，不传则使用 Kotlin 侧当前活动会话", "en": "Optional. Uses active Kotlin-side session when omitted." }, "type": "string", "required": false },
                { "name": "include_links", "description": { "zh": "可选，是否包含链接", "en": "Optional include links." }, "type": "boolean", "required": false },
                { "name": "include_images", "description": { "zh": "可选，是否包含图片", "en": "Optional include images." }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "content",
            "description": { "zh": "获取页面主要内容；当内容过长时自动写入文件并返回路径。", "en": "Get main page content; auto-save to file and return path when too long." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "可选，不传则使用 Kotlin 侧当前活动会话", "en": "Optional. Uses active Kotlin-side session when omitted." }, "type": "string", "required": false },
                { "name": "include_links", "description": { "zh": "可选，是否包含链接", "en": "Optional include links." }, "type": "boolean", "required": false },
                { "name": "include_images", "description": { "zh": "可选，是否包含图片", "en": "Optional include images." }, "type": "boolean", "required": false }
            ]
        },
        {
            "name": "close",
            "description": { "zh": "关闭网页会话。", "en": "Close web session." },
            "parameters": [
                { "name": "session_id", "description": { "zh": "可选，不传则关闭 Kotlin 侧当前活动会话", "en": "Optional. Closes active Kotlin-side session when omitted." }, "type": "string", "required": false },
                { "name": "close_all", "description": { "zh": "可选，是否关闭全部会话", "en": "Optional, close all sessions." }, "type": "boolean", "required": false }
            ]
        }
    ]
}
*/
const MAX_INLINE_WEB_CONTENT_CHARS = 12000;
const Web = (function () {
    function toPayload(raw) {
        if (raw == null) {
            return {};
        }
        if (typeof raw === 'string') {
            try {
                return JSON.parse(raw);
            }
            catch {
                return { value: raw };
            }
        }
        if (typeof raw === 'object' && typeof raw.value === 'string') {
            try {
                return JSON.parse(raw.value);
            }
            catch {
                return { ...raw, value: raw.value };
            }
        }
        if (typeof raw === 'object') {
            return raw;
        }
        return { value: String(raw) };
    }
    function normalizeHeaders(headers) {
        if (!headers || typeof headers !== 'object') {
            return undefined;
        }
        const result = {};
        for (const [key, value] of Object.entries(headers)) {
            if (value === undefined || value === null) {
                continue;
            }
            result[String(key)] = String(value);
        }
        return result;
    }
    function optionalSessionId(raw) {
        if (raw === undefined || raw === null) {
            return undefined;
        }
        const sid = String(raw).trim();
        return sid.length > 0 ? sid : undefined;
    }
    function extractPageContent(payload) {
        const candidates = [payload === null || payload === void 0 ? void 0 : payload.snapshot, payload === null || payload === void 0 ? void 0 : payload.content, payload === null || payload === void 0 ? void 0 : payload.text, payload === null || payload === void 0 ? void 0 : payload.value];
        for (const item of candidates) {
            if (typeof item === 'string' && item.length > 0) {
                return item;
            }
        }
        return '';
    }
    function sanitizeSessionId(sessionId) {
        if (!sessionId) {
            return 'default';
        }
        return sessionId.replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 40) || 'default';
    }
    async function persistPageContentIfTooLong(payload, sessionId) {
        const content = extractPageContent(payload);
        if (!content || content.length <= MAX_INLINE_WEB_CONTENT_CHARS) {
            return payload;
        }
        await Tools.Files.mkdir(OPERIT_CLEAN_ON_EXIT_DIR, true);
        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const rand = Math.floor(Math.random() * 1000000);
        const safeSessionId = sanitizeSessionId(sessionId);
        const filePath = `${OPERIT_CLEAN_ON_EXIT_DIR}/web_content_${safeSessionId}_${timestamp}_${rand}.txt`;
        await Tools.Files.write(filePath, content, false);
        return {
            ...payload,
            snapshot: '(saved_to_file)',
            snapshot_chars: content.length,
            snapshot_saved_to: filePath,
            operit_clean_on_exit_dir: OPERIT_CLEAN_ON_EXIT_DIR,
            hint: 'Content is large and saved to file. Use read_file_part or grep_code to inspect it.',
        };
    }
    async function start(params = {}) {
        return toPayload(await Tools.Net.startWeb({
            url: params.url,
            headers: normalizeHeaders(params.headers),
            user_agent: params.user_agent,
            session_name: params.session_name,
        }));
    }
    async function goto(params) {
        if (!params || !params.url) {
            throw new Error('url 参数必填');
        }
        return toPayload(await Tools.Net.webNavigate(optionalSessionId(params.session_id), String(params.url), normalizeHeaders(params.headers)));
    }
    async function click(params) {
        if (!params || !params.selector) {
            throw new Error('selector 参数必填');
        }
        return toPayload(await Tools.Net.webClick(optionalSessionId(params.session_id), String(params.selector), params.index !== undefined ? Number(params.index) : undefined));
    }
    async function fill(params) {
        if (!params || !params.selector) {
            throw new Error('selector 参数必填');
        }
        if (params.value === undefined || params.value === null) {
            throw new Error('value 参数必填');
        }
        return toPayload(await Tools.Net.webFill(optionalSessionId(params.session_id), String(params.selector), String(params.value)));
    }
    async function evaluate(params) {
        if (!params || !params.script) {
            throw new Error('script 参数必填');
        }
        return toPayload(await Tools.Net.webEval(optionalSessionId(params.session_id), String(params.script), params.timeout_ms !== undefined ? Number(params.timeout_ms) : undefined));
    }
    async function wait_for(params = {}) {
        return toPayload(await Tools.Net.webWaitFor(optionalSessionId(params.session_id), params.selector !== undefined ? String(params.selector) : undefined, params.timeout_ms !== undefined ? Number(params.timeout_ms) : undefined));
    }
    async function snapshot(params = {}) {
        const sessionId = optionalSessionId(params.session_id);
        const payload = toPayload(await Tools.Net.webSnapshot(sessionId, {
            include_links: params.include_links !== undefined ? Boolean(params.include_links) : undefined,
            include_images: params.include_images !== undefined ? Boolean(params.include_images) : undefined,
        }));
        return persistPageContentIfTooLong(payload, sessionId);
    }
    async function content(params = {}) {
        return snapshot(params);
    }
    async function close(params = {}) {
        const closeAll = Boolean(params.close_all);
        if (closeAll) {
            return toPayload(await Tools.Net.stopWeb({ close_all: true }));
        }
        const sid = optionalSessionId(params.session_id);
        if (sid) {
            return toPayload(await Tools.Net.stopWeb({ session_id: sid, close_all: false }));
        }
        return toPayload(await Tools.Net.stopWeb({ close_all: false }));
    }
    async function wrap(toolName, fn, params) {
        try {
            const data = await fn(params || {});
            const result = {
                success: true,
                message: `${toolName} 执行成功`,
                data,
            };
            complete(result);
        }
        catch (error) {
            const result = {
                success: false,
                message: `${toolName} 执行失败: ${(error === null || error === void 0 ? void 0 : error.message) || String(error)}`,
                error: String((error === null || error === void 0 ? void 0 : error.stack) || error),
            };
            complete(result);
        }
    }
    async function main() {
        complete({
            success: true,
            message: 'Web 已就绪，可调用 start/goto/click/fill/evaluate/wait_for/snapshot/content/close',
        });
    }
    return {
        start: (params) => wrap('start', start, params),
        goto: (params) => wrap('goto', goto, params),
        click: (params) => wrap('click', click, params),
        fill: (params) => wrap('fill', fill, params),
        evaluate: (params) => wrap('evaluate', evaluate, params),
        wait_for: (params) => wrap('wait_for', wait_for, params),
        snapshot: (params) => wrap('snapshot', snapshot, params),
        content: (params) => wrap('content', content, params),
        close: (params) => wrap('close', close, params),
        main,
    };
})();
exports.start = Web.start;
exports.goto = Web.goto;
exports.click = Web.click;
exports.fill = Web.fill;
exports.evaluate = Web.evaluate;
exports.wait_for = Web.wait_for;
exports.snapshot = Web.snapshot;
exports.content = Web.content;
exports.close = Web.close;
exports.main = Web.main;

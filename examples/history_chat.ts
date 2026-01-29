/* METADATA
{
    "name": "history_chat",
    "description": {
        "zh": "对话历史工具包：在当前话题中读取 OperitAI 内其它话题的消息（跨话题）。",
        "en": "Chat history tools: read messages from other OperitAI chats (cross-chat)."
    },
    "enabledByDefault": true,
    "tools": [
        {
            "name": "list_chats",
            "description": {
                "zh": "列出并筛选对话（用于获取 chat_id）。",
                "en": "List and filter chats (to discover chat_id)."
            },
            "parameters": [
                { "name": "query", "description": { "zh": "可选：标题筛选关键字", "en": "Optional title keyword" }, "type": "string", "required": false },
                { "name": "match", "description": { "zh": "可选：contains/exact/regex（默认 contains）", "en": "Optional: contains/exact/regex (default contains)" }, "type": "string", "required": false },
                { "name": "limit", "description": { "zh": "可选：最多返回条数（默认 50）", "en": "Optional max results (default 50)" }, "type": "number", "required": false },
                { "name": "sort_by", "description": { "zh": "可选：updatedAt/createdAt/messageCount（默认 updatedAt）", "en": "Optional: updatedAt/createdAt/messageCount (default updatedAt)" }, "type": "string", "required": false },
                { "name": "sort_order", "description": { "zh": "可选：asc/desc（默认 desc）", "en": "Optional: asc/desc (default desc)" }, "type": "string", "required": false }
            ]
        },
        {
            "name": "find_chat",
            "description": {
                "zh": "按标题查找一个对话并返回 chat_id。",
                "en": "Find a single chat by title and return chat_id."
            },
            "parameters": [
                { "name": "query", "description": { "zh": "标题关键字/正则", "en": "Title keyword/regex" }, "type": "string", "required": true },
                { "name": "match", "description": { "zh": "可选：contains/exact/regex（默认 contains）", "en": "Optional: contains/exact/regex (default contains)" }, "type": "string", "required": false },
                { "name": "index", "description": { "zh": "可选：当匹配多个时选择第 N 个（默认 0）", "en": "Optional: pick Nth when multiple matches (default 0)" }, "type": "number", "required": false }
            ]
        },
        {
            "name": "read_messages",
            "description": {
                "zh": "读取指定对话的消息（可按 chat_id 或 chat_title 指定）。",
                "en": "Read messages from a chat (by chat_id or chat_title)."
            },
            "parameters": [
                { "name": "chat_id", "description": { "zh": "目标对话 ID（可选）", "en": "Target chat id (optional)" }, "type": "string", "required": false },
                { "name": "chat_title", "description": { "zh": "目标对话标题（可选；当 chat_id 为空时使用）", "en": "Target chat title (optional; used when chat_id is empty)" }, "type": "string", "required": false },
                { "name": "chat_query", "description": { "zh": "可选：标题筛选关键字（当 chat_id/chat_title 为空时使用）", "en": "Optional title keyword (used when chat_id/chat_title is empty)" }, "type": "string", "required": false },
                { "name": "chat_index", "description": { "zh": "可选：当筛选结果有多个时选择第 N 个（默认 0）", "en": "Optional: pick Nth when multiple matches (default 0)" }, "type": "number", "required": false },
                { "name": "match", "description": { "zh": "可选：contains/exact/regex（默认 contains）", "en": "Optional: contains/exact/regex (default contains)" }, "type": "string", "required": false },
                { "name": "order", "description": { "zh": "可选：asc/desc（默认 desc）", "en": "Optional: asc/desc (default desc)" }, "type": "string", "required": false },
                { "name": "limit", "description": { "zh": "可选：返回消息条数（默认 20）", "en": "Optional: max number of messages (default 20)" }, "type": "number", "required": false }
            ]
        }
    ]
}
*/

const HistoryChat = (function () {

    interface ToolResponse {
        success: boolean;
        message: string;
        data?: any;
    }

    type ResolveChatIdParams = {
        chat_id?: string;
        chat_title?: string;
        chat_query?: string;
        chat_index?: number;
        match?: string;
    };

    type ReadMessagesParams = ResolveChatIdParams & {
        order?: string;
        limit?: number;
    };

    type ListChatsParams = {
        query?: string;
        match?: string;
        limit?: number;
        sort_by?: string;
        sort_order?: string;
    };

    type FindChatParams = {
        query: string;
        match?: string;
        index?: number;
    };

    type ChatInfo = {
        id: string;
        title: string;
        messageCount?: number;
        createdAt?: string;
        updatedAt?: string;
        isCurrent?: boolean;
    };

    function normalizeMatchMode(match?: string): 'contains' | 'exact' | 'regex' {
        const m = (match || '').trim().toLowerCase();
        if (m === 'exact' || m === 'regex' || m === 'contains') return m;
        return 'contains';
    }

    function isMatchTitle(title: string, query: string, matchMode: 'contains' | 'exact' | 'regex'): boolean {
        const t = (title || '').trim();
        const q = (query || '').trim();
        if (!q) return true;

        if (matchMode === 'exact') {
            return t === q;
        }
        if (matchMode === 'regex') {
            try {
                const re = new RegExp(q);
                return re.test(t);
            } catch {
                return false;
            }
        }
        return t.includes(q);
    }

    function toSortableNumber(value: any): number {
        if (typeof value === 'number') return value;
        if (typeof value === 'string') {
            const ts = Date.parse(value);
            if (!isNaN(ts)) return ts;
            const n = Number(value);
            if (!isNaN(n)) return n;
        }
        return 0;
    }

    async function fetchAllChats(): Promise<{ totalCount?: number; currentChatId?: string | null; chats: ChatInfo[] }> {
        const listResult: any = await toolCall('list_chats', {});
        const chats = (listResult && listResult.chats) ? listResult.chats : [];
        return {
            totalCount: listResult?.totalCount,
            currentChatId: listResult?.currentChatId ?? null,
            chats,
        };
    }

    async function list_chats_impl(params: ListChatsParams): Promise<ToolResponse> {
        const query = (params?.query ?? '').toString().trim();
        const matchMode = normalizeMatchMode(params?.match);

        const limitRaw = params && params.limit !== undefined ? Number(params.limit) : 50;
        const limit = (isNaN(limitRaw) ? 50 : limitRaw);
        const cappedLimit = Math.max(1, Math.min(200, limit));

        const sortByRaw = (params?.sort_by ?? 'updatedAt').toString().trim();
        const sortBy = (sortByRaw === 'createdAt' || sortByRaw === 'messageCount' || sortByRaw === 'updatedAt') ? sortByRaw : 'updatedAt';

        const sortOrderRaw = (params?.sort_order ?? 'desc').toString().trim().toLowerCase();
        const sortOrder = (sortOrderRaw === 'asc' || sortOrderRaw === 'desc') ? sortOrderRaw : 'desc';

        const { totalCount, currentChatId, chats } = await fetchAllChats();

        const matched = chats
            .filter((c: any) => isMatchTitle(String(c?.title ?? ''), query, matchMode))
            .slice();

        matched.sort((a: any, b: any) => {
            const av = toSortableNumber(a?.[sortBy]);
            const bv = toSortableNumber(b?.[sortBy]);
            return sortOrder === 'asc' ? (av - bv) : (bv - av);
        });

        const resultChats = matched.slice(0, cappedLimit);

        return {
            success: true,
            message: '对话列表获取完成',
            data: {
                totalCount: totalCount ?? chats.length,
                currentChatId: currentChatId ?? null,
                matchedCount: matched.length,
                chats: resultChats,
            }
        };
    }

    async function find_chat_impl(params: FindChatParams): Promise<ToolResponse> {
        const query = (params?.query ?? '').toString().trim();
        if (!query) {
            throw new Error('Missing parameter: query');
        }

        const matchMode = normalizeMatchMode(params?.match);
        const indexRaw = params && params.index !== undefined ? Number(params.index) : 0;
        const index = isNaN(indexRaw) ? 0 : indexRaw;

        const { chats } = await fetchAllChats();
        const matched = chats.filter((c: any) => isMatchTitle(String(c?.title ?? ''), query, matchMode));

        if (matched.length === 0) {
            throw new Error(`Chat not found by query: ${query}`);
        }

        const picked = matched[index];
        if (!picked) {
            throw new Error(`Chat index out of range: index=${index}, matched=${matched.length}`);
        }

        return {
            success: true,
            message: '对话查找完成',
            data: {
                chat: picked,
                matchedCount: matched.length,
            }
        };
    }

    async function resolveChatId(params: ResolveChatIdParams): Promise<string> {
        if (params && typeof params.chat_id === 'string' && params.chat_id.trim()) {
            return params.chat_id.trim();
        }

        const title = params && typeof params.chat_title === 'string' ? params.chat_title.trim() : '';
        const query = params && typeof params.chat_query === 'string' ? params.chat_query.trim() : '';
        const matchMode = normalizeMatchMode(params?.match);
        const indexRaw = params && params.chat_index !== undefined ? Number(params.chat_index) : 0;
        const index = isNaN(indexRaw) ? 0 : indexRaw;

        if (!title && !query) {
            throw new Error('Missing parameter: chat_id or chat_title or chat_query is required');
        }

        let chats: any[] = [];
        try {
            chats = (await fetchAllChats()).chats;
        } catch (e) {
            throw new Error('Unable to resolve chat selector because list_chats is unavailable.');
        }

        const needle = title || query;
        const matched = chats.filter((c: any) => isMatchTitle(String(c?.title ?? ''), needle, title ? 'exact' : matchMode));

        if (matched.length === 1) {
            return matched[0].id;
        }

        if (matched.length === 0) {
            throw new Error(`Chat not found by query: ${needle}`);
        }

        const picked = matched[index];
        if (picked) {
            return picked.id;
        }

        const preview = matched.slice(0, 5).map((c: any) => `${c.id} | ${c.title}`).join('\n');
        throw new Error(`Multiple chats matched query: ${needle}, please specify chat_index.\n${preview}`);
    }

    async function read_messages_impl(params: ReadMessagesParams): Promise<ToolResponse> {
        const chatId = await resolveChatId(params || {});

        const orderRaw = params && params.order !== undefined ? String(params.order).trim().toLowerCase() : '';
        const order = (orderRaw === 'asc' || orderRaw === 'desc') ? orderRaw : 'desc';

        const limitRaw = params && params.limit !== undefined ? Number(params.limit) : 20;
        const limit = isNaN(limitRaw) ? 20 : limitRaw;

        const result: any = await toolCall('get_chat_messages', {
            chat_id: chatId,
            order,
            limit,
        });

        const rawMessages: any[] = Array.isArray(result?.messages) ? result.messages : [];
        const text = rawMessages
            .map((m: any) => {
                const role = (m?.roleName ?? m?.sender ?? '').toString() || 'message';
                const ts = (m?.timestamp !== undefined && m?.timestamp !== null) ? String(m.timestamp) : '';
                const header = ts ? `[${ts}] ${role}` : role;
                return `${header}:\n${(m?.content ?? '').toString()}`;
            })
            .join('\n\n');

        return {
            success: true,
            message: '读取对话消息完成',
            data: {
                result,
                text,
            },
        };
    }

    async function wrapToolExecution<P>(func: (params: P) => Promise<ToolResponse>, params: P): Promise<void> {
        try {
            const result = await func(params);
            complete(result);
        } catch (error: any) {
            const message = (error && error.message) ? String(error.message) : String(error);
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `读取对话消息失败: ${message}`,
            });
        }
    }

    async function read_messages(params: ReadMessagesParams): Promise<void> {
        return await wrapToolExecution(read_messages_impl, params);
    }

    async function list_chats(params: ListChatsParams): Promise<void> {
        return await wrapToolExecution(list_chats_impl, params);
    }

    async function find_chat(params: FindChatParams): Promise<void> {
        return await wrapToolExecution(find_chat_impl, params);
    }

    async function main(): Promise<void> {
        complete({
            success: true,
            message: 'history_chat 工具包已加载',
            data: {
                hint: 'Use history_chat:read_messages with chat_id/chat_title.',
            },
        });
    }

    return {
        list_chats,
        find_chat,
        read_messages,
        main,
    };
})();

exports.list_chats = HistoryChat.list_chats;
exports.find_chat = HistoryChat.find_chat;
exports.read_messages = HistoryChat.read_messages;
exports.main = HistoryChat.main;

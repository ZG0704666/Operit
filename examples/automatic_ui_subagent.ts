/* METADATA
{
    name: "Automatic_ui_subagent"
    description: '''
兼容AutoGLM，提供基于独立UI控制器模型（例如 autoglm-phone-9b）的高层UI自动化子代理工具，用于根据自然语言意图自动规划并执行点击/输入/滑动等一系列界面操作。
当用户提出需要帮忙完成某个界面操作任务（例如打开应用、搜索内容、在多个页面之间完成一套步骤）时，可以调用本包由子代理自动规划和执行具体步骤。
'''

    tools: [
        {
            name: "usage_advice"
            description: '''
UI子代理使用建议：

- 会话复用（重要）：该子代理支持通过 agent_id 复用同一个虚拟屏幕会话。若你希望多次调用持续在同一虚拟屏幕/同一应用界面上操作，请在后续调用时尽量传入同一个 agent_id（可沿用上一次返回的 data.agentId 作为 agent_id）。
- 对话无状态（重要）：每次调用 run_subagent 对子代理来说都是一次全新的对话，不会自动记住上一次做了什么。因此主Agent必须把“已完成/下一步/关键信息”写进 intent，推荐格式：
  当前任务已经完成: ...
  你需要在此基础上进一步完成: ...
  可能用到的信息: ...
- 失败/半成功处理（重要）：当子代理返回失败、或“成功一半/基本成功/以为完成但实际未完成”时，主Agent应基于返回结果与当前界面现状，继续调用子代理要求其纠错并完成最终目标；只有在连续多次失败（例如 2-3 次）且无法推进时，才停止并汇报失败原因与可选替代方案。
- 完成判定（重要）：只有当最终目标动作确实完成（例如“已在正确视频的评论区成功发布评论”）才算任务完成；出现“进错视频/只找到评论区就停止”等情况，不属于完成，必须继续调用并明确要求其回退、定位正确目标、再完成动作。
- 并行子代理：可以并行运行 1-4 个子代理以加速，但并行时建议每个子代理使用不同的 agent_id，避免在同一虚拟屏幕上互相干扰。
- 任务拆分：当任务包含多个逻辑上独立的子目标时，建议将它们拆分成多个子任务，依次或并行调用。
- 单次流程：对于目标单一、流程连续的任务，应尽量在一次调用中给出完整意图，由子代理自动规划中间步骤。
'''
            parameters: []
        }

        {
            name: "run_subagent"
            description: '''
运行内置UI子代理（使用独立UI控制器模型）根据高层意图自动规划并执行一系列UI操作，例如自动点击、滑动、输入等。
'''
            parameters: [
                {
                    name: "intent"
                    description: "任务意图描述，例如：'打开微信并发送一条消息' 或 '在B站搜索某个视频'"
                    type: "string"
                    required: true
                }
                {
                    name: "max_steps"
                    description: "最大执行步数，默认20，可根据任务复杂度调整。"
                    type: "number"
                    required: false
                }
                {
                    name: "agent_id"
                    description: "可选：用于复用虚拟屏幕会话的 agentId。建议在多次调用时尽量传入同一个 agent_id（可沿用上一次返回的 data.agentId），否则会新建会话导致上下文/虚拟屏切换。"
                    type: "string"
                    required: false
                }
            ]
        }

        {
            name: "run_subagent_parallel"
            description: '''
并行运行 1-4 个 UI 子代理。

注意：并行调用时，每个子代理对它自身都是全新对话，因此 intent_1..4 需要由主Agent分别写清楚“已完成/下一步/关键信息”。
建议并行时每个子代理使用不同的 agent_id（或留空让系统自动创建），避免操作同一虚拟屏幕造成冲突。
如果并行任务中仅有部分子代理失败，则只对失败的子代理继续发起后续调用（补充纠错信息、提高约束），不要让已成功的子代理重复执行。
'''
            parameters: [
                {
                    name: "intent_1"
                    description: "第1个子代理意图（推荐使用：当前任务已经完成/你需要进一步完成/可能用到的信息 三段式）"
                    type: "string"
                    required: true
                }
                {
                    name: "max_steps_1"
                    description: "第1个子代理最大步数（默认20）"
                    type: "number"
                    required: false
                }
                {
                    name: "agent_id_1"
                    description: "第1个子代理 agent_id（可选，用于会话复用；并行建议不同）"
                    type: "string"
                    required: false
                }

                {
                    name: "intent_2"
                    description: "第2个子代理意图（可选）"
                    type: "string"
                    required: false
                }
                {
                    name: "max_steps_2"
                    description: "第2个子代理最大步数（默认20）"
                    type: "number"
                    required: false
                }
                {
                    name: "agent_id_2"
                    description: "第2个子代理 agent_id（可选）"
                    type: "string"
                    required: false
                }

                {
                    name: "intent_3"
                    description: "第3个子代理意图（可选）"
                    type: "string"
                    required: false
                }
                {
                    name: "max_steps_3"
                    description: "第3个子代理最大步数（默认20）"
                    type: "number"
                    required: false
                }
                {
                    name: "agent_id_3"
                    description: "第3个子代理 agent_id（可选）"
                    type: "string"
                    required: false
                }

                {
                    name: "intent_4"
                    description: "第4个子代理意图（可选）"
                    type: "string"
                    required: false
                }
                {
                    name: "max_steps_4"
                    description: "第4个子代理最大步数（默认20）"
                    type: "number"
                    required: false
                }
                {
                    name: "agent_id_4"
                    description: "第4个子代理 agent_id（可选）"
                    type: "string"
                    required: false
                }
            ]
        }
    ]
}
*/

const UIAutomationSubAgentTools = (function () {

    let cachedAgentId: string | undefined;

    interface ToolResponse {
        success: boolean;
        message: string;
        data?: any;
    }

    async function usage_advice(_params: {}): Promise<ToolResponse> {
        return {
            success: true,
            message: 'UI子代理使用建议',
            data: {
                advice:
                    "会话复用：多次调用尽量传入同一个 agent_id（可沿用上一次返回的 data.agentId）。\n" +
                    "对话无状态：每次调用对子代理都是全新对话，intent 需写清：当前任务已经完成/你需要进一步完成/可能用到的信息。\n" +
                    "失败/半成功处理：若失败或未真正完成（例如进错视频、只找到评论区就停），应基于当前界面与结果继续调用让其纠错并完成最终目标；仅在连续多次失败（例如 2-3 次）无法推进时才停止。\n" +
                    "并行调用：并行运行 1-4 个子代理时建议使用不同的 agent_id；若只有部分失败，仅继续调用失败的子代理，不要让已成功的重复执行。",
            },
        };
    }

    async function run_subagent(params: { intent: string, max_steps?: number, agent_id?: string }): Promise<ToolResponse> {
        const { intent, max_steps, agent_id } = params;
        const agentIdToUse = (agent_id && String(agent_id).length > 0) ? String(agent_id) : cachedAgentId;
        const result = await Tools.UI.runSubAgent(intent, max_steps, agentIdToUse);
        if (result && (result).agentId) {
            cachedAgentId = String((result).agentId);
        }
        return {
            success: true,
            message: 'UI子代理执行完成',
            data: result,
        };
    }

    async function run_subagent_parallel(params: {
        intent_1: string, max_steps_1?: number, agent_id_1?: string,
        intent_2?: string, max_steps_2?: number, agent_id_2?: string,
        intent_3?: string, max_steps_3?: number, agent_id_3?: string,
        intent_4?: string, max_steps_4?: number, agent_id_4?: string,
    }): Promise<ToolResponse> {
        const slots = [1, 2, 3, 4] as const;

        const tasks = slots
            .map((i) => {
                const intent = (params as any)[`intent_${i}`];
                if (!intent || String(intent).trim().length === 0) return null;

                const maxSteps = (params as any)[`max_steps_${i}`];
                const agentId = (params as any)[`agent_id_${i}`];

                return (async () => {
                    try {
                        const result = await Tools.UI.runSubAgent(
                            String(intent),
                            maxSteps === undefined ? undefined : Number(maxSteps),
                            agentId === undefined || agentId === null || String(agentId).length === 0 ? undefined : String(agentId)
                        );
                        return { index: i, success: true, result };
                    } catch (e: any) {
                        return { index: i, success: false, error: e?.message || String(e) };
                    }
                })();
            })
            .filter(Boolean) as Array<Promise<any>>;

        const results = await Promise.all(tasks);
        const okCount = results.filter((r) => r.success).length;
        return {
            success: true,
            message: `并行UI子代理执行完成：成功 ${okCount} 个 / 共 ${results.length} 个`,
            data: {
                results,
            },
        };
    }

    async function wrapToolExecution<P>(func: (params: P) => Promise<ToolResponse>, params: P) {
        try {
            const result = await func(params);
            complete(result);
        } catch (error: any) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }

    return {
        usage_advice: (params: {}) => wrapToolExecution(usage_advice, params),
        run_subagent: (params: { intent: string, max_steps?: number, agent_id?: string }) => wrapToolExecution(run_subagent, params),
        run_subagent_parallel: (params: {
            intent_1: string, max_steps_1?: number, agent_id_1?: string,
            intent_2?: string, max_steps_2?: number, agent_id_2?: string,
            intent_3?: string, max_steps_3?: number, agent_id_3?: string,
            intent_4?: string, max_steps_4?: number, agent_id_4?: string,
        }) => wrapToolExecution(run_subagent_parallel, params),
    };
})();

exports.usage_advice = UIAutomationSubAgentTools.usage_advice;
exports.run_subagent = UIAutomationSubAgentTools.run_subagent;
exports.run_subagent_parallel = UIAutomationSubAgentTools.run_subagent_parallel;

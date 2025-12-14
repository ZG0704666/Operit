/* METADATA
{
    "name": "Automatic_ui_subagent",
    "description": "兼容AutoGLM，提供基于独立UI控制器模型（例如 autoglm-phone-9b）的高层UI自动化子代理工具，用于根据自然语言意图自动规划并执行点击/输入/滑动等一系列界面操作。当用户提出需要帮忙完成某个界面操作任务（例如打开应用、搜索内容、在多个页面之间完成一套步骤）时，可以调用本包由子代理自动规划和执行具体步骤。",
    "tools": [
        {
            "name": "run_subagent",
            "description": "运行内置UI子代理（使用独立UI控制器模型）根据高层意图自动规划并执行一系列UI操作，例如自动点击、滑动、输入等。",
            "parameters": [
                { "name": "intent", "description": "任务意图描述，例如：'打开微信并发送一条消息' 或 '在B站搜索某个视频'", "type": "string", "required": true },
                { "name": "max_steps", "description": "最大执行步数，默认20，可根据任务复杂度调整。", "type": "number", "required": false }
            ]
        }
    ]
}
*/
const UIAutomationSubAgentTools = (function () {
    async function run_subagent(params) {
        const { intent, max_steps } = params;
        const result = await Tools.UI.runSubAgent(intent, max_steps);
        return {
            success: true,
            message: 'UI子代理执行完成',
            data: result,
        };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params);
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }
    return {
        run_subagent: (params) => wrapToolExecution(run_subagent, params),
    };
})();
exports.run_subagent = UIAutomationSubAgentTools.run_subagent;

package com.ai.assistance.operit.core.tools.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import androidx.core.content.FileProvider
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.core.tools.AppListData
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardUITools
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.AndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.common.displays.UIAutomationProgressOverlay
import com.ai.assistance.operit.ui.common.displays.VirtualDisplayOverlay
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ImagePoolManager
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Configuration for the PhoneAgent. */
data class AgentConfig(
    val maxSteps: Int = 20
)

/** Result of a single agent step. */
data class StepResult(
    val success: Boolean,
    val finished: Boolean,
    val action: ParsedAgentAction?,
    val thinking: String?,
    val message: String? = null
)

/** Parsed action from the model's response. */
data class ParsedAgentAction(
    val metadata: String,
    val actionName: String?,
    val fields: Map<String, String>
)

/**
 * AI-powered agent for automating Android phone interactions.
 *
 * The agent uses a vision-language model to understand screen content
 * and decide on actions to complete user tasks.
 */
class PhoneAgent(
    private val context: Context,
    private val config: AgentConfig,
    private val uiService: AIService, // 改为依赖 AIService 接口
    private val actionHandler: ActionHandler
) {
    private var _stepCount = 0
    val stepCount: Int
        get() = _stepCount

    private val _contextHistory = mutableListOf<Pair<String, String>>()
    val contextHistory: List<Pair<String, String>>
        get() = _contextHistory.toList()

    private var pauseFlow: StateFlow<Boolean>? = null

    private suspend fun awaitIfPaused() {
        val flow = pauseFlow ?: return
        if (!flow.value) {
            return
        }
        AppLogger.d("PhoneAgent", "awaitIfPaused: entering pause loop, delay starting")
        try {
            while (flow.value) {
                delay(200)
            }
        } finally {
            AppLogger.d("PhoneAgent", "awaitIfPaused: exiting pause loop")
        }
    }


    /**
     * Run the agent to complete a task.
     *
     * @param task Natural language description of the task.
     * @param systemPrompt System prompt for the UI automation agent.
     * @param onStep Optional callback invoked after each step with the StepResult.
     * @return Final message from the agent.
     */
    suspend fun run(
        task: String,
        systemPrompt: String,
        onStep: (suspend (StepResult) -> Unit)? = null,
        isPausedFlow: StateFlow<Boolean>? = null
    ): String {
        val floatingService = FloatingChatService.getInstance()
        val job = currentCoroutineContext()[Job]

        val hasShowerDisplayAtStart = try {
            ShowerController.getDisplayId() != null || ShowerController.getVideoSize() != null
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "Error checking Shower virtual display state", e)
            false
        }

        var useShowerUi = hasShowerDisplayAtStart
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context)
        var showerOverlay: VirtualDisplayOverlay? = if (useShowerUi) try {
            VirtualDisplayOverlay.getInstance(context)
        } catch (e: Exception) {
            AppLogger.e("PhoneAgent", "Error getting VirtualDisplayOverlay instance", e)
            null
        } else null

        val pausedMutable = isPausedFlow as? MutableStateFlow<Boolean>

        try {
            // Setup UI for agent run: hide window, then choose indicator based on whether Shower virtual display is active
            floatingService?.setFloatingWindowVisible(false)
            if (useShowerUi) {
                useShowerIndicatorForAgent(context)
            } else {
                useFullscreenStatusIndicatorForAgent()
            }
            if (useShowerUi) {
                showerOverlay?.showAutomationControls(
                    totalSteps = config.maxSteps,
                    initialStatus = "思考中...",
                    onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused },
                    onExit = { job?.cancel(CancellationException("User cancelled UI automation")) }
                )
            } else {
                progressOverlay.show(
                    config.maxSteps,
                    "Thinking...",
                    onCancel = { job?.cancel(CancellationException("User cancelled UI automation")) },
                    onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused }
                )
            }

            reset()
            _contextHistory.add("system" to systemPrompt)
            pauseFlow = isPausedFlow

            // First step with user prompt
            AppLogger.d("PhoneAgent", "run: starting first step for task='$task', hasShowerDisplayAtStart=$hasShowerDisplayAtStart")
            awaitIfPaused()
            AppLogger.d("PhoneAgent", "run: after awaitIfPaused for first step")
            var result = _executeStep(task, isFirst = true)
            AppLogger.d("PhoneAgent", "run: first step _executeStep completed, stepCount=$_stepCount, finished=${result.finished}")
            val firstAction = result.action
            val firstStatusText = when {
                result.finished -> result.message ?: "已完成"
                firstAction != null && firstAction.metadata == "do" -> {
                    val actionName = firstAction.actionName ?: ""
                    if (actionName.isNotEmpty()) "执行 ${actionName} 中..." else "执行操作中..."
                }
                else -> "思考中..."
            }

            if (!useShowerUi) {
                val hasShowerNow = try {
                    ShowerController.getDisplayId() != null || ShowerController.getVideoSize() != null
                } catch (e: Exception) {
                    AppLogger.e("PhoneAgent", "Error re-checking Shower virtual display state after first step", e)
                    false
                }

                if (hasShowerNow) {
                    useShowerUi = true
                    try {
                        progressOverlay.hide()
                    } catch (e: Exception) {
                        AppLogger.e("PhoneAgent", "Error hiding legacy UIAutomationProgressOverlay when switching to Shower UI (first step)", e)
                    }

                    try {
                        showerOverlay = VirtualDisplayOverlay.getInstance(context)
                    } catch (e: Exception) {
                        AppLogger.e("PhoneAgent", "Error getting VirtualDisplayOverlay instance when switching to Shower UI (first step)", e)
                        showerOverlay = null
                    }

                    if (showerOverlay != null) {
                        useShowerIndicatorForAgent(context)
                        showerOverlay?.showAutomationControls(
                            totalSteps = config.maxSteps,
                            initialStatus = firstStatusText,
                            onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused },
                            onExit = { job?.cancel(CancellationException("User cancelled UI automation")) }
                        )
                        showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, firstStatusText)
                    } else {
                        // Fallback: still use legacy overlay if Shower overlay is not available
                        progressOverlay.show(
                            config.maxSteps,
                            "Thinking...",
                            onCancel = { job?.cancel(CancellationException("User cancelled UI automation")) },
                            onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused }
                        )
                        progressOverlay.updateProgress(stepCount, config.maxSteps, firstStatusText)
                        useShowerUi = false
                    }
                } else {
                    progressOverlay.updateProgress(stepCount, config.maxSteps, firstStatusText)
                }
            } else {
                showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, firstStatusText)
            }

            onStep?.invoke(result)
            AppLogger.d("PhoneAgent", "run: onStep callback for first step completed")

            if (result.finished) {
                return result.message ?: "Task completed"
            }

            // Continue until finished or max steps reached
            while (_stepCount < config.maxSteps) {
                AppLogger.d("PhoneAgent", "run: starting loop iteration, current stepCount=$_stepCount")
                awaitIfPaused()
                AppLogger.d("PhoneAgent", "run: after awaitIfPaused in loop, current stepCount=$_stepCount")
                result = _executeStep(null, isFirst = false)
                AppLogger.d("PhoneAgent", "run: loop _executeStep completed, stepCount=$_stepCount, finished=${result.finished}")
                val action = result.action
                val statusText = when {
                    result.finished -> result.message ?: "已完成"
                    action != null && action.metadata == "do" -> {
                        val actionName = action.actionName ?: ""
                        if (actionName.isNotEmpty()) "执行 ${actionName} 中..." else "执行操作中..."
                    }
                    else -> "思考中..."
                }

                if (!useShowerUi) {
                    val hasShowerNow = try {
                        ShowerController.getDisplayId() != null || ShowerController.getVideoSize() != null
                    } catch (e: Exception) {
                        AppLogger.e("PhoneAgent", "Error re-checking Shower virtual display state in loop", e)
                        false
                    }

                    if (hasShowerNow) {
                        useShowerUi = true
                        try {
                            progressOverlay.hide()
                        } catch (e: Exception) {
                            AppLogger.e("PhoneAgent", "Error hiding legacy UIAutomationProgressOverlay when switching to Shower UI (loop)", e)
                        }

                        try {
                            showerOverlay = VirtualDisplayOverlay.getInstance(context)
                        } catch (e: Exception) {
                            AppLogger.e("PhoneAgent", "Error getting VirtualDisplayOverlay instance when switching to Shower UI (loop)", e)
                            showerOverlay = null
                        }

                        if (showerOverlay != null) {
                            useShowerIndicatorForAgent(context)
                            showerOverlay?.showAutomationControls(
                                totalSteps = config.maxSteps,
                                initialStatus = statusText,
                                onTogglePauseResume = { isPaused -> pausedMutable?.value = isPaused },
                                onExit = { job?.cancel(CancellationException("User cancelled UI automation")) }
                            )
                            showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, statusText)
                        } else {
                            // Fallback: still use legacy overlay if Shower overlay is not available
                            progressOverlay.show(
                                config.maxSteps,
                                "Thinking...",
                                onCancel = { job?.cancel(CancellationException("User cancelled UI automation")) },
                                onToggleTakeOver = { isPaused -> pausedMutable?.value = isPaused }
                            )
                            progressOverlay.updateProgress(stepCount, config.maxSteps, statusText)
                            useShowerUi = false
                        }
                    } else {
                        progressOverlay.updateProgress(stepCount, config.maxSteps, statusText)
                    }
                } else {
                    showerOverlay?.updateAutomationProgress(stepCount, config.maxSteps, statusText)
                }

                onStep?.invoke(result)
                AppLogger.d("PhoneAgent", "run: onStep callback for loop step completed, stepCount=$_stepCount")

                if (result.finished) {
                    return result.message ?: "Task completed"
                }
            }

            AppLogger.d("PhoneAgent", "run: max steps reached, stepCount=$_stepCount")
            return "Max steps reached"
        } finally {
            // Restore UI after agent run: show window, hide any indicators, hide progress
            AppLogger.d("PhoneAgent", "run: finishing, restoring UI")
            pauseFlow = null
            floatingService?.setFloatingWindowVisible(true)
            clearAgentIndicators(context)
            if (useShowerUi) {
                showerOverlay?.hideAutomationControls()
            } else {
                progressOverlay.hide()
            }
        }
    }

    /** Reset the agent state for a new task. */
    fun reset() {
        _contextHistory.clear()
        _stepCount = 0
    }

    /** Execute a single step of the agent loop. */
    private suspend fun _executeStep(userPrompt: String?, isFirst: Boolean): StepResult {
        _stepCount++
        AppLogger.d("PhoneAgent", "_executeStep: begin, step=$_stepCount, isFirst=$isFirst")

        AppLogger.d("PhoneAgent", "_executeStep: step=$_stepCount calling captureScreenshotForAgent")
        val screenshotLink = actionHandler.captureScreenshotForAgent()
        AppLogger.d("PhoneAgent", "_executeStep: step=$_stepCount captureScreenshotForAgent completed, linkNull=${screenshotLink == null}")
        val screenInfo = buildString {
            if (screenshotLink != null) {
                appendLine("[SCREENSHOT] Below is the latest screen image:")
                appendLine(screenshotLink)
            } else {
                appendLine("No screenshot available for this step.")
            }
        }.trim()

        val userMessage = if (isFirst) {
            "$userPrompt\n\n$screenInfo"
        } else {
            "** Screen Info **\n\n$screenInfo"
        }

        _contextHistory.add("user" to userMessage)

        // 直接使用传入的、已经为UI_CONTROLLER配置好的AIService实例
        AppLogger.d("PhoneAgent", "_executeStep: step=$_stepCount sending message to AI, messageLength=${userMessage.length}, historySize=${_contextHistory.size}")
        val responseStream = uiService.sendMessage(
            message = userMessage,
            chatHistory = _contextHistory.toList(),
            enableThinking = false, // 保持 false 以获取原始输出
            stream = true,
            preserveThinkInHistory = true // 确保在多步任务中保留完整的上下文
        )

        val contentBuilder = StringBuilder()
        responseStream.collect { chunk -> contentBuilder.append(chunk) }
        val fullResponse = contentBuilder.toString().trim()
        AppLogger.d("PhoneAgent", "_executeStep: step=$_stepCount AI response collected, length=${fullResponse.length}")

        // 对齐官方 Python 客户端：
        // 1. 优先用 finish(message=) / do(action=) 切分思考和动作
        // 2. 没有这些标记时，再回退到 <think>/<answer> 标签解析
        val (thinking, answer) = parseThinkingAndAction(fullResponse)
        AppLogger.d("PhoneAgent", "_executeStep: step=$_stepCount parsed thinking length=${thinking?.length ?: 0}, answer length=${answer.length}")

        // 严格按照官方格式将思考和动作重新组合，然后添加到历史记录中
        // 确保传递给模型的上下文是干净且格式正确的
        val historyEntry = "<think>$thinking</think><answer>$answer</answer>"
        _contextHistory.add("assistant" to historyEntry)

        val parsedAction = parseAgentAction(answer)
        AppLogger.d("PhoneAgent", "_executeStep: step=$_stepCount parsedAction metadata=${parsedAction.metadata}, action=${parsedAction.actionName}")

        actionHandler.removeImagesFromLastUserMessage(_contextHistory)

        if (parsedAction.metadata == "finish") {
            val message = parsedAction.fields["message"] ?: "Task finished."
            AppLogger.d("PhoneAgent", "_executeStep: step=$_stepCount finish with message length=${message.length}")
            return StepResult(success = true, finished = true, action = parsedAction, thinking = thinking, message = message)
        }

        if (parsedAction.metadata == "do") {
            AppLogger.d("PhoneAgent", "_executeStep: step=$_stepCount about to awaitIfPaused before executeAgentAction, action=${parsedAction.actionName}")
            awaitIfPaused()
            AppLogger.d("PhoneAgent", "_executeStep: step=$_stepCount calling executeAgentAction, action=${parsedAction.actionName}")
            val execResult = actionHandler.executeAgentAction(parsedAction)
            AppLogger.d("PhoneAgent", "_executeStep: step=$_stepCount executeAgentAction completed, success=${execResult.success}, shouldFinish=${execResult.shouldFinish}")
            if (execResult.shouldFinish) {
                 return StepResult(success = execResult.success, finished = true, action = parsedAction, thinking = thinking, message = execResult.message)
            }
            return StepResult(success = execResult.success, finished = false, action = parsedAction, thinking = thinking, message = execResult.message)
        }

        // Unknown action type
        val errorMessage = "Unknown action format: ${parsedAction.metadata}"
        AppLogger.e("PhoneAgent", "_executeStep: step=$_stepCount unknown action format, metadata=${parsedAction.metadata}")
        return StepResult(success = false, finished = true, action = parsedAction, thinking = thinking, message = errorMessage)
    }

    private fun extractTagContent(text: String, tag: String): String? {
        val pattern = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * 将完整回复拆分为思考(thinking)和动作(answer)两部分。
     * 规则与官方 Python ModelClient._parse_response 保持一致：
     * 1. 如果包含 "finish(message="，则其前面是思考，后面(含该标记)是动作；
     * 2. 否则如果包含 "do(action="，则其前面是思考，后面(含该标记)是动作；
     * 3. 否则退回到 <think>/<answer> 标签解析；
     * 4. 再否则，视为没有显式思考，整个内容作为动作。
     */
    private fun parseThinkingAndAction(content: String): Pair<String?, String> {
        val full = content.trim()

        // Rule 1: finish(message=
        val finishMarker = "finish(message="
        val finishIndex = full.indexOf(finishMarker)
        if (finishIndex >= 0) {
            val thinking = full.substring(0, finishIndex).trim().ifEmpty { null }
            val action = full.substring(finishIndex).trim()
            return thinking to action
        }

        // Rule 2: do(action=
        val doMarker = "do(action="
        val doIndex = full.indexOf(doMarker)
        if (doIndex >= 0) {
            val thinking = full.substring(0, doIndex).trim().ifEmpty { null }
            val action = full.substring(doIndex).trim()
            return thinking to action
        }

        // Rule 3: fallback to legacy XML-style tags
        val thinkTag = extractTagContent(full, "think")
        val answerTag = extractTagContent(full, "answer")
        if (thinkTag != null || answerTag != null) {
            return thinkTag to (answerTag ?: full)
        }

        // Rule 4: no markers at all
        return null to full
    }

    private fun parseAgentAction(raw: String): ParsedAgentAction {
        val original = raw.trim()
        val finishIndex = original.lastIndexOf("finish(")
        val doIndex = original.lastIndexOf("do(")
        val startIndex = when {
            finishIndex >= 0 && doIndex >= 0 -> maxOf(finishIndex, doIndex)
            finishIndex >= 0 -> finishIndex
            doIndex >= 0 -> doIndex
            else -> -1
        }

        val trimmed = if (startIndex >= 0) original.substring(startIndex).trim() else original

        if (trimmed.startsWith("finish")) {
            val messageRegex = Regex("""finish\s*\(\s*message\s*=\s*\"(.*)\"\s*\)""", RegexOption.DOT_MATCHES_ALL)
            val message = messageRegex.find(trimmed)?.groupValues?.getOrNull(1) ?: ""
            return ParsedAgentAction(metadata = "finish", actionName = null, fields = mapOf("message" to message))
        }

        if (!trimmed.startsWith("do")) {
            return ParsedAgentAction(metadata = "unknown", actionName = null, fields = emptyMap())
        }

        val inner = trimmed.removePrefix("do").trim().removeSurrounding("(", ")")
        val fields = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)\s*=\s*(?:\[(.*?)\]|\"(.*?)\"|'([^']*)'|([^,)]+))""")
        regex.findAll(inner).forEach { matchResult ->
            val key = matchResult.groupValues[1]
            val value = matchResult.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: ""
            fields[key] = value
        }

        return ParsedAgentAction(metadata = "do", actionName = fields["action"], fields = fields)
    }
}

private suspend fun useFullscreenStatusIndicatorForAgent() {
    val floatingService = FloatingChatService.getInstance()
    floatingService?.setStatusIndicatorVisible(true)
}

private suspend fun useShowerIndicatorForAgent(context: Context) {
    try {
        val overlay = VirtualDisplayOverlay.getInstance(context)
        overlay.setShowerBorderVisible(true)
    } catch (e: Exception) {
        AppLogger.e("PhoneAgent", "Error enabling Shower border indicator", e)
    }
    val floatingService = FloatingChatService.getInstance()
    floatingService?.setStatusIndicatorVisible(false)
}

private suspend fun clearAgentIndicators(context: Context) {
    try {
        val overlay = VirtualDisplayOverlay.getInstance(context)
        overlay.setShowerBorderVisible(false)
    } catch (e: Exception) {
        AppLogger.e("PhoneAgent", "Error disabling Shower border indicator", e)
    }
    val floatingService = FloatingChatService.getInstance()
    floatingService?.setStatusIndicatorVisible(false)
}

/** Handles the execution of parsed actions. */
class ActionHandler(
    private val context: Context,
    private var screenWidth: Int,
    private var screenHeight: Int,
    private val toolImplementations: ToolImplementations
) {
    data class ActionExecResult(
        val success: Boolean,
        val shouldFinish: Boolean,
        val message: String?
    )

    companion object {
        private const val POST_LAUNCH_DELAY_MS = 1000L
        private const val POST_NON_WAIT_ACTION_DELAY_MS = 500L
    }

    /**
     * Lightweight context describing whether we should route operations through Shower.
     */
    private data class ShowerUsageContext(
        val isAdbOrHigher: Boolean,
        val showerDisplayId: Int?
    ) {
        val hasShowerDisplay: Boolean get() = showerDisplayId != null
        val canUseShowerForInput: Boolean get() = isAdbOrHigher && showerDisplayId != null
    }

    private fun resolveShowerUsageContext(): ShowerUsageContext {
        AppLogger.d("ActionHandler", "resolveShowerUsageContext: fetching preferred permission level")
        val level = androidPermissionPreferences.getPreferredPermissionLevel() ?: AndroidPermissionLevel.STANDARD
        AppLogger.d("ActionHandler", "resolveShowerUsageContext: level=$level")
        var isAdbOrHigher = when (level) {
            AndroidPermissionLevel.DEBUGGER,
            AndroidPermissionLevel.ADMIN,
            AndroidPermissionLevel.ROOT -> true
            else -> false
        }

        if (isAdbOrHigher) {
            val experimentalEnabled = try {
                DisplayPreferencesManager.getInstance(context).isExperimentalVirtualDisplayEnabled()
            } catch (e: Exception) {
                AppLogger.e("ActionHandler", "Error reading experimental virtual display flag", e)
                true
            }
            if (!experimentalEnabled) {
                AppLogger.d(
                    "ActionHandler",
                    "resolveShowerUsageContext: experimental virtual display disabled, not using Shower"
                )
                isAdbOrHigher = false
            }
        }
        val showerId = try {
            ShowerController.getDisplayId()
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "Error getting Shower display id", e)
            null
        }
        AppLogger.d("ActionHandler", "resolveShowerUsageContext: isAdbOrHigher=$isAdbOrHigher, showerDisplayId=$showerId")
        return ShowerUsageContext(isAdbOrHigher = isAdbOrHigher, showerDisplayId = showerId)
    }

    suspend fun captureScreenshotForAgent(): String? {
        val showerCtx = resolveShowerUsageContext()
        val floatingService = FloatingChatService.getInstance()
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context)

        var screenshotLink: String? = null
        var dimensions: Pair<Int, Int>? = null

        AppLogger.d("ActionHandler", "captureScreenshotForAgent: start, canUseShowerForInput=${showerCtx.canUseShowerForInput}, hasShowerDisplay=${showerCtx.hasShowerDisplay}")

        if (showerCtx.canUseShowerForInput) {
            AppLogger.d("ActionHandler", "captureScreenshotForAgent: trying Shower screenshot")
            val (link, dims) = captureScreenshotViaShower()
            screenshotLink = link
            dimensions = dims
        }

        if (screenshotLink == null) {
            try {
                floatingService?.setStatusIndicatorVisible(false)
                progressOverlay.setOverlayVisible(false)
                AppLogger.d("ActionHandler", "captureScreenshotForAgent: UI hidden, starting 200ms delay")
                delay(200)
                AppLogger.d("ActionHandler", "captureScreenshotForAgent: delay finished")

                val screenshotTool = buildScreenshotTool()
                AppLogger.d("ActionHandler", "captureScreenshotForAgent: invoking toolImplementations.captureScreenshot...")
                val (filePath, fallbackDims) = toolImplementations.captureScreenshot(screenshotTool)
                AppLogger.d("ActionHandler", "captureScreenshotForAgent: toolImplementations.captureScreenshot returned. filePath=$filePath")
                
                if (filePath != null) {
                    val bitmap = BitmapFactory.decodeFile(filePath)
                    if (bitmap != null) {
                        val (compressedLink, _) = saveCompressedScreenshotFromBitmap(bitmap)
                        screenshotLink = compressedLink
                        dimensions = fallbackDims
                        bitmap.recycle()
                    } else {
                        AppLogger.e("ActionHandler", "Failed to decode screenshot file: $filePath")
                    }
                } else {
                    AppLogger.e("ActionHandler", "Fallback screenshot tool returned no file path")
                }
            } finally {
                val hasShowerDisplayNow = try {
                    ShowerController.getDisplayId() != null
                } catch (e: Exception) {
                    AppLogger.e("ActionHandler", "Error checking Shower display state in screenshot finally", e)
                    false
                }
                if (!hasShowerDisplayNow) {
                    floatingService?.setStatusIndicatorVisible(true)
                }
                progressOverlay.setOverlayVisible(true)
            }
        }

        if (dimensions != null) {
            screenWidth = dimensions.first
            screenHeight = dimensions.second
            AppLogger.d("ActionHandler", "Updated screen dimensions from screenshot: w=$screenWidth, h=$screenHeight")
        }
        AppLogger.d("ActionHandler", "captureScreenshotForAgent: end, linkNull=${screenshotLink == null}, width=${dimensions?.first}, height=${dimensions?.second}")
        return screenshotLink
    }

    private fun buildScreenshotTool(): AITool {
        return AITool(
            name = "capture_screenshot",
            parameters = emptyList()
        )
    }

    private suspend fun captureScreenshotViaShower(): Pair<String?, Pair<Int, Int>?> {
        return try {
            val pngBytes = ShowerVideoRenderer.captureCurrentFramePng()
            if (pngBytes == null || pngBytes.isEmpty()) {
                AppLogger.w("ActionHandler", "Shower WS screenshot returned no data")
                Pair(null, null)
            } else {
                val bitmap = BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)
                if (bitmap == null) {
                    AppLogger.e("ActionHandler", "Shower screenshot: failed to decode PNG bytes")
                    Pair(null, null)
                } else {
                    val result = saveCompressedScreenshotFromBitmap(bitmap)
                    bitmap.recycle()
                    result
                }
            }
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "Shower screenshot failed", e)
            Pair(null, null)
        }
    }

    private fun saveCompressedScreenshotFromBitmap(bitmap: Bitmap): Pair<String?, Pair<Int, Int>?> {
        return try {
            val originalWidth = bitmap.width
            val originalHeight = bitmap.height

            val prefs = DisplayPreferencesManager.getInstance(context)
            val format = prefs.getScreenshotFormat().uppercase(Locale.getDefault())
            val quality = prefs.getScreenshotQuality().coerceIn(50, 100)
            val scalePercent = prefs.getScreenshotScalePercent().coerceIn(50, 100)

            val screenshotDir = File("/sdcard/Download/Operit/cleanOnExit")
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs()
            }

            val shortName = System.currentTimeMillis().toString().takeLast(4)
            val (compressFormat, fileExt, effectiveQuality) = when (format) {
                "JPG", "JPEG" -> Triple(Bitmap.CompressFormat.JPEG, "jpg", quality)
                else -> Triple(Bitmap.CompressFormat.PNG, "png", 100)
            }

            val scaleFactor = scalePercent / 100.0
            val bitmapForSave = if (scaleFactor in 0.0..0.999) {
                val newWidth = (originalWidth * scaleFactor).toInt().coerceAtLeast(1)
                val newHeight = (originalHeight * scaleFactor).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            } else {
                bitmap
            }

            val file = File(screenshotDir, "$shortName.$fileExt")

            try {
                FileOutputStream(file).use { outputStream ->
                    val ok = bitmapForSave.compress(compressFormat, effectiveQuality, outputStream)
                    if (!ok) {
                        AppLogger.e("ActionHandler", "Shower screenshot: compression failed for ${file.absolutePath}")
                        return Pair(null, null)
                    }
                }
            } finally {
                if (bitmapForSave !== bitmap) {
                    bitmapForSave.recycle()
                }
            }

            val imageId = ImagePoolManager.addImage(file.absolutePath)
            if (imageId == "error") {
                AppLogger.e("ActionHandler", "Shower screenshot: failed to register image: ${file.absolutePath}")
                Pair(null, null)
            } else {
                Pair("<link type=\"image\" id=\"$imageId\"></link>", Pair(originalWidth, originalHeight))
            }
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "Error saving compressed screenshot", e)
            Pair(null, null)
        }
    }

    fun removeImagesFromLastUserMessage(history: MutableList<Pair<String, String>>) {
        // This is a placeholder for the actual image link removal logic
        val lastUserMessageIndex = history.indexOfLast { it.first == "user" }
        if (lastUserMessageIndex != -1) {
            val (role, content) = history[lastUserMessageIndex]
            if (content.contains("<link type=\"image\"")) {
                val stripped = content.replace(Regex("""<link type=\"image\".*?</link>"""), "").trim()
                history[lastUserMessageIndex] = role to stripped
            }
        }
    }

    suspend fun executeAgentAction(parsed: ParsedAgentAction): ActionExecResult {
        val actionName = parsed.actionName ?: return fail(message = "Missing action name")
        val fields = parsed.fields

        val showerCtx = resolveShowerUsageContext()
        return when (actionName) {
            "Launch" -> {
                val app = fields["app"]?.takeIf { it.isNotBlank() } ?: return fail(message = "No app name specified for Launch")
                val packageName = resolveAppPackageName(app)
                try {
                    val preferredLevel = androidPermissionPreferences.getPreferredPermissionLevel()
                        ?: AndroidPermissionLevel.STANDARD
                    val experimentalEnabled = try {
                        DisplayPreferencesManager.getInstance(context).isExperimentalVirtualDisplayEnabled()
                    } catch (e: Exception) {
                        AppLogger.e(
                            "ActionHandler",
                            "Error reading experimental virtual display flag for Launch",
                            e
                        )
                        true
                    }

                    if (preferredLevel == AndroidPermissionLevel.DEBUGGER && experimentalEnabled) {
                        val isShizukuRunning = ShizukuAuthorizer.isShizukuServiceRunning()
                        val hasShizukuPermission =
                            if (isShizukuRunning) ShizukuAuthorizer.hasShizukuPermission() else false

                        if (!isShizukuRunning || !hasShizukuPermission) {
                            val reason = if (!isShizukuRunning) {
                                ShizukuAuthorizer.getServiceErrorMessage().ifBlank { "Shizuku 服务未运行" }
                            } else {
                                ShizukuAuthorizer.getPermissionErrorMessage().ifBlank { "Shizuku 权限未授予" }
                            }
                            return fail(
                                shouldFinish = true,
                                message = "当前已选择 ADB 调试权限，但 Shizuku 不可用，无法启用实验性虚拟屏幕。\n" +
                                    reason +
                                    "\n\n请先在「权限授予」界面启动并授权 Shizuku，然后重新尝试 Launch 操作。"
                            )
                        }
                    }

                    if (showerCtx.isAdbOrHigher) {
                        // High-privilege path: use Shower server + virtual display.
                        val pm = context.packageManager
                        val hasLaunchableTarget = pm.getLaunchIntentForPackage(packageName) != null

                        ensureVirtualDisplayIfAdbOrHigher()

                        val metrics = context.resources.displayMetrics
                        val width = metrics.widthPixels
                        val height = metrics.heightPixels
                        val dpi = metrics.densityDpi

                        val bitrateKbps = try {
                            DisplayPreferencesManager.getInstance(context).getVirtualDisplayBitrateKbps()
                        } catch (e: Exception) {
                            AppLogger.e("ActionHandler", "Error reading virtual display bitrate preference", e)
                            3000
                        }

                        val created = ShowerController.ensureDisplay(context, width, height, dpi, bitrateKbps = bitrateKbps)
                        val launched = if (created && hasLaunchableTarget) ShowerController.launchApp(packageName) else false

                        if (created && launched) {
                            try {
                                VirtualDisplayOverlay.getInstance(context).updateCurrentAppPackageName(packageName)
                            } catch (_: Exception) {
                            }
                            // 成功在虚拟屏小窗启动后，切换到 Shower 边框指示并关闭全屏指示
                            useShowerIndicatorForAgent(context)
                            delay(POST_LAUNCH_DELAY_MS)
                            ok()
                        } else {
                            // 如果目标应用在 Shower 上启动失败或不存在，尝试启动桌面 fallback 应用
                            val desktopPackage = "com.ai.assistance.operit.desktop"
                            val requiredDesktopVersion = readRequiredDesktopVersion()
                            val installedDesktopVersion = try {
                                val info = pm.getPackageInfo(desktopPackage, 0)
                                info.versionName
                            } catch (e: Exception) {
                                null
                            }
                            val desktopIntent = pm.getLaunchIntentForPackage(desktopPackage)
                            val isDesktopInstalled = desktopIntent != null
                            val isDesktopVersionTooLow = requiredDesktopVersion != null && isDesktopInstalled &&
                                isVersionLower(installedDesktopVersion, requiredDesktopVersion)

                            if (isDesktopInstalled && !isDesktopVersionTooLow) {
                                // 桌面应用已安装：直接在 Shower 虚拟屏上启动桌面
                                val desktopLaunched = ShowerController.launchApp(desktopPackage)
                                if (desktopLaunched) {
                                    try {
                                        VirtualDisplayOverlay.getInstance(context).updateCurrentAppPackageName(desktopPackage)
                                    } catch (_: Exception) {
                                    }
                                    useShowerIndicatorForAgent(context)
                                    delay(POST_LAUNCH_DELAY_MS)
                                    ok()
                                } else {
                                    fail(message = "Failed to launch fallback desktop app on Shower virtual display: $desktopPackage")
                                }
                            } else {
                                // 桌面应用未安装：从 assets 拷贝 desktop.apk 到下载目录，并通过系统安装界面安装
                                try {
                                    val destDir = File("/sdcard/Download/Operit")
                                    if (!destDir.exists()) {
                                        destDir.mkdirs()
                                    }
                                    val destFile = File(destDir, "desktop.apk")
                                    context.assets.open("desktop.apk").use { input ->
                                        FileOutputStream(destFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }

                                    val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            destFile
                                        )
                                    } else {
                                        Uri.fromFile(destFile)
                                    }

                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    }

                                    context.startActivity(installIntent)

                                    // 交给用户通过系统安装界面完成安装，当前自动化任务到此结束
                                    return ok(
                                        shouldFinish = true,
                                        message = "Desktop app is not installed. Opened system installer for fallback desktop APK; please install it, then retry the Launch action."
                                    )
                                } catch (e: Exception) {
                                    AppLogger.e("PhoneAgent", "Error launching fallback desktop apk installer", e)
                                    return fail(message = "Error launching fallback desktop installer: ${e.message}")
                                }
                            }
                        }
                    } else {
                        // Fallback: legacy startApp on main display.
                        val systemTools = ToolGetter.getSystemOperationTools(context)
                        val result = systemTools.startApp(AITool("start_app", listOf(ToolParameter("package_name", packageName))))
                        if (result.success) {
                            delay(POST_LAUNCH_DELAY_MS)
                            ok()
                        } else {
                            fail(message = result.error ?: "Failed to launch app: $packageName")
                        }
                    }
                } catch (e: Exception) {
                    fail(message = "Exception while launching app $packageName: ${e.message}")
                }
            }
            "Tap" -> {
                val element = fields["element"] ?: return fail(message = "No element for Tap")
                val (x, y) = parseRelativePoint(element) ?: return fail(message = "Invalid coordinates for Tap: $element")
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okTap = ShowerController.tap(x, y)
                        if (okTap) ok() else fail(message = "Shower TAP failed at ($x,$y)")
                    } else {
                        val params = withDisplayParam(
                            listOf(
                                ToolParameter("x", x.toString()),
                                ToolParameter("y", y.toString())
                            )
                        )
                        val result = toolImplementations.tap(AITool("tap", params))
                        if (result.success) ok() else fail(message = result.error ?: "Tap failed at ($x,$y)")
                    }
                }
                if (exec.success && !exec.shouldFinish) {
                    delay(POST_NON_WAIT_ACTION_DELAY_MS)
                }
                exec
            }
            "Type" -> {
                val text = fields["text"] ?: ""
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        try {
                            AppLogger.d("ActionHandler", "Type via Shower: clearing field with KEYCODE_CLEAR")
                            val cleared = ShowerController.key(KeyEvent.KEYCODE_CLEAR)
                            if (!cleared) {
                                return@withAgentUiHiddenForAction fail(message = "Shower CLEAR key failed")
                            }

                            delay(300)

                            if (text.isEmpty()) {
                                return@withAgentUiHiddenForAction ok()
                            }

                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    ?: return@withAgentUiHiddenForAction fail(message = "Clipboard service not available")
                            clipboard.setPrimaryClip(ClipData.newPlainText("operit_input", text))

                            delay(100)

                            AppLogger.d("ActionHandler", "Type via Shower: pasting text with KEYCODE_PASTE")
                            val pasted = ShowerController.key(KeyEvent.KEYCODE_PASTE)
                            if (!pasted) {
                                return@withAgentUiHiddenForAction fail(message = "Shower PASTE key failed")
                            }

                            ok()
                        } catch (e: Exception) {
                            AppLogger.e("ActionHandler", "Error typing via Shower", e)
                            fail(message = "Error typing via Shower: ${e.message}")
                        }
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("text", text)))
                        val result = toolImplementations.setInputText(AITool("set_input_text", params))
                        if (result.success) ok() else fail(message = result.error ?: "Set input text failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) {
                    delay(POST_NON_WAIT_ACTION_DELAY_MS)
                }
                exec
            }
            "Swipe" -> {
                val start = fields["start"] ?: return fail(message = "Missing swipe start")
                val end = fields["end"] ?: return fail(message = "Missing swipe end")
                val (sx, sy) = parseRelativePoint(start) ?: return fail(message = "Invalid swipe start: $start")
                val (ex, ey) = parseRelativePoint(end) ?: return fail(message = "Invalid swipe end: $end")
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okSwipe = ShowerController.swipe(sx, sy, ex, ey)
                        if (okSwipe) ok() else fail(message = "Shower SWIPE failed")
                    } else {
                        val params = withDisplayParam(
                            listOf(
                                ToolParameter("start_x", sx.toString()),
                                ToolParameter("start_y", sy.toString()),
                                ToolParameter("end_x", ex.toString()),
                                ToolParameter("end_y", ey.toString())
                            )
                        )
                        val result = toolImplementations.swipe(AITool("swipe", params))
                        if (result.success) ok() else fail(message = result.error ?: "Swipe failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) {
                    delay(POST_NON_WAIT_ACTION_DELAY_MS)
                }
                exec
            }
            "Back" -> {
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okKey = ShowerController.key(KeyEvent.KEYCODE_BACK)
                        if (okKey) ok() else fail(message = "Shower BACK key failed")
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("key_code", "KEYCODE_BACK")))
                        val result = toolImplementations.pressKey(AITool("press_key", params))
                        if (result.success) ok() else fail(message = result.error ?: "Back key failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) {
                    delay(POST_NON_WAIT_ACTION_DELAY_MS)
                }
                exec
            }
            "Home" -> {
                val exec = withAgentUiHiddenForAction(showerCtx) {
                    if (showerCtx.canUseShowerForInput) {
                        val okKey = ShowerController.key(KeyEvent.KEYCODE_HOME)
                        if (okKey) ok() else fail(message = "Shower HOME key failed")
                    } else {
                        val params = withDisplayParam(listOf(ToolParameter("key_code", "KEYCODE_HOME")))
                        val result = toolImplementations.pressKey(AITool("press_key", params))
                        if (result.success) ok() else fail(message = result.error ?: "Home key failed")
                    }
                }
                if (exec.success && !exec.shouldFinish) {
                    delay(POST_NON_WAIT_ACTION_DELAY_MS)
                }
                exec
            }
            "Wait" -> {
                val seconds = fields["duration"]?.replace("seconds", "")?.trim()?.toDoubleOrNull() ?: 1.0
                AppLogger.d("ActionHandler", "Wait action: starting delay for $seconds seconds")
                delay((seconds * 1000).toLong().coerceAtLeast(0L))
                AppLogger.d("ActionHandler", "Wait action: delay finished")
                ok()
            }
            "Take_over" -> ok(shouldFinish = true, message = fields["message"] ?: "User takeover required")
            else -> fail(message = "Unknown action: $actionName")
        }
    }

    private suspend fun withAgentUiHiddenForAction(
        showerCtx: ShowerUsageContext,
        block: suspend () -> ActionExecResult
    ): ActionExecResult {
        if (showerCtx.canUseShowerForInput) {
            return block()
        }
        val progressOverlay = UIAutomationProgressOverlay.getInstance(context)
        try {
            progressOverlay.setOverlayVisible(false)
            AppLogger.d("ActionHandler", "withAgentUiHiddenForAction: UI hidden, starting 200ms delay")
            delay(200)
            AppLogger.d("ActionHandler", "withAgentUiHiddenForAction: delay finished, executing block")
            return block()
        } finally {
            progressOverlay.setOverlayVisible(true)
        }
    }

    private suspend fun ensureVirtualDisplayIfAdbOrHigher() {
        try {
            val level = androidPermissionPreferences.getPreferredPermissionLevel() ?: AndroidPermissionLevel.STANDARD
            val isAdbOrHigher = when (level) {
                AndroidPermissionLevel.DEBUGGER,
                AndroidPermissionLevel.ADMIN,
                AndroidPermissionLevel.ROOT -> true
                else -> false
            }

            if (!isAdbOrHigher) {
                return
            }

            // Start the Shower virtual display server when we have debugger/ADB-level permissions.
            val ok = ShowerServerManager.ensureServerStarted(context)
            if (ok) {
                // We do not know the concrete display id from here yet, but we still show the overlay
                // to indicate that a virtual display session is active.
                try {
                    val overlay = VirtualDisplayOverlay.getInstance(context)
                    overlay.show(0)
                } catch (e: Exception) {
                    AppLogger.e("ActionHandler", "Error showing Shower virtual display overlay", e)
                }
                AppLogger.d("ActionHandler", "Shower virtual display server started via AndroidShellExecutor")
            } else {
                AppLogger.w("ActionHandler", "Failed to start Shower server at ADB-level permission")
            }
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "Error ensuring Shower virtual display", e)
        }
    }

    private fun withDisplayParam(params: List<ToolParameter>): List<ToolParameter> {
        return try {
            val showerId = ShowerController.getDisplayId()
            if (showerId != null) {
                params + ToolParameter("display", showerId.toString())
            } else {
                val id = VirtualDisplayManager.getInstance(context).getDisplayId()
                if (id != null) params + ToolParameter("display", id.toString()) else params
            }
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "Error getting display id", e)
            params
        }
    }

    private fun ok(shouldFinish: Boolean = false, message: String? = null) = ActionExecResult(true, shouldFinish, message)
    private fun fail(shouldFinish: Boolean = false, message: String) = ActionExecResult(false, shouldFinish, message)

    private fun parseRelativePoint(value: String): Pair<Int, Int>? {
        val parts = value.trim().removeSurrounding("[", "]").split(",").map { it.trim() }
        if (parts.size < 2) return null
        val relX = parts[0].toIntOrNull() ?: return null
        val relY = parts[1].toIntOrNull() ?: return null
        return (relX / 1000.0 * screenWidth).toInt() to (relY / 1000.0 * screenHeight).toInt()
    }

    private fun readRequiredDesktopVersion(): String? {
        return try {
            context.assets.open("desktop_version.txt").bufferedReader().use { it.readText().trim() }
                .ifEmpty { null }
        } catch (e: Exception) {
            AppLogger.e("ActionHandler", "Error reading desktop_version.txt", e)
            null
        }
    }

    private fun isVersionLower(installed: String?, required: String): Boolean {
        if (installed == null) return true
        return compareVersionStrings(installed, required) < 0
    }

    private fun compareVersionStrings(v1: String, v2: String): Int {
        val parts1 = v1.split('.', '-', '_')
        val parts2 = v2.split('.', '-', '_')
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrNull(i)?.toIntOrNull() ?: 0
            val p2 = parts2.getOrNull(i)?.toIntOrNull() ?: 0
            if (p1 != p2) {
                return p1 - p2
            }
        }
        return 0
    }

    private suspend fun resolveAppPackageName(app: String): String {
        val trimmed = app.trim()
        val lowered = trimmed.lowercase(Locale.getDefault())

        fun lookup(): String? {
            return StandardUITools.APP_PACKAGES[app]
                ?: StandardUITools.APP_PACKAGES[trimmed]
                ?: StandardUITools.APP_PACKAGES[lowered]
        }

        val directHit = lookup()
        if (directHit != null) return directHit

        withContext(Dispatchers.IO) {
            StandardUITools.scanAndAddInstalledApps(context)
        }

        return lookup() ?: trimmed
    }
}

/** Interface for providing tool implementations to the ActionHandler. */
interface ToolImplementations {
    suspend fun tap(tool: AITool): ToolResult
    suspend fun longPress(tool: AITool): ToolResult
    suspend fun setInputText(tool: AITool): ToolResult
    suspend fun swipe(tool: AITool): ToolResult
    suspend fun pressKey(tool: AITool): ToolResult
    suspend fun captureScreenshot(tool: AITool): Pair<String?, Pair<Int, Int>?> // Returns filePath, not link
}

package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.ImageLinkParser
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.core.tools.AutomationExecutionResult
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.AppListData
import com.ai.assistance.operit.core.tools.defaultTool.ToolGetter
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.common.displays.UIOperationOverlay
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.ImagePoolManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Base class for UI automation tools - standard version does not support UI operations */
open class StandardUITools(protected val context: Context) {

    companion object {
        private const val TAG = "UITools"
        private const val COMMAND_TIMEOUT_SECONDS = 10L
        private const val OPERATION_NOT_SUPPORTED =
                "This operation is not supported in the standard version. Please use the accessibility or debugger version."

        private val APP_PACKAGES: Map<String, String> =
                mapOf(
                        // Social & Messaging
                        "微信" to "com.tencent.mm",
                        "QQ" to "com.tencent.mobileqq",
                        "微博" to "com.sina.weibo",
                        // E-commerce
                        "淘宝" to "com.taobao.taobao",
                        "京东" to "com.jingdong.app.mall",
                        "拼多多" to "com.xunmeng.pinduoduo",
                        "淘宝闪购" to "com.taobao.taobao",
                        "京东秒送" to "com.jingdong.app.mall",
                        // Lifestyle & Social
                        "小红书" to "com.xingin.xhs",
                        "豆瓣" to "com.douban.frodo",
                        "知乎" to "com.zhihu.android",
                        // Maps & Navigation
                        "高德地图" to "com.autonavi.minimap",
                        "百度地图" to "com.baidu.BaiduMap",
                        // Food & Services
                        "美团" to "com.sankuai.meituan",
                        "大众点评" to "com.dianping.v1",
                        "饿了么" to "me.ele",
                        "肯德基" to "com.yek.android.kfc.activitys",
                        // Travel
                        "携程" to "ctrip.android.view",
                        "铁路12306" to "com.MobileTicket",
                        "12306" to "com.MobileTicket",
                        "去哪儿" to "com.Qunar",
                        "去哪儿旅行" to "com.Qunar",
                        "滴滴出行" to "com.sdu.did.psnger",
                        // Video & Entertainment
                        "bilibili" to "tv.danmaku.bili",
                        "抖音" to "com.ss.android.ugc.aweme",
                        "快手" to "com.smile.gifmaker",
                        "腾讯视频" to "com.tencent.qqlive",
                        "爱奇艺" to "com.qiyi.video",
                        "优酷视频" to "com.youku.phone",
                        "芒果TV" to "com.hunantv.imgo.activity",
                        "红果短剧" to "com.phoenix.read",
                        // Music & Audio
                        "网易云音乐" to "com.netease.cloudmusic",
                        "QQ音乐" to "com.tencent.qqmusic",
                        "汽水音乐" to "com.luna.music",
                        "喜马拉雅" to "com.ximalaya.ting.android",
                        // Reading
                        "番茄小说" to "com.dragon.read",
                        "番茄免费小说" to "com.dragon.read",
                        "七猫免费小说" to "com.kmxs.reader",
                        // Productivity
                        "飞书" to "com.ss.android.lark",
                        "QQ邮箱" to "com.tencent.androidqqmail",
                        // AI & Tools
                        "豆包" to "com.larus.nova",
                        // Health & Fitness
                        "keep" to "com.gotokeep.keep",
                        "美柚" to "com.lingan.seeyou",
                        // News & Information
                        "腾讯新闻" to "com.tencent.news",
                        "今日头条" to "com.ss.android.article.news",
                        // Real Estate
                        "贝壳找房" to "com.lianjia.beike",
                        "安居客" to "com.anjuke.android.app",
                        // Finance
                        "同花顺" to "com.hexin.plat.android",
                        // Games
                        "星穹铁道" to "com.miHoYo.hkrpg",
                        "崩坏：星穹铁道" to "com.miHoYo.hkrpg",
                        "恋与深空" to "com.papegames.lysk.cn",
                        // System & Utilities (English mappings)
                        "AndroidSystemSettings" to "com.android.settings",
                        "Android System Settings" to "com.android.settings",
                        "Android  System Settings" to "com.android.settings",
                        "Android-System-Settings" to "com.android.settings",
                        "Settings" to "com.android.settings",
                        "AudioRecorder" to "com.android.soundrecorder",
                        "audiorecorder" to "com.android.soundrecorder",
                        "Bluecoins" to "com.rammigsoftware.bluecoins",
                        "bluecoins" to "com.rammigsoftware.bluecoins",
                        "Broccoli" to "com.flauschcode.broccoli",
                        "broccoli" to "com.flauschcode.broccoli",
                        "Booking.com" to "com.booking",
                        "Booking" to "com.booking",
                        "booking.com" to "com.booking",
                        "booking" to "com.booking",
                        "BOOKING.COM" to "com.booking",
                        "Chrome" to "com.android.chrome",
                        "chrome" to "com.android.chrome",
                        "Google Chrome" to "com.android.chrome",
                        "Clock" to "com.android.deskclock",
                        "clock" to "com.android.deskclock",
                        "Contacts" to "com.android.contacts",
                        "contacts" to "com.android.contacts",
                        "Duolingo" to "com.duolingo",
                        "duolingo" to "com.duolingo",
                        "Expedia" to "com.expedia.bookings",
                        "expedia" to "com.expedia.bookings",
                        "Files" to "com.android.fileexplorer",
                        "files" to "com.android.fileexplorer",
                        "File Manager" to "com.android.fileexplorer",
                        "file manager" to "com.android.fileexplorer",
                        "gmail" to "com.google.android.gm",
                        "Gmail" to "com.google.android.gm",
                        "GoogleMail" to "com.google.android.gm",
                        "Google Mail" to "com.google.android.gm",
                        "GoogleFiles" to "com.google.android.apps.nbu.files",
                        "googlefiles" to "com.google.android.apps.nbu.files",
                        "FilesbyGoogle" to "com.google.android.apps.nbu.files",
                        "GoogleCalendar" to "com.google.android.calendar",
                        "Google-Calendar" to "com.google.android.calendar",
                        "Google Calendar" to "com.google.android.calendar",
                        "google-calendar" to "com.google.android.calendar",
                        "google calendar" to "com.google.android.calendar",
                        "GoogleChat" to "com.google.android.apps.dynamite",
                        "Google Chat" to "com.google.android.apps.dynamite",
                        "Google-Chat" to "com.google.android.apps.dynamite",
                        "GoogleClock" to "com.google.android.deskclock",
                        "Google Clock" to "com.google.android.deskclock",
                        "Google-Clock" to "com.google.android.deskclock",
                        "GoogleContacts" to "com.google.android.contacts",
                        "Google-Contacts" to "com.google.android.contacts",
                        "Google Contacts" to "com.google.android.contacts",
                        "google-contacts" to "com.google.android.contacts",
                        "google contacts" to "com.google.android.contacts",
                        "GoogleDocs" to "com.google.android.apps.docs.editors.docs",
                        "Google Docs" to "com.google.android.apps.docs.editors.docs",
                        "googledocs" to "com.google.android.apps.docs.editors.docs",
                        "google docs" to "com.google.android.apps.docs.editors.docs",
                        "Google Drive" to "com.google.android.apps.docs",
                        "Google-Drive" to "com.google.android.apps.docs",
                        "google drive" to "com.google.android.apps.docs",
                        "google-drive" to "com.google.android.apps.docs",
                        "GoogleDrive" to "com.google.android.apps.docs",
                        "Googledrive" to "com.google.android.apps.docs",
                        "googledrive" to "com.google.android.apps.docs",
                        "GoogleFit" to "com.google.android.apps.fitness",
                        "googlefit" to "com.google.android.apps.fitness",
                        "GoogleKeep" to "com.google.android.keep",
                        "googlekeep" to "com.google.android.keep",
                        "GoogleMaps" to "com.google.android.apps.maps",
                        "Google Maps" to "com.google.android.apps.maps",
                        "googlemaps" to "com.google.android.apps.maps",
                        "google maps" to "com.google.android.apps.maps",
                        "Google Play Books" to "com.google.android.apps.books",
                        "Google-Play-Books" to "com.google.android.apps.books",
                        "google play books" to "com.google.android.apps.books",
                        "google-play-books" to "com.google.android.apps.books",
                        "GooglePlayBooks" to "com.google.android.apps.books",
                        "googleplaybooks" to "com.google.android.apps.books",
                        "GooglePlayStore" to "com.android.vending",
                        "Google Play Store" to "com.android.vending",
                        "Google-Play-Store" to "com.android.vending",
                        "GoogleSlides" to "com.google.android.apps.docs.editors.slides",
                        "Google Slides" to "com.google.android.apps.docs.editors.slides",
                        "Google-Slides" to "com.google.android.apps.docs.editors.slides",
                        "GoogleTasks" to "com.google.android.apps.tasks",
                        "Google Tasks" to "com.google.android.apps.tasks",
                        "Google-Tasks" to "com.google.android.apps.tasks",
                        "Joplin" to "net.cozic.joplin",
                        "joplin" to "net.cozic.joplin",
                        "McDonald" to "com.mcdonalds.app",
                        "mcdonald" to "com.mcdonalds.app",
                        "Osmand" to "net.osmand",
                        "osmand" to "net.osmand",
                        "PiMusicPlayer" to "com.Project100Pi.themusicplayer",
                        "pimusicplayer" to "com.Project100Pi.themusicplayer",
                        "Quora" to "com.quora.android",
                        "quora" to "com.quora.android",
                        "Reddit" to "com.reddit.frontpage",
                        "reddit" to "com.reddit.frontpage",
                        "RetroMusic" to "code.name.monkey.retromusic",
                        "retromusic" to "code.name.monkey.retromusic",
                        "SimpleCalendarPro" to "com.scientificcalculatorplus.simplecalculator.basiccalculator.mathcalc",
                        "SimpleSMSMessenger" to "com.simplemobiletools.smsmessenger",
                        "Telegram" to "org.telegram.messenger",
                        "temu" to "com.einnovation.temu",
                        "Temu" to "com.einnovation.temu",
                        "Tiktok" to "com.zhiliaoapp.musically",
                        "tiktok" to "com.zhiliaoapp.musically",
                        "Twitter" to "com.twitter.android",
                        "twitter" to "com.twitter.android",
                        "X" to "com.twitter.android",
                        "VLC" to "org.videolan.vlc",
                        "WeChat" to "com.tencent.mm",
                        "wechat" to "com.tencent.mm",
                        "Whatsapp" to "com.whatsapp",
                        "WhatsApp" to "com.whatsapp"
                )
    }

    // UI操作反馈覆盖层（使用单例避免多窗口叠加）
    protected val operationOverlay = UIOperationOverlay.getInstance(context)

    /** Gets the current UI page/window information */
    open suspend fun getPageInfo(tool: AITool): ToolResult {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    data class UINode(
            val className: String?,
            val text: String?,
            val contentDesc: String?,
            val resourceId: String?,
            val bounds: String?,
            val isClickable: Boolean,
            val children: MutableList<UINode> = mutableListOf()
    )

    protected fun UINode.toUINode(): SimplifiedUINode {
        return SimplifiedUINode(
                className = className,
                text = text,
                contentDesc = contentDesc,
                resourceId = resourceId,
                bounds = bounds,
                isClickable = isClickable,
                children = children.map { it.toUINode() }
        )
    }

    protected data class FocusInfo(
            var packageName: String? = null,
            var activityName: String? = null
    )

    /** Simulates a tap/click at specific coordinates */
    open suspend fun tap(tool: AITool): ToolResult {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /** Simulates a long press at specific coordinates */
    open suspend fun longPress(tool: AITool): ToolResult {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /** Simulates a click on an element identified by resource ID or class name */
    open suspend fun clickElement(tool: AITool): ToolResult {
                    return ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /** Sets text in an input field */
    open suspend fun setInputText(tool: AITool): ToolResult {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /** Simulates pressing a specific key */
    open suspend fun pressKey(tool: AITool): ToolResult {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /** Performs a swipe gesture */
    open suspend fun swipe(tool: AITool): ToolResult {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                error = OPERATION_NOT_SUPPORTED
        )
    }

    /**
     * Executes a lightweight UI automation subagent loop using the UI_CONTROLLER function type.
     * This subagent uses the UI_AUTOMATION_AGENT_PROMPT and returns an AutomationExecutionResult
     * that contains a log of all <think>/<answer> pairs and parsed actions.
     */
    open suspend fun runUiSubAgent(tool: AITool): ToolResult {
        val intent = tool.parameters.find { it.name == "intent" }?.value
        val maxSteps =
                tool.parameters
                        .find { it.name == "max_steps" }
                        ?.value
                        ?.toIntOrNull()
                        ?: 20

        if (intent.isNullOrBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Missing required parameter: intent"
            )
        }

        val uiConfig =
                EnhancedAIService.getModelConfigForFunction(context, FunctionType.UI_CONTROLLER)
        if (!uiConfig.enableDirectImageProcessing) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "当前 UI 控制器模型未启用识图能力，请在设置-功能模型中为 UI 控制器功能选择支持图片理解的模型后再试。"
            )
        }

        return try {
            val uiService =
                    EnhancedAIService.getAIServiceForFunction(context, FunctionType.UI_CONTROLLER)

            val systemPrompt = buildUiAutomationSystemPrompt()
            val history = mutableListOf<Pair<String, String>>()
            history.add("system" to systemPrompt)

            val metrics = context.resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            val actionLogs = mutableListOf<String>()
            var finished = false
            var step = 0
            var finalMessage: String? = null

            while (step < maxSteps && !finished) {
                step++

                val screenshotLink = captureScreenshotForAgent()

                val screenInfo = buildString {
                    if (screenshotLink != null) {
                        appendLine("[SCREENSHOT] Below is the latest screen image:")
                        appendLine(screenshotLink)
                    } else {
                        appendLine("No screenshot available for this step.")
                    }
                }.trim()

                val userMessage =
                        if (step == 1) {
                            buildString {
                                appendLine(intent)
                                appendLine()
                                appendLine(screenInfo)
                            }.trim()
                        } else {
                            buildString {
                                appendLine("** Screen Info **")
                                appendLine()
                                appendLine(screenInfo)
                            }.trim()
                        }

                history.add("user" to userMessage)

                val responseStream =
                        uiService.sendMessage(
                                message = userMessage,
                                chatHistory = history.toList(),
                                modelParameters = emptyList(),
                                enableThinking = false,
                                stream = true
                        )

                val contentBuilder = StringBuilder()
                responseStream.collect { chunk -> contentBuilder.append(chunk) }
                val fullResponse = contentBuilder.toString().trim()

                history.add("assistant" to fullResponse)

                val answer = extractTagContent(fullResponse, "answer") ?: fullResponse

                val parsed = parseAgentAction(answer)
                val logLine =
                        "step=$step, metadata=${parsed.metadata}, action=${parsed.actionName}, raw=${answer.replace("\n", " ").take(200)}"
                actionLogs.add(logLine)

                when (parsed.metadata) {
                    "finish" -> {
                        finished = true
                        finalMessage = parsed.fields["message"] ?: "Task finished."
                    }
                    "do" -> {
                        val execResult = executeAgentAction(parsed, screenWidth, screenHeight)
                        execResult.message?.let { msg ->
                            if (execResult.success) {
                                actionLogs.add("step=$step, info=$msg")
                            } else {
                                actionLogs.add("step=$step, error=$msg")
                            }
                        }
                        if (execResult.shouldFinish) {
                            finished = true
                            if (finalMessage == null) {
                                finalMessage = execResult.message
                            }
                        }
                    }
                    else -> {
                        // 未知格式，结束循环并返回错误信息
                        finished = true
                        finalMessage = "Unknown action format: ${parsed.metadata}"
                    }
                }

                AppLogger.d(
                        TAG,
                        "UI subagent step $step completed. metadata=${parsed.metadata}, action=${parsed.actionName}"
                )
                removeImagesFromLastUserMessage(history)
            }

            val success = finished
            val executionMessage = buildString {
                appendLine("UI automation subagent run summary:")
                appendLine("Intent: $intent")
                appendLine("Steps executed: $step / $maxSteps")
                appendLine("Finished: $finished")
                finalMessage?.let { appendLine("Final message: $it") }
                appendLine()
                appendLine("Action logs:")
                actionLogs.forEach { appendLine(it) }
            }

            val resultData =
                    AutomationExecutionResult(
                            functionName = "UIAutomationSubAgent",
                            providedParameters =
                                    mapOf(
                                            "intent" to intent,
                                            "max_steps" to maxSteps.toString()
                                    ),
                            executionSuccess = success,
                            executionMessage = executionMessage,
                            executionError = if (!success && finalMessage != null) finalMessage else null,
                            finalState = null,
                            executionSteps = step
                    )

            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error running UI subagent", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error running UI subagent: ${e.message}"
            )
        }
    }

    /**
     * Default screenshot implementation for the UI subagent.
     *
     * It captures the current screen to /sdcard/Download/Operit/cleanOnExit,
     * then registers the image in ImagePoolManager and returns a <link type="image" ...> tag.
     *
     * Subclasses can override this method if they have a more specialized screenshot pipeline.
     */
    protected open suspend fun captureScreenshotForAgent(): String? {
        return try {
            // Keep path consistent with automatic_ui_base.* so cleanup logic can be shared.
            val screenshotDir = File("/sdcard/Download/Operit/cleanOnExit")
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs()
            }

            val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
            val fileName = "ui_screenshot_${timestampFormat.format(Date())}.jpg"
            val file = File(screenshotDir, fileName)

            val floatingService = FloatingChatService.getInstance()
            try {
                // Temporarily hide the floating status indicator from screenshots (on main thread)
                withContext(Dispatchers.Main) {
                    floatingService?.setStatusIndicatorAlpha(0f)
                }
                // Give the system a brief moment to commit the alpha change to the compositor
                delay(50)
                val command = "screencap -p ${file.absolutePath}"
                val result = AndroidShellExecutor.executeShellCommand(command)
                if (!result.success) {
                    AppLogger.w(TAG, "captureScreenshotForAgent: screencap failed: ${result.stderr}")
                    return null
                }

                val imageId = ImagePoolManager.addImage(file.absolutePath)
                if (imageId == "error") {
                    AppLogger.e(TAG, "captureScreenshotForAgent: failed to register image: ${file.absolutePath}")
                    null
                } else {
                    "<link type=\"image\" id=\"$imageId\"></link>"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    floatingService?.setStatusIndicatorAlpha(1f)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "captureScreenshotForAgent failed", e)
            null
        }
    }

    private fun buildUiAutomationSystemPrompt(): String {
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        val datePart = sdf.format(Date())
        val weekdayNames =
                arrayOf(
                        "星期日",
                        "星期一",
                        "星期二",
                        "星期三",
                        "星期四",
                        "星期五",
                        "星期六"
                )
        val weekday = weekdayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        val formattedDate = "$datePart $weekday"
        return FunctionalPrompts.UI_AUTOMATION_AGENT_PROMPT.replace("{{current_date}}", formattedDate)
    }

    private data class ParsedAgentAction(
            val metadata: String,
            val actionName: String?,
            val fields: Map<String, String>
    )

    private data class ActionExecResult(
            val success: Boolean,
            val shouldFinish: Boolean,
            val message: String?
    )

    private fun parseRelativePoint(
            value: String,
            screenWidth: Int,
            screenHeight: Int
    ): Pair<Int, Int>? {
        val trimmed = value.trim().removePrefix("[").removeSuffix("]")
        val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val relX = parts[0].toIntOrNull() ?: return null
        val relY = parts[1].toIntOrNull() ?: return null
        val x = (relX / 1000.0 * screenWidth).toInt()
        val y = (relY / 1000.0 * screenHeight).toInt()
        return x to y
    }

    private fun resolveAppPackageName(app: String): String {
        val direct = APP_PACKAGES[app]
        if (direct != null) return direct

        val trimmed = app.trim()
        val trimmedMatch = APP_PACKAGES[trimmed]
        if (trimmedMatch != null) return trimmedMatch

        val lower = trimmed.lowercase(Locale.getDefault())
        val lowerMatch = APP_PACKAGES[lower]
        if (lowerMatch != null) return lowerMatch

        return trimmed
    }

    private fun removeImagesFromLastUserMessage(history: MutableList<Pair<String, String>>) {
        for (i in history.size - 1 downTo 0) {
            val (role, content) = history[i]
            if (role == "user") {
                if (ImageLinkParser.hasImageLinks(content)) {
                    val stripped = ImageLinkParser.removeImageLinks(content).trim()
                    history[i] = role to stripped
                }
                break
            }
        }
    }

    private suspend fun executeAgentAction(
            parsed: ParsedAgentAction,
            screenWidth: Int,
            screenHeight: Int
    ): ActionExecResult {
        if (parsed.metadata != "do") {
            return ActionExecResult(success = false, shouldFinish = false, message = "Unsupported metadata: ${parsed.metadata}")
        }

        val actionName = parsed.actionName ?: return ActionExecResult(
                success = false,
                shouldFinish = false,
                message = "Missing action name"
        )

        val fields = parsed.fields

        fun ok(shouldFinish: Boolean = false, message: String? = null) =
                ActionExecResult(success = true, shouldFinish = shouldFinish, message = message)
        fun fail(shouldFinish: Boolean = false, message: String) =
                ActionExecResult(success = false, shouldFinish = shouldFinish, message = message)

        return when (actionName) {
            "Launch" -> {
                val app = fields["app"]?.takeIf { it.isNotBlank() }
                        ?: return fail(message = "No app name specified for Launch")
                val packageName = resolveAppPackageName(app)
                try {
                    val systemTools = ToolGetter.getSystemOperationTools(context)
                    val startTool =
                            AITool(
                                    name = "start_app",
                                    parameters =
                                            listOf(
                                                    ToolParameter("package_name", packageName)
                                            )
                            )
                    val result = systemTools.startApp(startTool)
                    if (result.success) ok() else fail(message = result.error ?: "Failed to launch app: $packageName (input: $app)")
                } catch (e: Exception) {
                    fail(message = "Exception while launching app $packageName from input $app: ${e.message}")
                }
            }
            "Tap" -> {
                val element = fields["element"]?.takeIf { it.isNotBlank() }
                        ?: return fail(message = "No element coordinates for Tap")
                val (x, y) =
                        parseRelativePoint(element, screenWidth, screenHeight)
                                ?: return fail(message = "Invalid element coordinates for Tap: $element")
                val tapTool =
                        AITool(
                                name = "tap",
                                parameters =
                                        listOf(
                                                ToolParameter("x", x.toString()),
                                                ToolParameter("y", y.toString())
                                        )
                        )
                val result = tap(tapTool)
                if (result.success) ok() else fail(message = result.error ?: "Tap failed at ($x,$y)")
            }
            "LongPress" -> {
                val element = fields["element"]?.takeIf { it.isNotBlank() }
                        ?: return fail(message = "No element coordinates for LongPress")
                val (x, y) =
                        parseRelativePoint(element, screenWidth, screenHeight)
                                ?: return fail(message = "Invalid element coordinates for LongPress: $element")
                val longPressTool =
                        AITool(
                                name = "long_press",
                                parameters =
                                        listOf(
                                                ToolParameter("x", x.toString()),
                                                ToolParameter("y", y.toString())
                                        )
                        )
                val result = longPress(longPressTool)
                if (result.success) ok() else fail(message = result.error ?: "Long press failed at ($x,$y)")
            }
            "Type", "Type_Name" -> {
                val text = fields["text"] ?: ""
                val setTextTool =
                        AITool(
                                name = "set_input_text",
                                parameters = listOf(ToolParameter("text", text))
                        )
                val result = setInputText(setTextTool)
                if (result.success) ok() else fail(message = result.error ?: "Set input text failed")
            }
            "Swipe" -> {
                val start = fields["start"]?.takeIf { it.isNotBlank() }
                        ?: return fail(message = "Missing swipe start coordinates")
                val end = fields["end"]?.takeIf { it.isNotBlank() }
                        ?: return fail(message = "Missing swipe end coordinates")
                val (sx, sy) =
                        parseRelativePoint(start, screenWidth, screenHeight)
                                ?: return fail(message = "Invalid swipe start coordinates: $start")
                val (ex, ey) =
                        parseRelativePoint(end, screenWidth, screenHeight)
                                ?: return fail(message = "Invalid swipe end coordinates: $end")
                val swipeTool =
                        AITool(
                                name = "swipe",
                                parameters =
                                        listOf(
                                                ToolParameter("start_x", sx.toString()),
                                                ToolParameter("start_y", sy.toString()),
                                                ToolParameter("end_x", ex.toString()),
                                                ToolParameter("end_y", ey.toString())
                                        )
                        )
                val result = swipe(swipeTool)
                if (result.success) ok() else fail(message = result.error ?: "Swipe failed")
            }
            "List" -> {
                try {
                    val systemTools = ToolGetter.getSystemOperationTools(context)
                    val listTool =
                            AITool(
                                    name = "list_installed_apps",
                                    parameters =
                                            listOf(
                                                    ToolParameter("include_system_apps", "false")
                                            )
                            )
                    val result = systemTools.listInstalledApps(listTool)
                    if (result.success && result.result is AppListData) {
                        val apps = result.result.packages
                        val preview =
                                if (apps.isEmpty()) "(no apps found)"
                                else apps.take(20).joinToString("; ")
                        ok(message = "Installed apps (partial): $preview")
                    } else {
                        fail(message = result.error ?: "Failed to list installed apps")
                    }
                } catch (e: Exception) {
                    fail(message = "Exception while listing installed apps: ${e.message}")
                }
            }
            "Back" -> {
                val pressTool =
                        AITool(
                                name = "press_key",
                                parameters = listOf(ToolParameter("key_code", "KEYCODE_BACK"))
                        )
                val result = pressKey(pressTool)
                if (result.success) ok() else fail(message = result.error ?: "Back key failed")
            }
            "Home" -> {
                val pressTool =
                        AITool(
                                name = "press_key",
                                parameters = listOf(ToolParameter("key_code", "KEYCODE_HOME"))
                        )
                val result = pressKey(pressTool)
                if (result.success) ok() else fail(message = result.error ?: "Home key failed")
            }
            "Double Tap" -> {
                val element = fields["element"]?.takeIf { it.isNotBlank() }
                        ?: return fail(message = "No element coordinates for Double Tap")
                val (x, y) =
                        parseRelativePoint(element, screenWidth, screenHeight)
                                ?: return fail(message = "Invalid element coordinates for Double Tap: $element")
                val tapTool =
                        AITool(
                                name = "tap",
                                parameters =
                                        listOf(
                                                ToolParameter("x", x.toString()),
                                                ToolParameter("y", y.toString())
                                        )
                        )
                val first = tap(tapTool)
                delay(120)
                val second = tap(tapTool)
                if (first.success && second.success) ok()
                else fail(message = (first.error ?: second.error) ?: "Double tap failed at ($x,$y)")
            }
            "Long Press" -> {
                val element = fields["element"]?.takeIf { it.isNotBlank() }
                        ?: return fail(message = "No element coordinates for Long Press")
                val (x, y) =
                        parseRelativePoint(element, screenWidth, screenHeight)
                                ?: return fail(message = "Invalid element coordinates for Long Press: $element")
                // 通过起点终点相同且较长duration的滑动来模拟长按
                val longPressTool =
                        AITool(
                                name = "long_press",
                                parameters =
                                        listOf(
                                                ToolParameter("x", x.toString()),
                                                ToolParameter("y", y.toString())
                                        )
                        )
                val result = longPress(longPressTool)
                if (result.success) ok() else fail(message = result.error ?: "Long press failed at ($x,$y)")
            }
            "Wait" -> {
                val durationStr = fields["duration"] ?: "1 seconds"
                val seconds =
                        try {
                            durationStr.replace("seconds", "").trim().toDouble()
                        } catch (e: Exception) {
                            1.0
                        }
                val millis = (seconds * 1000).toLong().coerceAtLeast(0L)
                delay(millis)
                ok()
            }
            "Take_over" -> {
                val message = fields["message"] ?: "User takeover required"
                ok(shouldFinish = true, message = message)
            }
            "Note" -> {
                // 记笔记类动作，仅用于记录，不改变UI
                ok()
            }
            "Call_API" -> {
                // 目前不接外部API，只记录动作
                ok()
            }
            "Interact" -> {
                val message = fields["message"] ?: "User interaction required"
                ok(shouldFinish = true, message = message)
            }
            else -> fail(message = "Unknown action: $actionName")
        }
    }

    private fun parseAgentAction(raw: String): ParsedAgentAction {
        val original = raw.trim()

        // 允许模型在指令前后添加自然语言，只要包含形如 do(...) 或 finish(...) 的指令即可
        // 当同时存在多个指令时，优先使用最后一个（通常是当前真正要执行的动作）
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
            val messageRegex =
                    Regex("""finish\s*\(\s*message\s*=\s*\"(.*)\"\s*\)""", RegexOption.DOT_MATCHES_ALL)
            val match = messageRegex.find(trimmed)
            val message = match?.groupValues?.getOrNull(1) ?: ""
            return ParsedAgentAction(
                    metadata = "finish",
                    actionName = null,
                    fields = mapOf("message" to message)
            )
        }

        if (!trimmed.startsWith("do")) {
            return ParsedAgentAction(metadata = "unknown", actionName = null, fields = emptyMap())
        }

        val inner =
                trimmed.removePrefix("do").trim().let { s ->
                    if (s.startsWith("(" ) && s.endsWith(")")) s.substring(1, s.length - 1) else s
                }

        val fields = mutableMapOf<String, String>()
        var current = StringBuilder()
        var bracketDepth = 0
        var inQuotes = false
        var quoteChar = '\"'

        fun flushPart() {
            if (current.isNotBlank()) {
                val part = current.toString().trim()
                val idx = part.indexOf('=')
                if (idx > 0) {
                    val key = part.substring(0, idx).trim()
                    val value = part.substring(idx + 1).trim().trim('"', '\'')
                    fields[key] = value
                }
                current = StringBuilder()
            }
        }

        inner.forEach { ch ->
            when (ch) {
                '"', '\'' -> {
                    if (!inQuotes) {
                        inQuotes = true
                        quoteChar = ch
                    } else if (quoteChar == ch) {
                        inQuotes = false
                    }
                    current.append(ch)
                }
                '[' -> {
                    if (!inQuotes) bracketDepth++
                    current.append(ch)
                }
                ']' -> {
                    if (!inQuotes && bracketDepth > 0) bracketDepth--
                    current.append(ch)
                }
                ',' -> {
                    if (!inQuotes && bracketDepth == 0) {
                        flushPart()
                    } else {
                        current.append(ch)
                    }
                }
                else -> current.append(ch)
            }
        }
        flushPart()

        val actionName = fields["action"]
        return ParsedAgentAction(metadata = "do", actionName = actionName, fields = fields)
    }

    private fun extractTagContent(text: String, tag: String): String? {
        val pattern = Regex("""<$tag>(.*?)</$tag>""", RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(text)
        return match?.groupValues?.getOrNull(1)?.trim()
    }


    // 保留一些实用的辅助方法，供子类使用

    /** Helper method to extract attribute values from node text */
    protected fun extractAttribute(nodeText: String, attributeName: String): String {
        val pattern = "$attributeName=\"(.*?)\"".toRegex()
        val matchResult = pattern.find(nodeText)
        return matchResult?.groupValues?.get(1) ?: ""
    }
}

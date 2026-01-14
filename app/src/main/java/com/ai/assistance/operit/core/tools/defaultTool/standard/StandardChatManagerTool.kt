package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.core.tools.ChatCreationResultData
import com.ai.assistance.operit.core.tools.ChatListResultData
import com.ai.assistance.operit.core.tools.ChatServiceStartResultData
import com.ai.assistance.operit.core.tools.ChatSwitchResultData
import com.ai.assistance.operit.core.tools.MessageSendResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.services.ChatServiceCore
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 对话管理工具
 * 通过绑定 FloatingChatService 来管理对话，实现创建、切换、列出对话和发送消息等功能
 */
class StandardChatManagerTool(private val context: Context) {

    companion object {
        private const val TAG = "StandardChatManagerTool"
        private const val SERVICE_CONNECTION_TIMEOUT = 15000L // 15秒超时
        private const val RESPONSE_STREAM_ACQUIRE_TIMEOUT = 5000L
        private const val AI_RESPONSE_TIMEOUT = 300000L
    }

    private val appContext = context.applicationContext

    // Service 连接状态
    private var chatCore: ChatServiceCore? = null
    private var floatingService: FloatingChatService? = null
    private var isBound = false
    private var connectionDeferred = CompletableDeferred<Boolean>().apply { complete(false) }

    // Service 连接回调
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            AppLogger.d(TAG, "Service connected")
            val binder = service as? FloatingChatService.LocalBinder
            if (binder != null) {
                floatingService = binder.getService()
                chatCore = binder.getChatCore()
                isBound = true
                binder.setCloseCallback {
                    AppLogger.d(TAG, "Received close callback from FloatingChatService")
                    unbindService()
                }
                if (!connectionDeferred.isCompleted) {
                    connectionDeferred.complete(true)
                }
                AppLogger.d(TAG, "ChatServiceCore obtained successfully")
            } else {
                AppLogger.e(TAG, "Failed to cast binder")
                if (!connectionDeferred.isCompleted) {
                    connectionDeferred.complete(false)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            AppLogger.d(TAG, "Service disconnected")
            chatCore = null
            floatingService = null
            isBound = false
            if (!connectionDeferred.isCompleted) {
                connectionDeferred.complete(false)
            }
        }
    }

    /**
     * 确保服务已连接
     * @return 是否成功连接
     */
    private suspend fun ensureServiceConnected(startIntent: Intent? = null): Boolean {
        // 如果已经连接，直接返回
        if (isBound && chatCore != null) {
            if (startIntent != null) {
                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(startIntent)
                    } else {
                        appContext.startService(startIntent)
                    }
                }
            }
            return true
        }

        val prefs = appContext.getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("service_disabled_due_to_crashes", false)) {
            AppLogger.w(TAG, "FloatingChatService is disabled due to frequent crashes")
            if (!connectionDeferred.isCompleted) {
                connectionDeferred.complete(false)
            }
            return false
        }

        // 如果正在连接中，等待连接完成
        if (!connectionDeferred.isCompleted) {
            return try {
                withTimeout(SERVICE_CONNECTION_TIMEOUT) {
                    connectionDeferred.await()
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Service connection timeout", e)
                if (!connectionDeferred.isCompleted) {
                    connectionDeferred.complete(false)
                }
                false
            }
        }

        // 重新启动和绑定服务
        return try {
            // 重置 deferred
            connectionDeferred = CompletableDeferred()
            
            val intent = startIntent ?: Intent(appContext, FloatingChatService::class.java)

            val bound =
                withContext(Dispatchers.Main) {
                    // 启动服务
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(intent)
                    } else {
                        appContext.startService(intent)
                    }

                    // 绑定服务
                    appContext.bindService(
                        intent,
                        serviceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                }
            
            if (!bound) {
                AppLogger.e(TAG, "Failed to bind service")
                connectionDeferred.complete(false)
                return false
            }

            // 等待连接完成
            withTimeout(SERVICE_CONNECTION_TIMEOUT) {
                connectionDeferred.await()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to ensure service connected", e)
            connectionDeferred.completeExceptionally(e)
            false
        }
    }

    /**
     * 解绑服务
     */
    fun unbindService() {
        if (isBound) {
            try {
                appContext.unbindService(serviceConnection)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error unbinding service", e)
            }
        }

        isBound = false
        chatCore = null
        floatingService = null
        connectionDeferred = CompletableDeferred<Boolean>().apply { complete(false) }
        AppLogger.d(TAG, "Service unbound")
    }

    /**
     * 启动对话服务
     */
    suspend fun startChatService(tool: AITool): ToolResult {
        return try {
            val initialModeParam = tool.parameters.find { it.name == "initial_mode" }?.value?.trim()
            val autoEnterVoiceChatParam =
                tool.parameters.find { it.name == "auto_enter_voice_chat" }?.value?.trim()
            val wakeLaunchedParam = tool.parameters.find { it.name == "wake_launched" }?.value?.trim()
            val timeoutMsParam = tool.parameters.find { it.name == "timeout_ms" }?.value?.trim()

            val initialMode =
                initialModeParam
                    ?.takeIf { it.isNotBlank() }
                    ?.let { raw ->
                        runCatching { FloatingMode.valueOf(raw.uppercase()) }.getOrNull()
                    }
            if (initialModeParam != null && initialModeParam.isNotBlank() && initialMode == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "参数错误：initial_mode 无效：$initialModeParam"
                )
            }

            fun parseBooleanOrNull(value: String?): Boolean? {
                return when (value?.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            }

            val autoEnterVoiceChat = parseBooleanOrNull(autoEnterVoiceChatParam)
            if (autoEnterVoiceChatParam != null && autoEnterVoiceChat == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "参数错误：auto_enter_voice_chat 必须是 true/false"
                )
            }

            val wakeLaunched = parseBooleanOrNull(wakeLaunchedParam)
            if (wakeLaunchedParam != null && wakeLaunched == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "参数错误：wake_launched 必须是 true/false"
                )
            }

            val timeoutMs = timeoutMsParam?.takeIf { it.isNotBlank() }?.toLongOrNull()
            if (timeoutMsParam != null && timeoutMsParam.isNotBlank() && timeoutMs == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "参数错误：timeout_ms 必须是整数（毫秒）"
                )
            }

            val intent = Intent(appContext, FloatingChatService::class.java)
            if (initialMode != null) {
                intent.putExtra("INITIAL_MODE", initialMode.name)
            }
            if (autoEnterVoiceChat == true) {
                intent.putExtra(FloatingChatService.EXTRA_AUTO_ENTER_VOICE_CHAT, true)
            }
            if (wakeLaunched != null) {
                intent.putExtra(FloatingChatService.EXTRA_WAKE_LAUNCHED, wakeLaunched)
            }
            if (timeoutMs != null) {
                intent.putExtra(FloatingChatService.EXTRA_AUTO_EXIT_AFTER_MS, timeoutMs)
            }

            val connected = ensureServiceConnected(intent)
            
            if (connected) {
                try {
                    floatingService?.setFloatingWindowVisible(true)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to set floating window visible", e)
                }
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatServiceStartResultData(isConnected = true)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatServiceStartResultData(isConnected = false),
                    error = "对话服务启动失败或连接超时"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start chat service", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatServiceStartResultData(isConnected = false),
                error = "启动对话服务时发生错误: ${e.message}"
            )
        }
    }

    suspend fun stopChatService(tool: AITool): ToolResult {
        return try {
            try {
                floatingService?.setFloatingWindowVisible(false)
            } catch (_: Exception) {
            }

            unbindService()

            val intent = Intent(appContext, FloatingChatService::class.java)
            val stopped = runCatching { appContext.stopService(intent) }.getOrDefault(false)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(if (stopped) "已停止对话服务" else "已请求停止对话服务")
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to stop chat service", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "停止对话服务时发生错误: ${e.message}"
            )
        }
    }

    /**
     * 创建新的对话
     */
    suspend fun createNewChat(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatCreationResultData(chatId = ""),
                    error = "服务未连接"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatCreationResultData(chatId = ""),
                error = "ChatServiceCore 未初始化"
            )

            // 获取创建前的 chatId
            val oldChatId = core.currentChatId.value

            val group = tool.parameters.find { it.name == "group" }?.value?.trim()
            val effectiveGroup = group?.takeIf { it.isNotBlank() }
            
            // 创建新对话
            core.createNewChat(group = effectiveGroup)

            val newChatId = try {
                withTimeout(5000L) {
                    var id = core.currentChatId.value
                    while (id == null || id == oldChatId) {
                        delay(50)
                        id = core.currentChatId.value
                    }
                    id
                }
            } catch (_: TimeoutCancellationException) {
                null
            }

            if (newChatId != null) {
                // 等待新对话出现在列表中（尽量保证后续节点能在新对话上下文中运行）
                try {
                    withTimeout(2000L) {
                        core.chatHistories.first { histories ->
                            histories.any { it.id == newChatId }
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                }

                // 兜底：如果当前对话ID仍然不是新对话，则强制切换一次
                if (core.currentChatId.value != newChatId) {
                    core.switchChat(newChatId)
                    try {
                        withTimeout(2000L) {
                            core.currentChatId.first { it == newChatId }
                        }
                    } catch (_: TimeoutCancellationException) {
                    }
                }

                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatCreationResultData(chatId = newChatId)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatCreationResultData(chatId = ""),
                    error = "创建对话失败，未能获取新的对话ID"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create new chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatCreationResultData(chatId = ""),
                error = "创建对话时发生错误: ${e.message}"
            )
        }
    }

    /**
     * 列出所有对话
     */
    suspend fun listChats(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatListResultData(
                        totalCount = 0,
                        currentChatId = null,
                        chats = emptyList()
                    ),
                    error = "服务未连接"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatListResultData(
                    totalCount = 0,
                    currentChatId = null,
                    chats = emptyList()
                ),
                error = "ChatServiceCore 未初始化"
            )

            val chatHistories = core.chatHistories.value
            val currentChatId = core.currentChatId.value
            
            // 构建对话信息列表
            val chatInfoList = chatHistories.map { chat ->
                ChatListResultData.ChatInfo(
                    id = chat.id,
                    title = chat.title,
                    messageCount = chat.messages.size,
                    createdAt = chat.createdAt.toString(),
                    updatedAt = chat.updatedAt.toString(),
                    isCurrent = chat.id == currentChatId,
                    inputTokens = chat.inputTokens,
                    outputTokens = chat.outputTokens
                )
            }
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = ChatListResultData(
                    totalCount = chatHistories.size,
                    currentChatId = currentChatId,
                    chats = chatInfoList
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to list chats", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatListResultData(
                    totalCount = 0,
                    currentChatId = null,
                    chats = emptyList()
                ),
                error = "列出对话时发生错误: ${e.message}"
            )
        }
    }

    /**
     * 切换对话
     */
    suspend fun switchChat(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                    error = "服务未连接"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                error = "ChatServiceCore 未初始化"
            )

            val chatId = tool.parameters.find { it.name == "chat_id" }?.value
            if (chatId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                    error = "参数错误：缺少 chat_id"
                )
            }

            // 检查对话是否存在并获取标题
            val targetChat = core.chatHistories.value.find { it.id == chatId }
            if (targetChat == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = chatId, chatTitle = ""),
                    error = "对话不存在：$chatId"
                )
            }

            // 切换对话
            core.switchChat(chatId)
            
            // 等待切换完成（最多等待1秒）
            var attempts = 0
            while (attempts < 10 && core.currentChatId.value != chatId) {
                delay(100)
                attempts++
            }
            
            if (core.currentChatId.value == chatId) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = ChatSwitchResultData(
                        chatId = chatId,
                        chatTitle = targetChat.title
                    )
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = ChatSwitchResultData(chatId = chatId, chatTitle = targetChat.title),
                    error = "切换对话失败，当前对话ID未更新"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to switch chat", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = ChatSwitchResultData(chatId = "", chatTitle = ""),
                error = "切换对话时发生错误: ${e.message}"
            )
        }
    }

    /**
     * 向AI发送消息
     */
    suspend fun sendMessageToAI(tool: AITool): ToolResult {
        return try {
            if (!ensureServiceConnected()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = ""),
                    error = "服务未连接"
                )
            }

            val core = chatCore ?: return ToolResult(
                toolName = tool.name,
                success = false,
                result = MessageSendResultData(chatId = "", message = ""),
                error = "ChatServiceCore 未初始化"
            )

            val message = tool.parameters.find { it.name == "message" }?.value
            if (message.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = ""),
                    error = "参数错误：缺少 message"
                )
            }

            // 可选的 chat_id 参数
            val targetChatId = tool.parameters.find { it.name == "chat_id" }?.value
            
            // 如果指定了 chat_id，先切换到该对话
            if (!targetChatId.isNullOrBlank()) {
                val chatExists = core.chatHistories.value.any { it.id == targetChatId }
                if (!chatExists) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = MessageSendResultData(chatId = targetChatId, message = message),
                        error = "指定的对话不存在：$targetChatId"
                    )
                }
                
                // 切换到目标对话
                if (core.currentChatId.value != targetChatId) {
                    core.switchChat(targetChatId)
                    var attempts = 0
                    while (attempts < 20 && core.currentChatId.value != targetChatId) {
                        delay(100)
                        attempts++
                    }
                }
            } else {
                // 如果没有当前对话，创建一个新对话
                if (core.currentChatId.value == null) {
                    core.createNewChat()
                    var attempts = 0
                    while (attempts < 20 && core.currentChatId.value == null) {
                        delay(100)
                        attempts++
                    }
                }
            }

            val currentChatId = core.currentChatId.value
            if (currentChatId == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = "", message = message),
                    error = "无法获取当前对话ID"
                )
            }

            try {
                withTimeout(300000L) {
                    core.activeStreamingChatIds.first { activeChatIds ->
                        !activeChatIds.contains(currentChatId)
                    }
                }
            } catch (e: TimeoutCancellationException) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = currentChatId, message = message),
                    error = "上一条消息仍在处理中"
                )
            }

            // 设置消息文本
            core.updateUserMessage(message)

            // 发送消息（包含总结逻辑）
            core.sendUserMessage(PromptFunctionType.CHAT)

            val chatId = core.currentChatId.value

            if (chatId == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = currentChatId, message = message),
                    error = "无法获取当前对话ID"
                )
            }

            val responseStream: SharedStream<String> = try {
                var stream: SharedStream<String>? = core.getResponseStream(chatId)
                withTimeout(RESPONSE_STREAM_ACQUIRE_TIMEOUT) {
                    while (stream == null) {
                        val state = core.inputProcessingStateByChatId.value[chatId] ?: InputProcessingState.Idle
                        if (state is InputProcessingState.Error) {
                            throw IllegalStateException(state.message)
                        }
                        delay(50)
                        stream = core.getResponseStream(chatId)
                    }
                }
                requireNotNull(stream)
            } catch (e: TimeoutCancellationException) {
                runCatching { core.cancelMessage(chatId) }
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = currentChatId, message = message),
                    error = "等待AI响应超时"
                )
            }

            val aiResponse = try {
                withTimeout(AI_RESPONSE_TIMEOUT) {
                    val sb = StringBuilder()
                    responseStream.collect { chunk: String ->
                        sb.append(chunk)
                    }
                    sb.toString()
                }
            } catch (e: TimeoutCancellationException) {
                runCatching { core.cancelMessage(chatId) }
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(chatId = currentChatId, message = message),
                    error = "等待AI回复超时"
                )
            }

            val finalState =
                core.inputProcessingStateByChatId.value[chatId] ?: InputProcessingState.Idle
            if (finalState is InputProcessingState.Error) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = MessageSendResultData(
                        chatId = currentChatId,
                        message = message,
                        aiResponse = aiResponse,
                        receivedAt = System.currentTimeMillis()
                    ),
                    error = finalState.message
                )
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = MessageSendResultData(
                    chatId = currentChatId,
                    message = message,
                    aiResponse = aiResponse,
                    receivedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send message", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = MessageSendResultData(chatId = "", message = ""),
                error = "发送消息时发生错误: ${e.message}"
            )
        }
    }
}


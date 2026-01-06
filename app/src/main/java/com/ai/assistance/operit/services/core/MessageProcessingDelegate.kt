package com.ai.assistance.operit.services.core

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.tools.agent.PhoneAgentJobRegistry
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.model.InputProcessingState as EnhancedInputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.util.NetworkUtils
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.share
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.WaifuPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceBackupManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.core.tools.ToolProgressBus
import java.util.concurrent.ConcurrentHashMap

/** 委托类，负责处理消息处理相关功能 */
class MessageProcessingDelegate(
        private val context: Context,
        private val coroutineScope: CoroutineScope,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val getChatHistory: () -> List<ChatMessage>,
        private val addMessageToChat: suspend (String, ChatMessage) -> Unit,
        private val saveCurrentChat: () -> Unit,
        private val showErrorMessage: (String) -> Unit,
        private val updateChatTitle: (chatId: String, title: String) -> Unit,
        private val onTurnComplete: (chatId: String?, service: EnhancedAIService) -> Unit,
        private val onTokenLimitExceeded: suspend () -> Unit, // 新增：Token超限回调
        // 添加自动朗读相关的回调
        private val getIsAutoReadEnabled: () -> Boolean,
        private val speakMessage: (String) -> Unit
) {
    companion object {
        private const val TAG = "MessageProcessingDelegate"
    }

    // 角色卡管理器
    private val characterCardManager = CharacterCardManager.getInstance(context)
    
    // 模型配置管理器
    private val modelConfigManager = ModelConfigManager(context)
    
    // 功能配置管理器，用于获取正确的模型配置ID
    private val functionalConfigManager = FunctionalConfigManager(context)

    private val _userMessage = MutableStateFlow(TextFieldValue(""))
    val userMessage: StateFlow<TextFieldValue> = _userMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _inputProcessingState = MutableStateFlow<EnhancedInputProcessingState>(EnhancedInputProcessingState.Idle)
    val inputProcessingState: StateFlow<EnhancedInputProcessingState> = _inputProcessingState.asStateFlow()

    // 记录当前进行中的会话ID，用于在UI层进行按会话隔离的“停止/发送”按钮显示
    private val _activeStreamingChatId = MutableStateFlow<String?>(null)
    val activeStreamingChatId: StateFlow<String?> = _activeStreamingChatId.asStateFlow()

    private val _activeStreamingChatIds = MutableStateFlow<Set<String>>(emptySet())
    val activeStreamingChatIds: StateFlow<Set<String>> = _activeStreamingChatIds.asStateFlow()

    private val _inputProcessingStateByChatId =
        MutableStateFlow<Map<String, EnhancedInputProcessingState>>(emptyMap())
    val inputProcessingStateByChatId: StateFlow<Map<String, EnhancedInputProcessingState>> =
        _inputProcessingStateByChatId.asStateFlow()

    private val _scrollToBottomEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToBottomEvent = _scrollToBottomEvent.asSharedFlow()

    private val _nonFatalErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val nonFatalErrorEvent = _nonFatalErrorEvent.asSharedFlow()

    // 当前活跃的AI响应流
    private data class ChatRuntime(
        var responseStream: SharedStream<String>? = null,
        var streamCollectionJob: Job? = null,
        var stateCollectionJob: Job? = null,
        val isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    )

    private val chatRuntimes = ConcurrentHashMap<String, ChatRuntime>()

    private fun chatKey(chatId: String?): String = chatId ?: "__DEFAULT_CHAT__"

    private fun runtimeFor(chatId: String?): ChatRuntime {
        val key = chatKey(chatId)
        return chatRuntimes[key] ?: ChatRuntime().also { chatRuntimes[key] = it }
    }

    private fun updateGlobalLoadingState() {
        val anyLoading = chatRuntimes.values.any { it.isLoading.value }
        val activeChatIds = chatRuntimes
            .filter { (_, runtime) -> runtime.isLoading.value }
            .keys
            .filter { it != "__DEFAULT_CHAT__" }
            .toSet()

        _activeStreamingChatIds.value = activeChatIds
        _isLoading.value = anyLoading
    }

    private fun setChatInputProcessingState(chatId: String?, state: EnhancedInputProcessingState) {
        val key = chatKey(chatId)
        val map = _inputProcessingStateByChatId.value.toMutableMap()
        map[key] = state
        _inputProcessingStateByChatId.value = map
        if (_activeStreamingChatId.value == chatId) {
            _inputProcessingState.value = state
        }
    }

    // 获取当前活跃的AI响应流
    fun getCurrentResponseStream(): SharedStream<String>? = getResponseStream(_activeStreamingChatId.value)

    fun getResponseStream(chatId: String?): SharedStream<String>? {
        return chatRuntimes[chatKey(chatId)]?.responseStream
    }

    fun cancelMessage(chatId: String) {
        coroutineScope.launch {
            val chatRuntime = runtimeFor(chatId)

            setChatInputProcessingState(chatId, EnhancedInputProcessingState.Idle)

            val isActive = _activeStreamingChatId.value == chatId
            if (isActive) {
                _activeStreamingChatId.value = null
            }

            chatRuntime.streamCollectionJob?.cancel()
            chatRuntime.streamCollectionJob = null
            chatRuntime.stateCollectionJob?.cancel()
            chatRuntime.stateCollectionJob = null
            chatRuntime.isLoading.value = false
            chatRuntime.responseStream = null
            updateGlobalLoadingState()

            withContext(Dispatchers.IO) {
                AIMessageManager.cancelOperation(chatId)
                saveCurrentChat()
            }
        }
    }

    init {
        AppLogger.d(TAG, "MessageProcessingDelegate初始化: 创建滚动事件流")
    }

    fun updateUserMessage(message: String) {
        _userMessage.value = TextFieldValue(message)
    }

    fun updateUserMessage(value: TextFieldValue) {
        _userMessage.value = value
    }

    fun scrollToBottom() {
        _scrollToBottomEvent.tryEmit(Unit)
    }

    fun sendUserMessage(
            attachments: List<AttachmentInfo> = emptyList(),
            chatId: String? = null,
            workspacePath: String? = null,
            promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
            enableThinking: Boolean = false,
            thinkingGuidance: Boolean = false,
            enableMemoryQuery: Boolean = true, // 新增参数
            enableWorkspaceAttachment: Boolean = false, // 新增工作区附着参数
            maxTokens: Int,
            tokenUsageThreshold: Double,
            replyToMessage: ChatMessage? = null, // 新增回复消息参数
            isAutoContinuation: Boolean = false, // 标识是否为自动续写
            enableSummary: Boolean = true
    ) {
        if (_userMessage.value.text.isBlank() && attachments.isEmpty() && !isAutoContinuation) return
        val chatRuntime = runtimeFor(chatId)
        if (chatRuntime.isLoading.value) {
            return
        }

        val originalMessageText = _userMessage.value.text.trim()
        var messageText = originalMessageText
        
        _userMessage.value = TextFieldValue("")
        chatRuntime.isLoading.value = true
        updateGlobalLoadingState()
        _activeStreamingChatId.value = chatId
        setChatInputProcessingState(chatId, EnhancedInputProcessingState.Processing("正在处理消息..."))

        coroutineScope.launch(Dispatchers.IO) {
            // 检查这是否是聊天中的第一条用户消息（忽略AI的开场白）
            val isFirstMessage = getChatHistory().none { it.sender == "user" }
            if (isFirstMessage && chatId != null) {
                val newTitle =
                    when {
                        originalMessageText.isNotBlank() -> originalMessageText
                        attachments.isNotEmpty() -> attachments.first().fileName
                        else -> context.getString(R.string.new_conversation)
                    }
                updateChatTitle(chatId, newTitle)
            }

            AppLogger.d(TAG, "开始处理用户消息：附件数量=${attachments.size}")

            // 获取当前模型配置以检查是否启用直接图片处理
            // 聊天功能直接使用CHAT类型的配置
            val configId = functionalConfigManager.getConfigIdForFunction(FunctionType.CHAT)
            val currentModelConfig = modelConfigManager.getModelConfigFlow(configId).first()
            val enableDirectImageProcessing = currentModelConfig.enableDirectImageProcessing
            val enableDirectAudioProcessing = currentModelConfig.enableDirectAudioProcessing
            val enableDirectVideoProcessing = currentModelConfig.enableDirectVideoProcessing
            AppLogger.d(TAG, "直接图片处理状态: $enableDirectImageProcessing (配置ID: $configId)")

            // 1. 使用 AIMessageManager 构建最终消息
            val finalMessageContent = AIMessageManager.buildUserMessageContent(
                messageText,
                attachments,
                enableMemoryQuery,
                enableWorkspaceAttachment,
                workspacePath,
                replyToMessage,
                enableDirectImageProcessing,
                enableDirectAudioProcessing,
                enableDirectVideoProcessing
            )

            // 自动继续且原本消息为空时，不添加到聊天历史（虽然会发送"继续"给AI）
            val shouldAddUserMessageToChat =
                !(isAutoContinuation &&
                        originalMessageText.isBlank() &&
                        attachments.isEmpty())
            var userMessageAdded = false
            val userMessage = ChatMessage(
                sender = "user",
                content = finalMessageContent,
                roleName = "用户" // 用户消息的角色名固定为"用户"
            )

            // 在发送消息前，同步工作区状态
            if (!workspacePath.isNullOrBlank()) {
                try {
                    AppLogger.d(TAG, "Syncing workspace state for timestamp ${userMessage.timestamp}")
                    WorkspaceBackupManager.getInstance(context).syncState(workspacePath, userMessage.timestamp)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Workspace sync failed", e)
                    // 报告一个非致命错误，不会中断消息流程
                    _nonFatalErrorEvent.emit("工作区状态同步失败: ${e.message}")
                }
            }

            if (shouldAddUserMessageToChat && chatId != null) {
                // 等待消息添加到聊天历史完成，确保getChatHistory()包含新消息
                addMessageToChat(chatId, userMessage)
                userMessageAdded = true
            }

            lateinit var aiMessage: ChatMessage
            val activeChatId = chatId
            try {
                // if (!NetworkUtils.isNetworkAvailable(context)) {
                //     withContext(Dispatchers.Main) { showErrorMessage("网络连接不可用") }
                //     _isLoading.value = false
                //     _inputProcessingState.value = EnhancedInputProcessingState.Idle
                //     return@launch
                // }

                val service =
                    (activeChatId?.let { EnhancedAIService.getChatInstance(context, it) }
                        ?: getEnhancedAiService())
                        ?: run {
                            withContext(Dispatchers.Main) { showErrorMessage("AI服务未初始化") }
                            chatRuntime.isLoading.value = false
                            updateGlobalLoadingState()
                            _inputProcessingState.value = EnhancedInputProcessingState.Idle
                            return@launch
                        }

                // 监听此 chat 对应的 EnhancedAIService 状态，映射到 per-chat state
                chatRuntime.stateCollectionJob?.cancel()
                chatRuntime.stateCollectionJob =
                    coroutineScope.launch {
                        service.inputProcessingState.collect { state ->
                            setChatInputProcessingState(activeChatId, state)
                        }
                    }

                val startTime = System.currentTimeMillis()
                val deferred = CompletableDeferred<Unit>()

                // 获取角色信息用于通知
                val (characterName, avatarUri) = try {
                    val activeCard = characterCardManager.activeCharacterCardFlow.first()
                    val userPreferencesManager = UserPreferencesManager.getInstance(context)
                    val avatar = userPreferencesManager.getAiAvatarForCharacterCardFlow(activeCard.id).first()
                    Pair(activeCard.name, avatar)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "获取角色信息失败: ${e.message}", e)
                    Pair(null, null)
                }

                val chatHistory = getChatHistory()

                // 根据enableSummary控制Token阈值检查和Token超限回调
                val effectiveMaxTokens = if (enableSummary) maxTokens else 0
                val effectiveTokenUsageThreshold = if (enableSummary) tokenUsageThreshold else Double.POSITIVE_INFINITY
                val effectiveOnTokenLimitExceeded = if (enableSummary) onTokenLimitExceeded else null

                // 2. 使用 AIMessageManager 发送消息
                val responseStream = AIMessageManager.sendMessage(
                    enhancedAiService = service,
                    chatId = activeChatId,
                    messageContent = finalMessageContent,
                    //现在chatHistory 100%包含最新的用户输入，所以可以截掉
                    chatHistory = if (userMessageAdded && chatHistory.isNotEmpty()) {
                        chatHistory.subList(0, chatHistory.size - 1)
                    } else {
                        chatHistory
                    },
                    workspacePath = workspacePath,
                    promptFunctionType = promptFunctionType,
                    enableThinking = enableThinking,
                    thinkingGuidance = thinkingGuidance,
                    enableMemoryQuery = enableMemoryQuery, // Pass it here
                    maxTokens = effectiveMaxTokens,
                    tokenUsageThreshold = effectiveTokenUsageThreshold,
                    onNonFatalError = { error ->
                        _nonFatalErrorEvent.emit(error)
                    },
                    onTokenLimitExceeded = effectiveOnTokenLimitExceeded,
                    characterName = characterName,
                    avatarUri = avatarUri
                )

                // 将字符串流共享，以便多个收集器可以使用
                // 关键修改：设置 replay = Int.MAX_VALUE，确保 UI 重组（重新订阅）时能收到所有历史字符
                // 文本数据占用内存极小，全量缓冲不会造成内存压力
                val sharedCharStream =
                    responseStream.share(
                        scope = coroutineScope,
                        replay = Int.MAX_VALUE, 
                        onComplete = {
                            deferred.complete(Unit)
                            AppLogger.d(
                                TAG,
                                "共享流完成，耗时: ${System.currentTimeMillis() - startTime}ms"
                            )
                            chatRuntime.responseStream = null
                        }
                    )

                // 更新当前响应流，使其可以被其他组件（如悬浮窗）访问
                chatRuntime.responseStream = sharedCharStream

                // 获取当前激活角色卡的名称
                val currentRoleName = try {
                    characterCardManager.activeCharacterCardFlow.first().name
                } catch (e: Exception) {
                    "Operit" // 默认角色名
                }

                // 获取当前使用的provider和model信息
                val (provider, modelName) = try {
                    service.getProviderAndModelForFunction(com.ai.assistance.operit.data.model.FunctionType.CHAT)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "获取provider和model信息失败: ${e.message}", e)
                    Pair("", "")
                }

                aiMessage = ChatMessage(
                    sender = "ai", 
                    contentStream = sharedCharStream,
                    timestamp = System.currentTimeMillis()+50,
                    roleName = currentRoleName,
                    provider = provider,
                    modelName = modelName
                )
                AppLogger.d(
                    TAG,
                    "创建带流的AI消息, stream is null: ${aiMessage.contentStream == null}, timestamp: ${aiMessage.timestamp}"
                )

                // 检查是否启用waifu模式来决定是否显示流式过程
                val waifuPreferences = WaifuPreferences.getInstance(context)
                val isWaifuModeEnabled = waifuPreferences.enableWaifuModeFlow.first()
                
                // 只有在非waifu模式下才添加初始的AI消息
                if (!isWaifuModeEnabled) {
                    withContext(Dispatchers.Main) {
                        if (chatId != null) {
                            addMessageToChat(chatId, aiMessage)
                        }
                    }
                }
                
                // 启动一个独立的协程来收集流内容并持续更新数据库
                chatRuntime.streamCollectionJob =
                    coroutineScope.launch(Dispatchers.IO) {
                        val contentBuilder = StringBuilder()
                        sharedCharStream.collect { chunk ->
                            contentBuilder.append(chunk)
                            val content = contentBuilder.toString()
                            val updatedMessage = aiMessage.copy(content = content)
                            // 防止后续读取不到
                            aiMessage.content = content
                            
                            // 只有在非waifu模式下才显示流式更新
                            if (!isWaifuModeEnabled) {
                                if (chatId != null) {
                                    addMessageToChat(chatId, updatedMessage)
                                }
                                _scrollToBottomEvent.tryEmit(Unit)
                            }
                        }
                    }

                // 等待流完成，以便finally块可以正确执行来更新UI状态
                deferred.await()

                setChatInputProcessingState(chatId, EnhancedInputProcessingState.Completed)

                onTurnComplete(activeChatId, service)

                AppLogger.d(TAG, "AI响应处理完成，总耗时: ${System.currentTimeMillis() - startTime}ms")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    AppLogger.d(TAG, "消息发送被取消")
                    setChatInputProcessingState(chatId, EnhancedInputProcessingState.Idle)
                    throw e
                }
                AppLogger.e(TAG, "发送消息时出错", e)
                setChatInputProcessingState(
                    chatId,
                    EnhancedInputProcessingState.Error("发送消息失败: ${e.message}")
                )
                withContext(Dispatchers.Main) { showErrorMessage("发送消息失败: ${e.message}") }
            } finally {
                // 修改为使用 try-catch 来检查变量是否已初始化，而不是使用 ::var.isInitialized
                try {
                    // 尝试访问 aiMessage，如果未初始化会抛出 UninitializedPropertyAccessException
                    val finalContent = aiMessage.content
                    
                    // 检查是否启用了waifu模式并且内容适合分句
                    withContext(Dispatchers.IO) {
                        val waifuPreferences = WaifuPreferences.getInstance(context)
                        val isWaifuModeEnabled = waifuPreferences.enableWaifuModeFlow.first()
                        
                        if (isWaifuModeEnabled && WaifuMessageProcessor.shouldSplitMessage(finalContent)) {
                            AppLogger.d(TAG, "Waifu模式已启用，开始创建独立消息，内容长度: ${finalContent.length}")
                            
                            // 获取配置的字符延迟时间和标点符号设置
                            val charDelay = waifuPreferences.waifuCharDelayFlow.first().toLong()
                            val removePunctuation = waifuPreferences.waifuRemovePunctuationFlow.first()
                            
                            // 获取当前角色名
                            val currentRoleName = try {
                                characterCardManager.activeCharacterCardFlow.first().name
                            } catch (e: Exception) {
                                "Operit" // 默认角色名
                            }
                            
                            // 获取当前使用的provider和model信息（在finally块内重新获取）
                            val (provider, modelName) = try {
                                getEnhancedAiService()?.getProviderAndModelForFunction(com.ai.assistance.operit.data.model.FunctionType.CHAT) ?: Pair("", "")
                            } catch (e: Exception) {
                                AppLogger.e(TAG, "获取provider和model信息失败: ${e.message}", e)
                                Pair("", "")
                            }
                            
                            // 删除原始的空消息（因为在waifu模式下我们没有显示流式过程）
                            // 不需要显示空的AI消息
                            
                            // 启动一个协程来创建独立的句子消息
                            coroutineScope.launch(Dispatchers.IO) {
                                AppLogger.d(TAG, "开始Waifu独立消息创建，字符延迟: ${charDelay}ms/字符，移除标点: $removePunctuation")
                                
                                // 分割句子
                                val sentences = WaifuMessageProcessor.splitMessageBySentences(finalContent, removePunctuation)
                                AppLogger.d(TAG, "分割出${sentences.size}个句子")
                                
                                // 为每个句子创建独立的消息
                                for ((index, sentence) in sentences.withIndex()) {
                                    // 根据当前句子字符数计算延迟（模拟说话时间）
                                    val characterCount = sentence.length
                                    val calculatedDelay = WaifuMessageProcessor.calculateSentenceDelay(characterCount, charDelay)
                                    
                                    if (index > 0) {
                                        // 如果不是第一句，先延迟再发送
                                        AppLogger.d(TAG, "当前句字符数: $characterCount, 计算延迟: ${calculatedDelay}ms")
                                        delay(calculatedDelay)
                                    }
                                    
                                    AppLogger.d(TAG, "创建第${index + 1}个独立消息: $sentence")
                                    
                                    // 创建独立的AI消息（使用外层已获取的provider和modelName）
                                    val sentenceMessage = ChatMessage(
                                        sender = "ai",
                                        content = sentence,
                                        contentStream = null,
                                        timestamp = System.currentTimeMillis() + index * 10, // 确保时间戳不同
                                        roleName = currentRoleName, // 使用已获取的角色名
                                        provider = provider, // 使用外层获取的provider
                                        modelName = modelName // 使用外层获取的modelName
                                    )
                                    
                                    withContext(Dispatchers.Main) {
                                        if (chatId != null) {
                                            addMessageToChat(chatId, sentenceMessage)
                                        }
                                        // 如果启用了自动朗读，则朗读当前句子
                                        if (getIsAutoReadEnabled()) {
                                            speakMessage(sentence)
                                        }
                                        _scrollToBottomEvent.tryEmit(Unit)
                                    }
                                }
                                
                                AppLogger.d(TAG, "Waifu独立消息创建完成")
                            }
                        } else {
                            // 普通模式，直接清理流
                            val finalMessage = aiMessage.copy(content = finalContent, contentStream = null)
                            withContext(Dispatchers.Main) {
                                if (chatId != null) {
                                    addMessageToChat(chatId, finalMessage)
                                }
                                // 如果启用了自动朗读，则朗读完整消息
                                if (getIsAutoReadEnabled()) {
                                    speakMessage(finalContent)
                                }
                            }
                        }
                    }
                } catch (e: UninitializedPropertyAccessException) {
                    // aiMessage 未初始化，忽略清理步骤
                    AppLogger.d(TAG, "AI消息未初始化，跳过流清理步骤")
                } catch (e: Exception) {
                    AppLogger.e(TAG, "处理waifu模式时出错", e)
                    // 如果waifu模式处理失败，回退到普通模式
                    try {
                        val finalContent = aiMessage.content
                        val finalMessage = aiMessage.copy(content = finalContent)
                        withContext(Dispatchers.Main) {
                            if (chatId != null) {
                                addMessageToChat(chatId, finalMessage)
                            }
                        }
                    } catch (ex: Exception) {
                        AppLogger.e(TAG, "回退到普通模式也失败", ex)
                    }
                }
                // 清理job引用
                chatRuntime.streamCollectionJob = null
                chatRuntime.stateCollectionJob?.cancel()
                chatRuntime.stateCollectionJob = null
                chatRuntime.isLoading.value = false

                updateGlobalLoadingState()
            }
        }
    }

    fun cancelCurrentMessage() {
        val activeId = _activeStreamingChatId.value
        if (activeId != null) {
            cancelMessage(activeId)
            return
        }

        coroutineScope.launch {
            _inputProcessingState.value = EnhancedInputProcessingState.Idle
            updateGlobalLoadingState()
            withContext(Dispatchers.IO) {
                PhoneAgentJobRegistry.cancelAll("User cancelled")
                AIMessageManager.cancelCurrentOperation()
                saveCurrentChat()
            }
        }
    }

    /**
     * 强制重置加载状态，允许新的发送流程开始。
     * 主要用于在执行内部流程（如历史总结）后确保状态不会阻塞后续操作。
     */
    fun resetLoadingState() {
        updateGlobalLoadingState()
    }

    fun setActiveStreamingChatId(chatId: String?) {
        _activeStreamingChatId.value = chatId
    }

    fun setInputProcessingState(isProcessing: Boolean, message: String) {
        val activeId = _activeStreamingChatId.value
        if(isProcessing) {
            setChatInputProcessingState(activeId, EnhancedInputProcessingState.Processing(message))
        } else {
            setChatInputProcessingState(activeId, EnhancedInputProcessingState.Idle)
        }
    }

    /**
     * 处理来自 EnhancedAIService 的输入处理状态
     * @param state 输入处理状态
     */
    fun handleInputProcessingState(state: EnhancedInputProcessingState) {
        coroutineScope.launch(Dispatchers.Main) {
            if (state !is EnhancedInputProcessingState.ExecutingTool) {
                ToolProgressBus.clear()
            }
            setChatInputProcessingState(_activeStreamingChatId.value, state)
            // 当服务状态进入空闲或完成，如果当前没有任何会话在运行，则清理活跃会话标记
            if ((state is EnhancedInputProcessingState.Idle || state is EnhancedInputProcessingState.Completed) && !_isLoading.value) {
                _activeStreamingChatId.value = null
            }

            when (state) {
                is EnhancedInputProcessingState.Error -> {
                    showErrorMessage(state.message)
                }
                else -> {
                    // Do nothing for other states as they are handled by the state flow itself
                }
            }
        }
    }
}

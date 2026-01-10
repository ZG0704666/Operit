package com.ai.assistance.operit.api.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat
import com.ai.assistance.operit.util.AppLogger
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.graphics.drawable.IconCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.SpeechPrerollStore
import com.ai.assistance.operit.api.speech.SpeechService
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.services.UIDebuggerService
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.data.repository.WorkflowRepository
import com.ai.assistance.operit.util.WaifuMessageProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.InputStream


/** 前台服务，用于在AI进行长时间处理时保持应用活跃，防止被系统杀死。 该服务不执行实际工作，仅通过显示一个持久通知来提升应用的进程优先级。 */
class AIForegroundService : Service() {

    companion object {
        private const val TAG = "AIForegroundService"
        private const val NOTIFICATION_ID = 1
        private const val REPLY_NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "AI_SERVICE_CHANNEL"
        private const val CHANNEL_NAME = "Operit 正在运行"
        private const val REPLY_CHANNEL_ID = "AI_REPLY_CHANNEL"
        private const val REPLY_CHANNEL_NAME = "对话完成提醒"

        private const val ACTION_CANCEL_CURRENT_OPERATION = "com.ai.assistance.operit.action.CANCEL_CURRENT_OPERATION"
        private const val REQUEST_CODE_CANCEL_CURRENT_OPERATION = 9002

        private const val ACTION_EXIT_APP = "com.ai.assistance.operit.action.EXIT_APP"
        private const val REQUEST_CODE_EXIT_APP = 9003

        private const val ACTION_TOGGLE_WAKE_LISTENING = "com.ai.assistance.operit.action.TOGGLE_WAKE_LISTENING"
        private const val REQUEST_CODE_TOGGLE_WAKE_LISTENING = 9006

        private const val ACTION_SET_WAKE_LISTENING_SUSPENDED_FOR_IME =
            "com.ai.assistance.operit.action.SET_WAKE_LISTENING_SUSPENDED_FOR_IME"
        private const val EXTRA_IME_VISIBLE = "extra_ime_visible"

        @Volatile
        private var lastRequestedImeVisible: Boolean = false

        // 静态标志，用于从外部检查服务是否正在运行
        val isRunning = java.util.concurrent.atomic.AtomicBoolean(false)
        
        // Intent extras keys
        const val EXTRA_CHARACTER_NAME = "extra_character_name"
        const val EXTRA_REPLY_CONTENT = "extra_reply_content"
        const val EXTRA_AVATAR_URI = "extra_avatar_uri"
        const val EXTRA_STATE = "extra_state"
        const val STATE_RUNNING = "running"
        const val STATE_IDLE = "idle"

        fun setWakeListeningSuspendedForIme(context: Context, imeVisible: Boolean) {
            lastRequestedImeVisible = imeVisible
            if (!isRunning.get()) return
            val intent = Intent(context, AIForegroundService::class.java).apply {
                action = ACTION_SET_WAKE_LISTENING_SUSPENDED_FOR_IME
                putExtra(EXTRA_IME_VISIBLE, imeVisible)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to request IME wake listening suspend: ${e.message}", e)
            }
        }
    }

    private fun updateWakeListeningSuspendedForIme(imeVisible: Boolean) {
        if (wakeListeningSuspendedForIme == imeVisible) return
        wakeListeningSuspendedForIme = imeVisible
        AppLogger.d(TAG, "Wake listening suspended by IME: $wakeListeningSuspendedForIme")
        applyWakeListeningState()
    }

    private fun updateWakeListeningSuspendedForExternalRecording(externalRecording: Boolean) {
        if (wakeListeningSuspendedForExternalRecording == externalRecording) return
        wakeListeningSuspendedForExternalRecording = externalRecording
        AppLogger.d(TAG, "Wake listening suspended by external recording: $wakeListeningSuspendedForExternalRecording")
        applyWakeListeningState()
    }

    private fun applyWakeListeningState() {
        serviceScope.launch {
            val shouldListen =
                wakeListeningEnabled &&
                    !wakeListeningSuspendedForIme &&
                    !wakeListeningSuspendedForExternalRecording

            if (shouldListen) {
                startWakeListening()
            } else {
                stopWakeListening()
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification())
        }
    }

    private fun startRecordingStateMonitoring() {
        if (!wakeListeningEnabled) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (audioRecordingCallback != null) return

        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = am

        val callback =
            object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                    val myUid = Process.myUid()
                    val hasExternal =
                        configs.any { cfg ->
                            val uid = cfg.tryGetClientUid()
                            if (uid != null) {
                                uid != myUid
                            } else {
                                val isWakeListeningRunning = wakeListeningJob?.isActive == true
                                if (isWakeListeningRunning) {
                                    configs.size > 1
                                } else {
                                    configs.isNotEmpty()
                                }
                            }
                        }
                    updateWakeListeningSuspendedForExternalRecording(hasExternal)
                }
            }
        audioRecordingCallback = callback

        try {
            am.registerAudioRecordingCallback(callback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to register recording callback: ${e.message}", e)
            audioRecordingCallback = null
            audioManager = null
            return
        }

        try {
            val configs = am.activeRecordingConfigurations
            val myUid = Process.myUid()
            val hasExternal =
                configs.any { cfg ->
                    val uid = cfg.tryGetClientUid()
                    if (uid != null) {
                        uid != myUid
                    } else {
                        val isWakeListeningRunning = wakeListeningJob?.isActive == true
                        if (isWakeListeningRunning) {
                            configs.size > 1
                        } else {
                            configs.isNotEmpty()
                        }
                    }
                }
            updateWakeListeningSuspendedForExternalRecording(hasExternal)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read active recording configs: ${e.message}", e)
        }
    }

    private fun AudioRecordingConfiguration.tryGetClientUid(): Int? {
        return try {
            val method = javaClass.getMethod("getClientUid")
            (method.invoke(this) as? Int)
        } catch (_: Exception) {
            null
        }
    }

    private fun stopRecordingStateMonitoring() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val am = audioManager
        val callback = audioRecordingCallback

        if (am != null && callback != null) {
            try {
                am.unregisterAudioRecordingCallback(callback)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to unregister recording callback: ${e.message}", e)
            }
        }

        audioRecordingCallback = null
        audioManager = null
        wakeListeningSuspendedForExternalRecording = false
    }
    
    // 存储通知信息
    private var characterName: String? = null
    private var replyContent: String? = null
    private var avatarUri: String? = null
    private var isAiBusy: Boolean = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wakePrefs by lazy { WakeWordPreferences(applicationContext) }
    private val wakeSpeechProvider: SpeechService by lazy {
        val prefs = SpeechServicesPreferences(applicationContext)
        val selectedType = runBlocking { prefs.sttServiceTypeFlow.first() }
        val effectiveType =
            if (selectedType == SpeechServiceFactory.SpeechServiceType.OPENAI_STT) {
                SpeechServiceFactory.SpeechServiceType.SHERPA_MNN
            } else {
                selectedType
            }
        SpeechServiceFactory.createSpeechService(applicationContext, effectiveType)
    }
    private val workflowRepository by lazy { WorkflowRepository(applicationContext) }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var keepAliveOverlayView: View? = null
    private var keepAliveOverlayPermissionLogged = false

    private var wakeMonitorJob: Job? = null
    private var wakeListeningJob: Job? = null
    private var wakeResumeJob: Job? = null

    @Volatile
    private var currentWakePhrase: String = WakeWordPreferences.DEFAULT_WAKE_PHRASE

    @Volatile
    private var wakePhraseRegexEnabled: Boolean = WakeWordPreferences.DEFAULT_WAKE_PHRASE_REGEX_ENABLED

    @Volatile
    private var wakeListeningEnabled: Boolean = false

    @Volatile
    private var wakeListeningSuspendedForIme: Boolean = false

    @Volatile
    private var wakeListeningSuspendedForExternalRecording: Boolean = false

    private var audioManager: AudioManager? = null
    private var audioRecordingCallback: AudioManager.AudioRecordingCallback? = null

    private var lastWakeTriggerAtMs: Long = 0L

    private var lastSpeechWorkflowCheckAtMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        isRunning.set(true)
        wakeListeningSuspendedForIme = lastRequestedImeVisible
        AppLogger.d(TAG, "AI 前台服务创建。")
        createNotificationChannel()
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startWakeMonitoring()
        AppLogger.d(TAG, "AI 前台服务已启动。")
    }

    private fun hasRecordAudioPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private suspend fun tryPromoteToMicrophoneForeground(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (!hasRecordAudioPermission()) return false

        var waitedMs = 0L
        while (waitedMs < 3500L && ActivityLifecycleManager.getCurrentActivity() == null) {
            delay(150)
            waitedMs += 150
        }

        if (ActivityLifecycleManager.getCurrentActivity() == null) {
            AppLogger.w(TAG, "promote microphone foreground skipped: app not in foreground")
            return false
        }

        val types =
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        return try {
            startForeground(NOTIFICATION_ID, createNotification(), types)
            true
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "promote microphone foreground failed: ${e.message}", e)
            false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_EXIT_APP) {
            isRunning.set(false)
            isAiBusy = false

            try {
                AIMessageManager.cancelCurrentOperation()
            } catch (e: Exception) {
                AppLogger.e(TAG, "退出时取消当前AI任务失败: ${e.message}", e)
            }

            try {
                stopService(Intent(this, FloatingChatService::class.java))
            } catch (e: Exception) {
                AppLogger.e(TAG, "退出时停止 FloatingChatService 失败: ${e.message}", e)
            }

            try {
                stopService(Intent(this, UIDebuggerService::class.java))
            } catch (e: Exception) {
                AppLogger.e(TAG, "退出时停止 UIDebuggerService 失败: ${e.message}", e)
            }

            try {
                val activity = ActivityLifecycleManager.getCurrentActivity()
                activity?.runOnUiThread {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        activity.finishAndRemoveTask()
                    } else {
                        activity.finish()
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "退出时关闭前台界面失败: ${e.message}", e)
            }

            try {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NOTIFICATION_ID)
                manager.cancel(REPLY_NOTIFICATION_ID)
            } catch (e: Exception) {
                AppLogger.e(TAG, "退出时取消通知失败: ${e.message}", e)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                @Suppress("DEPRECATION")
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }

            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_TOGGLE_WAKE_LISTENING) {
            AppLogger.d(TAG, "收到 ACTION_TOGGLE_WAKE_LISTENING")
            serviceScope.launch {
                try {
                    val current = wakePrefs.alwaysListeningEnabledFlow.first()
                    AppLogger.d(TAG, "切换唤醒监听: $current -> ${!current}")
                    wakePrefs.saveAlwaysListeningEnabled(!current)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "切换唤醒监听失败: ${e.message}", e)
                }

                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, createNotification())
            }
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_SET_WAKE_LISTENING_SUSPENDED_FOR_IME) {
            val imeVisible = intent.getBooleanExtra(EXTRA_IME_VISIBLE, false)
            updateWakeListeningSuspendedForIme(imeVisible)
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_CANCEL_CURRENT_OPERATION) {
            try {
                AIMessageManager.cancelCurrentOperation()
                // 立即刷新通知状态（真正的状态重置由 EnhancedAIService.cancelConversation/stopAiService 完成）
                isAiBusy = false
            } catch (e: Exception) {
                AppLogger.e(TAG, "取消当前AI任务失败: ${e.message}", e)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification())
            return START_NOT_STICKY
        }

        // 从Intent中提取通知信息
        intent?.let {
            characterName = it.getStringExtra(EXTRA_CHARACTER_NAME)
            replyContent = it.getStringExtra(EXTRA_REPLY_CONTENT)
            avatarUri = it.getStringExtra(EXTRA_AVATAR_URI)
            AppLogger.d(TAG, "收到通知数据 - 角色: $characterName, 内容长度: ${replyContent?.length}, 头像: $avatarUri")

            val state = it.getStringExtra(EXTRA_STATE)
            if (state != null) {
                isAiBusy = state == STATE_RUNNING
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, createNotification())
                if (!isAiBusy) {
                    sendReplyNotificationIfEnabled()
                }
            }
        }
        
        // 返回 START_NOT_STICKY 表示如果服务被杀死，系统不需要尝试重启它。
        // 因为服务的生命周期由 EnhancedAIService 精确控制。
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        hideKeepAliveOverlay()
        stopWakeMonitoring()
        AppLogger.d(TAG, "AI 前台服务已销毁。")
    }

    override fun onBind(intent: Intent?): IBinder? {
        // 该服务是启动服务，不提供绑定功能。
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                    NotificationChannel(
                                    CHANNEL_ID,
                                    CHANNEL_NAME,
                                    NotificationManager.IMPORTANCE_LOW // 低重要性，避免打扰用户
                            )
                            .apply {
                                description = "保持 Operit 在后台运行。"
                            }

            val replyChannel =
                    NotificationChannel(
                                    REPLY_CHANNEL_ID,
                                    REPLY_CHANNEL_NAME,
                                    NotificationManager.IMPORTANCE_HIGH
                            )
                            .apply {
                                description = "对话完成后提醒你。"
                            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(replyChannel)
        }
    }

    private fun startWakeMonitoring() {
        if (wakeMonitorJob?.isActive == true) return
        AppLogger.d(TAG, "startWakeMonitoring")
        wakeMonitorJob =
            serviceScope.launch {
                launch {
                    wakePrefs.wakePhraseFlow.collectLatest { phrase ->
                        currentWakePhrase = phrase.ifBlank { WakeWordPreferences.DEFAULT_WAKE_PHRASE }
                        AppLogger.d(TAG, "唤醒词更新: '$currentWakePhrase'")
                    }
                }

                launch {
                    wakePrefs.wakePhraseRegexEnabledFlow.collectLatest { enabled ->
                        wakePhraseRegexEnabled = enabled
                        AppLogger.d(TAG, "唤醒词正则开关更新: enabled=$enabled")
                    }
                }

                wakePrefs.alwaysListeningEnabledFlow.collectLatest { enabled ->
                    wakeListeningEnabled = enabled
                    AppLogger.d(TAG, "唤醒监听开关更新: enabled=$enabled")

                    if (enabled) {
                        showKeepAliveOverlayIfPossible()
                    } else {
                        hideKeepAliveOverlay()
                    }

                    if (enabled) {
                        startRecordingStateMonitoring()
                    } else {
                        stopRecordingStateMonitoring()
                    }

                    applyWakeListeningState()

                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(NOTIFICATION_ID, createNotification())
                }
            }
    }

    private fun stopWakeMonitoring() {
        wakeMonitorJob?.cancel()
        wakeMonitorJob = null
        wakeResumeJob?.cancel()
        wakeResumeJob = null
        wakeListeningJob?.cancel()
        wakeListeningJob = null
        stopRecordingStateMonitoring()
        hideKeepAliveOverlay()
        try {
            serviceScope.cancel()
        } catch (_: Exception) {
        }
        try {
            wakeSpeechProvider.shutdown()
        } catch (_: Exception) {
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post {
                try {
                    action()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error on main thread", e)
                }
            }
        }
    }

    private fun showKeepAliveOverlayIfPossible() {
        if (keepAliveOverlayView != null) return
        if (!Settings.canDrawOverlays(this)) {
            if (!keepAliveOverlayPermissionLogged) {
                keepAliveOverlayPermissionLogged = true
                AppLogger.w(TAG, "Keep-alive overlay skipped: missing overlay permission")
            }
            return
        }

        runOnMainThread {
            if (keepAliveOverlayView != null) return@runOnMainThread
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val view = View(this)
                ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
                    val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                    updateWakeListeningSuspendedForIme(imeVisible)
                    insets
                }
                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                val params = WindowManager.LayoutParams(
                    1,
                    1,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = 0
                }

                wm.addView(view, params)
                keepAliveOverlayView = view
                ViewCompat.requestApplyInsets(view)
                AppLogger.d(TAG, "Keep-alive overlay shown")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to show keep-alive overlay: ${e.message}", e)
                keepAliveOverlayView = null
            }
        }
    }

    private fun hideKeepAliveOverlay() {
        val view = keepAliveOverlayView ?: return
        runOnMainThread {
            val current = keepAliveOverlayView ?: return@runOnMainThread
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(current)
                AppLogger.d(TAG, "Keep-alive overlay hidden")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to hide keep-alive overlay: ${e.message}", e)
            } finally {
                if (keepAliveOverlayView === view) {
                    keepAliveOverlayView = null
                }
            }
        }
    }

    private suspend fun startWakeListening() {
        if (!wakeListeningEnabled) return
        if (wakeListeningJob?.isActive == true) return

        AppLogger.d(TAG, "startWakeListening: phrase='$currentWakePhrase'")

        val micGranted =
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            AppLogger.e(TAG, "启动唤醒监听失败: 未授予 RECORD_AUDIO（请在系统设置中允许麦克风权限）")
            wakeListeningEnabled = false
            try {
                wakePrefs.saveAlwaysListeningEnabled(false)
            } catch (_: Exception) {
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification())
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            tryPromoteToMicrophoneForeground()
        }

        wakeResumeJob?.cancel()
        wakeResumeJob = null

        try {
            val initOk = wakeSpeechProvider.initialize()
            AppLogger.d(TAG, "唤醒识别器 initialize: ok=$initOk")
            val startOk = wakeSpeechProvider.startRecognition(
                languageCode = "zh-CN",
                continuousMode = true,
                partialResults = true
            )
            AppLogger.d(TAG, "唤醒识别器 startRecognition: ok=$startOk")
            if (!startOk) {
                wakeListeningEnabled = false
                try {
                    wakePrefs.saveAlwaysListeningEnabled(false)
                } catch (_: Exception) {
                }
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, createNotification())
                return
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动唤醒监听失败: ${e.message}", e)
            return
        }

        wakeListeningJob =
            serviceScope.launch {
                var lastText = ""
                var lastIsFinal = false
                wakeSpeechProvider.recognitionResultFlow.collectLatest { result ->
                    val text = result.text
                    if (text.isBlank()) return@collectLatest
                    if (text == lastText && result.isFinal == lastIsFinal) return@collectLatest
                    lastText = text
                    lastIsFinal = result.isFinal

                    AppLogger.d(
                        TAG,
                        "唤醒识别输出(${if (result.isFinal) "final" else "partial"}): '$text'"
                    )

                    try {
                        val now = System.currentTimeMillis()
                        val shouldCheckWorkflows = result.isFinal || now - lastSpeechWorkflowCheckAtMs >= 350L
                        if (shouldCheckWorkflows) {
                            lastSpeechWorkflowCheckAtMs = now
                            workflowRepository.triggerWorkflowsBySpeechEvent(text = text, isFinal = result.isFinal)
                        }
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Speech trigger processing failed: ${e.message}", e)
                    }

                    if (matchWakePhrase(text, currentWakePhrase, wakePhraseRegexEnabled)) {
                        val now = System.currentTimeMillis()
                        if (now - lastWakeTriggerAtMs < 3000L) return@collectLatest
                        lastWakeTriggerAtMs = now

                        AppLogger.d(TAG, "命中唤醒词: '$currentWakePhrase' in '$text'")
                        SpeechPrerollStore.capturePending(windowMs = 1600)
                        SpeechPrerollStore.armPending()
                        triggerWakeLaunch()

                        stopWakeListening()
                        scheduleWakeResume()
                    }
                }
            }
    }

    private suspend fun stopWakeListening() {
        AppLogger.d(TAG, "stopWakeListening")
        wakeResumeJob?.cancel()
        wakeResumeJob = null

        wakeListeningJob?.cancel()
        wakeListeningJob = null

        try {
            wakeSpeechProvider.cancelRecognition()
        } catch (_: Exception) {
        }
    }

    private fun scheduleWakeResume() {
        AppLogger.d(TAG, "scheduleWakeResume")
        wakeResumeJob?.cancel()
        wakeResumeJob =
            serviceScope.launch {
                var waitedMs = 0L
                while (isActive && waitedMs < 5000L) {
                    if (!wakeListeningEnabled) return@launch
                    if (FloatingChatService.getInstance() != null) break
                    delay(250)
                    waitedMs += 250
                }

                AppLogger.d(TAG, "等待悬浮窗启动: waitedMs=$waitedMs, instance=${FloatingChatService.getInstance() != null}")

                while (isActive) {
                    if (!wakeListeningEnabled) return@launch
                    if (FloatingChatService.getInstance() == null) break
                    delay(500)
                }

                AppLogger.d(TAG, "检测到悬浮窗已关闭，准备恢复唤醒监听")

                if (wakeListeningEnabled && !wakeListeningSuspendedForIme && !wakeListeningSuspendedForExternalRecording) {
                    startWakeListening()
                }
            }
    }

    private fun triggerWakeLaunch() {
        AppLogger.d(TAG, "triggerWakeLaunch: 打开全屏悬浮窗并进入语音")
        try {
            val floatingIntent = Intent(this, FloatingChatService::class.java).apply {
                putExtra("INITIAL_MODE", com.ai.assistance.operit.ui.floating.FloatingMode.FULLSCREEN.name)
                putExtra(FloatingChatService.EXTRA_AUTO_ENTER_VOICE_CHAT, true)
                putExtra(FloatingChatService.EXTRA_WAKE_LAUNCHED, true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(floatingIntent)
            } else {
                startService(floatingIntent)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "唤醒打开悬浮窗失败: ${e.message}", e)
        }
    }

    private fun matchWakePhrase(recognized: String, phrase: String, regexEnabled: Boolean): Boolean {
        if (regexEnabled) {
            if (phrase.isBlank()) return false
            return try {
                Regex(phrase).containsMatchIn(recognized)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Invalid wake phrase regex: '$phrase' (${e.message})")
                false
            }
        }

        val target = normalizeWakeText(phrase)
        if (target.isBlank()) return false
        val text = normalizeWakeText(recognized)
        return text.contains(target)
    }

    private fun normalizeWakeText(text: String): String {
        val cleaned =
            text
                .lowercase()
                .replace(
                    Regex("[\\s\\p{Punct}，。！？；：、“”‘’【】（）()\\[\\]{}<>《》]+"),
                    ""
                )
        return cleaned
    }

    private fun createNotification(): Notification {
        // 为了简单起见，使用一个安卓内置图标。
        // 在实际项目中，应替换为应用的自定义图标。
        val wakeListeningEnabledSnapshot = wakeListeningEnabled
        val wakeListeningSuspendedSnapshot = wakeListeningSuspendedForIme || wakeListeningSuspendedForExternalRecording
        val contentText = if (isAiBusy) {
            "AI is processing..."
        } else {
            if (wakeListeningEnabledSnapshot) {
                if (wakeListeningSuspendedSnapshot) {
                    "Operit 正在运行（唤醒暂停）"
                } else {
                    "Operit 正在运行（唤醒监听中）"
                }
            } else {
                "Operit 正在运行"
            }
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Operit")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // 使通知不可被用户清除

        val contentIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (contentIntent != null) {
            val contentPendingIntent = PendingIntent.getActivity(
                this,
                0,
                contentIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            builder.setContentIntent(contentPendingIntent)
        }

        val floatingIntent = Intent(this, FloatingChatService::class.java).apply {
            putExtra("INITIAL_MODE", com.ai.assistance.operit.ui.floating.FloatingMode.FULLSCREEN.name)
            putExtra(FloatingChatService.EXTRA_AUTO_ENTER_VOICE_CHAT, true)
        }
        val floatingPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                9005,
                floatingIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getService(
                this,
                9005,
                floatingIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        builder.addAction(
            android.R.drawable.ic_btn_speak_now,
            "语音悬浮窗",
            floatingPendingIntent
        )

        val toggleWakeIntent = Intent(this, AIForegroundService::class.java).apply {
            action = ACTION_TOGGLE_WAKE_LISTENING
        }
        val toggleWakePendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_TOGGLE_WAKE_LISTENING,
            toggleWakeIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        builder.addAction(
            android.R.drawable.ic_lock_silent_mode_off,
            if (wakeListeningEnabledSnapshot) "关闭唤醒" else "开启唤醒",
            toggleWakePendingIntent
        )

        val exitIntent = Intent(this, AIForegroundService::class.java).apply {
            action = ACTION_EXIT_APP
        }
        val exitPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_EXIT_APP,
            exitIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            "退出",
            exitPendingIntent
        )

        if (isAiBusy) {
            val cancelIntent = Intent(this, AIForegroundService::class.java).apply {
                action = ACTION_CANCEL_CURRENT_OPERATION
            }
            val pendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_CANCEL_CURRENT_OPERATION,
                cancelIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "停止",
                pendingIntent
            )
        }

        return builder.build()
    }
    
    /**
     * 如果用户启用了回复通知，则发送AI回复完成通知
     */
    private fun sendReplyNotificationIfEnabled() {
        try {
            AppLogger.d(TAG, "检查是否需要发送回复通知...")
            
            // 检查应用是否在前台
            val isAppInForeground = ActivityLifecycleManager.getCurrentActivity() != null
            if (isAppInForeground) {
                AppLogger.d(TAG, "应用在前台，无需发送通知")
                return
            }
            
            // 检查用户是否启用了回复通知
            val displayPreferences = DisplayPreferencesManager.getInstance(applicationContext)
            val enableReplyNotification = runBlocking {
                displayPreferences.enableReplyNotification.first()
            }
            
            if (!enableReplyNotification) {
                AppLogger.d(TAG, "回复通知已禁用，跳过发送")
                return
            }
            
            AppLogger.d(TAG, "准备发送AI回复通知...")
            
            // 清理回复内容，移除思考内容等
            val cleanedReplyContent = replyContent?.let { 
                WaifuMessageProcessor.cleanContentForWaifu(it) 
            } ?: ""
            
            // 创建点击通知后打开应用的Intent
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            
            // 构建通知
            val notificationBuilder = NotificationCompat.Builder(this, REPLY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(characterName ?: getString(R.string.notification_ai_reply_title))
                .setContentText(cleanedReplyContent.take(100).ifEmpty { getString(R.string.notification_ai_reply_content) })
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // 点击后自动消失
            
            // 如果有完整内容，使用BigTextStyle显示更多文本
            if (cleanedReplyContent.isNotEmpty()) {
                notificationBuilder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(cleanedReplyContent)
                        .setBigContentTitle(characterName ?: getString(R.string.notification_ai_reply_title))
                )
            }
            
            // 如果有头像，设置大图标
            val avatarUriString = avatarUri
            if (!avatarUriString.isNullOrEmpty()) {
                try {
                    val bitmap = loadBitmapFromUri(avatarUriString)
                    if (bitmap != null) {
                        notificationBuilder.setLargeIcon(bitmap)
                        AppLogger.d(TAG, "成功加载头像到通知")
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "加载头像失败: ${e.message}", e)
                }
            }
            
            val notification = notificationBuilder.build()
            
            // 发送通知
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(REPLY_NOTIFICATION_ID, notification)
            AppLogger.d(TAG, "AI回复通知已发送 (ID: $REPLY_NOTIFICATION_ID)")
        } catch (e: Exception) {
            AppLogger.e(TAG, "发送AI回复通知失败: ${e.message}", e)
        }
    }
    
    /**
     * 从URI加载Bitmap
     */
    private fun loadBitmapFromUri(uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "从URI加载Bitmap失败: ${e.message}", e)
            null
        }
    }
}

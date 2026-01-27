package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import android.os.Environment
import com.ai.assistance.llama.LlamaSession
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import java.io.File

class LlamaProvider(
    private val context: Context,
    private val modelName: String,
    private val threadCount: Int,
    private val contextSize: Int,
    private val providerType: ApiProviderType = ApiProviderType.LLAMA_CPP
) : AIService {

    companion object {
        private const val TAG = "LlamaProvider"

        fun getModelsDir(): File {
            return File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Operit/models/llama"
            )
        }

        fun getModelFile(_context: Context, modelName: String): File {
            return File(getModelsDir(), modelName)
        }
    }

    private var _inputTokenCount: Int = 0
    private var _outputTokenCount: Int = 0
    private var _cachedInputTokenCount: Int = 0

    @Volatile
    private var isCancelled = false

    private val sessionLock = Any()
    private var session: LlamaSession? = null

    override val inputTokenCount: Int
        get() = _inputTokenCount

    override val cachedInputTokenCount: Int
        get() = _cachedInputTokenCount

    override val outputTokenCount: Int
        get() = _outputTokenCount

    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    override fun resetTokenCounts() {
        _inputTokenCount = 0
        _outputTokenCount = 0
        _cachedInputTokenCount = 0
    }

    override fun cancelStreaming() {
        isCancelled = true
        synchronized(sessionLock) {
            session?.cancel()
        }
    }

    override fun release() {
        synchronized(sessionLock) {
            session?.release()
            session = null
        }
    }

    override suspend fun getModelsList(context: Context): Result<List<ModelOption>> {
        return ModelListFetcher.getLlamaLocalModels(context)
    }

    override suspend fun testConnection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        if (!LlamaSession.isAvailable()) {
            return@withContext Result.failure(Exception(LlamaSession.getUnavailableReason()))
        }

        val modelFile = getModelFile(context, modelName)
        if (!modelFile.exists()) {
            return@withContext Result.failure(Exception("模型文件不存在: ${modelFile.absolutePath}"))
        }

        val testSession = LlamaSession.create(
            pathModel = modelFile.absolutePath,
            nThreads = threadCount,
            nCtx = contextSize
        ) ?: return@withContext Result.failure(Exception("创建 llama.cpp 会话失败（nativeCreateSession 返回 0）"))

        testSession.release()
        Result.success("llama.cpp backend is available (native ready).")
    }

    override suspend fun calculateInputTokens(
        message: String,
        chatHistory: List<Pair<String, String>>,
        availableTools: List<ToolPrompt>?
    ): Int {
        return withContext(Dispatchers.IO) {
            kotlin.runCatching {
                val s = ensureSessionLocked()
                if (s == null) return@runCatching null

                val roles = ArrayList<String>(chatHistory.size + 1)
                val contents = ArrayList<String>(chatHistory.size + 1)
                for ((role, content) in chatHistory) {
                    roles.add(role)
                    contents.add(content)
                }
                roles.add("user")
                contents.add(message)

                val prompt = s.applyChatTemplate(roles, contents, true)
                    ?: return@runCatching null

                s.countTokens(prompt)
            }.getOrNull() ?: 0
        }
    }

    override suspend fun sendMessage(
        context: Context,
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        stream: Boolean,
        availableTools: List<ToolPrompt>?,
        preserveThinkInHistory: Boolean,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
        onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        isCancelled = false

        if (!LlamaSession.isAvailable()) {
            emit("错误: ${LlamaSession.getUnavailableReason()}")
            return@stream
        }

        val modelFile = getModelFile(context, modelName)
        if (!modelFile.exists()) {
            emit("错误: 模型文件不存在: ${modelFile.absolutePath}")
            return@stream
        }

        val s = withContext(Dispatchers.IO) {
            ensureSessionLocked()
        }
        if (s == null) {
            emit("错误: 创建 llama.cpp 会话失败（请检查模型文件与 JNI 编译输出）")
            return@stream
        }

        val roles = ArrayList<String>(chatHistory.size + 1)
        val contents = ArrayList<String>(chatHistory.size + 1)
        for ((role, content) in chatHistory) {
            roles.add(role)
            contents.add(content)
        }
        roles.add("user")
        contents.add(message)

        val prompt = withContext(Dispatchers.IO) {
            s.applyChatTemplate(roles, contents, true)
        }
        if (prompt.isNullOrBlank()) {
            emit("错误: 无法应用模型对话模板（llama_model_chat_template/llama_chat_apply_template）")
            return@stream
        }

        val temperature = modelParameters
            .firstOrNull { it.id == "temperature" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 1.0f
        val topP = modelParameters
            .firstOrNull { it.id == "top_p" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 1.0f
        val topK = modelParameters
            .firstOrNull { it.id == "top_k" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toInt() }
            ?: 0
        val repetitionPenalty = modelParameters
            .firstOrNull { it.id == "repetition_penalty" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 1.0f
        val frequencyPenalty = modelParameters
            .firstOrNull { it.id == "frequency_penalty" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 0.0f
        val presencePenalty = modelParameters
            .firstOrNull { it.id == "presence_penalty" && it.isEnabled }
            ?.let { (it.currentValue as? Number)?.toFloat() }
            ?: 0.0f

        withContext(Dispatchers.IO) {
            kotlin.runCatching {
                s.setSamplingParams(
                    temperature = temperature,
                    topP = topP,
                    topK = topK,
                    repetitionPenalty = repetitionPenalty,
                    frequencyPenalty = frequencyPenalty,
                    presencePenalty = presencePenalty,
                    penaltyLastN = 64
                )
            }
        }

        _inputTokenCount = kotlin.runCatching { s.countTokens(prompt) }.getOrElse { 0 }
        _outputTokenCount = 0
        onTokensUpdated(_inputTokenCount, 0, 0)

        val requestedMaxNewTokens = modelParameters
            .find { it.name == "max_tokens" }
            ?.let { (it.currentValue as? Number)?.toInt() }
            ?: -1

        AppLogger.d(TAG, "开始llama.cpp推理，history=${chatHistory.size}, threads=$threadCount, n_ctx=$contextSize")

        var outputTokenCount = 0
        val success = withContext(Dispatchers.IO) {
            s.generateStream(prompt, requestedMaxNewTokens) { token ->
                if (isCancelled) {
                    false
                } else {
                    outputTokenCount += 1
                    _outputTokenCount = outputTokenCount

                    runBlocking { emit(token) }

                    kotlin.runCatching {
                        kotlinx.coroutines.runBlocking {
                            onTokensUpdated(_inputTokenCount, 0, _outputTokenCount)
                        }
                    }

                    true
                }
            }
        }

        if (!success && !isCancelled) {
            kotlin.runCatching {
                onNonFatalError("llama.cpp 推理过程出现错误")
            }
            emit("\n\n[推理过程出现错误]")
        }

        AppLogger.i(TAG, "llama.cpp推理完成，输出token数: $_outputTokenCount")
    }

    private fun ensureSessionLocked(): LlamaSession? {
        synchronized(sessionLock) {
            session?.let { return it }
            val modelFile = getModelFile(context, modelName)
            val created = LlamaSession.create(
                pathModel = modelFile.absolutePath,
                nThreads = threadCount,
                nCtx = contextSize
            )
            session = created
            return created
        }
    }

}

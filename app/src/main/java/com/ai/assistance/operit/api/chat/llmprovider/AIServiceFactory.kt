package com.ai.assistance.operit.api.chat.llmprovider

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.json.JSONObject

/**
 * A factory for creating and managing a shared OkHttpClient instance.
 * Using a shared client allows for efficient reuse of connections and resources.
 */
private object SharedHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Increase the connection timeout to handle slow networks better.
            .connectTimeout(60, TimeUnit.SECONDS)
            // Set long read/write timeouts for streaming responses.
            .readTimeout(1000, TimeUnit.SECONDS)
            .writeTimeout(1000, TimeUnit.SECONDS)
            // Use a connection pool to reuse connections, improving latency and reducing resource usage.
            // Increased idle connections to 10 from the default of 5.
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            // Explicitly enable HTTP/2, which is the default but good to have declared.
            // OkHttp will use HTTP/2 if the server supports it, falling back to HTTP/1.1.
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }
}

/** AI服务工厂，根据提供商类型创建相应的AIService实例 */
object AIServiceFactory {

    /**
     * 解析自定义请求头的JSON字符串为Map
     */
    private fun parseCustomHeaders(customHeadersJson: String): Map<String, String> {
        return try {
            val headers = mutableMapOf<String, String>()
            if (customHeadersJson.isNotEmpty() && customHeadersJson != "{}") {
                val jsonObject = JSONObject(customHeadersJson)
                for (key in jsonObject.keys()) {
                    headers[key] = jsonObject.getString(key)
                }
            }
            headers
        } catch (e: Exception) {
            AppLogger.e("AIServiceFactory", "解析自定义请求头失败", e)
            emptyMap()
        }
    }

    /**
     * 创建AI服务实例
     *
     * @param config 模型配置数据
     * @param customHeadersJson 自定义请求头的JSON字符串
     * @param modelConfigManager 模型配置管理器，用于多API Key模式
     * @param context Android上下文，用于MNN等需要访问本地资源的提供商
     * @return 对应的AIService实现
     */
    fun createService(
        config: ModelConfigData,
        customHeadersJson: String,
        modelConfigManager: ModelConfigManager,
        context: Context
    ): AIService {
        val httpClient = SharedHttpClient.instance
        val customHeaders = parseCustomHeaders(customHeadersJson)

        // 根据配置决定使用单个API Key还是多API Key轮询
        val apiKeyProvider = if (config.useMultipleApiKeys) {
            MultiApiKeyProvider(config.id, modelConfigManager)
        } else {
            SingleApiKeyProvider(config.apiKey)
        }

        // 图片处理支持标志
        val supportsVision = config.enableDirectImageProcessing
        // 音频/视频输入支持标志（OpenAI兼容的多模态content数组）
        val supportsAudio = config.enableDirectAudioProcessing
        val supportsVideo = config.enableDirectVideoProcessing
        // Tool Call支持标志
        val enableToolCall = config.enableToolCall
        
        return when (config.apiProviderType) {
            // OpenAI格式，支持原生和兼容OpenAI API的服务
            ApiProviderType.OPENAI,
            ApiProviderType.OPENAI_GENERIC ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            ApiProviderType.OPENAI_RESPONSES ->
                OpenAIResponsesProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            // Claude格式，支持Anthropic Claude系列
            ApiProviderType.ANTHROPIC,
            ApiProviderType.ANTHROPIC_GENERIC -> ClaudeProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType, enableToolCall)

            // Gemini格式，支持Google Gemini系列及通用Gemini端点
            ApiProviderType.GOOGLE,
            ApiProviderType.GEMINI_GENERIC -> GeminiProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType, config.enableGoogleSearch, enableToolCall)

            // LM Studio使用OpenAI兼容格式
            ApiProviderType.LMSTUDIO ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            // Ollama使用OpenAI兼容格式
            ApiProviderType.OLLAMA ->
                OllamaProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            // MNN本地推理引擎
            ApiProviderType.MNN -> MNNProvider(
                context = context,
                modelName = config.modelName,  // 使用modelName而不是mnnModelPath
                forwardType = config.mnnForwardType,
                threadCount = config.mnnThreadCount,
                providerType = config.apiProviderType,
                enableToolCall = enableToolCall,
                supportsVision = supportsVision,
                supportsAudio = supportsAudio,
                supportsVideo = supportsVideo
            )

            // llama.cpp 本地推理引擎
            ApiProviderType.LLAMA_CPP -> LlamaProvider(
                context = context,
                modelName = config.modelName,
                threadCount = config.llamaThreadCount,
                contextSize = config.llamaContextSize,
                providerType = config.apiProviderType,
                enableToolCall = enableToolCall
            )

            // 阿里云（通义千问）使用专用的QwenProvider
            ApiProviderType.ALIYUN ->
                QwenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            // 其他中文服务商，当前使用OpenAI Provider (大多数兼容OpenAI格式)
            // 后续可根据需要实现专用Provider
            ApiProviderType.BAIDU ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.XUNFEI ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.ZHIPU ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.BAICHUAN ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.MOONSHOT ->
                KimiProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )

            // DeepSeek使用专用Provider（支持推理模式）
            ApiProviderType.DEEPSEEK ->
                DeepseekProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.MISTRAL ->
                MistralProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.SILICONFLOW ->
                QwenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.IFLOW ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.OPENROUTER ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.INFINIAI ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.ALIPAY_BAILING ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.DOUBAO ->
                DoubaoAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.PPINFRA ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
            ApiProviderType.OTHER ->
                OpenAIProvider(
                    apiEndpoint = config.apiEndpoint,
                    apiKeyProvider = apiKeyProvider,
                    modelName = config.modelName,
                    client = httpClient,
                    customHeaders = customHeaders,
                    providerType = config.apiProviderType,
                    supportsVision = supportsVision,
                    supportsAudio = supportsAudio,
                    supportsVideo = supportsVideo,
                    enableToolCall = enableToolCall
                )
        }
    }
}

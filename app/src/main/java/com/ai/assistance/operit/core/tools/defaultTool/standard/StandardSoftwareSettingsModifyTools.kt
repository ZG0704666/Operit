package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.ModelConfigConnectionTester
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.core.tools.FunctionModelBindingResultData
import com.ai.assistance.operit.core.tools.FunctionModelConfigResultData
import com.ai.assistance.operit.core.tools.FunctionModelConfigsResultData
import com.ai.assistance.operit.core.tools.FunctionModelMappingResultItem
import com.ai.assistance.operit.core.tools.ModelConfigConnectionTestItemResultData
import com.ai.assistance.operit.core.tools.ModelConfigConnectionTestResultData
import com.ai.assistance.operit.core.tools.ModelConfigCreateResultData
import com.ai.assistance.operit.core.tools.ModelConfigDeleteResultData
import com.ai.assistance.operit.core.tools.ModelConfigResultItem
import com.ai.assistance.operit.core.tools.ModelConfigUpdateResultData
import com.ai.assistance.operit.core.tools.ModelConfigsResultData
import com.ai.assistance.operit.core.tools.SpeechServicesConfigResultData
import com.ai.assistance.operit.core.tools.SpeechServicesUpdateResultData
import com.ai.assistance.operit.core.tools.SpeechSttHttpConfigResultItem
import com.ai.assistance.operit.core.tools.SpeechTtsHttpConfigResultItem
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.getModelByIndex
import com.ai.assistance.operit.data.model.getModelList
import com.ai.assistance.operit.data.model.getValidModelIndex
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.FunctionConfigMapping
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingStateRegistry
import com.ai.assistance.operit.ui.features.startup.screens.PluginStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** 软件设置修改工具（包含 MCP 重启与日志收集） */
class StandardSoftwareSettingsModifyTools(private val context: Context) {

    fun readEnvironmentVariable(tool: AITool): ToolResult {
        val key = tool.parameters.find { it.name == "key" }?.value?.trim().orEmpty()
        if (key.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: key"
            )
        }

        return try {
            val value = EnvPreferences.getInstance(context).getEnv(key)
            val resultJson =
                JSONObject().apply {
                    put("key", key)
                    put("value", value ?: JSONObject.NULL)
                    put("exists", value != null)
                }
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(resultJson.toString())
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to read environment variable: $key"
            )
        }
    }

    fun writeEnvironmentVariable(tool: AITool): ToolResult {
        val key = tool.parameters.find { it.name == "key" }?.value?.trim().orEmpty()
        if (key.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: key"
            )
        }

        val value = tool.parameters.find { it.name == "value" }?.value ?: ""
        return try {
            val envPreferences = EnvPreferences.getInstance(context)
            if (value.trim().isEmpty()) {
                envPreferences.removeEnv(key)
            } else {
                envPreferences.setEnv(key, value.trim())
            }

            val current = envPreferences.getEnv(key)
            val resultJson =
                JSONObject().apply {
                    put("key", key)
                    put("requestedValue", value)
                    put("value", current ?: JSONObject.NULL)
                    put("exists", current != null)
                    put("cleared", value.trim().isEmpty())
                }
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(resultJson.toString())
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to write environment variable: $key"
            )
        }
    }

    fun listSandboxPackages(tool: AITool, packageManager: PackageManager): ToolResult {
        return try {
            val availablePackages = packageManager.getAvailablePackages(forceRefresh = true)
            val importedSet = packageManager.getImportedPackages().toSet()
            val disabledSet = packageManager.getDisabledPackages().toSet()
            val externalPackagesPath = packageManager.getExternalPackagesPath()

            val packagesJson = JSONArray()
            availablePackages.entries
                .sortedBy { it.key.lowercase() }
                .forEach { (packageName, pkg) ->
                    val imported = importedSet.contains(packageName)
                    packagesJson.put(
                        JSONObject().apply {
                            put("packageName", packageName)
                            put("displayName", pkg.displayName.resolve(context))
                            put("description", pkg.description.resolve(context))
                            put("isBuiltIn", pkg.isBuiltIn)
                            put("enabledByDefault", pkg.enabledByDefault)
                            put("enabled", imported)
                            put("imported", imported)
                            put("isDisabledByUser", disabledSet.contains(packageName))
                            put("toolCount", pkg.tools.size)
                            put("manageMode", if (pkg.isBuiltIn) "toggle_only" else "file_and_toggle")
                        }
                    )
                }

            val resultJson =
                JSONObject().apply {
                    put("externalPackagesPath", externalPackagesPath)
                    put(
                        "scriptDevGuide",
                        "https://github.com/AAswordman/Operit/blob/main/docs/SCRIPT_DEV_GUIDE.md"
                    )
                    put("totalCount", availablePackages.size)
                    put("builtInCount", availablePackages.values.count { it.isBuiltIn })
                    put("externalCount", availablePackages.values.count { !it.isBuiltIn })
                    put("enabledCount", availablePackages.keys.count { importedSet.contains(it) })
                    put("disabledCount", availablePackages.keys.count { !importedSet.contains(it) })
                    put("packages", packagesJson)
                }

            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(resultJson.toString())
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to list sandbox packages"
            )
        }
    }

    fun setSandboxPackageEnabled(tool: AITool, packageManager: PackageManager): ToolResult {
        val packageName = tool.parameters.find { it.name == "package_name" }?.value?.trim().orEmpty()
        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: package_name"
            )
        }

        val enabledRaw = tool.parameters.find { it.name == "enabled" }?.value
        val enabled = parseBooleanParameter(enabledRaw)
        if (enabled == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Invalid required parameter: enabled (expected true/false)"
            )
        }

        val availablePackages = packageManager.getAvailablePackages(forceRefresh = true)
        if (!availablePackages.containsKey(packageName)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Sandbox package not found: $packageName"
            )
        }

        val previousEnabled = packageManager.isPackageImported(packageName)
        val operationMessage =
            if (enabled) {
                packageManager.importPackage(packageName)
            } else {
                packageManager.removePackage(packageName)
            }
        val currentEnabled = packageManager.isPackageImported(packageName)
        val success = currentEnabled == enabled

        val resultJson =
            JSONObject().apply {
                put("packageName", packageName)
                put("requestedEnabled", enabled)
                put("previousEnabled", previousEnabled)
                put("currentEnabled", currentEnabled)
                put("message", operationMessage)
            }

        return ToolResult(
            toolName = tool.name,
            success = success,
            result = StringResultData(resultJson.toString()),
            error =
                if (success) {
                    null
                } else {
                    "Failed to update sandbox package switch: $packageName"
                }
        )
    }

    suspend fun getSpeechServicesConfig(tool: AITool): ToolResult {
        return try {
            val prefs = SpeechServicesPreferences(context)
            val ttsServiceType = prefs.ttsServiceTypeFlow.first()
            val ttsHttpConfig = prefs.ttsHttpConfigFlow.first()
            val ttsCleanerRegexs = prefs.ttsCleanerRegexsFlow.first()
            val ttsSpeechRate = prefs.ttsSpeechRateFlow.first()
            val ttsPitch = prefs.ttsPitchFlow.first()

            val sttServiceType = prefs.sttServiceTypeFlow.first()
            val sttHttpConfig = prefs.sttHttpConfigFlow.first()

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    SpeechServicesConfigResultData(
                        ttsServiceType = ttsServiceType.name,
                        ttsHttpConfig =
                            SpeechTtsHttpConfigResultItem(
                                urlTemplate = ttsHttpConfig.urlTemplate,
                                apiKeySet = ttsHttpConfig.apiKey.isNotBlank(),
                                apiKeyPreview = maskSecret(ttsHttpConfig.apiKey),
                                headers = ttsHttpConfig.headers,
                                httpMethod = ttsHttpConfig.httpMethod,
                                requestBody = ttsHttpConfig.requestBody,
                                contentType = ttsHttpConfig.contentType,
                                voiceId = ttsHttpConfig.voiceId,
                                modelName = ttsHttpConfig.modelName
                            ),
                        ttsCleanerRegexs = ttsCleanerRegexs,
                        ttsSpeechRate = ttsSpeechRate,
                        ttsPitch = ttsPitch,
                        sttServiceType = sttServiceType.name,
                        sttHttpConfig =
                            SpeechSttHttpConfigResultItem(
                                endpointUrl = sttHttpConfig.endpointUrl,
                                apiKeySet = sttHttpConfig.apiKey.isNotBlank(),
                                apiKeyPreview = maskSecret(sttHttpConfig.apiKey),
                                modelName = sttHttpConfig.modelName
                            )
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to get speech services config"
            )
        }
    }

    suspend fun setSpeechServicesConfig(tool: AITool): ToolResult {
        return try {
            val prefs = SpeechServicesPreferences(context)

            val currentTtsServiceType = prefs.ttsServiceTypeFlow.first()
            val currentTtsHttpConfig = prefs.ttsHttpConfigFlow.first()
            val currentTtsCleanerRegexs = prefs.ttsCleanerRegexsFlow.first()
            val currentTtsSpeechRate = prefs.ttsSpeechRateFlow.first()
            val currentTtsPitch = prefs.ttsPitchFlow.first()

            val currentSttServiceType = prefs.sttServiceTypeFlow.first()
            val currentSttHttpConfig = prefs.sttHttpConfigFlow.first()

            val hasField = { name: String -> tool.parameters.any { it.name == name } }

            val ttsServiceType =
                getParameterValue(tool, "tts_service_type")?.let { raw ->
                    VoiceServiceFactory.VoiceServiceType.values().firstOrNull {
                        it.name.equals(raw.trim(), ignoreCase = true)
                    } ?: throw IllegalArgumentException("Invalid tts_service_type: $raw")
                } ?: currentTtsServiceType

            val sttServiceType =
                getParameterValue(tool, "stt_service_type")?.let { raw ->
                    when {
                        raw.trim().equals("SHERPA_MNN", ignoreCase = true) ->
                            SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN

                        else ->
                            SpeechServiceFactory.SpeechServiceType.values().firstOrNull {
                                it.name.equals(raw.trim(), ignoreCase = true)
                            } ?: throw IllegalArgumentException("Invalid stt_service_type: $raw")
                    }
                } ?: currentSttServiceType

            val ttsHeaders =
                if (hasField("tts_headers")) {
                    val raw = getParameterValue(tool, "tts_headers").orEmpty().trim()
                    if (raw.isBlank()) {
                        emptyMap()
                    } else {
                        val jsonObj =
                            try {
                                JSONObject(raw)
                            } catch (_: Exception) {
                                throw IllegalArgumentException("Invalid JSON object parameter: tts_headers")
                            }
                        val headers = mutableMapOf<String, String>()
                        jsonObj.keys().forEach { key ->
                            headers[key] = jsonObj.optString(key, "")
                        }
                        headers
                    }
                } else {
                    currentTtsHttpConfig.headers
                }

            val ttsCleanerRegexs =
                if (hasField("tts_cleaner_regexs")) {
                    val raw = getParameterValue(tool, "tts_cleaner_regexs").orEmpty().trim()
                    if (raw.isBlank()) {
                        emptyList()
                    } else {
                        val arr =
                            try {
                                JSONArray(raw)
                            } catch (_: Exception) {
                                throw IllegalArgumentException(
                                    "Invalid JSON array parameter: tts_cleaner_regexs"
                                )
                            }
                        buildList {
                            for (i in 0 until arr.length()) {
                                val item = arr.optString(i, "").trim()
                                if (item.isNotBlank()) add(item)
                            }
                        }
                    }
                } else {
                    currentTtsCleanerRegexs
                }

            val ttsHttpMethod =
                if (hasField("tts_http_method")) {
                    val method = getParameterValue(tool, "tts_http_method").orEmpty().trim().uppercase()
                    if (method != "GET" && method != "POST") {
                        throw IllegalArgumentException("Invalid tts_http_method: $method (expected GET/POST)")
                    }
                    method
                } else {
                    currentTtsHttpConfig.httpMethod
                }

            val ttsSpeechRate =
                if (hasField("tts_speech_rate")) {
                    getParameterValue(tool, "tts_speech_rate")?.trim()?.toFloatOrNull()
                        ?: throw IllegalArgumentException("Invalid number parameter: tts_speech_rate")
                } else {
                    currentTtsSpeechRate
                }

            val ttsPitch =
                if (hasField("tts_pitch")) {
                    getParameterValue(tool, "tts_pitch")?.trim()?.toFloatOrNull()
                        ?: throw IllegalArgumentException("Invalid number parameter: tts_pitch")
                } else {
                    currentTtsPitch
                }

            val ttsHttpConfig =
                currentTtsHttpConfig.copy(
                    urlTemplate =
                        if (hasField("tts_url_template")) {
                            getParameterValue(tool, "tts_url_template").orEmpty().trim()
                        } else {
                            currentTtsHttpConfig.urlTemplate
                        },
                    apiKey =
                        if (hasField("tts_api_key")) {
                            getParameterValue(tool, "tts_api_key").orEmpty().trim()
                        } else {
                            currentTtsHttpConfig.apiKey
                        },
                    headers = ttsHeaders,
                    httpMethod = ttsHttpMethod,
                    requestBody =
                        if (hasField("tts_request_body")) {
                            getParameterValue(tool, "tts_request_body").orEmpty()
                        } else {
                            currentTtsHttpConfig.requestBody
                        },
                    contentType =
                        if (hasField("tts_content_type")) {
                            getParameterValue(tool, "tts_content_type").orEmpty().trim()
                        } else {
                            currentTtsHttpConfig.contentType
                        },
                    voiceId =
                        if (hasField("tts_voice_id")) {
                            getParameterValue(tool, "tts_voice_id").orEmpty().trim()
                        } else {
                            currentTtsHttpConfig.voiceId
                        },
                    modelName =
                        if (hasField("tts_model_name")) {
                            getParameterValue(tool, "tts_model_name").orEmpty().trim()
                        } else {
                            currentTtsHttpConfig.modelName
                        }
                )

            val sttHttpConfig =
                currentSttHttpConfig.copy(
                    endpointUrl =
                        if (hasField("stt_endpoint_url")) {
                            getParameterValue(tool, "stt_endpoint_url").orEmpty().trim()
                        } else {
                            currentSttHttpConfig.endpointUrl
                        },
                    apiKey =
                        if (hasField("stt_api_key")) {
                            getParameterValue(tool, "stt_api_key").orEmpty().trim()
                        } else {
                            currentSttHttpConfig.apiKey
                        },
                    modelName =
                        if (hasField("stt_model_name")) {
                            getParameterValue(tool, "stt_model_name").orEmpty().trim()
                        } else {
                            currentSttHttpConfig.modelName
                        }
                )

            val updateFieldNames =
                listOf(
                    "tts_service_type",
                    "tts_url_template",
                    "tts_api_key",
                    "tts_headers",
                    "tts_http_method",
                    "tts_request_body",
                    "tts_content_type",
                    "tts_voice_id",
                    "tts_model_name",
                    "tts_cleaner_regexs",
                    "tts_speech_rate",
                    "tts_pitch",
                    "stt_service_type",
                    "stt_endpoint_url",
                    "stt_api_key",
                    "stt_model_name"
                ).filter { hasField(it) }
            val hasAnyUpdate = updateFieldNames.isNotEmpty()

            if (!hasAnyUpdate) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "No update fields provided for speech services config"
                )
            }

            prefs.saveTtsSettings(
                serviceType = ttsServiceType,
                httpConfig = ttsHttpConfig,
                cleanerRegexs = ttsCleanerRegexs,
                speechRate = ttsSpeechRate,
                pitch = ttsPitch
            )
            prefs.saveSttSettings(
                serviceType = sttServiceType,
                httpConfig = sttHttpConfig
            )

            VoiceServiceFactory.resetInstance()
            SpeechServiceFactory.resetInstance()

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    SpeechServicesUpdateResultData(
                        updated = true,
                        changedFields = updateFieldNames,
                        ttsServiceType = ttsServiceType.name,
                        sttServiceType = sttServiceType.name,
                        ttsApiKeySet = ttsHttpConfig.apiKey.isNotBlank(),
                        sttApiKeySet = sttHttpConfig.apiKey.isNotBlank()
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Invalid parameter"
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to set speech services config"
            )
        }
    }

    suspend fun listModelConfigs(tool: AITool): ToolResult {
        return try {
            val modelConfigManager = ModelConfigManager(context)
            val functionalConfigManager = FunctionalConfigManager(context)
            modelConfigManager.initializeIfNeeded()
            functionalConfigManager.initializeIfNeeded()

            val configIds = modelConfigManager.configListFlow.first()
            val mappingWithIndex = functionalConfigManager.functionConfigMappingWithIndexFlow.first()

            val configById = mutableMapOf<String, ModelConfigData>()
            val configs = mutableListOf<ModelConfigResultItem>()
            configIds.forEach { configId ->
                val config = modelConfigManager.getModelConfigFlow(configId).first()
                configById[configId] = config
                configs.add(modelConfigToResultItem(config))
            }

            val functionMappings = mutableListOf<FunctionModelMappingResultItem>()
            mappingWithIndex.entries
                .sortedBy { it.key.name }
                .forEach { (functionType, mapping) ->
                    val config = configById[mapping.configId]
                    functionMappings.add(
                        FunctionModelMappingResultItem(
                            functionType = functionType.name,
                            configId = mapping.configId,
                            configName = config?.name,
                            modelIndex = mapping.modelIndex,
                            selectedModel = config?.let { getModelByIndex(it.modelName, mapping.modelIndex) }
                        )
                    )
                }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    ModelConfigsResultData(
                        totalConfigCount = configIds.size,
                        defaultConfigId = ModelConfigManager.DEFAULT_CONFIG_ID,
                        configs = configs,
                        functionMappings = functionMappings
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to list model configs"
            )
        }
    }

    suspend fun createModelConfig(tool: AITool): ToolResult {
        return try {
            val modelConfigManager = ModelConfigManager(context)
            modelConfigManager.initializeIfNeeded()

            val name =
                getParameterValue(tool, "name")?.trim().takeUnless { it.isNullOrBlank() }
                    ?: "New Model Config"
            val configId = modelConfigManager.createConfig(name)
            val created = modelConfigManager.getModelConfigFlow(configId).first()

            val (updated, changedFields) = applyModelConfigUpdates(tool, created, includeName = false)
            val finalConfig =
                if (changedFields.isNotEmpty()) {
                    modelConfigManager.saveModelConfig(updated)
                    updated
                } else {
                    created
                }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    ModelConfigCreateResultData(
                        created = true,
                        config = modelConfigToResultItem(finalConfig),
                        changedFields = changedFields
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Invalid parameter"
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to create model config"
            )
        }
    }

    suspend fun updateModelConfig(tool: AITool): ToolResult {
        val configId = getParameterValue(tool, "config_id")?.trim().orEmpty()
        if (configId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: config_id"
            )
        }

        return try {
            val modelConfigManager = ModelConfigManager(context)
            val functionalConfigManager = FunctionalConfigManager(context)
            modelConfigManager.initializeIfNeeded()
            functionalConfigManager.initializeIfNeeded()

            val current =
                modelConfigManager.getModelConfig(configId)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Model config not found: $configId"
                    )

            val (updated, changedFields) = applyModelConfigUpdates(tool, current, includeName = true)
            val finalConfig =
                if (changedFields.isNotEmpty()) {
                    modelConfigManager.saveModelConfig(updated)
                    updated
                } else {
                    current
                }

            val mappingWithIndex = functionalConfigManager.functionConfigMappingWithIndexFlow.first()
            val affectedFunctions =
                mappingWithIndex.entries
                    .filter { it.value.configId == configId }
                    .map { it.key }
                    .sortedBy { it.name }
            affectedFunctions.forEach { functionType ->
                runCatching { EnhancedAIService.refreshServiceForFunction(context, functionType) }
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    ModelConfigUpdateResultData(
                        updated = changedFields.isNotEmpty(),
                        config = modelConfigToResultItem(finalConfig),
                        changedFields = changedFields,
                        affectedFunctions = affectedFunctions.map { it.name }
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Invalid parameter"
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to update model config: $configId"
            )
        }
    }

    suspend fun deleteModelConfig(tool: AITool): ToolResult {
        val configId = getParameterValue(tool, "config_id")?.trim().orEmpty()
        if (configId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: config_id"
            )
        }
        if (configId == ModelConfigManager.DEFAULT_CONFIG_ID) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "The default model config cannot be deleted"
            )
        }

        return try {
            val modelConfigManager = ModelConfigManager(context)
            val functionalConfigManager = FunctionalConfigManager(context)
            modelConfigManager.initializeIfNeeded()
            functionalConfigManager.initializeIfNeeded()

            val configList = modelConfigManager.configListFlow.first()
            if (!configList.contains(configId)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Model config not found: $configId"
                )
            }

            val mappingWithIndex = functionalConfigManager.functionConfigMappingWithIndexFlow.first()
            val updatedMapping = mappingWithIndex.toMutableMap()
            val affectedFunctions = mutableListOf<FunctionType>()

            mappingWithIndex.forEach { (functionType, mapping) ->
                if (mapping.configId == configId) {
                    updatedMapping[functionType] =
                        FunctionConfigMapping(
                            configId = FunctionalConfigManager.DEFAULT_CONFIG_ID,
                            modelIndex = 0
                        )
                    affectedFunctions.add(functionType)
                }
            }

            if (affectedFunctions.isNotEmpty()) {
                functionalConfigManager.saveFunctionConfigMappingWithIndex(updatedMapping)
            }

            modelConfigManager.deleteConfig(configId)

            affectedFunctions
                .sortedBy { it.name }
                .forEach { functionType ->
                    runCatching { EnhancedAIService.refreshServiceForFunction(context, functionType) }
                }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    ModelConfigDeleteResultData(
                        deleted = true,
                        configId = configId,
                        affectedFunctions = affectedFunctions.sortedBy { it.name }.map { it.name },
                        fallbackConfigId = FunctionalConfigManager.DEFAULT_CONFIG_ID
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to delete model config: $configId"
            )
        }
    }

    suspend fun listFunctionModelConfigs(tool: AITool): ToolResult {
        return try {
            val functionalConfigManager = FunctionalConfigManager(context)
            functionalConfigManager.initializeIfNeeded()

            val mappingWithIndex = functionalConfigManager.functionConfigMappingWithIndexFlow.first()

            val mappings = mutableListOf<FunctionModelMappingResultItem>()
            FunctionType.values().forEach { functionType ->
                val mapping =
                    mappingWithIndex[functionType]
                        ?: FunctionConfigMapping(FunctionalConfigManager.DEFAULT_CONFIG_ID, 0)
                mappings.add(
                    FunctionModelMappingResultItem(
                        functionType = functionType.name,
                        configId = mapping.configId,
                        modelIndex = mapping.modelIndex
                    )
                )
            }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    FunctionModelConfigsResultData(
                        defaultConfigId = FunctionalConfigManager.DEFAULT_CONFIG_ID,
                        mappings = mappings
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to list function model configs"
            )
        }
    }

    suspend fun getFunctionModelConfig(tool: AITool): ToolResult {
        val functionTypeRaw = getParameterValue(tool, "function_type")?.trim().orEmpty()
        if (functionTypeRaw.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: function_type"
            )
        }

        return try {
            val functionType =
                parseFunctionType(functionTypeRaw)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Invalid function_type: $functionTypeRaw"
                    )

            val modelConfigManager = ModelConfigManager(context)
            val functionalConfigManager = FunctionalConfigManager(context)
            modelConfigManager.initializeIfNeeded()
            functionalConfigManager.initializeIfNeeded()

            val mappingWithIndex = functionalConfigManager.functionConfigMappingWithIndexFlow.first()
            val mapping =
                mappingWithIndex[functionType]
                    ?: FunctionConfigMapping(FunctionalConfigManager.DEFAULT_CONFIG_ID, 0)

            val config =
                modelConfigManager.getModelConfig(mapping.configId)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Model config not found: ${mapping.configId}"
                    )

            val actualModelIndex = getValidModelIndex(config.modelName, mapping.modelIndex)
            val selectedModel = getModelByIndex(config.modelName, actualModelIndex)

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    FunctionModelConfigResultData(
                        defaultConfigId = FunctionalConfigManager.DEFAULT_CONFIG_ID,
                        functionType = functionType.name,
                        configId = mapping.configId,
                        configName = config.name,
                        modelIndex = mapping.modelIndex,
                        actualModelIndex = actualModelIndex,
                        selectedModel = selectedModel,
                        config = modelConfigToResultItem(config)
                    )
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to get function model config"
            )
        }
    }

    suspend fun setFunctionModelConfig(tool: AITool): ToolResult {
        val functionTypeRaw = getParameterValue(tool, "function_type")?.trim().orEmpty()
        if (functionTypeRaw.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: function_type"
            )
        }
        val configId = getParameterValue(tool, "config_id")?.trim().orEmpty()
        if (configId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: config_id"
            )
        }

        return try {
            val functionType =
                parseFunctionType(functionTypeRaw)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Invalid function_type: $functionTypeRaw"
                    )
            val requestedModelIndex =
                getOptionalIntParameter(tool, "model_index")?.coerceAtLeast(0) ?: 0

            val modelConfigManager = ModelConfigManager(context)
            val functionalConfigManager = FunctionalConfigManager(context)
            modelConfigManager.initializeIfNeeded()
            functionalConfigManager.initializeIfNeeded()

            val config =
                modelConfigManager.getModelConfig(configId)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Model config not found: $configId"
                    )
            val actualModelIndex = getValidModelIndex(config.modelName, requestedModelIndex)
            val selectedModel = getModelByIndex(config.modelName, actualModelIndex)

            functionalConfigManager.setConfigForFunction(functionType, configId, actualModelIndex)
            runCatching { EnhancedAIService.refreshServiceForFunction(context, functionType) }

            ToolResult(
                toolName = tool.name,
                success = true,
                result =
                    FunctionModelBindingResultData(
                        functionType = functionType.name,
                        configId = configId,
                        configName = config.name,
                        requestedModelIndex = requestedModelIndex,
                        actualModelIndex = actualModelIndex,
                        selectedModel = selectedModel
                    )
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Invalid parameter"
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to set function model config"
            )
        }
    }

    suspend fun testModelConfigConnection(tool: AITool): ToolResult {
        val configId = getParameterValue(tool, "config_id")?.trim().orEmpty()
        if (configId.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: config_id"
            )
        }

        return try {
            val requestedModelIndex =
                getOptionalIntParameter(tool, "model_index")?.coerceAtLeast(0) ?: 0
            val modelConfigManager = ModelConfigManager(context)
            modelConfigManager.initializeIfNeeded()

            val config =
                modelConfigManager.getModelConfig(configId)
                    ?: return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Model config not found: $configId"
                    )

            val customHeadersJson = ApiPreferences.getInstance(context).getCustomHeaders()
            val report =
                ModelConfigConnectionTester.run(
                    context = context,
                    modelConfigManager = modelConfigManager,
                    config = config,
                    customHeadersJson = customHeadersJson,
                    requestedModelIndex = requestedModelIndex
                )

            val testItems =
                report.items.map { item ->
                    ModelConfigConnectionTestItemResultData(
                        type = item.type.name.lowercase(),
                        success = item.success,
                        error = item.error
                    )
                }

            ToolResult(
                toolName = tool.name,
                success = report.success,
                result =
                    ModelConfigConnectionTestResultData(
                        configId = report.configId,
                        configName = report.configName,
                        providerType = report.providerType,
                        requestedModelIndex = report.requestedModelIndex,
                        actualModelIndex = report.actualModelIndex,
                        testedModelName = report.testedModelName,
                        strictToolCallFallbackUsed = report.strictToolCallFallbackUsed,
                        success = report.success,
                        totalTests = report.items.size,
                        passedTests = report.items.count { it.success },
                        failedTests = report.items.count { !it.success },
                        tests = testItems
                    ),
                error = if (report.success) null else "One or more connection tests failed"
            )
        } catch (e: IllegalArgumentException) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Invalid parameter"
            )
        } catch (e: Exception) {
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = e.message ?: "Failed to test model config connection"
            )
        }
    }

    suspend fun restartMcpWithLogs(tool: AITool): ToolResult {
        val timeoutMs =
            tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull()
                ?.coerceIn(5000L, 600000L)
                ?: 120000L

        val pluginLoadingState = PluginLoadingStateRegistry.getState()
        val lifecycleScope = PluginLoadingStateRegistry.getScope()

        if (pluginLoadingState == null || lifecycleScope == null) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Plugin loading state is unavailable. Open the main screen and retry."
            )
        }

        pluginLoadingState.reset()
        pluginLoadingState.show()
        pluginLoadingState.initializeMCPServer(context, lifecycleScope)

        val startAt = System.currentTimeMillis()
        while (true) {
            val elapsed = System.currentTimeMillis() - startAt
            val finished =
                pluginLoadingState.progress.value >= 0.999f &&
                    pluginLoadingState.message.value.isNotBlank()
            if (finished || elapsed >= timeoutMs) {
                break
            }
            delay(250L)
        }

        val elapsedMs = System.currentTimeMillis() - startAt
        val timedOut = elapsedMs >= timeoutMs
        val plugins = pluginLoadingState.plugins.value
        val pluginLogs = pluginLoadingState.pluginLogs.value
        val failedCount = plugins.count { it.status == PluginStatus.FAILED }
        val successCount = plugins.count { it.status == PluginStatus.SUCCESS }

        val pluginsJson = JSONArray()
        plugins.forEach { plugin ->
            pluginsJson.put(
                JSONObject().apply {
                    put("id", plugin.id)
                    put("displayName", plugin.displayName)
                    put("shortName", plugin.shortName)
                    put("status", plugin.status.name.lowercase())
                    put("message", plugin.message)
                    put("serviceName", plugin.serviceName)
                    put("log", pluginLogs[plugin.id].orEmpty())
                }
            )
        }

        val extraLogsJson = JSONObject()
        pluginLogs.forEach { (pluginId, logText) ->
            if (plugins.none { it.id == pluginId }) {
                extraLogsJson.put(pluginId, logText)
            }
        }

        val resultJson =
            JSONObject().apply {
                put("timeoutMs", timeoutMs)
                put("elapsedMs", elapsedMs)
                put("timedOut", timedOut)
                put("progress", pluginLoadingState.progress.value.toDouble())
                put("message", pluginLoadingState.message.value)
                put("pluginsTotal", pluginLoadingState.pluginsTotal.value)
                put("pluginsStarted", pluginLoadingState.pluginsStarted.value)
                put("successCount", successCount)
                put("failedCount", failedCount)
                put("plugins", pluginsJson)
                put("extraLogs", extraLogsJson)
            }

        val hasFailures = failedCount > 0
        return ToolResult(
            toolName = tool.name,
            success = !timedOut && !hasFailures,
            result = StringResultData(resultJson.toString()),
            error =
                when {
                    timedOut -> "MCP restart timed out after ${elapsedMs}ms"
                    hasFailures -> "Some MCP plugins failed to start"
                    else -> null
                }
        )
    }

    private fun getParameterValue(tool: AITool, name: String): String? {
        return tool.parameters.find { it.name == name }?.value
    }

    private fun getOptionalIntParameter(tool: AITool, name: String): Int? {
        val raw = getParameterValue(tool, name) ?: return null
        return raw.trim().toIntOrNull()
            ?: throw IllegalArgumentException("Invalid integer parameter: $name")
    }

    private fun parseFunctionType(value: String): FunctionType? {
        return FunctionType.values().firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }

    private fun parseApiProviderType(value: String): ApiProviderType? {
        return ApiProviderType.values().firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
    }

    private fun applyModelConfigUpdates(
        tool: AITool,
        current: ModelConfigData,
        includeName: Boolean
    ): Pair<ModelConfigData, List<String>> {
        var updated = current
        val changedFields = mutableListOf<String>()

        fun applyString(name: String, transform: (ModelConfigData, String) -> ModelConfigData) {
            val value = getParameterValue(tool, name) ?: return
            val trimmed = value.trim()
            updated = transform(updated, trimmed)
            changedFields.add(name)
        }

        fun applyInt(name: String, transform: (ModelConfigData, Int) -> ModelConfigData) {
            val raw = getParameterValue(tool, name) ?: return
            val parsed =
                raw.trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid integer parameter: $name")
            updated = transform(updated, parsed)
            changedFields.add(name)
        }

        fun applyFloat(name: String, transform: (ModelConfigData, Float) -> ModelConfigData) {
            val raw = getParameterValue(tool, name) ?: return
            val parsed =
                raw.trim().toFloatOrNull()
                    ?: throw IllegalArgumentException("Invalid number parameter: $name")
            updated = transform(updated, parsed)
            changedFields.add(name)
        }

        fun applyBoolean(name: String, transform: (ModelConfigData, Boolean) -> ModelConfigData) {
            val raw = getParameterValue(tool, name) ?: return
            val parsed =
                parseBooleanParameter(raw)
                    ?: throw IllegalArgumentException("Invalid boolean parameter: $name")
            updated = transform(updated, parsed)
            changedFields.add(name)
        }

        if (includeName) {
            applyString("name") { config, value -> config.copy(name = value) }
        }

        applyString("api_key") { config, value -> config.copy(apiKey = value) }
        applyString("api_endpoint") { config, value -> config.copy(apiEndpoint = value) }
        applyString("model_name") { config, value -> config.copy(modelName = value) }

        getParameterValue(tool, "api_provider_type")?.let { raw ->
            val provider =
                parseApiProviderType(raw)
                    ?: throw IllegalArgumentException("Invalid api_provider_type: $raw")
            updated = updated.copy(apiProviderType = provider)
            changedFields.add("api_provider_type")
        }

        applyBoolean("max_tokens_enabled") { config, value -> config.copy(maxTokensEnabled = value) }
        applyInt("max_tokens") { config, value -> config.copy(maxTokens = value.coerceAtLeast(1)) }
        applyBoolean("temperature_enabled") { config, value -> config.copy(temperatureEnabled = value) }
        applyFloat("temperature") { config, value -> config.copy(temperature = value) }
        applyBoolean("top_p_enabled") { config, value -> config.copy(topPEnabled = value) }
        applyFloat("top_p") { config, value -> config.copy(topP = value) }
        applyBoolean("top_k_enabled") { config, value -> config.copy(topKEnabled = value) }
        applyInt("top_k") { config, value -> config.copy(topK = value.coerceAtLeast(0)) }
        applyBoolean("presence_penalty_enabled") { config, value ->
            config.copy(presencePenaltyEnabled = value)
        }
        applyFloat("presence_penalty") { config, value -> config.copy(presencePenalty = value) }
        applyBoolean("frequency_penalty_enabled") { config, value ->
            config.copy(frequencyPenaltyEnabled = value)
        }
        applyFloat("frequency_penalty") { config, value -> config.copy(frequencyPenalty = value) }
        applyBoolean("repetition_penalty_enabled") { config, value ->
            config.copy(repetitionPenaltyEnabled = value)
        }
        applyFloat("repetition_penalty") { config, value -> config.copy(repetitionPenalty = value) }
        applyFloat("context_length") { config, value ->
            config.copy(contextLength = value.coerceAtLeast(1f))
        }
        applyFloat("max_context_length") { config, value ->
            config.copy(maxContextLength = value.coerceAtLeast(1f))
        }
        applyBoolean("enable_max_context_mode") { config, value ->
            config.copy(enableMaxContextMode = value)
        }
        applyFloat("summary_token_threshold") { config, value ->
            config.copy(summaryTokenThreshold = value.coerceIn(0f, 1f))
        }
        applyBoolean("enable_summary") { config, value -> config.copy(enableSummary = value) }
        applyBoolean("enable_summary_by_message_count") { config, value ->
            config.copy(enableSummaryByMessageCount = value)
        }
        applyInt("summary_message_count_threshold") { config, value ->
            config.copy(summaryMessageCountThreshold = value.coerceAtLeast(1))
        }
        applyString("custom_parameters") { config, value ->
            val json = value.ifBlank { "[]" }
            try {
                JSONArray(json)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid JSON array parameter: custom_parameters")
            }
            config.copy(
                customParameters = json,
                hasCustomParameters = json != "[]"
            )
        }

        applyInt("mnn_forward_type") { config, value -> config.copy(mnnForwardType = value) }
        applyInt("mnn_thread_count") { config, value -> config.copy(mnnThreadCount = value.coerceAtLeast(1)) }
        applyInt("llama_thread_count") { config, value -> config.copy(llamaThreadCount = value.coerceAtLeast(1)) }
        applyInt("llama_context_size") { config, value -> config.copy(llamaContextSize = value.coerceAtLeast(1)) }
        applyInt("request_limit_per_minute") { config, value ->
            config.copy(requestLimitPerMinute = value.coerceAtLeast(0))
        }
        applyInt("max_concurrent_requests") { config, value ->
            config.copy(maxConcurrentRequests = value.coerceAtLeast(0))
        }

        applyBoolean("enable_direct_image_processing") { config, value ->
            config.copy(enableDirectImageProcessing = value)
        }
        applyBoolean("enable_direct_audio_processing") { config, value ->
            config.copy(enableDirectAudioProcessing = value)
        }
        applyBoolean("enable_direct_video_processing") { config, value ->
            config.copy(enableDirectVideoProcessing = value)
        }
        applyBoolean("enable_google_search") { config, value -> config.copy(enableGoogleSearch = value) }
        applyBoolean("enable_tool_call") { config, value -> config.copy(enableToolCall = value) }
        applyBoolean("strict_tool_call") { config, value -> config.copy(strictToolCall = value) }

        if (updated.apiProviderType == ApiProviderType.MNN) {
            if (updated.enableToolCall) {
                updated = updated.copy(enableToolCall = false)
            }
            if (updated.strictToolCall) {
                updated = updated.copy(strictToolCall = false)
            }
        } else if (!updated.enableToolCall && updated.strictToolCall) {
            updated = updated.copy(strictToolCall = false)
        }

        return updated to changedFields.distinct()
    }

    private fun modelConfigToResultItem(config: ModelConfigData): ModelConfigResultItem {
        return ModelConfigResultItem(
            id = config.id,
            name = config.name,
            apiProviderType = config.apiProviderType.name,
            apiEndpoint = config.apiEndpoint,
            modelName = config.modelName,
            modelList = getModelList(config.modelName),
            apiKeySet = config.apiKey.isNotBlank(),
            apiKeyPreview = maskSecret(config.apiKey),
            maxTokensEnabled = config.maxTokensEnabled,
            maxTokens = config.maxTokens,
            temperatureEnabled = config.temperatureEnabled,
            temperature = config.temperature,
            topPEnabled = config.topPEnabled,
            topP = config.topP,
            topKEnabled = config.topKEnabled,
            topK = config.topK,
            presencePenaltyEnabled = config.presencePenaltyEnabled,
            presencePenalty = config.presencePenalty,
            frequencyPenaltyEnabled = config.frequencyPenaltyEnabled,
            frequencyPenalty = config.frequencyPenalty,
            repetitionPenaltyEnabled = config.repetitionPenaltyEnabled,
            repetitionPenalty = config.repetitionPenalty,
            hasCustomParameters = config.hasCustomParameters,
            customParameters = config.customParameters,
            contextLength = config.contextLength,
            maxContextLength = config.maxContextLength,
            enableMaxContextMode = config.enableMaxContextMode,
            summaryTokenThreshold = config.summaryTokenThreshold,
            enableSummary = config.enableSummary,
            enableSummaryByMessageCount = config.enableSummaryByMessageCount,
            summaryMessageCountThreshold = config.summaryMessageCountThreshold,
            mnnForwardType = config.mnnForwardType,
            mnnThreadCount = config.mnnThreadCount,
            llamaThreadCount = config.llamaThreadCount,
            llamaContextSize = config.llamaContextSize,
            enableDirectImageProcessing = config.enableDirectImageProcessing,
            enableDirectAudioProcessing = config.enableDirectAudioProcessing,
            enableDirectVideoProcessing = config.enableDirectVideoProcessing,
            enableGoogleSearch = config.enableGoogleSearch,
            enableToolCall = config.enableToolCall,
            strictToolCall = config.strictToolCall,
            requestLimitPerMinute = config.requestLimitPerMinute,
            maxConcurrentRequests = config.maxConcurrentRequests,
            useMultipleApiKeys = config.useMultipleApiKeys,
            apiKeyPoolCount = config.apiKeyPool.size
        )
    }

    private fun maskSecret(value: String): String {
        if (value.isBlank()) return ""
        return when {
            value.length <= 4 -> "*".repeat(value.length)
            else -> "${value.take(3)}***${value.takeLast(2)}"
        }
    }

    private fun parseBooleanParameter(value: String?): Boolean? {
        return when (value?.trim()?.lowercase()) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> null
        }
    }
}

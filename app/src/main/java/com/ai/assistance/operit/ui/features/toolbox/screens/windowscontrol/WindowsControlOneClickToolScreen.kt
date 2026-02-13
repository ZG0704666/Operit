package com.ai.assistance.operit.ui.features.toolbox.screens.windowscontrol

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.util.OperitPaths
import java.io.File
import java.io.FileNotFoundException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val WINDOWS_PACKAGE_NAME = "windows_control"
private const val WINDOWS_TOOL_TEST_CONNECTION_SCOPED = "windows_control:windows_test_connection"
private const val PC_AGENT_ZIP_NAME = "operit-pc-agent.zip"

private const val KEY_BASE_URL = "WINDOWS_AGENT_BASE_URL"
private const val KEY_TOKEN = "WINDOWS_AGENT_TOKEN"
private const val KEY_DEFAULT_SHELL = "WINDOWS_AGENT_DEFAULT_SHELL"
private const val KEY_TIMEOUT_MS = "WINDOWS_AGENT_TIMEOUT_MS"

private enum class ConnectionCardStatus {
    Idle,
    Checking,
    NotConfigured,
    Success,
    Failed
}

private data class ConnectionCardModel(
    val status: ConnectionCardStatus = ConnectionCardStatus.Idle,
    val baseUrl: String = "",
    val packageVersion: String = "",
    val agentVersion: String = "",
    val durationMs: String = "",
    val command: String = "",
    val error: String = ""
)

private data class ParsedConnectionPayload(
    val success: Boolean?,
    val baseUrl: String,
    val packageVersion: String,
    val agentVersion: String,
    val durationMs: String,
    val command: String,
    val error: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun WindowsControlOneClickToolScreen(navController: NavController) {
    CustomScaffold { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            WindowsControlOneClickScreen()
        }
    }
}

@Composable
private fun WindowsControlOneClickScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val envPreferences = remember { EnvPreferences.getInstance(context) }
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    val packageManager = remember { PackageManager.getInstance(context, toolHandler) }

    var baseUrl by remember { mutableStateOf(envPreferences.getEnv(KEY_BASE_URL).orEmpty()) }
    var token by remember { mutableStateOf(envPreferences.getEnv(KEY_TOKEN).orEmpty()) }
    var defaultShell by remember { mutableStateOf(envPreferences.getEnv(KEY_DEFAULT_SHELL).orEmpty()) }
    var timeoutMs by remember { mutableStateOf(envPreferences.getEnv(KEY_TIMEOUT_MS).orEmpty()) }
    var pastedConfigText by remember { mutableStateOf("") }

    var isSharingZip by remember { mutableStateOf(false) }
    var isSavingConfig by remember { mutableStateOf(false) }
    var isCheckingConnection by remember { mutableStateOf(false) }

    var step1Message by remember { mutableStateOf<String?>(null) }
    var step2Message by remember { mutableStateOf<String?>(null) }
    var connectionCard by remember { mutableStateOf(ConnectionCardModel()) }
    var connectionFixBaseUrlInput by remember { mutableStateOf(baseUrl) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun checkConnectionByTool() {
        scope.launch {
            isCheckingConnection = true
            connectionCard = connectionCard.copy(status = ConnectionCardStatus.Checking)
            try {
                val isImported = packageManager.isPackageImported(WINDOWS_PACKAGE_NAME)
                val savedBaseUrl = envPreferences.getEnv(KEY_BASE_URL).orEmpty().trim()
                val savedToken = envPreferences.getEnv(KEY_TOKEN).orEmpty().trim()
                if (!isImported || savedBaseUrl.isEmpty() || savedToken.isEmpty()) {
                    connectionCard =
                        ConnectionCardModel(
                            status = ConnectionCardStatus.NotConfigured,
                            baseUrl = savedBaseUrl,
                            error =
                                context.getString(
                                    R.string.windows_one_click_connection_package_not_enabled
                                )
                        )
                    connectionFixBaseUrlInput = savedBaseUrl
                    return@launch
                }

                val result =
                    withContext(Dispatchers.IO) {
                        toolHandler.executeTool(
                            AITool(
                                name = WINDOWS_TOOL_TEST_CONNECTION_SCOPED,
                                parameters = listOf(ToolParameter("timeout_ms", "8000"))
                            )
                        )
                    }

                val rawPayload = result.result.toString().trim()
                val parsed = parseConnectionPayload(rawPayload)
                val actualSuccess = parsed?.success ?: result.success
                val resolvedError =
                    firstNonBlank(
                        parsed?.error,
                        if (!actualSuccess) result.error else "",
                        if (!actualSuccess) rawPayload else ""
                    )

                connectionCard =
                    ConnectionCardModel(
                        status =
                            if (actualSuccess) {
                                ConnectionCardStatus.Success
                            } else {
                                ConnectionCardStatus.Failed
                            },
                        baseUrl = firstNonBlank(parsed?.baseUrl, savedBaseUrl),
                        packageVersion = parsed?.packageVersion.orEmpty(),
                        agentVersion = parsed?.agentVersion.orEmpty(),
                        durationMs = parsed?.durationMs.orEmpty(),
                        command = parsed?.command.orEmpty(),
                        error = resolvedError
                    )
                connectionFixBaseUrlInput = firstNonBlank(parsed?.baseUrl, savedBaseUrl)
            } catch (e: Exception) {
                connectionCard =
                    connectionCard.copy(
                        status = ConnectionCardStatus.Failed,
                        error = e.message ?: "unknown"
                    )
                connectionFixBaseUrlInput = firstNonBlank(connectionCard.baseUrl, baseUrl)
            } finally {
                isCheckingConnection = false
            }
        }
    }

    fun applyNewBaseUrlAndRetry() {
        errorMessage = null
        val rawInput = connectionFixBaseUrlInput.trim()
        if (rawInput.isEmpty()) {
            errorMessage = context.getString(R.string.windows_one_click_error_host_required)
            return
        }

        val normalized = normalizeBaseUrlInput(rawInput, connectionCard.baseUrl)
        baseUrl = normalized
        connectionFixBaseUrlInput = normalized
        saveOrRemoveEnv(envPreferences, KEY_BASE_URL, normalized)
        checkConnectionByTool()
    }

    fun sharePcAgentZip() {
        scope.launch {
            isSharingZip = true
            step1Message = null
            errorMessage = null
            try {
                val exportedZip = withContext(Dispatchers.IO) {
                    copyPcAgentZipToShareDirectory(context)
                }

                val shareResult = withContext(Dispatchers.IO) {
                    toolHandler.executeTool(
                        AITool(
                            name = "share_file",
                            parameters = listOf(
                                ToolParameter("path", exportedZip.absolutePath),
                                ToolParameter("title", context.getString(R.string.windows_one_click_share_title))
                            )
                        )
                    )
                }

                if (!shareResult.success) {
                    throw IllegalStateException(
                        shareResult.error ?: context.getString(R.string.windows_one_click_step1_error_share)
                    )
                }

                step1Message =
                    context.getString(
                        R.string.windows_one_click_step1_success,
                        exportedZip.absolutePath
                    )
            } catch (_: FileNotFoundException) {
                errorMessage = context.getString(R.string.windows_one_click_step1_error_missing_asset)
            } catch (e: Exception) {
                errorMessage =
                    context.getString(
                        R.string.windows_one_click_status_error,
                        e.message ?: "unknown"
                    )
            } finally {
                isSharingZip = false
            }
        }
    }

    fun saveConfigAndActivatePackage(
        baseUrlInput: String = baseUrl,
        tokenInput: String = token,
        defaultShellInput: String = defaultShell,
        timeoutMsInput: String = timeoutMs
    ) {
        scope.launch {
            isSavingConfig = true
            step2Message = null
            errorMessage = null

            val baseUrlValue = baseUrlInput.trim()
            if (baseUrlValue.isEmpty()) {
                errorMessage = context.getString(R.string.windows_one_click_error_host_required)
                isSavingConfig = false
                return@launch
            }

            val tokenValue = tokenInput.trim()
            if (tokenValue.isEmpty()) {
                errorMessage = context.getString(R.string.windows_one_click_error_token_required)
                isSavingConfig = false
                return@launch
            }

            try {
                withContext(Dispatchers.IO) {
                    saveOrRemoveEnv(envPreferences, KEY_BASE_URL, baseUrlValue)
                    saveOrRemoveEnv(envPreferences, KEY_TOKEN, tokenValue)
                    saveOrRemoveEnv(envPreferences, KEY_DEFAULT_SHELL, defaultShellInput.trim())
                    saveOrRemoveEnv(envPreferences, KEY_TIMEOUT_MS, timeoutMsInput.trim())

                    val importResult = packageManager.importPackage(WINDOWS_PACKAGE_NAME)
                    val importOk =
                        importResult.startsWith("Successfully imported package:") ||
                            importResult.contains("already imported")
                    if (!importOk) {
                        throw IllegalStateException(importResult)
                    }

                    val useResult = packageManager.usePackage(WINDOWS_PACKAGE_NAME)
                    if (!useResult.contains("Using package: $WINDOWS_PACKAGE_NAME")) {
                        throw IllegalStateException(useResult)
                    }
                }

                step2Message = context.getString(R.string.windows_one_click_status_success)
                checkConnectionByTool()
            } catch (e: Exception) {
                errorMessage =
                    context.getString(
                        R.string.windows_one_click_status_error,
                        e.message ?: "unknown"
                    )
            } finally {
                isSavingConfig = false
            }
        }
    }

    fun pasteConfigAndApply() {
        val raw = pastedConfigText.trim()
        if (raw.isEmpty()) {
            errorMessage = context.getString(R.string.windows_one_click_error_paste_empty)
            return
        }

        try {
            val payload = JSONObject(raw)
            val baseUrlValue = payload.optString(KEY_BASE_URL, baseUrl).trim()
            val tokenValue = payload.optString(KEY_TOKEN, token).trim()
            val defaultShellValue = payload.optString(KEY_DEFAULT_SHELL, defaultShell).trim()
            val timeoutMsValue = payload.optString(KEY_TIMEOUT_MS, timeoutMs).trim()

            baseUrl = baseUrlValue
            token = tokenValue
            defaultShell = defaultShellValue
            timeoutMs = timeoutMsValue
            connectionFixBaseUrlInput = baseUrlValue

            saveConfigAndActivatePackage(
                baseUrlInput = baseUrlValue,
                tokenInput = tokenValue,
                defaultShellInput = defaultShellValue,
                timeoutMsInput = timeoutMsValue
            )
        } catch (e: Exception) {
            errorMessage =
                context.getString(
                    R.string.windows_one_click_error_paste_invalid,
                    e.message ?: "unknown"
                )
        }
    }

    LaunchedEffect(Unit) {
        checkConnectionByTool()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.windows_one_click_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = stringResource(R.string.windows_one_click_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ConnectionOverviewCard(
            model = connectionCard,
            editableBaseUrl = connectionFixBaseUrlInput,
            onEditableBaseUrlChange = { connectionFixBaseUrlInput = it },
            onApplyBaseUrl = { applyNewBaseUrlAndRetry() },
            isCheckingConnection = isCheckingConnection
        )

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.windows_one_click_step1_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = stringResource(R.string.windows_one_click_step1_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { sharePcAgentZip() },
                    enabled = !isSharingZip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSharingZip) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp).height(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.windows_one_click_exporting))
                    } else {
                        Text(stringResource(R.string.windows_one_click_step1_button))
                    }
                }
            }
        }

        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.windows_one_click_step2_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = stringResource(R.string.windows_one_click_step2_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = pastedConfigText,
                    onValueChange = { pastedConfigText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.windows_one_click_field_config_text)) },
                    placeholder = { Text(stringResource(R.string.windows_one_click_field_config_text_placeholder)) },
                    minLines = 8
                )

                Text(
                    text = stringResource(R.string.windows_one_click_tip_env),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = { pasteConfigAndApply() },
                    enabled = !isSavingConfig,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSavingConfig) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp).height(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.windows_one_click_applying))
                    } else {
                        Text(stringResource(R.string.windows_one_click_button_paste_apply))
                    }
                }

                Button(
                    onClick = { checkConnectionByTool() },
                    enabled = !isCheckingConnection,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isCheckingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(16.dp).height(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.windows_one_click_connection_checking))
                    } else {
                        Text(stringResource(R.string.windows_one_click_connection_button))
                    }
                }
            }
        }

        step1Message?.let { msg ->
            StatusCard(text = msg, isError = false)
        }

        step2Message?.let { msg ->
            StatusCard(text = msg, isError = false)
        }

        errorMessage?.let { msg ->
            StatusCard(text = msg, isError = true)
        }
    }
}

@Composable
private fun ConnectionOverviewCard(
    model: ConnectionCardModel,
    editableBaseUrl: String,
    onEditableBaseUrlChange: (String) -> Unit,
    onApplyBaseUrl: () -> Unit,
    isCheckingConnection: Boolean
) {
    val statusTextRes =
        when (model.status) {
            ConnectionCardStatus.Idle -> R.string.windows_one_click_connection_state_idle
            ConnectionCardStatus.Checking -> R.string.windows_one_click_connection_state_checking
            ConnectionCardStatus.NotConfigured ->
                R.string.windows_one_click_connection_state_not_configured
            ConnectionCardStatus.Success -> R.string.windows_one_click_connection_state_ok
            ConnectionCardStatus.Failed -> R.string.windows_one_click_connection_state_failed
        }

    val containerColor =
        when (model.status) {
            ConnectionCardStatus.Success -> MaterialTheme.colorScheme.primaryContainer
            ConnectionCardStatus.Failed -> MaterialTheme.colorScheme.errorContainer
            ConnectionCardStatus.NotConfigured -> MaterialTheme.colorScheme.secondaryContainer
            ConnectionCardStatus.Checking -> MaterialTheme.colorScheme.tertiaryContainer
            ConnectionCardStatus.Idle -> MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        when (model.status) {
            ConnectionCardStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
            ConnectionCardStatus.Success -> MaterialTheme.colorScheme.onPrimaryContainer
            ConnectionCardStatus.NotConfigured -> MaterialTheme.colorScheme.onSecondaryContainer
            ConnectionCardStatus.Checking -> MaterialTheme.colorScheme.onTertiaryContainer
            ConnectionCardStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val icon =
        when (model.status) {
            ConnectionCardStatus.Success -> Icons.Default.CheckCircle
            ConnectionCardStatus.Failed -> Icons.Default.Error
            ConnectionCardStatus.NotConfigured -> Icons.Default.Settings
            ConnectionCardStatus.Checking -> Icons.Default.Settings
            ConnectionCardStatus.Idle -> Icons.Default.Computer
        }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = contentColor)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.windows_one_click_connection_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                    Text(
                        text = stringResource(statusTextRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }

            if (model.status == ConnectionCardStatus.Checking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(16.dp).height(16.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.windows_one_click_connection_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            } else {
                ConnectionMetaRow(
                    label = stringResource(R.string.windows_one_click_connection_field_base_url),
                    value = model.baseUrl,
                    contentColor = contentColor
                )
                ConnectionMetaRow(
                    label =
                        stringResource(
                            R.string.windows_one_click_connection_field_package_version
                        ),
                    value = model.packageVersion,
                    contentColor = contentColor
                )
                ConnectionMetaRow(
                    label = stringResource(R.string.windows_one_click_connection_field_agent_version),
                    value = model.agentVersion,
                    contentColor = contentColor
                )
                ConnectionMetaRow(
                    label = stringResource(R.string.windows_one_click_connection_field_duration),
                    value = model.durationMs,
                    contentColor = contentColor
                )
                ConnectionMetaRow(
                    label = stringResource(R.string.windows_one_click_connection_field_command),
                    value = model.command,
                    contentColor = contentColor
                )
                ConnectionMetaRow(
                    label = stringResource(R.string.windows_one_click_connection_field_error),
                    value = model.error,
                    contentColor = contentColor
                )

                if (
                    model.status == ConnectionCardStatus.Failed ||
                        model.status == ConnectionCardStatus.NotConfigured
                ) {
                    OutlinedTextField(
                        value = editableBaseUrl,
                        onValueChange = onEditableBaseUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                stringResource(
                                    R.string.windows_one_click_connection_fix_base_url_label
                                )
                            )
                        },
                        placeholder = {
                            Text(
                                stringResource(
                                    R.string
                                        .windows_one_click_connection_fix_base_url_placeholder
                                )
                            )
                        },
                        singleLine = true
                    )

                    Button(
                        onClick = onApplyBaseUrl,
                        enabled = !isCheckingConnection && editableBaseUrl.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCheckingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(16.dp).height(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.windows_one_click_connection_checking))
                        } else {
                            Text(
                                stringResource(
                                    R.string.windows_one_click_connection_fix_apply_button
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionMetaRow(label: String, value: String, contentColor: androidx.compose.ui.graphics.Color) {
    if (value.isBlank()) {
        return
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.width(96.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatusCard(text: String, isError: Boolean) {
    val containerColor =
        if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val contentColor =
        if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    val icon = if (isError) Icons.Default.Error else Icons.Default.CheckCircle

    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun firstNonBlank(vararg values: String?): String {
    for (value in values) {
        if (!value.isNullOrBlank()) {
            return value.trim()
        }
    }
    return ""
}

private fun normalizeBaseUrlInput(input: String, fallbackBaseUrl: String): String {
    val raw = input.trim().trimEnd('/')
    if (raw.isBlank()) {
        return ""
    }

    val fallbackPort = extractPortFromUrl(fallbackBaseUrl) ?: 58321
    val withScheme =
        if (raw.startsWith("http://", ignoreCase = true) ||
            raw.startsWith("https://", ignoreCase = true)
        ) {
            raw
        } else {
            "http://$raw"
        }

    return try {
        val uri = URI(withScheme)
        val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: "http"
        val host = uri.host?.trim().orEmpty()
        if (host.isBlank()) {
            return withScheme.trimEnd('/')
        }

        val port = if (uri.port in 1..65535) uri.port else fallbackPort
        val path = uri.rawPath?.takeIf { it.isNotBlank() && it != "/" } ?: ""
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        val fragment = uri.rawFragment?.let { "#$it" } ?: ""

        "$scheme://$host:$port$path$query$fragment".trimEnd('/')
    } catch (_: Exception) {
        withScheme.trimEnd('/')
    }
}

private fun extractPortFromUrl(raw: String): Int? {
    if (raw.isBlank()) {
        return null
    }

    return try {
        val uri = URI(raw.trim())
        if (uri.port in 1..65535) uri.port else null
    } catch (_: Exception) {
        null
    }
}

private fun parseConnectionPayload(raw: String): ParsedConnectionPayload? {
    if (raw.isBlank()) {
        return null
    }

    return try {
        val obj = JSONObject(raw)
        val health = obj.optJSONObject("health")
        val hasSuccess = obj.has("success") && !obj.isNull("success")
        val success = if (hasSuccess) obj.optBoolean("success") else null

        val durationValue = obj.opt("durationMs")
        val durationMs =
            if (durationValue == null || durationValue == JSONObject.NULL) {
                ""
            } else {
                durationValue.toString().trim()
            }

        ParsedConnectionPayload(
            success = success,
            baseUrl = obj.optString("agentBaseUrl").trim(),
            packageVersion = obj.optString("packageVersion").trim(),
            agentVersion =
                firstNonBlank(
                    obj.optString("agentVersion").trim(),
                    health?.optString("version")?.trim(),
                    health?.optString("agentVersion")?.trim()
                ),
            durationMs = durationMs,
            command = obj.optString("command").trim(),
            error = obj.optString("error").trim()
        )
    } catch (_: Exception) {
        null
    }
}

private fun saveOrRemoveEnv(envPreferences: EnvPreferences, key: String, value: String) {
    if (value.isBlank()) {
        envPreferences.removeEnv(key)
    } else {
        envPreferences.setEnv(key, value)
    }
}

@Throws(FileNotFoundException::class)
private fun copyPcAgentZipToShareDirectory(context: Context): File {
    val targetDir = OperitPaths.cleanOnExitDir()
    if (!targetDir.exists() && !targetDir.mkdirs()) {
        throw IllegalStateException("Failed to create export directory: ${targetDir.absolutePath}")
    }

    val outputFile = File(targetDir, PC_AGENT_ZIP_NAME)
    val assetPath = "pc_agent/$PC_AGENT_ZIP_NAME"

    context.assets.open(assetPath).use { input ->
        outputFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }

    return outputFile
}

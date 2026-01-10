package com.ai.assistance.operit.ui.features.assistant.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.WakeWordPreferences
import com.ai.assistance.operit.ui.features.assistant.components.AvatarConfigSection
import com.ai.assistance.operit.ui.features.assistant.components.AvatarPreviewSection
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel
import kotlinx.coroutines.launch

/** 助手配置屏幕 提供DragonBones模型预览和相关配置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantConfigScreen() {
    val context = LocalContext.current
    val viewModel: AssistantConfigViewModel =
        viewModel(factory = AssistantConfigViewModel.Factory(context))
    val uiState by viewModel.uiState.collectAsState()

    val wakePrefs = remember { WakeWordPreferences(context.applicationContext) }
    val wakeListeningEnabled by wakePrefs.alwaysListeningEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_ALWAYS_LISTENING_ENABLED)
    val wakePhrase by wakePrefs.wakePhraseFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_PHRASE)
    val wakePhraseRegexEnabled by wakePrefs.wakePhraseRegexEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_PHRASE_REGEX_ENABLED)
    val inactivityTimeoutSeconds by wakePrefs.voiceCallInactivityTimeoutSecondsFlow.collectAsState(
        initial = WakeWordPreferences.DEFAULT_VOICE_CALL_INACTIVITY_TIMEOUT_SECONDS
    )
    val wakeGreetingEnabled by wakePrefs.wakeGreetingEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_GREETING_ENABLED)
    val wakeGreetingText by wakePrefs.wakeGreetingTextFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT)
    val voiceAutoAttachEnabled by wakePrefs.voiceAutoAttachEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_VOICE_AUTO_ATTACH_ENABLED)
    val voiceAutoAttachScreenEnabled by wakePrefs.voiceAutoAttachScreenEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_VOICE_AUTO_ATTACH_SCREEN_ENABLED)
    val voiceAutoAttachNotificationsEnabled by wakePrefs.voiceAutoAttachNotificationsEnabledFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_VOICE_AUTO_ATTACH_NOTIFICATIONS_ENABLED)
    val voiceAutoAttachScreenKeyword by wakePrefs.voiceAutoAttachScreenKeywordFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_VOICE_AUTO_ATTACH_SCREEN_KEYWORD)
    val voiceAutoAttachNotificationsKeyword by wakePrefs.voiceAutoAttachNotificationsKeywordFlow.collectAsState(initial = WakeWordPreferences.DEFAULT_VOICE_AUTO_ATTACH_NOTIFICATIONS_KEYWORD)
    val coroutineScope = rememberCoroutineScope()

    val requestMicPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                coroutineScope.launch {
                    wakePrefs.saveAlwaysListeningEnabled(true)
                }
            } else {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.microphone_permission_denied_toast),
                    android.widget.Toast.LENGTH_SHORT
                )
                    .show()
            }
        }

    var wakePhraseInput by remember { mutableStateOf("") }
    var inactivityTimeoutInput by remember { mutableStateOf("") }
    var wakeGreetingTextInput by remember { mutableStateOf("") }
    var voiceAutoAttachScreenKeywordInput by remember { mutableStateOf("") }
    var voiceAutoAttachNotificationsKeywordInput by remember { mutableStateOf("") }

    var voiceWakeupExpanded by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(wakePhrase) {
        if (wakePhraseInput.isBlank()) {
            wakePhraseInput = wakePhrase
        }
    }

    LaunchedEffect(inactivityTimeoutSeconds) {
        if (inactivityTimeoutInput.isBlank()) {
            inactivityTimeoutInput = inactivityTimeoutSeconds.toString()
        }
    }

    LaunchedEffect(wakeGreetingText) {
        if (wakeGreetingTextInput.isBlank()) {
            wakeGreetingTextInput = wakeGreetingText
        }
    }

    LaunchedEffect(voiceAutoAttachScreenKeyword) {
        if (voiceAutoAttachScreenKeywordInput.isBlank()) {
            voiceAutoAttachScreenKeywordInput = voiceAutoAttachScreenKeyword
        }
    }

    LaunchedEffect(voiceAutoAttachNotificationsKeyword) {
        if (voiceAutoAttachNotificationsKeywordInput.isBlank()) {
            voiceAutoAttachNotificationsKeywordInput = voiceAutoAttachNotificationsKeyword
        }
    }

    // 启动文件选择器
    val zipFileLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    // 导入选择的zip文件
                    viewModel.importAvatarFromZip(uri)
                }
            }
        }

    // 打开文件选择器的函数
    val openZipFilePicker = {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(
                    Intent.EXTRA_MIME_TYPES,
                    arrayOf("application/zip", "application/x-zip-compressed")
                )
            }
        zipFileLauncher.launch(intent)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState(initial = uiState.scrollPosition)

    // 在 Composable 函数中获取字符串资源，以便在 LaunchedEffect 中使用
    val operationSuccessString = context.getString(R.string.operation_success)
    val errorOccurredString = context.getString(R.string.error_occurred_simple)

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { position ->
            viewModel.updateScrollPosition(position)
        }
    }

    // 显示操作结果的 SnackBar
    LaunchedEffect(uiState.operationSuccess, uiState.errorMessage) {
        if (uiState.operationSuccess) {
            snackbarHostState.showSnackbar(operationSuccessString)
            viewModel.clearOperationSuccess()
        } else if (uiState.errorMessage != null) {
            snackbarHostState.showSnackbar(uiState.errorMessage ?: errorOccurredString)
            viewModel.clearErrorMessage()
        }
    }

    CustomScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 主要内容
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(scrollState)
            ) {
                // Avatar预览区域
                AvatarPreviewSection(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    uiState = uiState,
                    onDeleteCurrentModel =
                        uiState.currentAvatarConfig?.let { model ->
                            { viewModel.deleteAvatar(model.id) }
                        }
                )

                Spacer(modifier = Modifier.height(8.dp))

                AvatarConfigSection(
                    viewModel = viewModel,
                    uiState = uiState,
                    onImportClick = { openZipFilePicker() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Voice Wake-up Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { voiceWakeupExpanded = !voiceWakeupExpanded }
                            .padding(start = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.voice_wakeup_section_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (voiceWakeupExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription =
                                if (voiceWakeupExpanded) stringResource(R.string.collapse)
                                else stringResource(R.string.expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (voiceWakeupExpanded) Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Always Listening
                        CompactSwitchRow(
                            title = stringResource(R.string.voice_wakeup_always_listen_title),
                            description = stringResource(R.string.voice_wakeup_always_listen_desc),
                            checked = wakeListeningEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val granted =
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        coroutineScope.launch {
                                            wakePrefs.saveAlwaysListeningEnabled(true)
                                        }
                                    } else {
                                        requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                } else {
                                    coroutineScope.launch {
                                        wakePrefs.saveAlwaysListeningEnabled(false)
                                    }
                                }
                            }
                        )

                        // Wake Phrase Input
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = wakePhraseInput,
                            onValueChange = { newValue ->
                                wakePhraseInput = newValue
                                coroutineScope.launch {
                                    wakePrefs.saveWakePhrase(newValue.ifBlank { WakeWordPreferences.DEFAULT_WAKE_PHRASE })
                                }
                            },
                            singleLine = true,
                            label = { Text(stringResource(R.string.voice_wakeup_phrase_label)) },
                            supportingText = { Text(stringResource(R.string.voice_wakeup_phrase_supporting)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        // Regex Toggle
                        CompactSwitchRow(
                            title = stringResource(R.string.voice_wakeup_regex_title),
                            description = stringResource(R.string.voice_wakeup_regex_desc),
                            checked = wakePhraseRegexEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    wakePrefs.saveWakePhraseRegexEnabled(enabled)
                                }
                            }
                        )

                        // Timeout Input
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = inactivityTimeoutInput,
                            onValueChange = { newValue ->
                                val filtered = newValue.filter { it.isDigit() }
                                inactivityTimeoutInput = filtered
                                val parsed = filtered.toIntOrNull()
                                if (parsed != null) {
                                    val clamped = parsed.coerceIn(1, 600)
                                    coroutineScope.launch {
                                        wakePrefs.saveVoiceCallInactivityTimeoutSeconds(clamped)
                                    }
                                }
                            },
                            singleLine = true,
                            label = { Text(stringResource(R.string.voice_wakeup_inactivity_timeout_label)) },
                            supportingText = { Text(stringResource(R.string.voice_wakeup_inactivity_timeout_supporting)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        // Greeting Toggle
                        CompactSwitchRow(
                            title = stringResource(R.string.voice_wakeup_greeting_title),
                            description = stringResource(R.string.voice_wakeup_greeting_desc),
                            checked = wakeGreetingEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    wakePrefs.saveWakeGreetingEnabled(enabled)
                                }
                            }
                        )

                        // Greeting Text Input
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = wakeGreetingTextInput,
                            onValueChange = { newValue ->
                                wakeGreetingTextInput = newValue
                                coroutineScope.launch {
                                    wakePrefs.saveWakeGreetingText(newValue.ifBlank { WakeWordPreferences.DEFAULT_WAKE_GREETING_TEXT })
                                }
                            },
                            singleLine = true,
                            enabled = wakeGreetingEnabled,
                            label = { Text(stringResource(R.string.voice_wakeup_greeting_text_label)) },
                            supportingText = { Text(stringResource(R.string.voice_wakeup_greeting_text_supporting)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 0.dp))

                        Text(
                            text = stringResource(R.string.voice_keyword_attachments_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )

                        CompactSwitchRow(
                            title = stringResource(R.string.voice_keyword_attachments_enabled_title),
                            description = stringResource(R.string.voice_keyword_attachments_enabled_desc),
                            checked = voiceAutoAttachEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    wakePrefs.saveVoiceAutoAttachEnabled(enabled)
                                }
                            }
                        )

                        CompactSwitchRow(
                            title = stringResource(R.string.voice_keyword_attachments_screen_title),
                            description = stringResource(R.string.voice_keyword_attachments_screen_desc),
                            checked = voiceAutoAttachEnabled && voiceAutoAttachScreenEnabled,
                            enabled = voiceAutoAttachEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    wakePrefs.saveVoiceAutoAttachScreenEnabled(enabled)
                                }
                            }
                        )

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = voiceAutoAttachScreenKeywordInput,
                            onValueChange = { newValue ->
                                voiceAutoAttachScreenKeywordInput = newValue
                                coroutineScope.launch {
                                    wakePrefs.saveVoiceAutoAttachScreenKeyword(newValue)
                                }
                            },
                            singleLine = true,
                            enabled = voiceAutoAttachEnabled && voiceAutoAttachScreenEnabled,
                            label = { Text(stringResource(R.string.voice_keyword_attachments_screen_keyword_label)) },
                            supportingText = { Text(stringResource(R.string.voice_keyword_attachments_keyword_supporting)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )

                        CompactSwitchRow(
                            title = stringResource(R.string.voice_keyword_attachments_notifications_title),
                            description = stringResource(R.string.voice_keyword_attachments_notifications_desc),
                            checked = voiceAutoAttachEnabled && voiceAutoAttachNotificationsEnabled,
                            enabled = voiceAutoAttachEnabled,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    wakePrefs.saveVoiceAutoAttachNotificationsEnabled(enabled)
                                }
                            }
                        )

                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = voiceAutoAttachNotificationsKeywordInput,
                            onValueChange = { newValue ->
                                voiceAutoAttachNotificationsKeywordInput = newValue
                                coroutineScope.launch {
                                    wakePrefs.saveVoiceAutoAttachNotificationsKeyword(newValue)
                                }
                            },
                            singleLine = true,
                            enabled = voiceAutoAttachEnabled && voiceAutoAttachNotificationsEnabled,
                            label = { Text(stringResource(R.string.voice_keyword_attachments_notifications_keyword_label)) },
                            supportingText = { Text(stringResource(R.string.voice_keyword_attachments_keyword_supporting)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }
                }

                // 底部空间
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 加载指示器覆盖层
            if (uiState.isLoading || uiState.isImporting) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surface
                                    .copy(alpha = 0.7f)
                            ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text =
                                if (uiState.isImporting) stringResource(R.string.importing_model)
                                else stringResource(R.string.processing),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

package com.ai.assistance.operit.ui.features.assistant.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel

@Composable
fun AvatarConfigSection(
    viewModel: AssistantConfigViewModel,
    uiState: AssistantConfigViewModel.UiState,
    onImportClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.avatar_config),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Expanded Content
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        ModelSelector(
                            models = uiState.avatarConfigs,
                            currentModelId = uiState.currentAvatarConfig?.id,
                            onModelSelected = { viewModel.switchAvatar(it) },
                            onModelDelete = { viewModel.deleteAvatar(it) }
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onImportClick) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.import_model),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (uiState.currentAvatarConfig != null && uiState.config != null) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Scale Slider
                    Text(
                        text = stringResource(R.string.scale, String.format("%.2f", uiState.config.scale)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = uiState.config.scale,
                        onValueChange = { viewModel.updateScale(it) },
                        valueRange = 0.1f..2.0f
                    )

                    // TranslationX Slider
                    Text(
                        text = stringResource(R.string.x_translation, String.format("%.1f", uiState.config.translateX)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = uiState.config.translateX,
                        onValueChange = { viewModel.updateTranslateX(it) },
                        valueRange = -500f..500f
                    )

                    // TranslationY Slider
                    Text(
                        text = stringResource(R.string.y_translation, String.format("%.1f", uiState.config.translateY)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = uiState.config.translateY,
                        onValueChange = { viewModel.updateTranslateY(it) },
                        valueRange = -500f..500f
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                HowToImportSection()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
        models: List<com.ai.assistance.operit.data.repository.AvatarConfig>,
        currentModelId: String?,
        onModelSelected: (String) -> Unit,
        onModelDelete: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val currentModel = models.find { it.id == currentModelId }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    if (showDeleteDialog != null) {
        AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text(stringResource(R.string.confirm_delete_model_title)) },
                text = { Text(stringResource(R.string.confirm_delete_model_message)) },
                confirmButton = {
                    TextButton(
                            onClick = {
                                onModelDelete(showDeleteDialog!!)
                                showDeleteDialog = null
                            }
                    ) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
                value = currentModel?.name ?: stringResource(R.string.select_model),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.current_model)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (model in models) {
                DropdownMenuItem(
                        text = { Text(model.name) },
                        onClick = {
                            onModelSelected(model.id)
                            expanded = false
                        },
                        trailingIcon = {
                            IconButton(onClick = { showDeleteDialog = model.id }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        }
                )
            }
        }
    }
}

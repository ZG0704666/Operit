package com.ai.assistance.operit.ui.features.packages.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.skill.SkillPackage
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun SkillManagerScreen(
    packageManager: PackageManager,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var skills by remember { mutableStateOf<Map<String, SkillPackage>>(emptyMap()) }

    var selectedSkillName by remember { mutableStateOf<String?>(null) }
    var selectedSkillContent by remember { mutableStateOf<String?>(null) }

    val refreshSkills: suspend () -> Unit = {
        skills = packageManager.getAvailableSkillPackages()
    }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    var fileName: String? = null
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex("_display_name")
                        if (cursor.moveToFirst() && nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }

                    val fileNameNonNull = fileName ?: "skill.zip"
                    if (!fileNameNonNull.endsWith(".zip", ignoreCase = true)) {
                        snackbarHostState.showSnackbar("仅支持 .zip 文件")
                        return@launch
                    }

                    val inputStream = context.contentResolver.openInputStream(uri)
                    val tempFile = File(context.cacheDir, fileNameNonNull)
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    val result = packageManager.importSkillFromZip(tempFile)
                    tempFile.delete()

                    refreshSkills()
                    snackbarHostState.showSnackbar(result)
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("导入失败: ${e.message}")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshSkills()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.skills),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            modifier = Modifier.size(24.dp),
                            onClick = {
                                scope.launch {
                                    refreshSkills()
                                    snackbarHostState.showSnackbar("已刷新")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = packageManager.getSkillsDirectoryPath(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (skills.isEmpty()) {
                Text(
                    text = "未找到任何Skill。请将Skill文件夹放入: ${packageManager.getSkillsDirectoryPath()}，并确保其中包含 SKILL.md。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    val sortedSkills = skills.values.sortedBy { it.name }
                    items(sortedSkills, key = { it.name }) { skill ->
                        SkillListItem(
                            skill = skill,
                            onClick = {
                                val name = skill.name
                                selectedSkillName = name
                                selectedSkillContent = packageManager.readSkillContent(name) ?: ""
                            }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { zipPicker.launch("application/zip") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.import_action)
            )
        }
    }

    if (selectedSkillName != null && selectedSkillContent != null) {
        val skillName = selectedSkillName!!
        AlertDialog(
            onDismissRequest = {
                selectedSkillName = null
                selectedSkillContent = null
            },
            title = { Text(text = skillName) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = selectedSkillContent ?: "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val ok = packageManager.deleteSkill(skillName)
                            if (ok) {
                                refreshSkills()
                                snackbarHostState.showSnackbar("已删除: $skillName")
                            } else {
                                snackbarHostState.showSnackbar("删除失败: $skillName")
                            }
                        }
                        selectedSkillName = null
                        selectedSkillContent = null
                    }
                ) {
                    Text(text = "删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedSkillName = null
                        selectedSkillContent = null
                    }
                ) {
                    Text(text = "关闭")
                }
            }
        )
    }
}

@Composable
private fun SkillListItem(
    skill: SkillPackage,
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .width(3.dp)
                    .height(22.dp),
                color = accentColor,
                shape = RoundedCornerShape(2.dp)
            ) {}
            Spacer(modifier = Modifier.width(10.dp))

            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = accentColor
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (skill.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = skill.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

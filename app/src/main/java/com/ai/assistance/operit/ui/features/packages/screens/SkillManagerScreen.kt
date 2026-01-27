package com.ai.assistance.operit.ui.features.packages.screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.core.tools.skill.SkillPackage
import com.ai.assistance.operit.data.preferences.SkillVisibilityPreferences
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SkillManagerScreen(
    skillRepository: SkillRepository,
    snackbarHostState: SnackbarHostState,
    onNavigateToSkillMarket: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val skillVisibilityPreferences = remember { SkillVisibilityPreferences.getInstance(context) }

    var skills by remember { mutableStateOf<Map<String, SkillPackage>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedSkillName by remember { mutableStateOf<String?>(null) }
    var selectedSkillContent by remember { mutableStateOf<String?>(null) }

    var showImportDialog by remember { mutableStateOf(false) }
    var importTabIndex by remember { mutableStateOf(0) }
    var repoUrlInput by remember { mutableStateOf("") }
    var zipUri by remember { mutableStateOf<Uri?>(null) }
    var zipFileName by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    val refreshSkills: suspend () -> Unit = {
        isLoading = true
        try {
            val loaded =
                withContext(Dispatchers.IO) {
                    skillRepository.getAvailableSkillPackages()
                }
            skills = loaded
        } finally {
            isLoading = false
        }
    }

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            zipUri = it
            try {
                var fileName: String? = null
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex("_display_name")
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
                zipFileName = fileName ?: "skill.zip"
            } catch (_: Exception) {
                zipFileName = "skill.zip"
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
                                    snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_refreshed))
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
                        text = skillRepository.getSkillsDirectoryPath(),
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
                    text = stringResource(R.string.skillmgr_no_skills_found, skillRepository.getSkillsDirectoryPath()),
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
                            skillVisibilityPreferences = skillVisibilityPreferences,
                            onClick = {
                                val name = skill.name
                                selectedSkillName = name
                                selectedSkillContent = null
                                scope.launch {
                                    val content =
                                        withContext(Dispatchers.IO) {
                                            skillRepository.readSkillContent(name)
                                        }
                                    selectedSkillContent = content ?: ""
                                }
                            }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = onNavigateToSkillMarket,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Store,
                    contentDescription = stringResource(R.string.screen_title_skill_market)
                )
            }

            FloatingActionButton(
                onClick = { showImportDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.import_action)
                )
            }
        }

        if (skills.isEmpty() && (isLoading || isImporting)) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) showImportDialog = false },
            title = { Text(stringResource(R.string.import_or_install_skill)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = importTabIndex,
                        edgePadding = 8.dp,
                        divider = {},
                        indicator = { tabPositions ->
                            if (importTabIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    with(TabRowDefaults) { Modifier.tabIndicatorOffset(tabPositions[importTabIndex]) }
                                )
                            }
                        }
                    ) {
                        Tab(
                            selected = importTabIndex == 0,
                            onClick = { importTabIndex = 0 },
                            text = { Text(stringResource(R.string.import_from_repo), maxLines = 1) }
                        )
                        Tab(
                            selected = importTabIndex == 1,
                            onClick = { importTabIndex = 1 },
                            text = { Text(stringResource(R.string.import_from_zip), maxLines = 1) }
                        )
                    }

                    when (importTabIndex) {
                        0 -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.enter_repo_info),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    enabled = !isImporting,
                                    onClick = {
                                        showImportDialog = false
                                        onNavigateToSkillMarket()
                                    }
                                ) {
                                    Text(stringResource(R.string.get_skill))
                                }
                            }

                            OutlinedTextField(
                                value = repoUrlInput,
                                onValueChange = { repoUrlInput = it },
                                label = { Text(stringResource(R.string.repo_link)) },
                                placeholder = { Text("https://github.com/username/repo") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                enabled = !isImporting
                            )
                        }

                        1 -> {
                            Text(
                                text = stringResource(R.string.select_skill_plugin_zip),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = zipFileName,
                                    onValueChange = { },
                                    label = { Text(stringResource(R.string.skill_zip_package)) },
                                    placeholder = { Text(stringResource(R.string.select_zip_file)) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    readOnly = true,
                                    enabled = !isImporting
                                )

                                IconButton(
                                    enabled = !isImporting,
                                    onClick = { zipPicker.launch("application/zip") }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = stringResource(R.string.select_file)
                                    )
                                }
                            }
                        }
                    }

                    if (isImporting) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.processing))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = {
                        scope.launch {
                            when (importTabIndex) {
                                0 -> {
                                    val url = repoUrlInput.trim()
                                    if (url.isBlank()) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.enter_repo_info))
                                        return@launch
                                    }
                                    isImporting = true
                                    try {
                                        val result = skillRepository.importSkillFromGitHubRepo(url)
                                        refreshSkills()
                                        snackbarHostState.showSnackbar(result)
                                        showImportDialog = false
                                    } finally {
                                        isImporting = false
                                    }
                                }

                                1 -> {
                                    val uri = zipUri
                                    if (uri == null) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.select_zip_file))
                                        return@launch
                                    }
                                    isImporting = true
                                    try {
                                        val nameToUse = zipFileName.ifBlank { "skill.zip" }
                                        if (!nameToUse.endsWith(".zip", ignoreCase = true)) {
                                            snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_only_zip_files))
                                            return@launch
                                        }

                                        val result = withContext(Dispatchers.IO) {
                                            val tempFile = File(context.cacheDir, nameToUse)
                                            try {
                                                context.contentResolver.openInputStream(uri)?.use { input ->
                                                    tempFile.outputStream().use { output ->
                                                        input.copyTo(output)
                                                    }
                                                } ?: throw IllegalStateException(context.getString(R.string.skillmgr_cannot_read_file))

                                                skillRepository.importSkillFromZip(tempFile)
                                            } finally {
                                                try {
                                                    tempFile.delete()
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }

                                        refreshSkills()
                                        snackbarHostState.showSnackbar(result)
                                        showImportDialog = false
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_import_failed, e.message ?: ""))
                                    } finally {
                                        isImporting = false
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.import_action))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isImporting,
                    onClick = { showImportDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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
                            val ok = skillRepository.deleteSkill(skillName)
                            if (ok) {
                                refreshSkills()
                                snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_deleted, skillName))
                            } else {
                                snackbarHostState.showSnackbar(context.getString(R.string.skillmgr_delete_failed, skillName))
                            }
                        }
                        selectedSkillName = null
                        selectedSkillContent = null
                    }
                ) {
                    Text(text = stringResource(R.string.skillmgr_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedSkillName = null
                        selectedSkillContent = null
                    }
                ) {
                    Text(text = stringResource(R.string.skillmgr_close))
                }
            }
        )
    }
}

@Composable
private fun SkillListItem(
    skill: SkillPackage,
    skillVisibilityPreferences: SkillVisibilityPreferences,
    onClick: () -> Unit
) {
    val accentColor = MaterialTheme.colorScheme.primary
    var visibleToAi by remember(skill.name) {
        mutableStateOf(skillVisibilityPreferences.isSkillVisibleToAi(skill.name))
    }

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

            Switch(
                modifier = Modifier.scale(0.8f),
                checked = visibleToAi,
                onCheckedChange = { checked ->
                    visibleToAi = checked
                    skillVisibilityPreferences.setSkillVisibleToAi(skill.name, checked)
                }
            )
        }
    }
}

package com.ai.assistance.operit.core.tools.packTool

import android.content.Context
import android.os.Looper
import com.ai.assistance.operit.core.chat.logMessageTiming
import com.ai.assistance.operit.core.chat.messageTimingNow
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.PackageToolExecutor
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.ToolPackageState
import com.ai.assistance.operit.core.tools.agent.ShowerController
import com.ai.assistance.operit.core.tools.condition.ConditionEvaluator
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.mcp.MCPPackage
import com.ai.assistance.operit.core.tools.mcp.MCPServerConfig
import com.ai.assistance.operit.core.tools.mcp.MCPToolExecutor
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.data.preferences.SkillVisibilityPreferences
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.data.preferences.DisplayPreferencesManager
import com.ai.assistance.operit.data.preferences.EnvPreferences
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.data.model.PackageToolPromptCategory
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolResult
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.hjson.JsonValue

/**
 * Manages the loading, registration, and handling of tool packages
 *
 * Package Lifecycle:
 * 1. Available Packages: All packages in assets (both JS and HJSON format)
 * 2. Imported Packages: Packages that user has imported (but not necessarily using)
 * 3. Used Packages: Packages that are loaded and registered with AI in current session
 */
class PackageManager
private constructor(private val context: Context, private val aiToolHandler: AIToolHandler) {
    companion object {
        private const val TAG = "PackageManager"
        private const val TOOLPKG_TAG = "ToolPkg"
        private const val PACKAGES_DIR = "packages" // Directory for packages
        private const val ASSETS_PACKAGES_DIR = "packages" // Directory in assets for packages
        private const val PACKAGE_PREFS = "com.ai.assistance.operit.core.tools.PackageManager"
        private const val IMPORTED_PACKAGES_KEY = "imported_packages"
        private const val DISABLED_PACKAGES_KEY = "disabled_packages"
        private const val ACTIVE_PACKAGES_KEY = "active_packages"
        private const val TOOLPKG_SUBPACKAGE_STATES_KEY = "toolpkg_subpackage_states"
        private const val TOOLPKG_EXTENSION = ".toolpkg"
        private const val TOOLPKG_CACHE_DIR = "toolpkg_cache"
        private const val TOOLPKG_CACHE_SIGNATURE_FILE = ".toolpkg-cache-signature"

        @Volatile
        private var INSTANCE: PackageManager? = null

        fun getInstance(context: Context, aiToolHandler: AIToolHandler): PackageManager {
            return INSTANCE
                ?: synchronized(this) {
                    INSTANCE
                        ?: PackageManager(context.applicationContext, aiToolHandler).also {
                            INSTANCE = it
                        }
                }
        }
    }

    // Map of package name to package description (all available packages in market)
    private val availablePackages = ConcurrentHashMap<String, ToolPackage>()

    private val packageLoadErrors = ConcurrentHashMap<String, String>()

    private val activePackageToolNames = ConcurrentHashMap<String, Set<String>>()

    private val activePackageStateIds = ConcurrentHashMap<String, String?>()

    private val toolPkgContainers = ConcurrentHashMap<String, ToolPkgContainerRuntime>()
    private val toolPkgSubpackageByPackageName = ConcurrentHashMap<String, ToolPkgSubpackageRuntime>()

    data class ToolPkgSubpackageInfo(
        val packageName: String,
        val subpackageId: String,
        val displayName: String,
        val description: String,
        val enabledByDefault: Boolean,
        val toolCount: Int,
        val enabled: Boolean
    )

    data class ToolPkgContainerDetails(
        val packageName: String,
        val displayName: String,
        val description: String,
        val version: String,
        val resourceCount: Int,
        val uiModuleCount: Int,
        val subpackages: List<ToolPkgSubpackageInfo>
    )

    data class ToolPkgToolboxUiModule(
        val containerPackageName: String,
        val toolPkgId: String,
        val uiModuleId: String,
        val runtime: String,
        val screen: String,
        val title: String,
        val description: String,
        val moduleSpec: Map<String, Any?>
    )

    data class ToolPkgAppLifecycleHook(
        val containerPackageName: String,
        val hookId: String,
        val event: String,
        val functionName: String
    )

    data class ToolPkgMessageProcessingPlugin(
        val containerPackageName: String,
        val pluginId: String,
        val functionName: String
    )

    data class ToolPkgXmlRenderPlugin(
        val containerPackageName: String,
        val pluginId: String,
        val tag: String,
        val functionName: String
    )

    data class ToolPkgInputMenuTogglePlugin(
        val containerPackageName: String,
        val pluginId: String,
        val functionName: String
    )

    data class ToolPkgToolLifecycleHook(
        val containerPackageName: String,
        val hookId: String,
        val functionName: String
    )

    data class ToolPkgPromptInputHook(
        val containerPackageName: String,
        val hookId: String,
        val functionName: String
    )

    data class ToolPkgPromptHistoryHook(
        val containerPackageName: String,
        val hookId: String,
        val functionName: String
    )

    data class ToolPkgSystemPromptComposeHook(
        val containerPackageName: String,
        val hookId: String,
        val functionName: String
    )

    data class ToolPkgToolPromptComposeHook(
        val containerPackageName: String,
        val hookId: String,
        val functionName: String
    )

    data class ToolPkgPromptFinalizeHook(
        val containerPackageName: String,
        val hookId: String,
        val functionName: String
    )


    @Volatile
    private var isInitialized = false
    private val initLock = Any()
    private val toolPkgCacheLock = Any()
    @Volatile
    private var initializationFuture: CompletableFuture<Unit>? = null
    private val initializationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val skillManager by lazy { SkillManager.getInstance(context) }

    private val skillVisibilityPreferences by lazy { SkillVisibilityPreferences.getInstance(context) }

    // JavaScript engine for executing JS package code
    private val jsEngine by lazy { JsEngine(context) }
    private val toolPkgExecutionEngines = ConcurrentHashMap<String, JsEngine>()
    private val toolPkgFacade by lazy { PackageManagerToolPkgFacade(this) }

    // Environment preferences for package-level env variables
    private val envPreferences by lazy { EnvPreferences.getInstance(context) }

    // MCP Manager instance (lazy loading)
    private val mcpManager by lazy { MCPManager.getInstance(context) }

    private fun logToolPkgInfo(message: String) {
        AppLogger.i(TOOLPKG_TAG, "PKG: $message")
    }

    private fun logToolPkgError(message: String, tr: Throwable? = null) {
        if (tr == null) {
            AppLogger.e(TOOLPKG_TAG, "PKG: $message")
        } else {
            AppLogger.e(TOOLPKG_TAG, "PKG: $message", tr)
        }
    }

    internal val contextInternal: Context
        get() = context

    internal val jsEngineInternal: JsEngine
        get() = jsEngine

    internal fun getToolPkgExecutionEngine(contextKey: String): JsEngine {
        val normalizedKey = contextKey.trim().ifBlank { "toolpkg_main:default" }
        return toolPkgExecutionEngines.computeIfAbsent(normalizedKey) { JsEngine(context) }
    }

    private fun destroyToolPkgExecutionEngine(contextKey: String) {
        val normalizedKey = contextKey.trim().ifBlank { return }
        toolPkgExecutionEngines.remove(normalizedKey)?.destroy()
    }

    private fun destroyDefaultToolPkgExecutionEngine(packageName: String) {
        val normalizedPackageName = normalizePackageName(packageName)
        if (normalizedPackageName.isBlank()) {
            return
        }
        destroyToolPkgExecutionEngine("toolpkg_main:$normalizedPackageName")
    }

    internal val toolPkgContainersInternal: MutableMap<String, ToolPkgContainerRuntime>
        get() = toolPkgContainers

    internal val toolPkgSubpackageByPackageNameInternal: MutableMap<String, ToolPkgSubpackageRuntime>
        get() = toolPkgSubpackageByPackageName

    internal fun resolveToolPkgSubpackageRuntimeInternal(nameOrId: String): ToolPkgSubpackageRuntime? {
        return resolveToolPkgSubpackageRuntime(nameOrId)
    }

    internal fun getToolPkgMainScriptInternal(containerPackageName: String): String? {
        return getToolPkgMainScript(containerPackageName)
    }

    // Get the external packages directory
    private val externalPackagesDir: File
        get() {
            val dir = File(context.getExternalFilesDir(null), PACKAGES_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            AppLogger.d(TAG, "External packages directory: ${dir.absolutePath}")
            return dir
        }

    private val toolPkgCacheRootDir: File
        get() {
            val dir = File(context.filesDir, TOOLPKG_CACHE_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    internal fun ensureInitialized() {
        if (isInitialized) return
        val isMainThread = Looper.myLooper() == Looper.getMainLooper()
        val future = ensureInitializationStarted()
        if (isMainThread) {
            // Never block main thread for toolpkg parsing: it requires WebView main-thread callbacks.
            return
        }
        try {
            future.get()
        } catch (e: Exception) {
            val threadName = Thread.currentThread().name
            logToolPkgError("ensureInitialized failed on background thread, thread=$threadName, reason=${e.message ?: e.javaClass.simpleName}", e)
            throw IllegalStateException("PackageManager initialization failed", e)
        }
    }

    private fun ensureInitializationStarted(): CompletableFuture<Unit> {
        synchronized(initLock) {
            if (isInitialized) {
                return CompletableFuture.completedFuture(Unit)
            }
            initializationFuture?.let {
                return it
            }

            val future = CompletableFuture<Unit>()
            initializationFuture = future

            initializationScope.launch {
                val initStart = System.currentTimeMillis()
                try {
                    // Create packages directory if it doesn't exist
                    externalPackagesDir

                    // Load available packages info (metadata only) from assets and external storage
                    loadAvailablePackages()

                    // Automatically import built-in packages that are enabled by default
                    initializeDefaultPackages()
                    reconcileToolPkgCaches()

                    synchronized(initLock) {
                        isInitialized = true
                    }
                    logToolPkgInfo("initialization coroutine success, totalMs=${System.currentTimeMillis() - initStart}")
                    future.complete(Unit)
                } catch (e: Exception) {
                    logToolPkgError(
                        "initialization coroutine failed after ${System.currentTimeMillis() - initStart}ms, reason=${e.message ?: e.javaClass.simpleName}",
                        e
                    )
                    future.completeExceptionally(e)
                } finally {
                    synchronized(initLock) {
                        if (initializationFuture === future) {
                            initializationFuture = null
                        }
                    }
                }
            }
            return future
        }
    }

    private fun resolveToolPkgSubpackageRuntime(nameOrId: String): ToolPkgSubpackageRuntime? {
        val candidate = nameOrId.trim()
        if (candidate.isBlank()) {
            return null
        }

        toolPkgSubpackageByPackageName[candidate]?.let { return it }

        return toolPkgSubpackageByPackageName.values.firstOrNull {
            it.subpackageId.equals(candidate, ignoreCase = true)
        }
    }

    internal fun normalizePackageName(packageName: String): String {
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) {
            return trimmed
        }
        return resolveToolPkgSubpackageRuntime(trimmed)?.packageName ?: trimmed
    }

    private fun normalizeImportedPackageNames(packageNames: List<String>): List<String> {
        val normalized = LinkedHashSet<String>()
        packageNames.forEach { original ->
            val canonical = normalizePackageName(original)
            if (canonical.isNotBlank()) {
                normalized.add(canonical)
            }
        }
        return normalized.toList()
    }

    private fun normalizeToolPkgSubpackageStates(states: Map<String, Boolean>): Map<String, Boolean> {
        val normalized = linkedMapOf<String, Boolean>()
        states.forEach { (name, enabled) ->
            val canonical = normalizePackageName(name)
            if (!toolPkgSubpackageByPackageName.containsKey(canonical)) {
                return@forEach
            }

            val isCanonicalKey = name.trim().equals(canonical, ignoreCase = true)
            if (isCanonicalKey || !normalized.containsKey(canonical)) {
                normalized[canonical] = enabled
            }
        }
        return normalized
    }

    private fun toolPkgCacheDirName(packageName: String): String {
        val normalized = packageName.trim()
        val safeName =
            normalized
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .ifBlank { "toolpkg" }
        val hash = Integer.toHexString(normalized.hashCode())
        return "$safeName-$hash"
    }

    private fun toolPkgCacheDir(packageName: String): File {
        return File(toolPkgCacheRootDir, toolPkgCacheDirName(packageName))
    }

    private fun deleteToolPkgCacheDir(packageName: String) {
        synchronized(toolPkgCacheLock) {
            val dir = toolPkgCacheDir(packageName)
            if (dir.exists() && !dir.deleteRecursively()) {
                AppLogger.w(TAG, "Failed to delete toolpkg cache dir: ${dir.absolutePath}")
            }
        }
    }

    private fun buildToolPkgCacheSignature(runtime: ToolPkgContainerRuntime): String? {
        return when (runtime.sourceType) {
            ToolPkgSourceType.EXTERNAL -> {
                val sourceFile = File(runtime.sourcePath)
                if (!sourceFile.exists()) {
                    null
                } else {
                    buildString {
                        append("external|")
                        append(sourceFile.absolutePath)
                        append('|')
                        append(sourceFile.length())
                        append('|')
                        append(sourceFile.lastModified())
                        append('|')
                        append(runtime.version)
                        append('|')
                        append(runtime.mainEntry)
                    }
                }
            }
            ToolPkgSourceType.ASSET -> {
                val apkFile = File(context.packageResourcePath)
                buildString {
                    append("asset|")
                    append(runtime.sourcePath)
                    append('|')
                    append(apkFile.length())
                    append('|')
                    append(apkFile.lastModified())
                    append('|')
                    append(runtime.version)
                    append('|')
                    append(runtime.mainEntry)
                }
            }
        }
    }

    private fun extractToolPkgArchive(runtime: ToolPkgContainerRuntime, destinationDir: File): Boolean {
        return when (runtime.sourceType) {
            ToolPkgSourceType.EXTERNAL ->
                ToolPkgArchiveParser.extractZipEntriesFromExternal(
                    zipFilePath = runtime.sourcePath,
                    destinationDir = destinationDir
                )
            ToolPkgSourceType.ASSET ->
                ToolPkgArchiveParser.extractZipEntriesFromAsset(
                    context = context,
                    assetPath = runtime.sourcePath,
                    destinationDir = destinationDir
                )
        }
    }

    private fun ensureToolPkgCache(runtime: ToolPkgContainerRuntime): File? {
        val signature = buildToolPkgCacheSignature(runtime) ?: return null
        synchronized(toolPkgCacheLock) {
            val cacheDir = toolPkgCacheDir(runtime.packageName)
            val signatureFile = File(cacheDir, TOOLPKG_CACHE_SIGNATURE_FILE)
            val mainScriptFile = File(cacheDir, runtime.mainEntry)

            if (cacheDir.exists() &&
                signatureFile.exists() &&
                signatureFile.readText() == signature &&
                mainScriptFile.exists()
            ) {
                return cacheDir
            }

            deleteToolPkgCacheDir(runtime.packageName)
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw IllegalStateException("Failed to create toolpkg cache dir: ${cacheDir.absolutePath}")
            }

            return try {
                if (!extractToolPkgArchive(runtime, cacheDir)) {
                    deleteToolPkgCacheDir(runtime.packageName)
                    null
                } else {
                    signatureFile.writeText(signature)
                    cacheDir
                }
            } catch (e: Exception) {
                deleteToolPkgCacheDir(runtime.packageName)
                AppLogger.e(TAG, "Failed to extract toolpkg cache: ${runtime.packageName}", e)
                null
            }
        }
    }

    private fun reconcileToolPkgCaches() {
        synchronized(toolPkgCacheLock) {
            val importedPackages = getImportedPackagesInternal().toSet()
            val availableContainerNames = toolPkgContainers.keys.toSet()
            val importedContainerNames = linkedSetOf<String>().apply {
                toolPkgContainers.keys.forEach { containerName ->
                    if (importedPackages.contains(containerName)) {
                        add(containerName)
                    }
                }
                toolPkgSubpackageByPackageName.values.forEach { subpackage ->
                    if (importedPackages.contains(subpackage.packageName)) {
                        add(subpackage.containerPackageName)
                    }
                }
            }

            val expectedCacheDirNames = importedContainerNames.map(::toolPkgCacheDirName).toSet()
            toolPkgCacheRootDir.listFiles()?.forEach { child ->
                if (!expectedCacheDirNames.contains(child.name) && !child.deleteRecursively()) {
                    AppLogger.w(TAG, "Failed to remove stale toolpkg cache: ${child.absolutePath}")
                }
            }

            importedContainerNames.forEach { containerName ->
                val runtime = toolPkgContainers[containerName] ?: return@forEach
                ensureToolPkgCache(runtime)
            }

            availableContainerNames
                .filterNot { importedContainerNames.contains(it) }
                .forEach(::deleteToolPkgCacheDir)
        }
    }

    fun resolvePackageForDisplay(packageName: String): ToolPackage? {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        val toolPackage = availablePackages[normalizedPackageName] ?: return null
        return selectToolPackageState(toolPackage)
    }

    fun isToolPkgContainer(packageName: String): Boolean {
        return toolPkgFacade.isToolPkgContainer(packageName)
    }

    fun isToolPkgSubpackage(packageName: String): Boolean {
        return toolPkgFacade.isToolPkgSubpackage(packageName)
    }

    fun isTopLevelPackage(packageName: String): Boolean {
        ensureInitialized()
        return resolveToolPkgSubpackageRuntime(packageName) == null
    }

    fun getTopLevelAvailablePackages(forceRefresh: Boolean = false): Map<String, ToolPackage> {
        val packages = getAvailablePackages(forceRefresh)
        return packages.filterKeys { !toolPkgSubpackageByPackageName.containsKey(it) }
    }

    fun getToolPkgContainerDetails(
        packageName: String,
        resolveContext: Context? = null
    ): ToolPkgContainerDetails? {
        return toolPkgFacade.getToolPkgContainerDetails(packageName, resolveContext)
    }

    fun getToolPkgToolboxUiModules(
        runtime: String = TOOLPKG_RUNTIME_COMPOSE_DSL,
        resolveContext: Context? = null
    ): List<ToolPkgToolboxUiModule> {
        return toolPkgFacade.getToolPkgToolboxUiModules(runtime, resolveContext)
    }

    fun getToolPkgAppLifecycleHooks(event: String): List<ToolPkgAppLifecycleHook> {
        return toolPkgFacade.getToolPkgAppLifecycleHooks(event)
    }

    fun setToolPkgSubpackageEnabled(subpackagePackageName: String, enabled: Boolean): Boolean {
        return toolPkgFacade.setToolPkgSubpackageEnabled(subpackagePackageName, enabled)
    }

    fun findPreferredPackageNameForSubpackageId(
        subpackageId: String,
        preferImported: Boolean = true
    ): String? {
        return toolPkgFacade.findPreferredPackageNameForSubpackageId(subpackageId, preferImported)
    }

    fun copyToolPkgResourceToFileBySubpackageId(
        subpackageId: String,
        resourceKey: String,
        destinationFile: File,
        preferImportedContainer: Boolean = true
    ): Boolean {
        return toolPkgFacade.copyToolPkgResourceToFileBySubpackageId(
            subpackageId = subpackageId,
            resourceKey = resourceKey,
            destinationFile = destinationFile,
            preferImportedContainer = preferImportedContainer
        )
    }

    fun copyToolPkgResourceToFile(
        containerPackageName: String,
        resourceKey: String,
        destinationFile: File
    ): Boolean {
        return toolPkgFacade.copyToolPkgResourceToFile(
            containerPackageName = containerPackageName,
            resourceKey = resourceKey,
            destinationFile = destinationFile
        )
    }

    fun getToolPkgResourceOutputFileName(
        packageNameOrSubpackageId: String,
        resourceKey: String,
        preferImportedContainer: Boolean = true
    ): String? {
        return toolPkgFacade.getToolPkgResourceOutputFileName(
            packageNameOrSubpackageId = packageNameOrSubpackageId,
            resourceKey = resourceKey,
            preferImportedContainer = preferImportedContainer
        )
    }

    fun getToolPkgComposeDslScriptBySubpackageId(
        subpackageId: String,
        uiModuleId: String? = null,
        preferImportedContainer: Boolean = true
    ): String? {
        return toolPkgFacade.getToolPkgComposeDslScriptBySubpackageId(
            subpackageId = subpackageId,
            uiModuleId = uiModuleId,
            preferImportedContainer = preferImportedContainer
        )
    }

    fun getToolPkgComposeDslScript(
        containerPackageName: String,
        uiModuleId: String? = null
    ): String? {
        return toolPkgFacade.getToolPkgComposeDslScript(
            containerPackageName = containerPackageName,
            uiModuleId = uiModuleId
        )
    }

    private fun getToolPkgMainScript(containerPackageName: String): String? {
        val totalStartTime = messageTimingNow()
        ensureInitialized()
        val normalizedContainerPackageName = normalizePackageName(containerPackageName)
        val runtime = toolPkgContainers[normalizedContainerPackageName] ?: return null
        val importedSet = getImportedPackages().toSet()
        if (!importedSet.contains(runtime.packageName)) {
            return null
        }
        if (runtime.mainEntry.isBlank()) {
            return null
        }

        return try {
            val readBytesStartTime = messageTimingNow()
            val bytes = readToolPkgResourceBytes(runtime, runtime.mainEntry) ?: return null
            logMessageTiming(
                stage = "toolpkg.getMainScript.readBytes",
                startTimeMs = readBytesStartTime,
                details = "container=${runtime.packageName}, sourceType=${runtime.sourceType}, entry=${runtime.mainEntry}, bytes=${bytes.size}"
            )
            val script = bytes.toString(StandardCharsets.UTF_8)
            logMessageTiming(
                stage = "toolpkg.getMainScript.total",
                startTimeMs = totalStartTime,
                details = "container=${runtime.packageName}, entry=${runtime.mainEntry}, scriptLength=${script.length}"
            )
            script
        } catch (e: Exception) {
            AppLogger.e(
                TAG,
                "Failed to read toolpkg main script: ${runtime.packageName}:${runtime.mainEntry}",
                e
            )
            null
        }
    }

    fun getToolPkgComposeDslScreenPath(
        containerPackageName: String,
        uiModuleId: String? = null
    ): String? {
        return toolPkgFacade.getToolPkgComposeDslScreenPath(
            containerPackageName = containerPackageName,
            uiModuleId = uiModuleId
        )
    }

    fun runToolPkgMainHook(
        containerPackageName: String,
        functionName: String,
        event: String,
        eventName: String? = null,
        pluginId: String? = null,
        eventPayload: Map<String, Any?> = emptyMap(),
        onIntermediateResult: ((Any?) -> Unit)? = null
    ): Result<Any?> {
        return toolPkgFacade.runToolPkgMainHook(
            containerPackageName = containerPackageName,
            functionName = functionName,
            event = event,
            eventName = eventName,
            pluginId = pluginId,
            eventPayload = eventPayload,
            onIntermediateResult = onIntermediateResult
        )
    }

    fun getToolPkgMessageProcessingPlugins(): List<ToolPkgMessageProcessingPlugin> {
        return toolPkgFacade.getToolPkgMessageProcessingPlugins()
    }

    fun getToolPkgXmlRenderPlugins(tagName: String): List<ToolPkgXmlRenderPlugin> {
        return toolPkgFacade.getToolPkgXmlRenderPlugins(tagName)
    }

    fun getToolPkgInputMenuTogglePlugins(): List<ToolPkgInputMenuTogglePlugin> {
        return toolPkgFacade.getToolPkgInputMenuTogglePlugins()
    }

    fun getToolPkgToolLifecycleHooks(): List<ToolPkgToolLifecycleHook> {
        return toolPkgFacade.getToolPkgToolLifecycleHooks()
    }

    fun getToolPkgPromptInputHooks(): List<ToolPkgPromptInputHook> {
        return toolPkgFacade.getToolPkgPromptInputHooks()
    }

    fun getToolPkgPromptHistoryHooks(): List<ToolPkgPromptHistoryHook> {
        return toolPkgFacade.getToolPkgPromptHistoryHooks()
    }

    fun getToolPkgSystemPromptComposeHooks(): List<ToolPkgSystemPromptComposeHook> {
        return toolPkgFacade.getToolPkgSystemPromptComposeHooks()
    }

    fun getToolPkgToolPromptComposeHooks(): List<ToolPkgToolPromptComposeHook> {
        return toolPkgFacade.getToolPkgToolPromptComposeHooks()
    }

    fun getToolPkgPromptFinalizeHooks(): List<ToolPkgPromptFinalizeHook> {
        return toolPkgFacade.getToolPkgPromptFinalizeHooks()
    }

    fun readToolPkgTextResource(
        packageNameOrSubpackageId: String,
        resourcePath: String,
        preferImportedContainer: Boolean = true
    ): String? {
        return toolPkgFacade.readToolPkgTextResource(
            packageNameOrSubpackageId = packageNameOrSubpackageId,
            resourcePath = resourcePath,
            preferImportedContainer = preferImportedContainer
        )
    }

    /**
     * Automatically imports built-in packages that are marked as enabled by default.
     * This ensures that essential or commonly used packages are available without
     * manual user intervention. It also respects a user's choice to disable a
     * default package.
     */
    private fun initializeDefaultPackages() {
        val importedPackages = getImportedPackagesInternal().toMutableSet()
        val disabledPackages = getDisabledPackagesInternal().toSet()
        var packagesChanged = false

        synchronized(initLock) {
            availablePackages.values.forEach { toolPackage ->
                if (
                    toolPackage.isBuiltIn &&
                    toolPackage.enabledByDefault &&
                    !toolPkgSubpackageByPackageName.containsKey(toolPackage.name) &&
                    !disabledPackages.contains(toolPackage.name)
                ) {
                    if (importedPackages.add(toolPackage.name)) {
                        packagesChanged = true
                        AppLogger.d(TAG, "Auto-importing default package: ${toolPackage.name}")
                    }
                }
            }
        }

        if (packagesChanged) {
            val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
            val updatedJson = Json.encodeToString(importedPackages.toList())
            prefs.edit().putString(IMPORTED_PACKAGES_KEY, updatedJson).apply()
            AppLogger.d(TAG, "Updated imported packages with default packages.")
        }
    }

    /**
     * Loads all available packages metadata (from assets and external storage).
     * Includes legacy JS packages and new .toolpkg containers/subpackages.
     */
    private fun loadAvailablePackages() {
        val loadStart = System.currentTimeMillis()
        val stagedPackageLoadErrors = mutableMapOf<String, String>()
        val stagedAvailablePackages = mutableMapOf<String, ToolPackage>()
        val stagedToolPkgContainers = mutableMapOf<String, ToolPkgContainerRuntime>()
        val stagedToolPkgSubpackages = mutableMapOf<String, ToolPkgSubpackageRuntime>()

        val assetManager = context.assets
        val packageFiles = assetManager.list(ASSETS_PACKAGES_DIR) ?: emptyArray()
        logToolPkgInfo("loadAvailablePackages start, assetPackageCount=${packageFiles.size}")

        for (fileName in packageFiles) {
            val assetPath = "$ASSETS_PACKAGES_DIR/$fileName"
            try {
                when {
                    fileName.endsWith(".js", ignoreCase = true) -> {
                        val packageMetadata =
                            loadPackageFromJsAsset(assetPath) { key, error ->
                                stagedPackageLoadErrors[key] = error
                            }
                        if (packageMetadata != null) {
                            stagedAvailablePackages[packageMetadata.name] = packageMetadata.copy(isBuiltIn = true)
                        }
                    }
                    fileName.endsWith(TOOLPKG_EXTENSION, ignoreCase = true) -> {
                        val loadResult =
                            loadToolPkgFromAsset(assetPath) { key, error ->
                                stagedPackageLoadErrors[key] = error
                            }
                        if (loadResult != null) {
                            registerToolPkgInto(
                                loadResult = loadResult,
                                availablePackagesTarget = stagedAvailablePackages,
                                toolPkgContainersTarget = stagedToolPkgContainers,
                                toolPkgSubpackageByPackageNameTarget = stagedToolPkgSubpackages,
                                packageLoadErrorsTarget = stagedPackageLoadErrors
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Unexpected error while loading asset package: $assetPath", e)
                logToolPkgError("loadAvailablePackages asset parse failed, source=$assetPath", e)
                stagedPackageLoadErrors[fileName.substringBeforeLast('.')] =
                    "$assetPath: ${e.stackTraceToString()}"
            }
        }

        if (externalPackagesDir.exists()) {
            val externalFiles = externalPackagesDir.listFiles() ?: emptyArray()
            for (file in externalFiles) {
                if (!file.isFile) continue
                try {
                    when {
                        file.name.endsWith(".js", ignoreCase = true) -> {
                            val packageMetadata =
                                loadPackageFromJsFile(file) { key, error ->
                                    stagedPackageLoadErrors[key] = error
                                }
                            if (packageMetadata != null) {
                                stagedAvailablePackages[packageMetadata.name] = packageMetadata.copy(isBuiltIn = false)
                            }
                        }
                        file.name.endsWith(TOOLPKG_EXTENSION, ignoreCase = true) -> {
                            val loadResult =
                                loadToolPkgFromExternalFile(file) { key, error ->
                                    stagedPackageLoadErrors[key] = error
                                }
                            if (loadResult != null) {
                                registerToolPkgInto(
                                    loadResult = loadResult,
                                    availablePackagesTarget = stagedAvailablePackages,
                                    toolPkgContainersTarget = stagedToolPkgContainers,
                                    toolPkgSubpackageByPackageNameTarget = stagedToolPkgSubpackages,
                                    packageLoadErrorsTarget = stagedPackageLoadErrors
                                )
                            }
                        }
                }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Unexpected error while loading external package: ${file.absolutePath}", e)
                    logToolPkgError("loadAvailablePackages external parse failed, source=${file.absolutePath}", e)
                    stagedPackageLoadErrors[file.nameWithoutExtension] =
                        "${file.absolutePath}: ${e.stackTraceToString()}"
                }
            }
        }

        synchronized(initLock) {
            packageLoadErrors.clear()
            packageLoadErrors.putAll(stagedPackageLoadErrors)

            availablePackages.clear()
            availablePackages.putAll(stagedAvailablePackages)

            toolPkgContainers.clear()
            toolPkgContainers.putAll(stagedToolPkgContainers)

            toolPkgSubpackageByPackageName.clear()
            toolPkgSubpackageByPackageName.putAll(stagedToolPkgSubpackages)
        }
        reconcileToolPkgCaches()
        logToolPkgInfo(
            "loadAvailablePackages finish, elapsedMs=${System.currentTimeMillis() - loadStart}, available=${stagedAvailablePackages.size}, containers=${stagedToolPkgContainers.size}, subpackages=${stagedToolPkgSubpackages.size}, errors=${stagedPackageLoadErrors.size}"
        )
    }

    private fun registerToolPkg(loadResult: ToolPkgLoadResult): Boolean {
        return registerToolPkgInto(
            loadResult = loadResult,
            availablePackagesTarget = availablePackages,
            toolPkgContainersTarget = toolPkgContainers,
            toolPkgSubpackageByPackageNameTarget = toolPkgSubpackageByPackageName,
            packageLoadErrorsTarget = packageLoadErrors
        )
    }

    private fun registerToolPkgInto(
        loadResult: ToolPkgLoadResult,
        availablePackagesTarget: MutableMap<String, ToolPackage>,
        toolPkgContainersTarget: MutableMap<String, ToolPkgContainerRuntime>,
        toolPkgSubpackageByPackageNameTarget: MutableMap<String, ToolPkgSubpackageRuntime>,
        packageLoadErrorsTarget: MutableMap<String, String>
    ): Boolean {
        val containerName = loadResult.containerPackage.name
        if (availablePackagesTarget.containsKey(containerName)) {
            packageLoadErrorsTarget[containerName] = "Duplicate package name: $containerName"
            AppLogger.w(TAG, "Skipped duplicated toolpkg container: $containerName")
            return false
        }

        val duplicateSubpackages =
            loadResult.subpackagePackages
                .map { it.name }
                .filter { availablePackagesTarget.containsKey(it) }

        if (duplicateSubpackages.isNotEmpty()) {
            packageLoadErrorsTarget[containerName] =
                "Duplicate subpackage names: ${duplicateSubpackages.joinToString(", ")}"
            AppLogger.w(TAG, "Skipped toolpkg '$containerName' due to duplicate subpackages: $duplicateSubpackages")
            return false
        }

        availablePackagesTarget[containerName] = loadResult.containerPackage
        toolPkgContainersTarget[containerName] = loadResult.containerRuntime

        loadResult.subpackagePackages.forEach { subpackage ->
            availablePackagesTarget[subpackage.name] = subpackage
        }

        loadResult.containerRuntime.subpackages.forEach { runtime ->
            toolPkgSubpackageByPackageNameTarget[runtime.packageName] = runtime
        }
        return true
    }

    /** Loads a complete ToolPackage from a JavaScript file */
    private fun loadPackageFromJsFile(
        file: File,
        reportPackageLoadError: (key: String, error: String) -> Unit = { key, error ->
            packageLoadErrors[key] = error
        }
    ): ToolPackage? {
        try {
            val jsContent = file.readText()
            return parseJsPackage(jsContent) { key, error ->
                reportPackageLoadError(key, "${file.path}: $error")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading package from JS file: ${file.path}", e)
            reportPackageLoadError(file.nameWithoutExtension, "${file.path}: ${e.stackTraceToString()}")
            return null
        }
    }

    /** Loads a complete ToolPackage from a JavaScript file in assets */
    private fun loadPackageFromJsAsset(
        assetPath: String,
        reportPackageLoadError: (key: String, error: String) -> Unit = { key, error ->
            packageLoadErrors[key] = error
        }
    ): ToolPackage? {
        try {
            val assetManager = context.assets
            val jsContent = assetManager.open(assetPath).bufferedReader().use { it.readText() }
            return parseJsPackage(jsContent) { key, error ->
                reportPackageLoadError(key, "$assetPath: $error")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading package from JS asset: $assetPath", e)
            reportPackageLoadError(
                assetPath.substringAfterLast("/").removeSuffix(".js"),
                "$assetPath: ${e.stackTraceToString()}"
            )
            return null
        }
    }

    private fun loadToolPkgFromExternalFile(
        file: File,
        reportPackageLoadError: (key: String, error: String) -> Unit = { key, error ->
            packageLoadErrors[key] = error
        }
    ): ToolPkgLoadResult? {
        val startMs = System.currentTimeMillis()
        return try {
            file.inputStream().use { input ->
                val entries = ToolPkgArchiveParser.readZipEntries(input)
                jsEngine.withTemporaryToolPkgTextResourceResolver(
                    resolver = { _, resourcePath ->
                        val normalizedPath = ToolPkgArchiveParser.normalizeZipEntryPath(resourcePath)
                        if (normalizedPath == null) {
                            null
                        } else {
                            ToolPkgArchiveParser.findZipEntryContent(entries, normalizedPath)
                                ?.toString(StandardCharsets.UTF_8)
                        }
                    }
                ) {
                    ToolPkgArchiveParser.parseToolPkgFromEntries(
                        entries = entries,
                        sourceType = ToolPkgSourceType.EXTERNAL,
                        sourcePath = file.absolutePath,
                        isBuiltIn = false,
                        parseJsPackage = { jsContent, onError -> parseJsPackage(jsContent, onError) },
                        parseMainRegistration = { mainScriptText, toolPkgId, mainScriptPath ->
                            ToolPkgMainRegistrationScriptParser.parse(
                                script = mainScriptText,
                                toolPkgId = toolPkgId,
                                mainScriptPath = mainScriptPath,
                                jsEngine = jsEngine
                            )
                        },
                        reportPackageLoadError = { key, error ->
                            reportPackageLoadError(key, error)
                        }
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading toolpkg from external file: ${file.absolutePath}", e)
            logToolPkgError(
                "loadToolPkgFromExternalFile failed, source=${file.absolutePath}, elapsedMs=${System.currentTimeMillis() - startMs}, reason=${e.message ?: e.javaClass.simpleName}",
                e
            )
            reportPackageLoadError(file.nameWithoutExtension, "${file.absolutePath}: ${e.stackTraceToString()}")
            null
        }
    }

    private fun loadToolPkgFromAsset(
        assetPath: String,
        reportPackageLoadError: (key: String, error: String) -> Unit = { key, error ->
            packageLoadErrors[key] = error
        }
    ): ToolPkgLoadResult? {
        val startMs = System.currentTimeMillis()
        return try {
            context.assets.open(assetPath).use { input ->
                val entries = ToolPkgArchiveParser.readZipEntries(input)
                jsEngine.withTemporaryToolPkgTextResourceResolver(
                    resolver = { _, resourcePath ->
                        val normalizedPath = ToolPkgArchiveParser.normalizeZipEntryPath(resourcePath)
                        if (normalizedPath == null) {
                            null
                        } else {
                            ToolPkgArchiveParser.findZipEntryContent(entries, normalizedPath)
                                ?.toString(StandardCharsets.UTF_8)
                        }
                    }
                ) {
                    ToolPkgArchiveParser.parseToolPkgFromEntries(
                        entries = entries,
                        sourceType = ToolPkgSourceType.ASSET,
                        sourcePath = assetPath,
                        isBuiltIn = true,
                        parseJsPackage = { jsContent, onError -> parseJsPackage(jsContent, onError) },
                        parseMainRegistration = { mainScriptText, toolPkgId, mainScriptPath ->
                            ToolPkgMainRegistrationScriptParser.parse(
                                script = mainScriptText,
                                toolPkgId = toolPkgId,
                                mainScriptPath = mainScriptPath,
                                jsEngine = jsEngine
                            )
                        },
                        reportPackageLoadError = { key, error ->
                            reportPackageLoadError(key, error)
                        }
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading toolpkg from asset: $assetPath", e)
            logToolPkgError(
                "loadToolPkgFromAsset failed, source=$assetPath, elapsedMs=${System.currentTimeMillis() - startMs}, reason=${e.message ?: e.javaClass.simpleName}",
                e
            )
            reportPackageLoadError(
                assetPath.substringAfterLast('/').removeSuffix(TOOLPKG_EXTENSION),
                "$assetPath: ${e.stackTraceToString()}"
            )
            null
        }
    }

    internal fun readToolPkgResourceBytes(
        runtime: ToolPkgContainerRuntime,
        normalizedResourcePath: String
    ): ByteArray? {
        val normalizedPath = ToolPkgArchiveParser.normalizeZipEntryPath(normalizedResourcePath) ?: return null
        val cacheDir = ensureToolPkgCache(runtime) ?: return null
        val resourceFile = File(cacheDir, normalizedPath)
        if (!resourceFile.exists() || !resourceFile.isFile) {
            return null
        }
        return resourceFile.readBytes()
    }

    /**
     * Parses a JavaScript package file into a ToolPackage object Uses the metadata in the file
     * header and extracts function definitions using JsEngine
     */
    private fun parseJsPackage(
        jsContent: String,
        onError: (key: String, error: String) -> Unit = { _, _ -> }
    ): ToolPackage? {
        try {
            // Extract metadata from comments at the top of the file
            val metadataString = extractMetadataFromJs(jsContent)

            // 先将元数据解析为 JSONObject 以便修改 tools 数组中的每个元素
            val metadataJson = org.json.JSONObject(JsonValue.readHjson(metadataString).toString())

            // 统一历史键名/值格式，避免 enabledByDefault 在 Kotlin 侧被错误解析为默认值
            normalizeJsPackageMetadata(metadataJson)

            // 检查并修复 tools 数组中的元素，确保每个工具都有 script 字段
            if (metadataJson.has("tools") && metadataJson.get("tools") is org.json.JSONArray) {
                val toolsArray = metadataJson.getJSONArray("tools")
                for (i in 0 until toolsArray.length()) {
                    val tool = toolsArray.getJSONObject(i)
                    if (!tool.has("script")) {
                        // 添加一个临时的空 script 字段
                        tool.put("script", "")
                    }
                }
            }

            // 检查并修复 states.tools 数组中的元素，确保每个工具都有 script 字段
            if (metadataJson.has("states") && metadataJson.get("states") is org.json.JSONArray) {
                val statesArray = metadataJson.getJSONArray("states")
                for (i in 0 until statesArray.length()) {
                    val state = statesArray.optJSONObject(i) ?: continue
                    if (state.has("tools") && state.get("tools") is org.json.JSONArray) {
                        val toolsArray = state.getJSONArray("tools")
                        for (j in 0 until toolsArray.length()) {
                            val tool = toolsArray.getJSONObject(j)
                            if (!tool.has("script")) {
                                tool.put("script", "")
                            }
                        }
                    }
                }
            }

            // 使用修改后的 JSON 字符串进行反序列化
            val jsonString = metadataJson.toString()

            val jsonConfig = Json { ignoreUnknownKeys = true }
            val packageMetadata = jsonConfig.decodeFromString<ToolPackage>(jsonString)

            // 更新所有工具，使用相同的完整脚本内容，但记录每个工具的函数名
            val tools =
                packageMetadata.tools.map { tool ->
                    // 检查函数是否存在于脚本中
                    if (!tool.advice) {
                        validateToolFunctionExists(jsContent, tool.name)
                    }

                    // 使用整个脚本，并记录函数名，而不是提取单个函数
                    tool.copy(script = jsContent)
                }

            val states =
                packageMetadata.states.map { state ->
                    val stateTools =
                        state.tools.map { tool ->
                            if (!tool.advice) {
                                validateToolFunctionExists(jsContent, tool.name)
                            }
                            tool.copy(script = jsContent)
                        }
                    state.copy(tools = stateTools)
                }

            return packageMetadata.copy(tools = tools, states = states)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing JS package: ${e.message}", e)
            val fallbackKey = try {
                val metadataString = extractMetadataFromJs(jsContent)
                val metadataJson = org.json.JSONObject(JsonValue.readHjson(metadataString).toString())
                metadataJson.optString("name").takeIf { it.isNotBlank() } ?: "unknown"
            } catch (_: Exception) {
                "unknown"
            }
            onError(fallbackKey, e.stackTraceToString())
            return null
        }
    }

    private fun normalizeJsPackageMetadata(metadataJson: org.json.JSONObject) {
        normalizeBooleanFieldAlias(
            metadataJson = metadataJson,
            canonicalKey = "enabledByDefault",
            legacyAlias = "enabled_by_default"
        )
        normalizeBooleanFieldAlias(
            metadataJson = metadataJson,
            canonicalKey = "isBuiltIn",
            legacyAlias = "is_built_in"
        )
        normalizeCategoryField(metadataJson)
    }

    private fun normalizeBooleanFieldAlias(
        metadataJson: org.json.JSONObject,
        canonicalKey: String,
        legacyAlias: String
    ) {
        if (!metadataJson.has(canonicalKey) && metadataJson.has(legacyAlias)) {
            metadataJson.put(canonicalKey, metadataJson.opt(legacyAlias))
        }

        if (!metadataJson.has(canonicalKey)) {
            return
        }

        val normalized = normalizeToBoolean(metadataJson.opt(canonicalKey)) ?: return
        metadataJson.put(canonicalKey, normalized)
    }

    private fun normalizeToBoolean(value: Any?): Boolean? {
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> {
                when (value.trim().lowercase()) {
                    "true", "1", "yes", "on" -> true
                    "false", "0", "no", "off" -> false
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun normalizeCategoryField(metadataJson: org.json.JSONObject) {
        val normalized =
            metadataJson
                .opt("category")
                ?.toString()
                ?.trim()
                .orEmpty()

        metadataJson.put("category", normalized.ifBlank { "Other" })
    }

    fun getPackageLoadErrors(): Map<String, String> {
        ensureInitialized()
        return packageLoadErrors.toMap()
    }

    /** 验证JavaScript文件中是否存在指定的函数 这确保了我们可以在运行时调用该函数 */
    private fun validateToolFunctionExists(jsContent: String, toolName: String): Boolean {
        // 各种函数声明模式
        val patterns =
            listOf(
                """async\s+function\s+$toolName\s*\(""",
                """function\s+$toolName\s*\(""",
                """exports\.$toolName\s*=\s*(?:async\s+)?function""",
                """(?:const|let|var)\s+$toolName\s*=\s*(?:async\s+)?\(""",
                """exports\.$toolName\s*=\s*(?:async\s+)?\(?"""
            )

        for (pattern in patterns) {
            if (pattern.toRegex().find(jsContent) != null) {
                return true
            }
        }

        AppLogger.w(TAG, "Could not find function '$toolName' in JavaScript file")
        return false
    }

    /** Extracts the metadata from JS comments at the top of the file */
    private fun extractMetadataFromJs(jsContent: String): String {
        val metadataPattern = """/\*\s*METADATA\s*([\s\S]*?)\*/""".toRegex()
        val match = metadataPattern.find(jsContent)

        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            // If no metadata block is found, return empty metadata
            "{}"
        }
    }

    /**
     * Returns the path to the external packages directory This can be used to show the user where
     * the packages are stored for manual editing
     */
    fun getExternalPackagesPath(): String {
        // 为了更易读，改成Android/data/包名/files/packages的形式
        return "Android/data/${context.packageName}/files/packages"
    }

    /**
     * Imports a package from external storage path.
     * Supports legacy JS/TS/HJSON files and .toolpkg containers.
     */
    fun importPackageFromExternalStorage(filePath: String): String {
        try {
            ensureInitialized()

            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                return "Cannot access file at path: $filePath"
            }

            val lowerPath = filePath.lowercase()
            val isToolPkg = lowerPath.endsWith(TOOLPKG_EXTENSION)
            val isJsLike = lowerPath.endsWith(".js") || lowerPath.endsWith(".ts")
            val isHjson = lowerPath.endsWith(".hjson")

            if (!isToolPkg && !isJsLike && !isHjson) {
                return "Only .toolpkg, HJSON, JavaScript (.js) and TypeScript (.ts) package files are supported"
            }

            if (isToolPkg) {
                val preview = loadToolPkgFromExternalFile(file)
                    ?: return "Failed to parse toolpkg file"
                val containerName = preview.containerPackage.name
                if (availablePackages.containsKey(containerName)) {
                    return "A package with name '$containerName' already exists in available packages"
                }

                val conflictSubpackages =
                    preview.subpackagePackages
                        .map { it.name }
                        .filter { availablePackages.containsKey(it) }
                if (conflictSubpackages.isNotEmpty()) {
                    return "Subpackage name conflict: ${conflictSubpackages.joinToString(", ")}"
                }

                val destinationFile = File(externalPackagesDir, file.name)
                if (file.absolutePath != destinationFile.absolutePath) {
                    file.inputStream().use { input ->
                        destinationFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }

                val loadedFromDestination = loadToolPkgFromExternalFile(destinationFile)
                    ?: return "Failed to parse copied toolpkg file"
                if (!registerToolPkg(loadedFromDestination)) {
                    return "Failed to register toolpkg '$containerName' due to naming conflict"
                }

                return "Successfully imported toolpkg: $containerName\nStored at: ${destinationFile.absolutePath}"
            }

            val packageMetadata =
                if (isHjson) {
                    val hjsonContent = file.readText()
                    val metadataJson = org.json.JSONObject(JsonValue.readHjson(hjsonContent).toString())
                    normalizeJsPackageMetadata(metadataJson)
                    val jsonString = metadataJson.toString()
                    val jsonConfig = Json { ignoreUnknownKeys = true }
                    jsonConfig.decodeFromString<ToolPackage>(jsonString)
                } else {
                    loadPackageFromJsFile(file)
                        ?: return "Failed to parse ${if (lowerPath.endsWith(".ts")) "TypeScript" else "JavaScript"} package file"
                }

            if (availablePackages.containsKey(packageMetadata.name)) {
                return "A package with name '${packageMetadata.name}' already exists in available packages"
            }

            val destinationFile = File(externalPackagesDir, file.name)
            if (file.absolutePath != destinationFile.absolutePath) {
                file.inputStream().use { input ->
                    destinationFile.outputStream().use { output -> input.copyTo(output) }
                }
            }

            availablePackages[packageMetadata.name] = packageMetadata.copy(isBuiltIn = false)

            AppLogger.d(TAG, "Successfully imported external package to: ${destinationFile.absolutePath}")
            return "Successfully imported package: ${packageMetadata.name}\nStored at: ${destinationFile.absolutePath}"
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error importing package from external storage", e)
            return "Error importing package: ${e.message}"
        }
    }

    /**
     * Import a package by name, adding it to the user's imported packages list.
     * For toolpkg containers this may also activate default-enabled subpackages.
     */
    fun importPackage(packageName: String): String {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)

        if (!availablePackages.containsKey(normalizedPackageName)) {
            return "Package not found in available packages: $normalizedPackageName"
        }

        val importedPackages = LinkedHashSet(getImportedPackages())
        val subpackageStates = getToolPkgSubpackageStatesInternal().toMutableMap()

        val containerRuntime = toolPkgContainers[normalizedPackageName]
        if (containerRuntime != null) {
            val containerAlreadyImported = importedPackages.contains(normalizedPackageName)
            importedPackages.add(normalizedPackageName)

            containerRuntime.subpackages.forEach { subpackage ->
                val shouldEnable =
                    subpackageStates[subpackage.packageName] ?: subpackage.enabledByDefault
                subpackageStates.putIfAbsent(subpackage.packageName, shouldEnable)

                if (shouldEnable) {
                    importedPackages.add(subpackage.packageName)
                } else {
                    importedPackages.remove(subpackage.packageName)
                }
            }

            saveImportedPackages(importedPackages.toList())
            saveToolPkgSubpackageStates(subpackageStates)
            removeFromDisabledPackages(normalizedPackageName)
            ensureToolPkgCache(containerRuntime)

            val message =
                if (containerAlreadyImported) {
                    "ToolPkg container '$normalizedPackageName' is already enabled"
                } else {
                    "Successfully enabled toolpkg container: $normalizedPackageName"
                }
            AppLogger.d(TAG, message)
            return message
        }

        val subpackageRuntime = toolPkgSubpackageByPackageName[normalizedPackageName]
        if (subpackageRuntime != null) {
            importedPackages.add(subpackageRuntime.containerPackageName)
            importedPackages.add(normalizedPackageName)
            subpackageStates[normalizedPackageName] = true

            saveImportedPackages(importedPackages.toList())
            saveToolPkgSubpackageStates(subpackageStates)
            removeFromDisabledPackages(subpackageRuntime.containerPackageName)
            toolPkgContainers[subpackageRuntime.containerPackageName]?.let { runtime ->
                ensureToolPkgCache(runtime)
            }

            val message = "Successfully enabled toolpkg subpackage: $normalizedPackageName"
            AppLogger.d(TAG, message)
            return message
        }

        if (importedPackages.contains(normalizedPackageName)) {
            return "Package '$normalizedPackageName' is already imported"
        }

        importedPackages.add(normalizedPackageName)
        saveImportedPackages(importedPackages.toList())
        removeFromDisabledPackages(normalizedPackageName)

        AppLogger.d(TAG, "Successfully imported package: $normalizedPackageName")
        return "Successfully imported package: $normalizedPackageName"
    }

    /**
     * Activates and loads a package for use in the current AI session This loads the full package
     * data and registers its tools with AIToolHandler
     * @param packageName The name of the imported package to use
     * @return Package description and tools for AI prompt enhancement, or error message
     */
    fun usePackage(packageName: String): String {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)

        val containerRuntime = toolPkgContainers[normalizedPackageName]
        if (containerRuntime != null) {
            return "ToolPkg container '$normalizedPackageName' is not a package and cannot be activated."
        }

        // First check if packageName is a standard imported package (priority)
        val importedPackages = getImportedPackages()
        val subpackageRuntime = toolPkgSubpackageByPackageName[normalizedPackageName]
        if (subpackageRuntime != null &&
            !importedPackages.contains(subpackageRuntime.containerPackageName)
        ) {
            return "ToolPkg container '${subpackageRuntime.containerPackageName}' is not enabled. Package '$normalizedPackageName' is inactive."
        }
        if (importedPackages.contains(normalizedPackageName)) {
            // Load the full package data for a standard package
            val toolPackage =
                getPackageTools(normalizedPackageName)
                    ?: return "Failed to load package data for: $normalizedPackageName"

            // Validate required environment variables, if any
            if (toolPackage.env.isNotEmpty()) {
                val missingRequiredEnv = mutableListOf<String>()
                val missingOptionalEnv = mutableListOf<Pair<String, String>>() // env name, default value

                toolPackage.env.forEach { envVar ->
                    val envName = envVar.name.trim()
                    if (envName.isEmpty()) return@forEach

                    val value = try {
                        envPreferences.getEnv(envName)
                    } catch (e: Exception) {
                        AppLogger.e(
                            TAG,
                            "Error reading environment variable '$envName' for package '$normalizedPackageName'",
                            e
                        )
                        null
                    }

                    if (envVar.required) {
                        // Check required environment variables
                        if (value.isNullOrEmpty()) {
                            missingRequiredEnv.add(envName)
                        }
                    } else {
                        // Check optional environment variables
                        if (value.isNullOrEmpty()) {
                            if (envVar.defaultValue != null) {
                                // Use default value for optional env vars
                                missingOptionalEnv.add(envName to envVar.defaultValue)
                                AppLogger.d(
                                    TAG,
                                    "Optional env var '$envName' not set for package '$normalizedPackageName', using default value: ${envVar.defaultValue}"
                                )
                            } else {
                                // Optional env var without default value is acceptable
                                AppLogger.d(
                                    TAG,
                                    "Optional env var '$envName' not set for package '$normalizedPackageName' (no default value)"
                                )
                            }
                        }
                    }
                }

                // Only fail if required environment variables are missing
                if (missingRequiredEnv.isNotEmpty()) {
                    val msg =
                        buildString {
                            append("Package '")
                            append(normalizedPackageName)
                            append("' requires environment variable")
                            if (missingRequiredEnv.size > 1) append("s")
                            append(": ")
                            append(missingRequiredEnv.joinToString(", "))
                            append(". Please set them before using this package.")
                        }
                    AppLogger.w(TAG, msg)
                    return msg
                }

                // Log info about optional env vars using defaults
                if (missingOptionalEnv.isNotEmpty()) {
                    AppLogger.i(
                        TAG,
                        "Package '$normalizedPackageName' will use default values for optional env vars: ${missingOptionalEnv.map { it.first }.joinToString(", ")}"
                    )
                }
            }

            // Register the package tools with AIToolHandler
            val selectedPackage = selectToolPackageState(toolPackage)
            registerPackageTools(selectedPackage)

            AppLogger.d(TAG, "Successfully loaded and activated package: $normalizedPackageName")

            // Generate and return the system prompt enhancement
            return generatePackageSystemPrompt(selectedPackage)
        }

        // Then check if it's a Skill package
        if (skillManager.getAvailableSkills().containsKey(normalizedPackageName) &&
            !skillVisibilityPreferences.isSkillVisibleToAi(normalizedPackageName)
        ) {
            return "Skill '$normalizedPackageName' is set to not show to AI"
        }

        val skillPrompt = skillManager.getSkillSystemPrompt(normalizedPackageName)
        if (skillPrompt != null) {
            return skillPrompt
        }

        // Next check if it's an MCP server by checking with MCPManager
        if (isRegisteredMCPServer(normalizedPackageName)) {
            return useMCPServer(normalizedPackageName)
        }

        return "Package not found: $normalizedPackageName. Please import it first or register it as an MCP server."
    }

    fun getActivePackageStateId(packageName: String): String? {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        return activePackageStateIds[normalizedPackageName]
    }

    /**
     * Wrapper for tool execution: builds ToolResult for the 'use_package' tool.
     * Keeps registration site minimal by centralizing result construction here.
     */
    fun executeUsePackageTool(toolName: String, packageName: String): ToolResult {
        if (packageName.isBlank()) {
            return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Missing required parameter: package_name"
            )
        }

        val normalizedPackageName = normalizePackageName(packageName)
        if (isToolPkgContainer(normalizedPackageName)) {
            return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "ToolPkg container '$normalizedPackageName' is not a package and cannot be activated."
            )
        }

        if (skillManager.getAvailableSkills().containsKey(normalizedPackageName) &&
            !skillVisibilityPreferences.isSkillVisibleToAi(normalizedPackageName)
        ) {
            return ToolResult(
                toolName = toolName,
                success = false,
                result = StringResultData(""),
                error = "Skill '$normalizedPackageName' is set to not show to AI"
            )
        }

        val text = usePackage(normalizedPackageName)
        return ToolResult(
            toolName = toolName,
            success = true,
            result = StringResultData(text)
        )
    }

    /**
     * 检查是否是已注册的MCP服务器
     *
     * @param serverName 服务器名称
     * @return 如果是已注册的MCP服务器则返回true
     */
    private fun isRegisteredMCPServer(serverName: String): Boolean {
        return mcpManager.isServerRegistered(serverName)
    }

    /**
     * 获取所有可用的MCP服务器包
     *
     * @return MCP服务器列表
     */
    fun getAvailableServerPackages(): Map<String, MCPServerConfig> {
        return mcpManager.getRegisteredServers()
    }

    // Helper function to determine if a package is an MCP server
    private fun isMCPServerPackage(toolPackage: ToolPackage): Boolean {
        // Check if any tool has MCP script placeholder
        return if (toolPackage.tools.isNotEmpty()) {
            val script = toolPackage.tools[0].script
            script.contains("/* MCPJS") // Check for MCP script marker
        } else {
            false
        }
    }

    /** Registers all tools in a package with the AIToolHandler */
    private fun registerPackageTools(toolPackage: ToolPackage) {
        val packageToolExecutor = PackageToolExecutor(toolPackage, context, this)
        val executableTools = toolPackage.tools.filter { !it.advice }
        val newToolNames = executableTools.map { packageTool -> "${toolPackage.name}:${packageTool.name}" }.toSet()
        val oldToolNames = activePackageToolNames[toolPackage.name] ?: emptySet()
        (oldToolNames - newToolNames).forEach { toolName ->
            aiToolHandler.unregisterTool(toolName)
        }
        activePackageToolNames[toolPackage.name] = newToolNames

        // Register each tool with the format packageName:toolName
        executableTools.forEach { packageTool ->
            val toolName = "${toolPackage.name}:${packageTool.name}"
            aiToolHandler.registerTool(toolName) { tool ->
                packageToolExecutor.invoke(tool)
            }
        }
    }

    private fun selectToolPackageState(toolPackage: ToolPackage): ToolPackage {
        if (toolPackage.states.isEmpty()) {
            activePackageStateIds.remove(toolPackage.name)
            return toolPackage
        }

        val capabilities = buildConditionCapabilitiesSnapshot()
        val selectedState = toolPackage.states.firstOrNull { state ->
            ConditionEvaluator.evaluate(state.condition, capabilities)
        }

        if (selectedState == null) {
            activePackageStateIds.remove(toolPackage.name)
            return toolPackage
        }

        activePackageStateIds[toolPackage.name] = selectedState.id

        val mergedTools = mergeToolsForState(toolPackage.tools, selectedState)
        return toolPackage.copy(tools = mergedTools)
    }

    private fun mergeToolsForState(baseTools: List<PackageTool>, state: ToolPackageState): List<PackageTool> {
        val toolMap = linkedMapOf<String, PackageTool>()
        if (state.inheritTools) {
            baseTools.forEach { toolMap[it.name] = it }
        }
        state.excludeTools.forEach { toolMap.remove(it) }
        state.tools.forEach { toolMap[it.name] = it }
        return toolMap.values.toList()
    }

    private fun buildConditionCapabilitiesSnapshot(): Map<String, Any?> {
        val level = try {
            androidPermissionPreferences.getPreferredPermissionLevel() ?: AndroidPermissionLevel.STANDARD
        } catch (_: Exception) {
            AndroidPermissionLevel.STANDARD
        }

        val shizukuAvailable = try {
            ShizukuAuthorizer.isShizukuServiceRunning() && ShizukuAuthorizer.hasShizukuPermission()
        } catch (_: Exception) {
            false
        }

        val experimentalEnabled = try {
            DisplayPreferencesManager.getInstance(context).isExperimentalVirtualDisplayEnabled()
        } catch (_: Exception) {
            true
        }

        val adbOrHigher = when (level) {
            AndroidPermissionLevel.DEBUGGER,
            AndroidPermissionLevel.ADMIN,
            AndroidPermissionLevel.ROOT -> true
            else -> false
        }

        val virtualDisplayCapable = adbOrHigher && experimentalEnabled && (level != AndroidPermissionLevel.DEBUGGER || shizukuAvailable)

        return mapOf(
            "ui.virtual_display" to virtualDisplayCapable,
            "android.permission_level" to level,
            "android.shizuku_available" to shizukuAvailable,
            "ui.shower_display" to (try { ShowerController.getDisplayId("default") != null } catch (_: Exception) { false })
        )
    }

    /** Generates a system prompt enhancement for the imported package */
    private fun generatePackageSystemPrompt(toolPackage: ToolPackage): String {
        val sb = StringBuilder()

        sb.appendLine("Using package: ${toolPackage.name}")
        sb.appendLine("Use Time: ${java.time.LocalDateTime.now()}")
        sb.appendLine("Description: ${toolPackage.description.resolve(context)}")
        sb.appendLine()
        sb.appendLine("Available tools in this package:")

        toolPackage.tools.forEach { tool ->
            val toolLabel =
                if (tool.advice) {
                    "- (advice): ${tool.description.resolve(context)}"
                } else {
                    "- ${toolPackage.name}:${tool.name}: ${tool.description.resolve(context)}"
                }
            sb.appendLine(toolLabel)
            if (tool.parameters.isNotEmpty()) {
                sb.appendLine("  Parameters:")
                tool.parameters.forEach { param ->
                    val requiredText = if (param.required) "(required)" else "(optional)"
                    sb.appendLine("  - ${param.name} ${requiredText}: ${param.description.resolve(context)}")
                }
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Gets a list of all available packages for discovery (the "market").
     *
     * By default this returns the in-memory cache to avoid re-scanning assets/external storage
     * on every call (which is expensive and can spam logs).
     *
     * @param forceRefresh Set to true to explicitly rescan package sources.
     * @return A map of package name to description
     */
    fun getAvailablePackages(forceRefresh: Boolean = false): Map<String, ToolPackage> {
        ensureInitialized()
        if (forceRefresh) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                initializationScope.launch {
                    runCatching { loadAvailablePackages() }
                        .onFailure { error ->
                            AppLogger.e(TAG, "Failed to refresh packages on background", error)
                            logToolPkgError("forceRefresh background reload failed", error)
                        }
                }
            } else {
                loadAvailablePackages()
            }
        }
        return availablePackages
    }

    /**
     * Get a list of all imported packages
     * @return A list of imported package names
     */
    fun getImportedPackages(): List<String> {
        ensureInitialized()
        return getImportedPackagesInternal()
    }

    private fun getImportedPackagesInternal(): List<String> {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val packagesJson = prefs.getString(IMPORTED_PACKAGES_KEY, "[]")
        return try {
            val jsonConfig = Json { ignoreUnknownKeys = true }
            val rawPackages = jsonConfig.decodeFromString<List<String>>(packagesJson ?: "[]")
            val normalizedPackages = normalizeImportedPackageNames(rawPackages)
            if (!isInitialized) {
                normalizedPackages
            } else {
                cleanupNonExistentPackages(normalizedPackages)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error decoding imported packages", e)
            emptyList()
        }
    }

    /**
     * 清理导入列表中不存在的包。
     * 自动移除那些已经被删除但仍然在导入列表中的包。
     */
    private fun cleanupNonExistentPackages(currentPackages: List<String>): List<String> {
        // Serialize cleanup with package reload to avoid transient map states
        // (e.g. during forceRefresh) causing accidental removal of valid imports.
        synchronized(initLock) {
            val normalizedPackages = normalizeImportedPackageNames(currentPackages)
            val cleanedPackages = normalizedPackages.filter { packageName ->
                availablePackages.containsKey(packageName)
            }

            if (cleanedPackages.size != currentPackages.size || cleanedPackages != currentPackages) {
                val removed = currentPackages.filter { !cleanedPackages.contains(it) }
                AppLogger.d(
                    TAG,
                    "Found ${removed.size} non-existent packages in imported list: $removed"
                )
                saveImportedPackages(cleanedPackages)
                AppLogger.d(TAG, "Cleaned up imported packages list. Removed: $removed")
            }

            val states = getToolPkgSubpackageStatesInternal()
            val cleanedStates =
                states.filterKeys { packageName ->
                    toolPkgSubpackageByPackageName.containsKey(packageName)
                }

            if (cleanedStates.size != states.size) {
                saveToolPkgSubpackageStates(cleanedStates)
            }

            return cleanedPackages
        }
    }

    /**
     * Get a list of all disabled packages
     * @return A list of disabled package names
     */
    fun getDisabledPackages(): List<String> {
        ensureInitialized()
        return getDisabledPackagesInternal()
    }

    private fun getDisabledPackagesInternal(): List<String> {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val packagesJson = prefs.getString(DISABLED_PACKAGES_KEY, "[]")
        return try {
            val jsonConfig = Json { ignoreUnknownKeys = true }
            jsonConfig.decodeFromString<List<String>>(packagesJson ?: "[]")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error decoding disabled packages", e)
            emptyList()
        }
    }

    /** Helper to save disabled packages */
    private fun saveDisabledPackages(disabledPackages: List<String>) {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val updatedJson = Json.encodeToString(disabledPackages)
        prefs.edit().putString(DISABLED_PACKAGES_KEY, updatedJson).apply()
    }

    internal fun saveImportedPackages(importedPackages: List<String>) {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val updatedJson = Json.encodeToString(importedPackages)
        prefs.edit().putString(IMPORTED_PACKAGES_KEY, updatedJson).apply()
    }

    internal fun getToolPkgSubpackageStatesInternal(): Map<String, Boolean> {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val statesJson = prefs.getString(TOOLPKG_SUBPACKAGE_STATES_KEY, "{}")
        return try {
            val jsonConfig = Json { ignoreUnknownKeys = true }
            val rawStates = jsonConfig.decodeFromString<Map<String, Boolean>>(statesJson ?: "{}")
            if (!isInitialized) {
                return rawStates
            }
            val normalizedStates = normalizeToolPkgSubpackageStates(rawStates)
            if (normalizedStates != rawStates) {
                saveToolPkgSubpackageStates(normalizedStates)
            }
            normalizedStates
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error decoding toolpkg subpackage states", e)
            emptyMap()
        }
    }

    internal fun saveToolPkgSubpackageStates(states: Map<String, Boolean>) {
        val prefs = context.getSharedPreferences(PACKAGE_PREFS, Context.MODE_PRIVATE)
        val updatedJson = Json.encodeToString(states)
        prefs.edit().putString(TOOLPKG_SUBPACKAGE_STATES_KEY, updatedJson).apply()
    }

    private fun removeFromDisabledPackages(packageName: String) {
        val disabledPackages = getDisabledPackages().toMutableList()
        if (disabledPackages.remove(packageName)) {
            saveDisabledPackages(disabledPackages)
            AppLogger.d(TAG, "Removed package from disabled list: $packageName")
        }
    }

    private fun addToDisabledIfDefaultEnabled(packageName: String) {
        val toolPackage = availablePackages[packageName]
        if (toolPackage != null && toolPackage.isBuiltIn && toolPackage.enabledByDefault) {
            val disabledPackages = getDisabledPackages().toMutableList()
            if (!disabledPackages.contains(packageName)) {
                disabledPackages.add(packageName)
                saveDisabledPackages(disabledPackages)
                AppLogger.d(TAG, "Added default package to disabled list: $packageName")
            }
        }
    }

    internal fun unregisterPackageTools(packageName: String) {
        val activeTools = activePackageToolNames.remove(packageName).orEmpty()
        activeTools.forEach { toolName ->
            aiToolHandler.unregisterTool(toolName)
        }
        activePackageStateIds.remove(packageName)
    }

    /**
     * Get the tools for a loaded package
     * @param packageName The name of the loaded package
     * @return The ToolPackage object or null if the package is not loaded
     */
    fun getPackageTools(packageName: String): ToolPackage? {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        return availablePackages[normalizedPackageName]
    }

    fun getEffectivePackageTools(packageName: String): ToolPackage? {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        val toolPackage = availablePackages[normalizedPackageName] ?: return null
        return selectToolPackageState(toolPackage)
    }

    /** Checks if a package is imported */
    fun isPackageImported(packageName: String): Boolean {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        val importedPackages = getImportedPackages()
        if (!importedPackages.contains(normalizedPackageName)) {
            return false
        }
        val subpackageRuntime = toolPkgSubpackageByPackageName[normalizedPackageName]
        if (subpackageRuntime != null) {
            return importedPackages.contains(subpackageRuntime.containerPackageName)
        }
        return true
    }

    /**
     * Remove an imported package.
     * For toolpkg containers this also disables/removes all internal subpackages.
     */
    fun removePackage(packageName: String): String {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)

        val currentPackages = LinkedHashSet(getImportedPackages())
        val subpackageStates = getToolPkgSubpackageStatesInternal().toMutableMap()
        var packageWasRemoved = false

        val containerRuntime = toolPkgContainers[normalizedPackageName]
        if (containerRuntime != null) {
            packageWasRemoved = currentPackages.remove(normalizedPackageName) || packageWasRemoved
            unregisterPackageTools(normalizedPackageName)

            containerRuntime.subpackages.forEach { subpackage ->
                packageWasRemoved = currentPackages.remove(subpackage.packageName) || packageWasRemoved
                unregisterPackageTools(subpackage.packageName)
            }

            saveImportedPackages(currentPackages.toList())
            saveToolPkgSubpackageStates(subpackageStates)
            addToDisabledIfDefaultEnabled(normalizedPackageName)
            deleteToolPkgCacheDir(normalizedPackageName)
            destroyDefaultToolPkgExecutionEngine(normalizedPackageName)

            return if (packageWasRemoved) {
                "Successfully disabled toolpkg container: $normalizedPackageName"
            } else {
                "ToolPkg container is already disabled: $normalizedPackageName"
            }
        }

        if (toolPkgSubpackageByPackageName.containsKey(normalizedPackageName)) {
            packageWasRemoved = currentPackages.remove(normalizedPackageName)
            subpackageStates[normalizedPackageName] = false
            unregisterPackageTools(normalizedPackageName)

            saveImportedPackages(currentPackages.toList())
            saveToolPkgSubpackageStates(subpackageStates)

            return if (packageWasRemoved) {
                "Successfully removed package: $normalizedPackageName"
            } else {
                "Package not found in imported list: $normalizedPackageName"
            }
        }

        packageWasRemoved = currentPackages.remove(normalizedPackageName)
        unregisterPackageTools(normalizedPackageName)
        addToDisabledIfDefaultEnabled(normalizedPackageName)

        return if (packageWasRemoved) {
            saveImportedPackages(currentPackages.toList())
            AppLogger.d(TAG, "Removed package from imported list: $normalizedPackageName")
            "Successfully removed package: $normalizedPackageName"
        } else {
            AppLogger.d(TAG, "Package not found in imported list: $normalizedPackageName")
            "Package not found in imported list: $normalizedPackageName"
        }
    }

    /**
     * Get the script content for a package by name
     * @param packageName The name of the package
     * @return The full JavaScript content of the package or null if not found
     */
    fun getPackageScript(packageName: String): String? {
        ensureInitialized()
        val normalizedPackageName = normalizePackageName(packageName)
        val toolPackage = availablePackages[normalizedPackageName] ?: return null

        // Load script based on whether it's built-in or external
        // All tools in a package share the same script, so we can get it from any tool
        return if (toolPackage.tools.isNotEmpty()) {
            toolPackage.tools[0].script
        } else {
            null
        }
    }

    /**
     * 使用MCP服务器
     *
     * @param serverName 服务器名称
     * @return 成功或失败的消息
     */
    fun useMCPServer(serverName: String): String {
        // 检查服务器是否已注册
        if (!mcpManager.isServerRegistered(serverName)) {
            return "MCP server '$serverName' does not exist or is not registered."
        }

        // 获取服务器配置
        val serverConfig =
            mcpManager.getRegisteredServers()[serverName]
                ?: return "Cannot get MCP server configuration: $serverName"

        // 创建MCP包
        val mcpPackage =
            MCPPackage.fromServer(context, serverConfig)
                ?: return "Cannot connect to MCP server: $serverName"

        // 转换为标准工具包
        val toolPackage = mcpPackage.toToolPackage()

        // 获取或创建MCP工具执行器
        val mcpToolExecutor = MCPToolExecutor(context, mcpManager)

        // 注册包中的每个工具 - 使用 serverName:toolName 格式
        toolPackage.tools.forEach { packageTool ->
            val toolName = "$serverName:${packageTool.name}"

            // 使用MCP特定的执行器注册工具
            aiToolHandler.registerTool(
                name = toolName,
                executor = mcpToolExecutor
            )

            AppLogger.d(TAG, "Registered MCP tool: $toolName")
        }

        return generateMCPSystemPrompt(toolPackage, serverName)
    }

    /** 为MCP服务器生成系统提示 */
    private fun generateMCPSystemPrompt(toolPackage: ToolPackage, serverName: String): String {
        val sb = StringBuilder()

        sb.appendLine("Using MCP server: $serverName")
        sb.appendLine("Time: ${java.time.LocalDateTime.now()}")
        sb.appendLine("Description: ${toolPackage.description.resolve(context)}")
        sb.appendLine()
        sb.appendLine("Available tools:")

        toolPackage.tools.forEach { tool ->
            // 使用 serverName:toolName 格式
            sb.appendLine("- $serverName:${tool.name}: ${tool.description.resolve(context)}")
            if (tool.parameters.isNotEmpty()) {
                sb.appendLine("  Parameters:")
                tool.parameters.forEach { param ->
                    val requiredText = if (param.required) "(required)" else "(optional)"
                    sb.appendLine("  - ${param.name} ${requiredText}: ${param.description.resolve(context)}")
                }
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Deletes a package file from external storage and removes it from the in-memory cache.
     * This action is permanent and cannot be undone.
     */
    fun deletePackage(packageName: String): Boolean {
        val normalizedPackageName = normalizePackageName(packageName)
        AppLogger.d(TAG, "Attempting to delete package: $normalizedPackageName")
        ensureInitialized()

        if (toolPkgSubpackageByPackageName.containsKey(normalizedPackageName)) {
            // Subpackage is part of a toolpkg archive; only remove enable state.
            removePackage(normalizedPackageName)
            return true
        }

        val packageFile = findPackageFile(normalizedPackageName)

        if (packageFile == null || !packageFile.exists()) {
            AppLogger.w(
                TAG,
                "Package file not found for deletion: $normalizedPackageName. It might be already deleted or never existed."
            )
            removePackage(normalizedPackageName)
            removeFromCachesAfterDelete(normalizedPackageName)
            return true
        }

        AppLogger.d(TAG, "Found package file to delete: ${packageFile.absolutePath}")

        val fileDeleted = packageFile.delete()

        if (fileDeleted) {
            AppLogger.d(TAG, "Successfully deleted package file: ${packageFile.absolutePath}")
            removePackage(normalizedPackageName)
            removeFromCachesAfterDelete(normalizedPackageName)
            AppLogger.d(TAG, "Package '$normalizedPackageName' fully deleted.")
            return true
        }

        AppLogger.e(TAG, "Failed to delete package file: ${packageFile.absolutePath}")
        return false
    }

    private fun removeFromCachesAfterDelete(packageName: String) {
        if (toolPkgContainers.containsKey(packageName)) {
            val container = toolPkgContainers.remove(packageName)
            availablePackages.remove(packageName)
            deleteToolPkgCacheDir(packageName)
            destroyDefaultToolPkgExecutionEngine(packageName)

            val states = getToolPkgSubpackageStatesInternal().toMutableMap()
            container?.subpackages?.forEach { subpackage ->
                availablePackages.remove(subpackage.packageName)
                toolPkgSubpackageByPackageName.remove(subpackage.packageName)
                states.remove(subpackage.packageName)
            }
            saveToolPkgSubpackageStates(states)
            return
        }

        deleteToolPkgCacheDir(packageName)
        availablePackages.remove(packageName)
        toolPkgSubpackageByPackageName.remove(packageName)
    }

    /**
     * Finds the File object for a given package name in external storage.
     */
    private fun findPackageFile(packageName: String): File? {
        val normalizedPackageName = normalizePackageName(packageName)
        val externalPackagesDir = File(context.getExternalFilesDir(null), PACKAGES_DIR)
        if (!externalPackagesDir.exists()) return null

        val containerRuntime = toolPkgContainers[normalizedPackageName]
        if (containerRuntime != null && containerRuntime.sourceType == ToolPkgSourceType.EXTERNAL) {
            val candidate = File(containerRuntime.sourcePath)
            if (candidate.exists()) {
                return candidate
            }
        }

        val jsFile = File(externalPackagesDir, "$normalizedPackageName.js")
        if (jsFile.exists()) return jsFile

        externalPackagesDir.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach

            if (file.name.endsWith(".js", ignoreCase = true)) {
                val loadedPackage = loadPackageFromJsFile(file)
                if (loadedPackage?.name == normalizedPackageName) {
                    return file
                }
            }

            if (file.name.endsWith(TOOLPKG_EXTENSION, ignoreCase = true)) {
                val loadedToolPkg = loadToolPkgFromExternalFile(file)
                if (loadedToolPkg?.containerPackage?.name == normalizedPackageName) {
                    return file
                }
            }
        }

        return null
    }

    /**
     * 将 ToolPackage 转换为 PackageToolPromptCategory
     * 用于生成结构化的包工具提示词
     *
     * @param toolPackage 要转换的工具包
     * @return PackageToolPromptCategory 对象
     */
    fun toPromptCategory(toolPackage: ToolPackage): PackageToolPromptCategory {
        val toolPrompts = toolPackage.tools.map { packageTool ->
            // 将 PackageTool 转换为 ToolPrompt
            val parametersString = if (packageTool.parameters.isNotEmpty()) {
                packageTool.parameters.joinToString(", ") { param ->
                    val required = if (param.required) "required" else "optional"
                    "${param.name} (${param.type}, $required)"
                }
            } else {
                ""
            }

            ToolPrompt(
                name = packageTool.name,
                description = packageTool.description.resolve(context),
                parameters = parametersString
            )
        }

        return PackageToolPromptCategory(
            packageName = toolPackage.name,
            packageDescription = toolPackage.description.resolve(context),
            tools = toolPrompts
        )
    }

    /**
     * 获取所有已导入包的提示词分类列表
     *
     * @return 已导入包的 PackageToolPromptCategory 列表
     */
    fun getImportedPackagesPromptCategories(): List<PackageToolPromptCategory> {
        ensureInitialized()
        val importedPackageNames = getImportedPackages()
        return importedPackageNames.mapNotNull { packageName ->
            getPackageTools(packageName)
                ?.takeIf { it.tools.isNotEmpty() }
                ?.let { toolPackage ->
                    toPromptCategory(toolPackage)
                }
        }
    }

    /** Clean up resources when the manager is no longer needed */
    fun destroy() {
        toolPkgExecutionEngines.values.forEach { engine ->
            engine.destroy()
        }
        toolPkgExecutionEngines.clear()
        jsEngine.destroy()
        mcpManager.shutdown()
    }
}

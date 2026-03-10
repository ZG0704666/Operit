package com.ai.assistance.operit.core.tools.javascript

import android.content.Context
import android.os.Build
import com.ai.assistance.operit.util.AppLogger
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileOutputStream
import java.util.LinkedHashMap
import java.util.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

internal class JsExternalJavaCodeLoader(private val context: Context) {
    private enum class SourceType(val wireName: String) {
        DEX("dex"),
        JAR("jar");
    }

    private data class LoadOptions(
        val nativeLibraryDir: String?
    )

    private data class LoadedArtifact(
        val sourceType: SourceType,
        val sourcePath: String,
        val nativeLibraryDir: String?,
        val classLoader: ClassLoader
    ) {
        fun toJson(index: Int, alreadyLoaded: Boolean): JSONObject {
            return JSONObject()
                .put("index", index)
                .put("type", sourceType.wireName)
                .put("path", sourcePath)
                .put("nativeLibraryDir", nativeLibraryDir)
                .put("alreadyLoaded", alreadyLoaded)
        }
    }

    companion object {
        private const val TAG = "JsExternalJavaCodeLoader"
    }

    private val loadedArtifacts = LinkedHashMap<String, LoadedArtifact>()

    @Synchronized
    fun getEffectiveClassLoader(baseClassLoader: ClassLoader): ClassLoader {
        return loadedArtifacts.values.lastOrNull()?.classLoader ?: baseClassLoader
    }

    @Synchronized
    fun loadDex(path: String, optionsJson: String, baseClassLoader: ClassLoader): String {
        return load(SourceType.DEX, path, optionsJson, baseClassLoader)
    }

    @Synchronized
    fun loadJar(path: String, optionsJson: String, baseClassLoader: ClassLoader): String {
        return load(SourceType.JAR, path, optionsJson, baseClassLoader)
    }

    @Synchronized
    fun listLoadedArtifacts(): String {
        val payload = JSONArray()
        loadedArtifacts.values.forEachIndexed { index, artifact ->
            payload.put(artifact.toJson(index = index, alreadyLoaded = true))
        }
        return success(payload)
    }

    private fun load(
        sourceType: SourceType,
        path: String,
        optionsJson: String,
        baseClassLoader: ClassLoader
    ): String {
        return try {
            val normalizedPath = normalizeSourcePath(path)
            val options = parseOptions(optionsJson)
            val sourceFile = File(normalizedPath)

            require(sourceFile.exists()) { "external code file does not exist: $normalizedPath" }
            require(sourceFile.isFile) { "external code path is not a file: $normalizedPath" }
            require(sourceFile.canRead()) { "external code file is not readable: $normalizedPath" }

            val canonicalPath = sourceFile.canonicalPath
            validateSourceFile(sourceType = sourceType, sourceFile = sourceFile, canonicalPath = canonicalPath)
            ensureJvmCompatibilitySystemProperties()

            val nativeLibraryDir = resolveNativeLibraryDir(options.nativeLibraryDir)
            val artifactKey = buildArtifactKey(sourceType, canonicalPath, nativeLibraryDir)
            loadedArtifacts[artifactKey]?.let { existing ->
                return success(existing.toJson(indexOf(artifactKey), alreadyLoaded = true))
            }

            val preparedSourceFile =
                prepareLoadableSourceFile(
                    sourceType = sourceType,
                    sourceFile = sourceFile,
                    canonicalPath = canonicalPath
                )
            val optimizedDir = ensureOptimizedDir()
            val parent = getEffectiveClassLoader(baseClassLoader)
            val classLoader =
                DexClassLoader(
                    preparedSourceFile.absolutePath,
                    optimizedDir.absolutePath,
                    nativeLibraryDir,
                    parent
                )

            val artifact =
                LoadedArtifact(
                    sourceType = sourceType,
                    sourcePath = preparedSourceFile.absolutePath,
                    nativeLibraryDir = nativeLibraryDir,
                    classLoader = classLoader
                )
            loadedArtifacts[artifactKey] = artifact
            success(artifact.toJson(indexOf(artifactKey), alreadyLoaded = false))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load external ${sourceType.wireName}: ${e.message}", e)
            failure(e.message ?: "failed to load external ${sourceType.wireName}")
        }
    }

    private fun validateSourceFile(sourceType: SourceType, sourceFile: File, canonicalPath: String) {
        when (sourceType) {
            SourceType.DEX -> {
                val extension = sourceFile.extension.lowercase()
                require(extension == "dex") {
                    "loadDex only accepts .dex files, got: $canonicalPath"
                }
            }

            SourceType.JAR -> {
                val extension = sourceFile.extension.lowercase()
                require(extension == "jar") {
                    "loadJar only accepts .jar files, got: $canonicalPath"
                }
                ZipFile(sourceFile).use { zip ->
                    require(zip.getEntry("classes.dex") != null) {
                        "jar must contain classes.dex; plain JVM bytecode jars are not supported on Android"
                    }
                }
            }
        }
    }

    private fun normalizeSourcePath(path: String): String {
        val normalized = path.trim()
        require(normalized.isNotEmpty()) { "external code path is required" }
        return normalized
    }

    private fun parseOptions(optionsJson: String): LoadOptions {
        val normalized = optionsJson.trim()
        if (normalized.isEmpty()) {
            return LoadOptions(nativeLibraryDir = null)
        }

        val parsed = JSONObject(normalized)
        val nativeLibraryDir = parsed.optString("nativeLibraryDir").trim().ifEmpty { null }
        return LoadOptions(nativeLibraryDir = nativeLibraryDir)
    }

    private fun resolveNativeLibraryDir(nativeLibraryDir: String?): String? {
        val normalized = nativeLibraryDir?.trim()?.ifEmpty { null } ?: return null
        val dir = File(normalized)
        require(dir.exists()) { "native library dir does not exist: $normalized" }
        require(dir.isDirectory) { "native library dir is not a directory: $normalized" }
        return dir.canonicalPath
    }

    private fun prepareLoadableSourceFile(
        sourceType: SourceType,
        sourceFile: File,
        canonicalPath: String
    ): File {
        val targetDir = ensurePreparedSourceDir()
        val targetFile = File(targetDir, buildPreparedSourceFileName(sourceType, canonicalPath))

        if (targetFile.exists()) {
            require(targetFile.delete()) {
                "failed to replace prepared external ${sourceType.wireName}: ${targetFile.absolutePath}"
            }
        }

        sourceFile.inputStream().use { input ->
            FileOutputStream(targetFile).use { output ->
                require(targetFile.setReadOnly()) {
                    "failed to mark prepared external ${sourceType.wireName} as read-only: ${targetFile.absolutePath}"
                }
                input.copyTo(output)
                output.fd.sync()
            }
        }

        require(targetFile.exists() && targetFile.isFile) {
            "prepared external ${sourceType.wireName} is unavailable: ${targetFile.absolutePath}"
        }
        require(targetFile.canRead()) {
            "prepared external ${sourceType.wireName} is not readable: ${targetFile.absolutePath}"
        }
        require(!targetFile.canWrite()) {
            "prepared external ${sourceType.wireName} must be read-only: ${targetFile.absolutePath}"
        }

        return targetFile
    }

    private fun ensureJvmCompatibilitySystemProperties() {
        val filesDirPath = context.filesDir.absolutePath
        val cacheDirPath = (context.cacheDir ?: context.codeCacheDir ?: context.filesDir).absolutePath
        val locale = Locale.getDefault()
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        val osArch = if (is64Bit) "aarch64" else "arm"
        val archDataModel = if (is64Bit) "64" else "32"

        ensureSystemProperty("os.name", "Linux")
        ensureSystemProperty("os.arch", osArch)
        ensureSystemProperty("sun.arch.data.model", archDataModel)
        ensureSystemProperty("user.home", filesDirPath)
        ensureSystemProperty("user.dir", filesDirPath)
        ensureSystemProperty("java.io.tmpdir", cacheDirPath)
        ensureSystemProperty("user.language", locale.language.ifBlank { "en" })
        if (locale.country.isNotBlank()) {
            ensureSystemProperty("user.country", locale.country)
        }
    }

    private fun ensureSystemProperty(key: String, value: String) {
        if (value.isBlank()) {
            return
        }
        val current = System.getProperty(key)?.trim().orEmpty()
        if (current.isBlank()) {
            System.setProperty(key, value)
        }
    }

    private fun ensureOptimizedDir(): File {
        return ensureManagedDirectory("js-external-code-optimized")
    }

    private fun ensurePreparedSourceDir(): File {
        return ensureManagedDirectory("js-external-code-sources")
    }

    private fun ensureManagedDirectory(childName: String): File {
        val parentDir = context.codeCacheDir ?: context.cacheDir
        requireNotNull(parentDir) { "app cache directory is unavailable" }
        val managedDir = File(parentDir, childName)
        if (!managedDir.exists()) {
            managedDir.mkdirs()
        }
        require(managedDir.exists() && managedDir.isDirectory) {
            "managed code directory is unavailable: ${managedDir.absolutePath}"
        }
        return managedDir
    }

    private fun buildPreparedSourceFileName(sourceType: SourceType, canonicalPath: String): String {
        val digest =
            MessageDigest.getInstance("SHA-256")
                .digest("${sourceType.wireName}|$canonicalPath".toByteArray())
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "${sourceType.wireName}-$digest.${sourceType.wireName}"
    }

    private fun buildArtifactKey(
        sourceType: SourceType,
        canonicalPath: String,
        nativeLibraryDir: String?
    ): String {
        return listOf(sourceType.wireName, canonicalPath, nativeLibraryDir.orEmpty()).joinToString("|")
    }

    private fun indexOf(artifactKey: String): Int {
        return loadedArtifacts.keys.indexOf(artifactKey).coerceAtLeast(0)
    }

    private fun success(data: Any?): String {
        return JSONObject()
            .put("success", true)
            .put("data", data)
            .toString()
    }

    private fun failure(message: String): String {
        return JSONObject()
            .put("success", false)
            .put("error", message)
            .toString()
    }
}

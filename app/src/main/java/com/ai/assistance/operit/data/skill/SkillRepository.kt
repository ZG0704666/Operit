package com.ai.assistance.operit.data.skill

import android.content.Context
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.core.tools.skill.SkillPackage
import com.ai.assistance.operit.util.AppLogger
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SkillRepository private constructor(private val context: Context) {

    companion object {
        @Volatile private var INSTANCE: SkillRepository? = null

        private const val TAG = "SkillRepository"
        private const val CONNECT_TIMEOUT = 15_000
        private const val READ_TIMEOUT = 30_000
        private const val BUFFER_SIZE = 64 * 1024

        fun getInstance(context: Context): SkillRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SkillRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val skillManager by lazy { SkillManager.getInstance(context) }

    private data class GitHubSkillTarget(
        val owner: String,
        val repo: String,
        val ref: String?,
        val subDir: String?
    )

    fun getSkillsDirectoryPath(): String = skillManager.getSkillsDirectoryPath()

    fun getAvailableSkillPackages(): Map<String, SkillPackage> = skillManager.getAvailableSkills()

    fun readSkillContent(skillName: String): String? = skillManager.readSkillContent(skillName)

    fun deleteSkill(skillName: String): Boolean = skillManager.deleteSkill(skillName)

    suspend fun importSkillFromZip(zipFile: File): String {
        return withContext(Dispatchers.IO) {
            skillManager.importSkillFromZip(zipFile)
        }
    }

    suspend fun importSkillFromGitHubRepo(repoUrl: String): String {
        return withContext(Dispatchers.IO) {
            val target = parseGitHubSkillTarget(repoUrl)
                ?: return@withContext "无效的 GitHub 仓库 URL"

            val owner = target.owner
            val repoName = target.repo
            val ref = target.ref ?: getGithubDefaultBranch(owner, repoName)
                ?: return@withContext "无法确定 $owner/$repoName 的默认分支"

            val encodedRef = encodePathSegment(ref)
            val zipUrl = "https://codeload.github.com/$owner/$repoName/zip/$encodedRef"
            val suffix = (target.subDir ?: "repo")
                .replace('/', '_')
                .take(60)
            val tempFile = File(context.cacheDir, "skill_${owner}_${repoName}_$suffix.zip")
            if (tempFile.exists()) tempFile.delete()

            try {
                val skillsRootDir = File(getSkillsDirectoryPath())
                val beforeDirs = skillsRootDir.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()

                val downloaded = downloadFromUrl(zipUrl, tempFile)
                if (!downloaded || !tempFile.exists() || tempFile.length() <= 0L) {
                    if (tempFile.exists()) tempFile.delete()
                    return@withContext "下载仓库 ZIP 文件失败"
                }

                val result = skillManager.importSkillFromZip(tempFile, target.subDir)
                tempFile.delete()

                // Write repoUrl marker for reliable installed-state detection.
                if (result.startsWith("已导入 Skill:")) {
                    val afterDirs = skillsRootDir.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toSet() ?: emptySet()
                    val newDirs = afterDirs - beforeDirs
                    val newDirName = newDirs.singleOrNull()
                    if (!newDirName.isNullOrBlank()) {
                        try {
                            File(skillsRootDir, newDirName)
                                .resolve(".operit_repo_url")
                                .writeText(repoUrl.trim())
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Failed to write .operit_repo_url marker", e)
                        }
                    }
                }

                result
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to import skill from GitHub repo", e)
                if (tempFile.exists()) tempFile.delete()
                "导入失败: ${e.message}"
            }
        }
    }

    private fun parseGitHubSkillTarget(inputUrlRaw: String): GitHubSkillTarget? {
        val inputUrl = inputUrlRaw.trim()
        if (inputUrl.isBlank()) return null

        val urlWithScheme = if (inputUrl.startsWith("http://", ignoreCase = true) || inputUrl.startsWith("https://", ignoreCase = true)) {
            inputUrl
        } else {
            "https://$inputUrl"
        }

        val urlNoFragment = urlWithScheme.substringBefore('#')
        val uri = try {
            URI(urlNoFragment)
        } catch (_: Exception) {
            return null
        }

        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty()
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.size < 2) return null

        fun cleanRepoName(repoRaw: String): String {
            return repoRaw.removeSuffix(".git")
        }

        return when {
            host == "github.com" || host.endsWith(".github.com") -> {
                val owner = segments[0]
                val repo = cleanRepoName(segments[1])
                if (owner.isBlank() || repo.isBlank()) return null

                var ref: String? = null
                var subDir: String? = null

                if (segments.size >= 4 && (segments[2] == "tree" || segments[2] == "blob")) {
                    ref = segments[3]
                    val remainder = if (segments.size > 4) segments.subList(4, segments.size).joinToString("/") else ""
                    if (remainder.isNotBlank()) {
                        subDir = if (segments[2] == "blob") {
                            if (remainder.endsWith("SKILL.md", ignoreCase = true) || remainder.endsWith("skill.md", ignoreCase = true)) {
                                remainder.substringBeforeLast('/')
                            } else {
                                remainder.substringBeforeLast('/').ifBlank { null }
                            }
                        } else {
                            remainder
                        }
                    }
                }

                GitHubSkillTarget(owner = owner, repo = repo, ref = ref, subDir = subDir)
            }

            host == "raw.githubusercontent.com" -> {
                if (segments.size < 4) return null
                val owner = segments[0]
                val repo = cleanRepoName(segments[1])
                val ref = segments[2]
                val remainder = segments.subList(3, segments.size).joinToString("/")
                val subDir = if (remainder.endsWith("SKILL.md", ignoreCase = true) || remainder.endsWith("skill.md", ignoreCase = true)) {
                    remainder.substringBeforeLast('/')
                } else {
                    remainder.substringBeforeLast('/').ifBlank { null }
                }
                GitHubSkillTarget(owner = owner, repo = repo, ref = ref, subDir = subDir)
            }

            else -> null
        }
    }

    private fun encodePathSegment(value: String): String {
        return try {
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        } catch (_: Exception) {
            value
        }
    }

    private fun downloadFromUrl(zipUrl: String, outFile: File): Boolean {
        val url = URL(zipUrl)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doInput = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
        }

        connection.connect()
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            AppLogger.e(TAG, "Download failed, HTTP ${connection.responseCode}")
            return false
        }

        BufferedInputStream(connection.inputStream).use { input ->
            FileOutputStream(outFile).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }

        return true
    }

    private fun getGithubDefaultBranch(owner: String, repoName: String): String? {
        val apiUrl = "https://api.github.com/repos/$owner/$repoName"
        return try {
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JsonParser.parseString(response).asJsonObject
                jsonObject.get("default_branch")?.asString
            } else {
                AppLogger.e(TAG, "GitHub API failed, HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to fetch GitHub default branch", e)
            null
        }
    }
}

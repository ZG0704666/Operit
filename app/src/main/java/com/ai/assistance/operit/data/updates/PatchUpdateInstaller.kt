package com.ai.assistance.operit.data.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.ai.assistance.operit.util.GithubReleaseUtil
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext
import kotlin.math.min

object PatchUpdateInstaller {
    private const val TAG = "PatchUpdateInstaller"

    enum class Stage {
        SELECTING_MIRROR,
        DOWNLOADING_META,
        DOWNLOADING_PATCH,
        APPLYING_PATCH,
        VERIFYING_APK,
        READY_TO_INSTALL
    }

    data class MirrorProbeSummary(
        val ok: Boolean,
        val latencyMs: Long?,
        val speedBytesPerSec: Long?,
        val error: String?
    )

    sealed class ProgressEvent {
        data class StageChanged(val stage: Stage, val message: String) : ProgressEvent()
        data class MirrorProbeStarted(val total: Int) : ProgressEvent()
        data class MirrorProbeResult(
            val name: String,
            val summary: MirrorProbeSummary,
            val completed: Int,
            val total: Int
        ) : ProgressEvent()
        data class MirrorSelected(val name: String) : ProgressEvent()
        data class DownloadProgress(
            val label: String,
            val readBytes: Long,
            val totalBytes: Long?,
            val speedBytesPerSec: Long
        ) : ProgressEvent()
    }

    private const val PROBE_TIMEOUT_MS = 2500

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val WORK_DIR_NAME = "patch_update"

    fun installApk(context: Context, apkFile: File) {
        val apkUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        context.startActivity(intent)
    }

    suspend fun downloadAndPreparePatchUpdateWithProgress(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        onEvent: (ProgressEvent) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onEvent(ProgressEvent.StageChanged(Stage.SELECTING_MIRROR, "正在选择镜像"))
        val mirrorKey = selectFastestMirrorKeyWithProgress(
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            onEvent = onEvent
        )
        onEvent(ProgressEvent.MirrorSelected(mirrorKey))

        val selectedPatchUrl = selectMirrorUrl(patchUrl, mirrorKey)
        val selectedMetaUrl = selectMirrorUrl(metaUrl, mirrorKey)

        downloadAndPreparePatchUpdateInternal(
            context = context,
            patchUrl = selectedPatchUrl,
            metaUrl = selectedMetaUrl,
            onEvent = onEvent
        )
    }

    suspend fun downloadAndPreparePatchUpdateWithProgressUsingMirror(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        mirrorKey: String,
        onEvent: (ProgressEvent) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val selectedPatchUrl = selectMirrorUrl(patchUrl, mirrorKey)
        val selectedMetaUrl = selectMirrorUrl(metaUrl, mirrorKey)

        downloadAndPreparePatchUpdateInternal(
            context = context,
            patchUrl = selectedPatchUrl,
            metaUrl = selectedMetaUrl,
            onEvent = onEvent
        )
    }

    private suspend fun downloadAndPreparePatchUpdateInternal(
        context: Context,
        patchUrl: String,
        metaUrl: String,
        onEvent: (ProgressEvent) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val workDir = prepareCleanWorkDir(context)
        val metaFile = File(workDir, "patch_meta.json")
        val patchFile = File(workDir, "patch.zip")
        val outApk = File(workDir, "rebuilt.apk")

        onEvent(ProgressEvent.StageChanged(Stage.DOWNLOADING_META, "正在下载补丁元信息"))
        downloadToFile(
            url = metaUrl,
            out = metaFile,
            label = "补丁元信息",
            onEvent = onEvent
        )
        val metaJson = JSONObject(metaFile.readText())

        val format = metaJson.optString("format", "")
        if (format != "apkraw-1") {
            throw IllegalStateException("Unsupported patch format: $format")
        }

        val baseApk = File(context.applicationInfo.sourceDir)
        val baseShaExpected = metaJson.optString("baseSha256", "")
        if (baseShaExpected.isNotBlank()) {
            val baseShaActual = sha256Hex(baseApk)
            if (!baseShaActual.equals(baseShaExpected, ignoreCase = true)) {
                throw IllegalStateException("Base sha256 mismatch")
            }
        }

        onEvent(ProgressEvent.StageChanged(Stage.DOWNLOADING_PATCH, "正在下载补丁包"))
        downloadToFile(
            url = patchUrl,
            out = patchFile,
            label = "补丁包",
            onEvent = onEvent
        )

        val patchShaExpected = metaJson.optString("patchSha256", "")
        if (patchShaExpected.isNotBlank()) {
            onEvent(ProgressEvent.StageChanged(Stage.VERIFYING_APK, "正在校验补丁包"))
            val patchShaActual = sha256Hex(patchFile)
            if (!patchShaActual.equals(patchShaExpected, ignoreCase = true)) {
                throw IllegalStateException("Patch sha256 mismatch")
            }
        }

        onEvent(ProgressEvent.StageChanged(Stage.APPLYING_PATCH, "正在合并补丁"))
        applyApkrawPatch(baseApk, patchFile, metaJson, outApk)

        val targetShaExpected = metaJson.optString("targetSha256", "")
        if (targetShaExpected.isNotBlank()) {
            onEvent(ProgressEvent.StageChanged(Stage.VERIFYING_APK, "正在校验新 APK"))
            val targetShaActual = sha256Hex(outApk)
            if (!targetShaActual.equals(targetShaExpected, ignoreCase = true)) {
                throw IllegalStateException("Target sha256 mismatch")
            }
        }

        cleanupPatchWorkDir(workDir, outApk)
        onEvent(ProgressEvent.StageChanged(Stage.READY_TO_INSTALL, "准备安装"))
        outApk
    }

    private fun cleanupPatchWorkDir(workDir: File, keepFile: File) {
        val keep = runCatching { keepFile.canonicalFile }.getOrElse { keepFile.absoluteFile }
        val files = workDir.listFiles() ?: return
        for (f in files) {
            val fc = runCatching { f.canonicalFile }.getOrElse { f.absoluteFile }
            if (fc.path == keep.path) continue
            runCatching {
                if (f.isDirectory) {
                    f.deleteRecursively()
                } else {
                    f.delete()
                }
            }.onFailure { e ->
                AppLogger.w(TAG, "Failed to cleanup patch work file: ${f.absolutePath}", e)
            }
        }
    }


    private fun prepareCleanWorkDir(context: Context): File {
        val workDir = File(context.cacheDir, WORK_DIR_NAME)
        if (workDir.exists()) {
            runCatching { workDir.deleteRecursively() }
        }
        workDir.mkdirs()
        return workDir
    }

    private fun selectMirrorUrl(originalUrl: String, mirrorKey: String): String {
        if (mirrorKey == "GitHub") return originalUrl
        val mirrors = GithubReleaseUtil.getMirroredUrls(originalUrl)
        return mirrors[mirrorKey] ?: originalUrl
    }

    private suspend fun selectFastestMirrorKeyWithProgress(
        patchUrl: String,
        metaUrl: String,
        onEvent: (ProgressEvent) -> Unit
    ): String {
        val patchMirrors = GithubReleaseUtil.getMirroredUrls(patchUrl)
        val metaMirrors = GithubReleaseUtil.getMirroredUrls(metaUrl)

        val keys = LinkedHashSet<String>().apply {
            addAll(patchMirrors.keys)
            addAll(metaMirrors.keys)
            add("GitHub")
        }

        onEvent(ProgressEvent.MirrorProbeStarted(keys.size))

        var completed = 0
        var bestKey: String? = null
        var bestSpeed = Long.MIN_VALUE
        var bestLatency = Long.MAX_VALUE

        for (key in keys) {
            coroutineContext.ensureActive()

            val patchProbeUrl = if (key == "GitHub") patchUrl else patchMirrors[key] ?: patchUrl
            val metaProbeUrl = if (key == "GitHub") metaUrl else metaMirrors[key] ?: metaUrl

            val patchRes =
                GithubReleaseUtil.probeMirrorUrls(
                    mapOf(key to patchProbeUrl),
                    timeoutMs = PROBE_TIMEOUT_MS
                )[key]
            val metaRes =
                GithubReleaseUtil.probeMirrorUrls(
                    mapOf(key to metaProbeUrl),
                    timeoutMs = PROBE_TIMEOUT_MS
                )[key]

            val ok = patchRes?.ok == true && metaRes?.ok == true
            val speed =
                if (patchRes?.bytesPerSec != null && metaRes?.bytesPerSec != null) {
                    min(patchRes.bytesPerSec!!, metaRes.bytesPerSec!!)
                } else {
                    null
                }
            val latency =
                when {
                    patchRes?.latencyMs != null && metaRes?.latencyMs != null ->
                        maxOf(patchRes.latencyMs, metaRes.latencyMs)
                    patchRes?.latencyMs != null -> patchRes.latencyMs
                    metaRes?.latencyMs != null -> metaRes.latencyMs
                    else -> null
                }
            val error =
                when {
                    patchRes?.ok != true -> patchRes?.error ?: "PATCH_PROBE_FAILED"
                    metaRes?.ok != true -> metaRes?.error ?: "META_PROBE_FAILED"
                    else -> null
                }

            completed += 1
            val summary = MirrorProbeSummary(ok = ok, latencyMs = latency, speedBytesPerSec = speed, error = error)
            onEvent(ProgressEvent.MirrorProbeResult(name = key, summary = summary, completed = completed, total = keys.size))

            if (ok) {
                val speedValue = speed ?: -1L
                val latencyValue = latency ?: Long.MAX_VALUE
                if (speed != null) {
                    if (speedValue > bestSpeed || (speedValue == bestSpeed && latencyValue < bestLatency)) {
                        bestKey = key
                        bestSpeed = speedValue
                        bestLatency = latencyValue
                    }
                } else if (bestKey == null) {
                    bestKey = key
                    bestLatency = latencyValue
                }
            }
        }

        return bestKey ?: "GitHub"
    }

    private suspend fun downloadToFile(
        url: String,
        out: File,
        label: String,
        onEvent: (ProgressEvent) -> Unit
    ) {
        val req = Request.Builder().url(url).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}: ${resp.message}")
            }
            val body = resp.body ?: throw IllegalStateException("Empty response body")
            val total = body.contentLength().takeIf { it > 0 }
            out.parentFile?.mkdirs()
            if (out.exists()) {
                runCatching { out.delete() }
            }

            val buf = ByteArray(128 * 1024)
            var readTotal = 0L
            var lastNotifyAt = 0L
            var lastSpeedSampleAt = 0L
            var lastSpeedSampleBytes = 0L
            var speedBytesPerSec = 0L

            onEvent(
                ProgressEvent.DownloadProgress(
                    label = label,
                    readBytes = 0L,
                    totalBytes = total,
                    speedBytesPerSec = speedBytesPerSec
                )
            )

            FileOutputStream(out).use { fos ->
                body.byteStream().use { ins ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = ins.read(buf)
                        if (n <= 0) break
                        fos.write(buf, 0, n)
                        readTotal += n.toLong()

                        val now = System.currentTimeMillis()
                        if (lastSpeedSampleAt == 0L) {
                            lastSpeedSampleAt = now
                            lastSpeedSampleBytes = readTotal
                        } else {
                            val elapsed = now - lastSpeedSampleAt
                            if (elapsed >= 300) {
                                val delta = readTotal - lastSpeedSampleBytes
                                if (delta >= 0) {
                                    speedBytesPerSec = (delta * 1000L) / elapsed.coerceAtLeast(1L)
                                }
                                lastSpeedSampleAt = now
                                lastSpeedSampleBytes = readTotal
                            }
                        }

                        if (now - lastNotifyAt >= 300) {
                            lastNotifyAt = now
                            onEvent(
                                ProgressEvent.DownloadProgress(
                                    label = label,
                                    readBytes = readTotal,
                                    totalBytes = total,
                                    speedBytesPerSec = speedBytesPerSec
                                )
                            )
                        }
                    }
                }
            }

            onEvent(
                ProgressEvent.DownloadProgress(
                    label = label,
                    readBytes = readTotal,
                    totalBytes = total,
                    speedBytesPerSec = speedBytesPerSec
                )
            )
        }
    }

    private fun applyApkrawPatch(baseApk: File, patchZip: File, meta: JSONObject, outApk: File) {
        val entriesJson = meta.optJSONArray("apkRawEntries") ?: JSONArray()
        val tailName = meta.optString("apkRawTailFile", "tail.bin")

        val baseMap = RandomAccessFile(baseApk, "r").use { raf ->
            readCentralDirectory(raf)
        }

        if (outApk.exists()) outApk.delete()
        outApk.parentFile?.mkdirs()

        val md = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(BufferedOutputStream(FileOutputStream(outApk)), md).use { dout ->
            RandomAccessFile(baseApk, "r").use { raf ->
                ZipFile(patchZip).use { pz ->
                    for (i in 0 until entriesJson.length()) {
                        val ent = entriesJson.getJSONObject(i)
                        val name = ent.optString("name", "")
                        val mode = ent.optString("mode", "")
                        if (name.isBlank()) throw IllegalStateException("Bad apkRawEntries")

                        if (mode == "copy") {
                            val cd = baseMap[name] ?: throw IllegalStateException("Base apk missing entry: $name")
                            copyLocalRecord(raf, cd, dout)
                        } else if (mode == "add") {
                            val recordPath = ent.optString("recordPath", "")
                            if (recordPath.isBlank()) throw IllegalStateException("apkraw add missing recordPath")
                            val ze = pz.getEntry(recordPath) ?: throw IllegalStateException("patch zip missing $recordPath")
                            pz.getInputStream(ze).use { ins ->
                                ins.copyTo(dout)
                            }
                        } else {
                            throw IllegalStateException("Bad apkraw mode: $mode")
                        }
                    }

                    val tailEntry = pz.getEntry(tailName) ?: throw IllegalStateException("patch zip missing $tailName")
                    pz.getInputStream(tailEntry).use { ins ->
                        ins.copyTo(dout)
                    }
                }
            }
            dout.flush()
        }
    }

    private data class CdEntry(
        val localHeaderOffset: Long,
        val compressedSize: Long
    )

    private fun readCentralDirectory(raf: RandomAccessFile): Map<String, CdEntry> {
        val fileLen = raf.length()
        val readLen = min(65557L, fileLen).toInt()
        val buf = ByteArray(readLen)
        raf.seek(fileLen - readLen)
        raf.readFully(buf)

        var eocdIndex = -1
        for (i in readLen - 22 downTo 0) {
            if (buf[i] == 0x50.toByte() &&
                buf[i + 1] == 0x4b.toByte() &&
                buf[i + 2] == 0x05.toByte() &&
                buf[i + 3] == 0x06.toByte()
            ) {
                eocdIndex = i
                break
            }
        }
        if (eocdIndex < 0) throw IllegalStateException("EOCD not found")

        val eocdOffset = (fileLen - readLen + eocdIndex).toLong()
        raf.seek(eocdOffset)
        val eocd = ByteArray(22)
        raf.readFully(eocd)

        val totalEntries = readUShortLE(eocd, 10)
        val cdOffset = readUIntLE(eocd, 16)

        raf.seek(cdOffset)
        val out = LinkedHashMap<String, CdEntry>(totalEntries)
        repeat(totalEntries) {
            val hdr = ByteArray(46)
            raf.readFully(hdr)
            val sig = readUIntLE(hdr, 0)
            if (sig != 0x02014b50L) throw IllegalStateException("Bad central directory signature")

            val flags = readUShortLE(hdr, 8)
            val compressedSize = readUIntLE(hdr, 20)
            val fileNameLen = readUShortLE(hdr, 28)
            val extraLen = readUShortLE(hdr, 30)
            val commentLen = readUShortLE(hdr, 32)
            val localHeaderOffset = readUIntLE(hdr, 42)

            val nameBytes = ByteArray(fileNameLen)
            raf.readFully(nameBytes)
            val charset = if ((flags and 0x800) != 0) Charsets.UTF_8 else Charsets.ISO_8859_1
            val name = String(nameBytes, charset)

            if (extraLen > 0) raf.skipBytes(extraLen)
            if (commentLen > 0) raf.skipBytes(commentLen)

            if (!name.endsWith("/")) {
                out[name] = CdEntry(localHeaderOffset = localHeaderOffset, compressedSize = compressedSize)
            }
        }

        return out
    }

    private fun copyLocalRecord(raf: RandomAccessFile, cd: CdEntry, out: java.io.OutputStream) {
        raf.seek(cd.localHeaderOffset)

        val lfh = ByteArray(30)
        raf.readFully(lfh)
        val sig = readUIntLE(lfh, 0)
        if (sig != 0x04034b50L) throw IllegalStateException("Bad local header signature")

        val flags = readUShortLE(lfh, 6)
        val fileNameLen = readUShortLE(lfh, 26)
        val extraLen = readUShortLE(lfh, 28)

        out.write(lfh)

        if (fileNameLen + extraLen > 0) {
            val nameExtra = ByteArray(fileNameLen + extraLen)
            raf.readFully(nameExtra)
            out.write(nameExtra)
        }

        copyBytes(raf, out, cd.compressedSize)

        if ((flags and 0x08) != 0) {
            val first4 = ByteArray(4)
            raf.readFully(first4)
            out.write(first4)
            val ddSig = readUIntLE(first4, 0)
            if (ddSig == 0x08074b50L) {
                val rest = ByteArray(12)
                raf.readFully(rest)
                out.write(rest)
            } else {
                val rest = ByteArray(8)
                raf.readFully(rest)
                out.write(rest)
            }
        }
    }

    private fun copyBytes(raf: RandomAccessFile, out: java.io.OutputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(1024 * 1024)
        while (remaining > 0) {
            val toRead = min(remaining, buffer.size.toLong()).toInt()
            val read = raf.read(buffer, 0, toRead)
            if (read <= 0) throw IllegalStateException("Unexpected EOF")
            out.write(buffer, 0, read)
            remaining -= read.toLong()
        }
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val r = ins.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    private fun readUShortLE(buf: ByteArray, off: Int): Int {
        return (buf[off].toInt() and 0xff) or ((buf[off + 1].toInt() and 0xff) shl 8)
    }

    private fun readUIntLE(buf: ByteArray, off: Int): Long {
        return (buf[off].toLong() and 0xff) or
            ((buf[off + 1].toLong() and 0xff) shl 8) or
            ((buf[off + 2].toLong() and 0xff) shl 16) or
            ((buf[off + 3].toLong() and 0xff) shl 24)
    }
}

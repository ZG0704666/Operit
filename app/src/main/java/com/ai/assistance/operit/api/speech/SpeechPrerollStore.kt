package com.ai.assistance.operit.api.speech

import com.ai.assistance.operit.util.AppLogger

object SpeechPrerollStore {
    private const val TAG = "SpeechPrerollStore"

    private const val SAMPLE_RATE = 16000
    private const val CAPACITY_MS = 2500

    private val lock = Any()

    private val capacitySamples: Int = (SAMPLE_RATE * CAPACITY_MS) / 1000
    private val ring: ShortArray = ShortArray(capacitySamples)

    private var writePos: Int = 0
    private var filled: Int = 0

    private var pending: ShortArray? = null
    private var pendingCapturedAtMs: Long = 0L
    private var pendingArmed: Boolean = false

    fun appendPcm(pcm: ShortArray, length: Int) {
        if (length <= 0) return
        val n = minOf(length, pcm.size)
        synchronized(lock) {
            var idx = 0
            while (idx < n) {
                val toCopy = minOf(n - idx, capacitySamples - writePos)
                java.lang.System.arraycopy(pcm, idx, ring, writePos, toCopy)
                writePos += toCopy
                if (writePos >= capacitySamples) writePos = 0
                filled = minOf(capacitySamples, filled + toCopy)
                idx += toCopy
            }
        }
    }

    fun capturePending(windowMs: Int = 1600) {
        val now = System.currentTimeMillis()
        val requested = ((SAMPLE_RATE * windowMs) / 1000).coerceAtLeast(0)
        val snapshot: ShortArray?
        synchronized(lock) {
            val available = filled
            if (available <= 0 || requested <= 0) {
                snapshot = null
            } else {
                val take = minOf(available, requested)
                val out = ShortArray(take)
                val start = ((writePos - take) % capacitySamples + capacitySamples) % capacitySamples
                val firstLen = minOf(take, capacitySamples - start)
                java.lang.System.arraycopy(ring, start, out, 0, firstLen)
                val remain = take - firstLen
                if (remain > 0) {
                    java.lang.System.arraycopy(ring, 0, out, firstLen, remain)
                }
                snapshot = out
            }

            pending = snapshot
            pendingCapturedAtMs = now
            pendingArmed = false
        }

        if (snapshot != null) {
            AppLogger.d(TAG, "Captured pending preroll: samples=${snapshot.size}, ms=${snapshot.size * 1000 / SAMPLE_RATE}")
        } else {
            AppLogger.d(TAG, "Captured pending preroll: empty")
        }
    }

    fun consumePending(maxAgeMs: Long = 10_000L): ShortArray? {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (!pendingArmed) return null
            val p = pending ?: return null
            if (now - pendingCapturedAtMs > maxAgeMs) {
                pending = null
                pendingArmed = false
                return null
            }
            pending = null
            pendingArmed = false
            return p
        }
    }

    fun armPending() {
        synchronized(lock) {
            pendingArmed = pending != null
        }
    }

    fun clear() {
        synchronized(lock) {
            writePos = 0
            filled = 0
            pending = null
            pendingCapturedAtMs = 0L
            pendingArmed = false
        }
    }
}

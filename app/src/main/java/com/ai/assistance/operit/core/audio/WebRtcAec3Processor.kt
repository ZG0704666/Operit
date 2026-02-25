package com.ai.assistance.operit.core.audio

class WebRtcAec3Processor(
    sampleRate: Int = 16000,
    channels: Int = 1,
) : AutoCloseable {

    private val lock = Any()
    private var nativeHandle: Long = nativeCreate(sampleRate, channels)
    private var farBuffer = ShortArray(2048)

    fun processCaptureInPlace(capturePcm: ShortArray, length: Int) {
        if (length <= 0) return

        synchronized(lock) {
            if (nativeHandle == 0L) return

            ensureFarBufferCapacity(length)
            val farRead = AecReferenceAudioBus.pop(farBuffer, length)
            if (farRead > 0) {
                nativeAnalyzeRender(nativeHandle, farBuffer, farRead)
            }

            nativeProcessCapture(nativeHandle, capturePcm, length)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (nativeHandle != 0L) {
                nativeRelease(nativeHandle)
                nativeHandle = 0L
            }
        }
    }

    private fun ensureFarBufferCapacity(length: Int) {
        if (farBuffer.size < length) {
            farBuffer = ShortArray(length)
        }
    }

    private external fun nativeCreate(sampleRate: Int, channels: Int): Long

    private external fun nativeRelease(handle: Long)

    private external fun nativeAnalyzeRender(handle: Long, renderPcm: ShortArray, length: Int)

    private external fun nativeProcessCapture(handle: Long, capturePcm: ShortArray, length: Int)

    companion object {
        init {
            System.loadLibrary("audioaec")
        }
    }
}

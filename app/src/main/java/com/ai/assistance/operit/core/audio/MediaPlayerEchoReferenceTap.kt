package com.ai.assistance.operit.core.audio

import android.media.audiofx.Visualizer
import com.ai.assistance.operit.util.AppLogger

private const val TAG = "MediaPlayerEchoRefTap"

class MediaPlayerEchoReferenceTap {
    private var visualizer: Visualizer? = null

    fun attachToSession(audioSessionId: Int) {
        release()
        if (audioSessionId <= 0) return

        try {
            val captureSizeRange = Visualizer.getCaptureSizeRange()
            val captureSize = captureSizeRange[1].coerceAtMost(2048)
            val captureRate = (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(1000)

            visualizer = Visualizer(audioSessionId).apply {
                this.captureSize = captureSize
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {
                            val wave = waveform ?: return
                            if (wave.isEmpty()) return

                            val pcm = ShortArray(wave.size)
                            for (i in wave.indices) {
                                pcm[i] = ((wave[i].toInt() - 128) shl 8).toShort()
                            }

                            val sampleRateHz =
                                if (samplingRate > 0) {
                                    (samplingRate / 1000).coerceAtLeast(1)
                                } else {
                                    16000
                                }

                            AecReferenceAudioBus.offerPcm16(pcm, pcm.size, sampleRateHz)
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                        }
                    },
                    captureRate,
                    true,
                    false,
                )
                enabled = true
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to tap MediaPlayer session=$audioSessionId", e)
            release()
        }
    }

    fun release() {
        val v = visualizer
        visualizer = null

        if (v != null) {
            try {
                v.enabled = false
            } catch (_: Exception) {
            }
            try {
                v.release()
            } catch (_: Exception) {
            }
        }
    }
}

package com.ai.assistance.operit.core.audio

object AecReferenceAudioBus {
    private const val TARGET_SAMPLE_RATE = 16000
    private const val BUFFER_SECONDS = 8
    private const val BUFFER_CAPACITY = TARGET_SAMPLE_RATE * BUFFER_SECONDS

    private val lock = Any()
    private val ringBuffer = ShortArray(BUFFER_CAPACITY)
    private var head = 0
    private var size = 0

    fun clear() {
        synchronized(lock) {
            head = 0
            size = 0
        }
    }

    fun offerPcm16(samples: ShortArray, length: Int, sourceSampleRate: Int) {
        if (length <= 0) return

        val safeLength = length.coerceAtMost(samples.size)
        val normalized =
            if (sourceSampleRate == TARGET_SAMPLE_RATE || sourceSampleRate <= 0) {
                samples.copyOf(safeLength)
            } else {
                resampleToTarget(samples, safeLength, sourceSampleRate)
            }

        pushInternal(normalized, normalized.size)
    }

    fun pop(out: ShortArray, maxSamples: Int): Int {
        if (maxSamples <= 0 || out.isEmpty()) return 0
        val target = maxSamples.coerceAtMost(out.size)

        synchronized(lock) {
            val read = minOf(target, size)
            if (read <= 0) return 0

            for (i in 0 until read) {
                val idx = (head + i) % BUFFER_CAPACITY
                out[i] = ringBuffer[idx]
            }

            head = (head + read) % BUFFER_CAPACITY
            size -= read
            return read
        }
    }

    private fun pushInternal(samples: ShortArray, length: Int) {
        if (length <= 0) return

        synchronized(lock) {
            for (i in 0 until length) {
                if (size == BUFFER_CAPACITY) {
                    head = (head + 1) % BUFFER_CAPACITY
                    size--
                }

                val tail = (head + size) % BUFFER_CAPACITY
                ringBuffer[tail] = samples[i]
                size++
            }
        }
    }

    private fun resampleToTarget(input: ShortArray, length: Int, sourceRate: Int): ShortArray {
        if (length <= 0) return ShortArray(0)

        val outputLength =
            ((length.toLong() * TARGET_SAMPLE_RATE) / sourceRate.toLong())
                .toInt()
                .coerceAtLeast(1)

        val output = ShortArray(outputLength)
        for (i in 0 until outputLength) {
            val srcIndex =
                ((i.toLong() * sourceRate.toLong()) / TARGET_SAMPLE_RATE.toLong())
                    .toInt()
                    .coerceIn(0, length - 1)
            output[i] = input[srcIndex]
        }

        return output
    }
}

package com.ai.assistance.mmd

object MmdNative {

    init {
        MmdLibraryLoader.loadLibraries()
    }

    @JvmStatic external fun nativeIsAvailable(): Boolean

    @JvmStatic external fun nativeGetUnavailableReason(): String

    @JvmStatic external fun nativeGetLastError(): String

    @JvmStatic external fun nativeReadModelName(pathModel: String): String?

    @JvmStatic external fun nativeReadModelSummary(pathModel: String): LongArray?

    @JvmStatic external fun nativeReadMotionModelName(pathMotion: String): String?

    @JvmStatic external fun nativeReadMotionSummary(pathMotion: String): LongArray?
}

package com.ai.assistance.operit.util

import android.os.Environment
import java.io.File

object OperitPaths {

    private const val OPERIT_DIR_NAME = "Operit"
    private const val CLEAN_ON_EXIT_DIR_NAME = "cleanOnExit"
    private const val MCP_PLUGINS_DIR_NAME = "mcp_plugins"
    private const val BRIDGE_DIR_NAME = "bridge"
    private const val EXPORTS_DIR_NAME = "exports"
    private const val WORKSPACE_DIR_NAME = "workspace"
    private const val TEST_DIR_NAME = "test"

    fun downloadsDir(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    fun operitRootDir(): File {
        return ensureDir(File(downloadsDir(), OPERIT_DIR_NAME))
    }

    fun cleanOnExitDir(): File {
        return ensureDir(File(operitRootDir(), CLEAN_ON_EXIT_DIR_NAME))
    }

    fun mcpPluginsDir(): File {
        return ensureDir(File(operitRootDir(), MCP_PLUGINS_DIR_NAME))
    }

    fun bridgeDir(): File {
        return ensureDir(File(operitRootDir(), BRIDGE_DIR_NAME))
    }

    fun exportsDir(): File {
        return ensureDir(File(operitRootDir(), EXPORTS_DIR_NAME))
    }

    fun workspaceDir(): File {
        return ensureDir(File(operitRootDir(), WORKSPACE_DIR_NAME))
    }

    fun testDir(): File {
        return ensureDir(File(operitRootDir(), TEST_DIR_NAME))
    }

    fun operitRootPathSdcard(): String {
        return "/sdcard/Download/$OPERIT_DIR_NAME"
    }

    fun cleanOnExitPathSdcard(): String {
        return "${operitRootPathSdcard()}/$CLEAN_ON_EXIT_DIR_NAME"
    }

    fun bridgePathSdcard(): String {
        return "${operitRootPathSdcard()}/$BRIDGE_DIR_NAME"
    }

    fun exportsPathSdcard(): String {
        return "${operitRootPathSdcard()}/$EXPORTS_DIR_NAME"
    }

    fun workspacePathSdcard(chatId: String): String {
        return "${operitRootPathSdcard()}/$WORKSPACE_DIR_NAME/$chatId"
    }

    fun testPathSdcard(): String {
        return "${operitRootPathSdcard()}/$TEST_DIR_NAME"
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
}

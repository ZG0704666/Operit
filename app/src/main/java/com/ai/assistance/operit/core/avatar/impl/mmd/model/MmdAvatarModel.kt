package com.ai.assistance.operit.core.avatar.impl.mmd.model

import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarType
import java.io.File

data class MmdAvatarModel(
    override val id: String,
    override val name: String,
    val basePath: String,
    val modelFile: String,
    val motionFile: String? = null
) : AvatarModel {

    override val type: AvatarType
        get() = AvatarType.MMD

    val modelPath: String
        get() = File(basePath, modelFile).absolutePath

    val motionPath: String?
        get() = motionFile?.takeIf { it.isNotBlank() }?.let { File(basePath, it).absolutePath }

    val displayMotionName: String?
        get() = motionFile?.takeIf { it.isNotBlank() }
}

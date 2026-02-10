package com.ai.assistance.operit.core.avatar.impl.mmd.control

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.state.AvatarState
import com.ai.assistance.operit.core.avatar.impl.mmd.model.MmdAvatarModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MmdAvatarController(
    private val model: MmdAvatarModel
) : AvatarController {

    private val _state = MutableStateFlow(AvatarState())
    override val state: StateFlow<AvatarState> = _state.asStateFlow()

    private val _scale = MutableStateFlow(1.0f)
    val scale: StateFlow<Float> = _scale.asStateFlow()

    private val _translateX = MutableStateFlow(0.0f)
    val translateX: StateFlow<Float> = _translateX.asStateFlow()

    private val _translateY = MutableStateFlow(0.0f)
    val translateY: StateFlow<Float> = _translateY.asStateFlow()

    override val availableAnimations: List<String>
        get() = model.displayMotionName?.let { listOf(it) } ?: emptyList()

    override fun setEmotion(newEmotion: AvatarEmotion) {
        _state.value = _state.value.copy(emotion = newEmotion)
    }

    override fun playAnimation(animationName: String, loop: Int) {
        if (!availableAnimations.contains(animationName)) {
            return
        }

        _state.value = _state.value.copy(
            currentAnimation = animationName,
            isLooping = loop == 0
        )
    }

    override fun lookAt(x: Float, y: Float) {
    }

    override fun updateSettings(settings: Map<String, Any>) {
        settings["scale"]?.let { if (it is Number) _scale.value = it.toFloat() }
        settings["translateX"]?.let { if (it is Number) _translateX.value = it.toFloat() }
        settings["translateY"]?.let { if (it is Number) _translateY.value = it.toFloat() }
    }
}

@Composable
fun rememberMmdAvatarController(model: MmdAvatarModel): MmdAvatarController {
    return remember(model) { MmdAvatarController(model) }
}

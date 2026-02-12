package com.ai.assistance.operit.core.avatar.impl.gltf.view

import android.content.Context
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.view.Choreographer
import android.view.SurfaceView
import com.ai.assistance.operit.util.AppLogger
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.Manipulator
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class GltfSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), Choreographer.FrameCallback {

    companion object {
        private const val TAG = "GltfSurfaceView"
        private const val DEFAULT_CAMERA_PITCH = 8.0f
        private const val DEFAULT_CAMERA_YAW = 0.0f
        private const val DEFAULT_CAMERA_DISTANCE_SCALE = 0.5f
        private const val MIN_CAMERA_DISTANCE_SCALE = 0.0f
        private const val MAX_CAMERA_DISTANCE_SCALE = 10.0f
        private const val MIN_CAMERA_TARGET_HEIGHT = -2.0f
        private const val MAX_CAMERA_TARGET_HEIGHT = 2.0f
        private const val BASE_CAMERA_DISTANCE = 0.5
        private const val CAMERA_DISTANCE_EPSILON = 1e-6
        private const val CAMERA_TARGET_X = 0.0
        private const val CAMERA_TARGET_Y = 0.0
        private const val CAMERA_TARGET_Z = 0.0
        private const val MODEL_ORBIT_PIVOT_Y = 0.0f

        init {
            Utils.init()
        }
    }

    private val choreographer = Choreographer.getInstance()

    private val modelViewer = ModelViewer(this)

    private var currentModelPath: String? = null
    private var requestedAnimationName: String? = null
    private var requestedLooping: Boolean = false

    private var cameraPitchDegrees: Float = DEFAULT_CAMERA_PITCH
    private var cameraYawDegrees: Float = DEFAULT_CAMERA_YAW
    private var cameraDistanceScale: Float = DEFAULT_CAMERA_DISTANCE_SCALE
    private var cameraTargetHeightOffset: Float = 0.0f
    private var cameraTargetX: Double = CAMERA_TARGET_X
    private var cameraTargetY: Double = CAMERA_TARGET_Y
    private var cameraTargetZ: Double = CAMERA_TARGET_Z

    private var activeAnimationIndex: Int = -1
    private var animationStartNanos: Long = System.nanoTime()

    private var sunLightEntity: Int = 0
    private var fillLightEntity: Int = 0
    private var rimLightEntity: Int = 0
    private var indirectLight: IndirectLight? = null

    private var baseRootTransform: FloatArray? = null
    private var baseEntityTransforms: Map<Int, FloatArray> = emptyMap()

    private var hasLoggedManipulatorUnavailable: Boolean = false
    private var hasLoggedManipulatorApplyFailure: Boolean = false

    private val cameraManipulatorField by lazy {
        runCatching {
            ModelViewer::class.java.declaredFields
                .firstOrNull { field -> field.type == Manipulator::class.java }
                ?.apply { isAccessible = true }
        }.getOrNull()
    }

    private val uiHelperField by lazy {
        runCatching {
            ModelViewer::class.java.declaredFields
                .firstOrNull { field -> field.type == UiHelper::class.java }
                ?.apply { isAccessible = true }
        }.getOrNull()
    }

    private var animationNames: List<String> = emptyList()
    private var animationNameToIndex: Map<String, Int> = emptyMap()
    private var animationDurations: Map<Int, Float> = emptyMap()

    @Volatile
    private var isRendering: Boolean = false

    @Volatile
    private var onRenderErrorListener: ((String) -> Unit)? = null

    @Volatile
    private var onAnimationsDiscoveredListener: ((List<String>) -> Unit)? = null

    init {
        setupTransparentSurface()
        setupCameraDefaults()
        setupSceneLighting()
    }

    fun setOnRenderErrorListener(listener: ((String) -> Unit)?) {
        onRenderErrorListener = listener
    }

    fun setOnAnimationsDiscoveredListener(listener: ((List<String>) -> Unit)?) {
        onAnimationsDiscoveredListener = listener
    }

    fun setModelPath(path: String) {
        val normalizedPath = path.trim()
        if (normalizedPath.isEmpty() || normalizedPath == currentModelPath) {
            return
        }

        loadModel(normalizedPath)
    }

    fun setAnimationState(animationName: String?, isLooping: Boolean) {
        val normalizedName = animationName?.trim()?.takeIf { it.isNotEmpty() }
        if (requestedAnimationName == normalizedName && requestedLooping == isLooping) {
            return
        }

        requestedAnimationName = normalizedName
        requestedLooping = isLooping
        restartAnimationClock()
        applyRequestedAnimationSelection()
    }

    fun setCameraPose(
        pitchDegrees: Float,
        yawDegrees: Float,
        distanceScale: Float,
        targetHeightOffset: Float
    ) {
        val clampedPitch = pitchDegrees.coerceIn(-89f, 89f)
        val normalizedYaw = yawDegrees.coerceIn(-180f, 180f)
        val clampedDistanceScale = distanceScale.coerceIn(MIN_CAMERA_DISTANCE_SCALE, MAX_CAMERA_DISTANCE_SCALE)
        val clampedTargetHeight = targetHeightOffset.coerceIn(MIN_CAMERA_TARGET_HEIGHT, MAX_CAMERA_TARGET_HEIGHT)

        val unchanged =
            cameraPitchDegrees == clampedPitch &&
                cameraYawDegrees == normalizedYaw &&
                cameraDistanceScale == clampedDistanceScale &&
                cameraTargetHeightOffset == clampedTargetHeight
        if (unchanged) {
            return
        }

        cameraPitchDegrees = clampedPitch
        cameraYawDegrees = normalizedYaw
        cameraDistanceScale = clampedDistanceScale
        cameraTargetHeightOffset = clampedTargetHeight
        applyCameraPose()
    }

    fun onResume() {
        if (isRendering) {
            return
        }
        isRendering = true
        restartAnimationClock()
        choreographer.postFrameCallback(this)
    }

    fun onPause() {
        if (!isRendering) {
            return
        }
        isRendering = false
        choreographer.removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRendering) {
            return
        }

        choreographer.postFrameCallback(this)
        renderFrame(frameTimeNanos)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyCameraPose()
    }

    override fun onDetachedFromWindow() {
        onPause()
        super.onDetachedFromWindow()
    }

    private fun renderFrame(frameTimeNanos: Long) {
        try {
            // Re-apply camera each frame because ModelViewer.render() internally updates camera from manipulator.
            applyCameraPose()
            applyAnimation(frameTimeNanos)
            modelViewer.render(frameTimeNanos)
        } catch (e: Exception) {
            dispatchError("Failed to render glTF frame: ${e.message ?: "unknown error"}")
        }
    }

    private fun applyAnimation(frameTimeNanos: Long) {
        val animator = modelViewer.animator ?: return
        val animationCount = animator.animationCount
        if (animationCount <= 0) {
            return
        }

        val animationIndex = activeAnimationIndex
        if (animationIndex !in 0 until animationCount) {
            return
        }

        val elapsedSeconds = max((frameTimeNanos - animationStartNanos).toDouble() / 1_000_000_000.0, 0.0).toFloat()
        val durationSeconds = animationDurations[animationIndex]
            ?: runCatching { animator.getAnimationDuration(animationIndex) }.getOrNull()

        val sampleTime = when {
            durationSeconds != null && durationSeconds > 0f && requestedLooping -> elapsedSeconds % durationSeconds
            durationSeconds != null && durationSeconds > 0f -> min(elapsedSeconds, durationSeconds)
            else -> elapsedSeconds
        }

        animator.applyAnimation(animationIndex, sampleTime)
        animator.updateBoneMatrices()
    }

    private fun loadModel(modelPath: String) {
        val modelFile = File(modelPath)
        if (!modelFile.exists() || !modelFile.isFile) {
            dispatchError("glTF model file not found: $modelPath")
            return
        }

        val extension = modelFile.extension.lowercase()
        if (extension != "glb" && extension != "gltf") {
            dispatchError("Unsupported glTF model extension: .$extension (expected .glb or .gltf)")
            return
        }

        try {
            val modelBuffer = readFileToDirectByteBuffer(modelFile)
            if (extension == "glb") {
                modelViewer.loadModelGlb(modelBuffer)
            } else {
                val baseDirectory = modelFile.parentFile
                modelViewer.loadModelGltf(modelBuffer) { relativeUri ->
                    val resourceFile = resolveResourceFile(baseDirectory, relativeUri)
                    readFileToDirectByteBuffer(resourceFile)
                }
            }

            currentModelPath = modelPath
            runCatching { modelViewer.transformToUnitCube() }
            captureBaseRootTransform()
            applyCameraPose()
            refreshDiscoveredAnimations()
            restartAnimationClock()
            applyRequestedAnimationSelection()

            AppLogger.i(TAG, "Loaded glTF model: $modelPath")
        } catch (e: Exception) {
            currentModelPath = null
            activeAnimationIndex = -1
            baseRootTransform = null
            baseEntityTransforms = emptyMap()
            animationNames = emptyList()
            animationNameToIndex = emptyMap()
            animationDurations = emptyMap()
            cameraTargetX = CAMERA_TARGET_X
            cameraTargetY = CAMERA_TARGET_Y
            cameraTargetZ = CAMERA_TARGET_Z
            onAnimationsDiscoveredListener?.invoke(emptyList())
            dispatchError("Failed to load glTF model: ${e.message ?: "unknown error"}")
        }
    }

    private fun refreshDiscoveredAnimations() {
        val animator = modelViewer.animator
        val animationCount = animator?.animationCount ?: 0
        if (animator == null || animationCount <= 0) {
            animationNames = emptyList()
            animationNameToIndex = emptyMap()
            animationDurations = emptyMap()
            onAnimationsDiscoveredListener?.invoke(emptyList())
            return
        }

        val names = ArrayList<String>(animationCount)
        val nameIndexMap = LinkedHashMap<String, Int>(animationCount)
        val durationMap = LinkedHashMap<Int, Float>(animationCount)

        for (index in 0 until animationCount) {
            val rawName = runCatching { animator.getAnimationName(index) }.getOrNull().orEmpty().trim()
            val safeName = if (rawName.isBlank()) "Animation $index" else rawName
            names.add(safeName)
            nameIndexMap[safeName] = index

            val duration = runCatching { animator.getAnimationDuration(index) }.getOrNull()
            if (duration != null && duration > 0f) {
                durationMap[index] = duration
            }
        }

        animationNames = names
        animationNameToIndex = nameIndexMap
        animationDurations = durationMap
        onAnimationsDiscoveredListener?.invoke(names)
    }

    private fun applyRequestedAnimationSelection() {
        val animator = modelViewer.animator
        val animationCount = animator?.animationCount ?: 0
        if (animator == null || animationCount <= 0) {
            activeAnimationIndex = -1
            return
        }

        activeAnimationIndex = resolveAnimationIndex(requestedAnimationName, animationCount)
    }

    private fun resolveAnimationIndex(animationName: String?, animationCount: Int): Int {
        if (animationCount <= 0) {
            return -1
        }

        if (animationName.isNullOrBlank()) {
            return -1
        }

        animationNameToIndex[animationName]?.let { index ->
            return index
        }

        val parsedIndex = animationName.toIntOrNull()
        if (parsedIndex != null && parsedIndex in 0 until animationCount) {
            return parsedIndex
        }

        val normalized = animationName.lowercase()
        for (index in animationNames.indices) {
            if (animationNames[index].lowercase() == normalized) {
                return index
            }
        }

        return -1
    }

    private fun restartAnimationClock() {
        animationStartNanos = System.nanoTime()
    }

    private fun setupTransparentSurface() {
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)

        val clearOptions = Renderer.ClearOptions().apply {
            clear = true
            discard = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
        modelViewer.renderer.clearOptions = clearOptions
        modelViewer.view.blendMode = View.BlendMode.TRANSLUCENT
        modelViewer.view.setPostProcessingEnabled(false)

        val uiHelper = runCatching { uiHelperField?.get(modelViewer) as? UiHelper }.getOrNull()
        uiHelper?.setOpaque(false)
    }

    private fun setupCameraDefaults() {
        modelViewer.cameraNear = 0.0005f
        modelViewer.cameraFar = 500.0f
    }

    private fun setupSceneLighting() {
        if (sunLightEntity != 0) {
            return
        }

        val engine = modelViewer.engine
        sunLightEntity = createDirectionalLight(
            engine = engine,
            colorR = 1.0f,
            colorG = 0.98f,
            colorB = 0.95f,
            intensity = 150_000.0f,
            directionX = 0.25f,
            directionY = -1.0f,
            directionZ = -0.35f
        )
        fillLightEntity = createDirectionalLight(
            engine = engine,
            colorR = 0.90f,
            colorG = 0.96f,
            colorB = 1.0f,
            intensity = 75_000.0f,
            directionX = -0.55f,
            directionY = -0.30f,
            directionZ = 0.75f
        )
        rimLightEntity = createDirectionalLight(
            engine = engine,
            colorR = 1.0f,
            colorG = 0.95f,
            colorB = 0.90f,
            intensity = 52_000.0f,
            directionX = 0.05f,
            directionY = 0.45f,
            directionZ = 1.0f
        )

        if (indirectLight == null) {
            indirectLight = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(1.15f, 1.15f, 1.15f))
                .intensity(100_000.0f)
                .build(engine)
        }
        modelViewer.scene.indirectLight = indirectLight
        modelViewer.scene.skybox = null
    }

    private fun createDirectionalLight(
        engine: com.google.android.filament.Engine,
        colorR: Float,
        colorG: Float,
        colorB: Float,
        intensity: Float,
        directionX: Float,
        directionY: Float,
        directionZ: Float
    ): Int {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(colorR, colorG, colorB)
            .intensity(intensity)
            .direction(directionX, directionY, directionZ)
            .castShadows(false)
            .build(engine, entity)
        modelViewer.scene.addEntity(entity)
        return entity
    }

    private fun applyCameraPose() {
        runCatching {
            val pitchRadians = Math.toRadians(cameraPitchDegrees.toDouble())
            val yawRadians = Math.toRadians(cameraYawDegrees.toDouble())
            val rawDistance = BASE_CAMERA_DISTANCE * cameraDistanceScale.toDouble()
            val distance = if (abs(rawDistance) < CAMERA_DISTANCE_EPSILON) {
                CAMERA_DISTANCE_EPSILON
            } else {
                rawDistance
            }

            val targetY = cameraTargetY + cameraTargetHeightOffset.toDouble()

            val horizontalFactor = cos(pitchRadians)
            val eyeX = cameraTargetX + distance * horizontalFactor * sin(yawRadians)
            val eyeY = targetY + distance * sin(pitchRadians)
            val eyeZ = cameraTargetZ + distance * horizontalFactor * cos(yawRadians)

            applyManipulatorPose(
                eyeX = eyeX.toFloat(),
                eyeY = eyeY.toFloat(),
                eyeZ = eyeZ.toFloat(),
                targetX = cameraTargetX.toFloat(),
                targetY = targetY.toFloat(),
                targetZ = cameraTargetZ.toFloat()
            )

            modelViewer.camera.lookAt(
                eyeX,
                eyeY,
                eyeZ,
                cameraTargetX,
                targetY,
                cameraTargetZ,
                0.0,
                1.0,
                0.0
            )

            // Always apply model-space orbit fallback so pitch/yaw works even when manipulator behavior differs by device.
            applyModelOrbitFallback(pitchDegrees = cameraPitchDegrees, yawDegrees = cameraYawDegrees)
        }.onFailure { error ->
            dispatchError("Failed to apply glTF camera: ${error.message ?: "unknown error"}")
        }
    }

    private fun applyManipulatorPose(
        eyeX: Float,
        eyeY: Float,
        eyeZ: Float,
        targetX: Float,
        targetY: Float,
        targetZ: Float
    ): Boolean {
        val field = cameraManipulatorField
        if (field == null) {
            if (!hasLoggedManipulatorUnavailable) {
                AppLogger.w(TAG, "Manipulator field unavailable, using root-transform fallback.")
                hasLoggedManipulatorUnavailable = true
            }
            return false
        }
        return runCatching {
            val viewportWidth = max(width, 1)
            val viewportHeight = max(height, 1)
            val manipulator = Manipulator.Builder()
                .viewport(viewportWidth, viewportHeight)
                .targetPosition(targetX, targetY, targetZ)
                .upVector(0f, 1f, 0f)
                .orbitHomePosition(eyeX, eyeY, eyeZ)
                .panning(false)
                .build(Manipulator.Mode.ORBIT)

            manipulator.jumpToBookmark(manipulator.homeBookmark)
            field.set(modelViewer, manipulator)

            val currentFieldValue = field.get(modelViewer)
            currentFieldValue === manipulator
        }.onFailure { error ->
            if (!hasLoggedManipulatorApplyFailure) {
                AppLogger.w(TAG, "Failed to apply manipulator pose: ${error.message}")
                hasLoggedManipulatorApplyFailure = true
            }
        }.getOrDefault(false)
    }

    private fun captureBaseRootTransform() {
        val asset = modelViewer.asset ?: run {
            baseRootTransform = null
            baseEntityTransforms = emptyMap()
            cameraTargetX = CAMERA_TARGET_X
            cameraTargetY = CAMERA_TARGET_Y
            cameraTargetZ = CAMERA_TARGET_Z
            return
        }

        val transformManager = modelViewer.engine.transformManager

        val rootEntity = asset.root
        val rootInstance = transformManager.getInstance(rootEntity)
        if (rootInstance != 0) {
            val rootTransform = transformManager.getTransform(rootInstance, FloatArray(16))
            baseRootTransform = rootTransform
            cameraTargetX = rootTransform[12].toDouble()
            cameraTargetY = CAMERA_TARGET_Y
            cameraTargetZ = rootTransform[14].toDouble()
        } else {
            baseRootTransform = null
            cameraTargetX = CAMERA_TARGET_X
            cameraTargetY = CAMERA_TARGET_Y
            cameraTargetZ = CAMERA_TARGET_Z
        }

        val captured = LinkedHashMap<Int, FloatArray>()
        asset.entities.forEach { entity ->
            val instance = transformManager.getInstance(entity)
            if (instance != 0) {
                captured[entity] = transformManager.getTransform(instance, FloatArray(16))
            }
        }
        baseEntityTransforms = captured
    }

    private fun applyModelOrbitFallback(pitchDegrees: Float, yawDegrees: Float) {
        val asset = modelViewer.asset ?: return
        val transformManager = modelViewer.engine.transformManager

        val rotationY = createRotationYMatrix((-yawDegrees).toDouble())
        val rotationX = createRotationXMatrix((-pitchDegrees).toDouble())
        val rotation = multiplyMat4(rotationY, rotationX)
        val pivotedRotation = buildPivotedRotation(rotation)

        val base = baseRootTransform
        if (base != null) {
            val rootInstance = transformManager.getInstance(asset.root)
            if (rootInstance != 0) {
                val composed = multiplyMat4(base, pivotedRotation)
                transformManager.setTransform(rootInstance, composed)
                return
            }
        }

        if (baseEntityTransforms.isNotEmpty()) {
            baseEntityTransforms.forEach { (entity, transform) ->
                val instance = transformManager.getInstance(entity)
                if (instance != 0) {
                    val composed = multiplyMat4(transform, pivotedRotation)
                    transformManager.setTransform(instance, composed)
                }
            }
        }
    }

    private fun buildPivotedRotation(rotation: FloatArray): FloatArray {
        val translateToPivot = createTranslationMatrix(0f, MODEL_ORBIT_PIVOT_Y, 0f)
        val translateBack = createTranslationMatrix(0f, -MODEL_ORBIT_PIVOT_Y, 0f)
        return multiplyMat4(translateToPivot, multiplyMat4(rotation, translateBack))
    }

    private fun createRotationXMatrix(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()

        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, c, s, 0f,
            0f, -s, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun createRotationYMatrix(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()

        return floatArrayOf(
            c, 0f, -s, 0f,
            0f, 1f, 0f, 0f,
            s, 0f, c, 0f,
            0f, 0f, 0f, 1f
        )
    }

    private fun createTranslationMatrix(tx: Float, ty: Float, tz: Float): FloatArray {
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            tx, ty, tz, 1f
        )
    }

    private fun multiplyMat4(left: FloatArray, right: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += left[k * 4 + row] * right[column * 4 + k]
                }
                result[column * 4 + row] = sum
            }
        }
        return result
    }

    private fun resolveResourceFile(baseDirectory: File?, relativeUri: String): File {
        val trimmedUri = relativeUri.trim()
        require(trimmedUri.isNotEmpty()) { "Empty glTF resource URI." }

        val candidate = if (File(trimmedUri).isAbsolute) {
            File(trimmedUri)
        } else {
            File(baseDirectory, trimmedUri)
        }

        val canonicalFile = candidate.canonicalFile
        require(canonicalFile.exists() && canonicalFile.isFile) {
            "Missing glTF resource: ${canonicalFile.absolutePath}"
        }

        return canonicalFile
    }

    private fun readFileToDirectByteBuffer(file: File): ByteBuffer {
        val bytes = file.readBytes()
        return ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(bytes)
                flip()
            }
    }

    private fun dispatchError(message: String) {
        if (message.isBlank()) {
            return
        }

        AppLogger.e(TAG, message)
        onRenderErrorListener?.invoke(message)
    }
}

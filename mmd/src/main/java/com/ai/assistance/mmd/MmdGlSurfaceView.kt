package com.ai.assistance.mmd

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MmdGlSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer = MmdPreviewRenderer()

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setZOrderOnTop(true)
        setBackgroundColor(Color.TRANSPARENT)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        requestHighRefreshRateIfSupported()
    }

    fun setModelPath(path: String) {
        renderer.setModelPath(path)
    }

    fun setAnimationState(animationName: String?, isLooping: Boolean) {
        renderer.setAnimationState(animationName, isLooping)
    }

    fun setModelRotation(rotationX: Float, rotationY: Float, rotationZ: Float) {
        renderer.setModelRotation(rotationX, rotationY, rotationZ)
    }

    fun setOnRenderErrorListener(listener: ((String) -> Unit)?) {
        renderer.setOnErrorListener(listener)
    }

    override fun onResume() {
        super.onResume()
        requestHighRefreshRateIfSupported()
    }

    private fun requestHighRefreshRateIfSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }

        val surface = holder.surface ?: return
        if (!surface.isValid) {
            return
        }

        try {
            val setFrameRateMethod =
                surface.javaClass.getMethod(
                    "setFrameRate",
                    Float::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!
                )
            setFrameRateMethod.invoke(surface, 120f, 0)
        } catch (_: Throwable) {
        }
    }
}

private data class DrawBatch(
    val firstVertex: Int,
    val vertexCount: Int,
    val textureSlot: Int
)

private class MmdPreviewRenderer : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "MmdGlRenderer"
        private const val MAX_ANIMATED_MESH_UPDATES_PER_SECOND = 60f
        private const val MIN_ANIMATED_MESH_UPDATE_INTERVAL_MS =
            (1000f / MAX_ANIMATED_MESH_UPDATES_PER_SECOND).toLong()

        private const val STRIDE_FLOATS = 8
        private const val POSITION_OFFSET_FLOATS = 0
        private const val NORMAL_OFFSET_FLOATS = 3
        private const val UV_OFFSET_FLOATS = 6
        private const val STRIDE_BYTES = STRIDE_FLOATS * 4

        private const val VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aTexCoord;
            varying float vLight;
            varying vec2 vTexCoord;
            void main() {
                vec3 normal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
                vec3 keyLightDir = normalize(vec3(0.35, 0.65, 1.0));
                vec3 fillLightDir = normalize(vec3(-0.45, 0.25, 0.9));

                float key = max(dot(normal, keyLightDir), 0.0);
                float fill = max(dot(normal, fillLightDir), 0.0);
                float ambient = 0.38;

                vLight = min(ambient + key * 0.72 + fill * 0.28, 1.5);
                vTexCoord = aTexCoord;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying float vLight;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            uniform float uUseTexture;
            void main() {
                vec4 texColor = texture2D(uTexture, vec2(vTexCoord.x, 1.0 - vTexCoord.y));
                vec3 fallbackColor = vec3(0.86, 0.83, 0.9);
                vec3 baseColor = mix(fallbackColor, texColor.rgb, uUseTexture);
                float alpha = mix(1.0, texColor.a, uUseTexture);

                vec3 litColor = baseColor * vLight;
                litColor = mix(litColor, baseColor, 0.18);
                litColor = pow(max(litColor, vec3(0.0)), vec3(0.9));

                gl_FragColor = vec4(clamp(litColor, 0.0, 1.0), alpha);
            }
        """
    }

    @Volatile
    private var pendingModelPath: String? = null

    @Volatile
    private var pendingAnimationName: String? = null

    @Volatile
    private var pendingAnimationLooping: Boolean = false

    @Volatile
    private var pendingRotationX: Float = 18f

    @Volatile
    private var pendingRotationY: Float = 0f

    @Volatile
    private var pendingRotationZ: Float = 0f

    private var currentModelPath: String? = null
    private var vertexBuffer: FloatBuffer? = null

    private var activeRotationX: Float = 18f
    private var activeRotationY: Float = 0f
    private var activeRotationZ: Float = 0f
    private var vertexCount: Int = 0

    private var activeAnimationName: String? = null
    private var activeAnimationLooping: Boolean = false
    private var activeAnimationStartedAtMs: Long = 0L
    private var activeMotionPath: String? = null
    private var activeMotionMaxFrame: Int = 0
    private var lastAnimatedMeshUpdateAtMs: Long = Long.MIN_VALUE
    private var lastAnimatedSampledFrame: Float = Float.NaN
    private var lastAnimatedMotionPath: String? = null

    private var drawBatches: List<DrawBatch> = emptyList()
    private var textureIdsBySlot: IntArray = IntArray(0)

    private var centerX = 0f
    private var centerY = 0f
    private var centerZ = 0f
    private var fitScale = 1f

    private var aspectRatio = 1f
    private var cameraDistance = 3f
    private var nearClip = 0.1f
    private var farClip = 100f

    private var lastLoadAttemptAtMs = 0L
    private var lastFailedModelPath: String? = null

    private var program = 0
    private var positionHandle = -1
    private var normalHandle = -1
    private var texCoordHandle = -1
    private var mvpHandle = -1
    private var modelHandle = -1
    private var useTextureHandle = -1
    private var textureSamplerHandle = -1

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var onErrorListener: ((String) -> Unit)? = null

    fun setOnErrorListener(listener: ((String) -> Unit)?) {
        onErrorListener = listener
    }

    fun setModelPath(path: String) {
        pendingModelPath = path
    }

    fun setAnimationState(animationName: String?, isLooping: Boolean) {
        pendingAnimationName = animationName?.takeIf { it.isNotBlank() }
        pendingAnimationLooping = isLooping
    }

    fun setModelRotation(rotationX: Float, rotationY: Float, rotationZ: Float) {
        pendingRotationX = rotationX
        pendingRotationY = rotationY
        pendingRotationZ = rotationZ
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        currentModelPath = null
        clearMesh()
        clearTextureSet()
        activeAnimationName = pendingAnimationName
        activeAnimationLooping = pendingAnimationLooping
        activeAnimationStartedAtMs = SystemClock.uptimeMillis()
        activeMotionPath = null
        activeMotionMaxFrame = 0
        lastAnimatedMeshUpdateAtMs = Long.MIN_VALUE
        lastAnimatedSampledFrame = Float.NaN
        lastAnimatedMotionPath = null
        activeRotationX = pendingRotationX
        activeRotationY = pendingRotationY
        activeRotationZ = pendingRotationZ

        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        if (program == 0) {
            dispatchError("Failed to create GL program for MMD renderer.")
            return
        }

        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMvpMatrix")
        modelHandle = GLES20.glGetUniformLocation(program, "uModelMatrix")
        useTextureHandle = GLES20.glGetUniformLocation(program, "uUseTexture")
        textureSamplerHandle = GLES20.glGetUniformLocation(program, "uTexture")

        if (
            positionHandle < 0 ||
            normalHandle < 0 ||
            texCoordHandle < 0 ||
            mvpHandle < 0 ||
            modelHandle < 0 ||
            useTextureHandle < 0 ||
            textureSamplerHandle < 0
        ) {
            dispatchError(
                "GL program missing required handles: pos=$positionHandle normal=$normalHandle uv=$texCoordHandle mvp=$mvpHandle model=$modelHandle useTex=$useTextureHandle sampler=$textureSamplerHandle"
            )
            program = 0
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)

        val safeHeight = if (height <= 0) 1 else height
        aspectRatio = width.toFloat() / safeHeight.toFloat()
        updateProjectionAndView()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (program == 0) {
            return
        }

        val newPath = pendingModelPath
        if (!newPath.isNullOrBlank() && newPath != currentModelPath) {
            val now = SystemClock.uptimeMillis()
            val shouldRetry =
                lastFailedModelPath != newPath ||
                    (now - lastLoadAttemptAtMs) >= 1500L

            if (shouldRetry) {
                lastLoadAttemptAtMs = now
                val loaded = loadPreviewAssets(newPath)
                if (loaded) {
                    currentModelPath = newPath
                    activeMotionPath = null
                    activeMotionMaxFrame = 0
                    lastAnimatedMeshUpdateAtMs = Long.MIN_VALUE
                    lastAnimatedSampledFrame = Float.NaN
                    lastAnimatedMotionPath = null
                    lastFailedModelPath = null
                } else {
                    lastFailedModelPath = newPath
                }
            }
        }

        val localVertexBuffer = vertexBuffer ?: return
        if (vertexCount <= 0) return

        val nowMs = SystemClock.uptimeMillis()
        val requestedAnimation = pendingAnimationName
        val requestedLooping = pendingAnimationLooping
        if (requestedAnimation != activeAnimationName || requestedLooping != activeAnimationLooping) {
            activeAnimationName = requestedAnimation
            activeAnimationLooping = requestedLooping
            activeAnimationStartedAtMs = nowMs
            activeMotionPath = null
            activeMotionMaxFrame = 0
            lastAnimatedMeshUpdateAtMs = Long.MIN_VALUE
            lastAnimatedSampledFrame = Float.NaN
            lastAnimatedMotionPath = null
        }

        val stableModelPath = currentModelPath
        if (!requestedAnimation.isNullOrBlank() && !stableModelPath.isNullOrBlank()) {
            val resolvedMotionPath = File(File(stableModelPath).parentFile, requestedAnimation).absolutePath
            if (resolvedMotionPath != activeMotionPath) {
                activeMotionPath = resolvedMotionPath
                activeMotionMaxFrame = MmdNative.nativeReadMotionMaxFrame(resolvedMotionPath).coerceAtLeast(0)
                activeAnimationStartedAtMs = nowMs
                lastAnimatedMeshUpdateAtMs = Long.MIN_VALUE
                lastAnimatedSampledFrame = Float.NaN
                lastAnimatedMotionPath = null
            }

            if (activeMotionMaxFrame > 0) {
                val elapsedFrames = ((nowMs - activeAnimationStartedAtMs).coerceAtLeast(0L) * 30f) / 1000f
                val maxFrameFloat = activeMotionMaxFrame.toFloat()
                val sampledFrame = if (activeAnimationLooping && maxFrameFloat > 0f) {
                    elapsedFrames % (maxFrameFloat + 1f)
                } else {
                    min(elapsedFrames, maxFrameFloat)
                }

                if (shouldUpdateAnimatedMesh(nowMs, resolvedMotionPath, sampledFrame)) {
                    val animatedMesh = MmdNative.nativeBuildPreviewAnimatedMesh(
                        pathModel = stableModelPath,
                        pathMotion = resolvedMotionPath,
                        frame = sampledFrame
                    )
                    if (animatedMesh != null && animatedMesh.size == vertexCount * STRIDE_FLOATS) {
                        localVertexBuffer.position(0)
                        localVertexBuffer.put(animatedMesh)
                        localVertexBuffer.position(0)
                        lastAnimatedMeshUpdateAtMs = nowMs
                        lastAnimatedSampledFrame = sampledFrame
                        lastAnimatedMotionPath = resolvedMotionPath
                    }
                }
            }
        } else {
            activeMotionPath = null
            activeMotionMaxFrame = 0
            lastAnimatedMeshUpdateAtMs = Long.MIN_VALUE
            lastAnimatedSampledFrame = Float.NaN
            lastAnimatedMotionPath = null
        }

        activeRotationX = pendingRotationX
        activeRotationY = pendingRotationY
        activeRotationZ = pendingRotationZ

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, -centerX, -centerY, -centerZ)
        Matrix.scaleM(modelMatrix, 0, fitScale, fitScale, fitScale)
        Matrix.rotateM(modelMatrix, 0, activeRotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, activeRotationY, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, activeRotationZ, 0f, 0f, 1f)

        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, modelMatrix, 0)

        localVertexBuffer.position(POSITION_OFFSET_FLOATS)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, localVertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        localVertexBuffer.position(NORMAL_OFFSET_FLOATS)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, localVertexBuffer)
        GLES20.glEnableVertexAttribArray(normalHandle)

        localVertexBuffer.position(UV_OFFSET_FLOATS)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, localVertexBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        for (batch in drawBatches) {
            val textureId = if (batch.textureSlot in textureIdsBySlot.indices) {
                textureIdsBySlot[batch.textureSlot]
            } else {
                0
            }

            val hasTexture = textureId != 0
            GLES20.glUniform1f(useTextureHandle, if (hasTexture) 1f else 0f)

            if (hasTexture) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
                GLES20.glUniform1i(textureSamplerHandle, 0)
            }

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, batch.firstVertex, batch.vertexCount)

            if (hasTexture) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            }
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun loadPreviewAssets(modelPath: String): Boolean {
        if (!MmdInspector.isAvailable()) {
            dispatchError(MmdInspector.unavailableReason().ifBlank { "MMD backend is unavailable." })
            clearMesh()
            clearTextureSet()
            return false
        }

        val rawMesh = MmdNative.nativeBuildPreviewMesh(modelPath)
        if (rawMesh == null) {
            dispatchError(MmdInspector.getLastError().ifBlank { "Failed to build preview mesh from model." })
            clearMesh()
            clearTextureSet()
            return false
        }

        if (rawMesh.isEmpty() || rawMesh.size % STRIDE_FLOATS != 0) {
            dispatchError("Invalid preview mesh data returned by native layer.")
            clearMesh()
            clearTextureSet()
            return false
        }

        val floatBuffer = ByteBuffer
            .allocateDirect(rawMesh.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(rawMesh)
                position(0)
            }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        for (index in rawMesh.indices step STRIDE_FLOATS) {
            val x = rawMesh[index]
            val y = rawMesh[index + 1]
            val z = rawMesh[index + 2]

            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
        }

        centerX = (minX + maxX) * 0.5f
        centerY = (minY + maxY) * 0.5f
        centerZ = (minZ + maxZ) * 0.5f

        val extentX = maxX - minX
        val extentY = maxY - minY
        val extentZ = maxZ - minZ
        val maxExtent = max(extentX, max(extentY, extentZ)).coerceAtLeast(0.0001f)

        fitScale = 1f
        val radius = (maxExtent * 0.5f).coerceAtLeast(0.1f)
        cameraDistance = max(1.8f, radius * 3.0f + 0.7f)
        nearClip = max(0.01f, radius / 100f)
        farClip = max(120f, cameraDistance + radius * 40f)
        updateProjectionAndView()

        val parsedBatches = parseDrawBatches(
            batchData = MmdNative.nativeBuildPreviewBatches(modelPath),
            totalVertices = rawMesh.size / STRIDE_FLOATS
        )

        clearTextureSet()
        val texturePaths = MmdNative.nativeReadPreviewTexturePaths(modelPath).orEmpty()
        textureIdsBySlot = loadTextureSlots(texturePaths)

        vertexBuffer = floatBuffer
        vertexCount = rawMesh.size / STRIDE_FLOATS
        drawBatches = parsedBatches

        val loadedTextures = textureIdsBySlot.count { it != 0 }
        Log.i(
            TAG,
            "Loaded MMD preview assets. vertices=$vertexCount batches=${drawBatches.size} textures=$loadedTextures/${textureIdsBySlot.size} modelPath=$modelPath"
        )
        return true
    }

    private fun parseDrawBatches(batchData: IntArray?, totalVertices: Int): List<DrawBatch> {
        if (totalVertices <= 0) {
            return emptyList()
        }

        if (batchData == null || batchData.isEmpty() || batchData.size % 3 != 0) {
            return listOf(DrawBatch(firstVertex = 0, vertexCount = totalVertices, textureSlot = -1))
        }

        val batches = mutableListOf<DrawBatch>()
        var cursor = 0
        while (cursor + 2 < batchData.size) {
            val firstVertexRaw = batchData[cursor]
            val vertexCountRaw = batchData[cursor + 1]
            val textureSlot = batchData[cursor + 2]
            cursor += 3

            if (firstVertexRaw < 0 || vertexCountRaw <= 0) {
                continue
            }

            if (firstVertexRaw >= totalVertices) {
                continue
            }

            val clampedCount = min(vertexCountRaw, totalVertices - firstVertexRaw)
            if (clampedCount <= 0) {
                continue
            }

            batches.add(
                DrawBatch(
                    firstVertex = firstVertexRaw,
                    vertexCount = clampedCount,
                    textureSlot = textureSlot
                )
            )
        }

        if (batches.isEmpty()) {
            return listOf(DrawBatch(firstVertex = 0, vertexCount = totalVertices, textureSlot = -1))
        }

        return batches
    }

    private fun loadTextureSlots(texturePaths: Array<out String>): IntArray {
        if (texturePaths.isEmpty()) {
            return IntArray(0)
        }

        return IntArray(texturePaths.size) { index ->
            val originalPath = texturePaths[index]
            var textureId = loadTextureFromPath(originalPath)
            if (textureId != 0) {
                return@IntArray textureId
            }

            val alternativePath = findAlternativeTexturePath(originalPath)
            if (!alternativePath.isNullOrBlank() && alternativePath != originalPath) {
                textureId = loadTextureFromPath(alternativePath)
            }

            textureId
        }
    }

    private fun findAlternativeTexturePath(texturePath: String): String? {
        val originalFile = File(texturePath)
        val parent = originalFile.parentFile ?: return null
        if (!parent.exists() || !parent.isDirectory) {
            return null
        }

        val baseName = originalFile.nameWithoutExtension
        val candidateExts = listOf("png", "jpg", "jpeg", "webp", "bmp", "tga")

        for (ext in candidateExts) {
            val lower = File(parent, "$baseName.$ext")
            if (lower.exists() && lower.isFile) {
                return lower.absolutePath
            }

            val upper = File(parent, "$baseName.${ext.uppercase()}")
            if (upper.exists() && upper.isFile) {
                return upper.absolutePath
            }
        }

        return null
    }

    private fun loadTextureFromPath(texturePath: String?): Int {
        val normalizedPath = texturePath?.trim()?.takeIf { it.isNotEmpty() } ?: return 0
        val textureFile = File(normalizedPath)
        if (!textureFile.exists() || !textureFile.isFile) {
            Log.w(TAG, "Texture file does not exist: $normalizedPath")
            return 0
        }

        val textureIds = IntArray(1)
        GLES20.glGenTextures(1, textureIds, 0)
        val generatedTextureId = textureIds[0]
        if (generatedTextureId == 0) {
            Log.w(TAG, "Failed to allocate OpenGL texture id for: $normalizedPath")
            return 0
        }

        val uploadSuccess = uploadTexturePixels(generatedTextureId, normalizedPath)
        if (!uploadSuccess) {
            GLES20.glDeleteTextures(1, textureIds, 0)
            return 0
        }

        return generatedTextureId
    }

    private fun uploadTexturePixels(textureId: Int, texturePath: String): Boolean {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val bitmap = BitmapFactory.decodeFile(texturePath)
        if (bitmap != null) {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            return true
        }

        val size = MmdNative.nativeDecodeImageSize(texturePath)
        val rgbaBytes = MmdNative.nativeDecodeImageRgba(texturePath)
        if (size == null || size.size < 2 || rgbaBytes == null) {
            Log.w(TAG, "Failed to decode texture bitmap: $texturePath")
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            return false
        }

        val width = size[0]
        val height = size[1]
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "Invalid native decoded image size: ${width}x$height, file=$texturePath")
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            return false
        }

        val expectedSizeLong = width.toLong() * height.toLong() * 4L
        if (expectedSizeLong <= 0L || expectedSizeLong > Int.MAX_VALUE) {
            Log.w(TAG, "Native decoded image size overflow: file=$texturePath")
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            return false
        }

        val expectedSize = expectedSizeLong.toInt()
        if (rgbaBytes.size < expectedSize) {
            Log.w(TAG, "Native decoded image buffer too small: got=${rgbaBytes.size} expected=$expectedSize file=$texturePath")
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            return false
        }

        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        val rgbaBuffer = ByteBuffer.wrap(rgbaBytes, 0, expectedSize)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            rgbaBuffer
        )
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return true
    }

    private fun updateProjectionAndView() {
        val safeAspect = if (aspectRatio > 0f) aspectRatio else 1f
        Matrix.perspectiveM(projectionMatrix, 0, 45f, safeAspect, nearClip, farClip)
        Matrix.setLookAtM(
            viewMatrix,
            0,
            0f,
            0.18f,
            cameraDistance,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f
        )
    }

    private fun shouldUpdateAnimatedMesh(nowMs: Long, motionPath: String, sampledFrame: Float): Boolean {
        if (motionPath != lastAnimatedMotionPath) {
            return true
        }

        if (lastAnimatedSampledFrame.isNaN()) {
            return true
        }

        if (lastAnimatedMeshUpdateAtMs == Long.MIN_VALUE) {
            return true
        }

        if (nowMs - lastAnimatedMeshUpdateAtMs < MIN_ANIMATED_MESH_UPDATE_INTERVAL_MS) {
            return false
        }

        return abs(sampledFrame - lastAnimatedSampledFrame) > 0.0001f
    }

    private fun clearMesh() {
        vertexBuffer = null
        vertexCount = 0
        drawBatches = emptyList()
    }

    private fun clearTextureSet() {
        if (textureIdsBySlot.isNotEmpty()) {
            val textures = textureIdsBySlot.filter { it != 0 }.toIntArray()
            if (textures.isNotEmpty()) {
                GLES20.glDeleteTextures(textures.size, textures, 0)
            }
        }
        textureIdsBySlot = IntArray(0)
    }

    private fun dispatchError(message: String) {
        if (message.isBlank()) return
        Log.e(TAG, message)
        mainHandler.post {
            onErrorListener?.invoke(message)
        }
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        if (vertexShader == 0) return 0

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        if (fragmentShader == 0) return 0

        val shaderProgram = GLES20.glCreateProgram()
        if (shaderProgram == 0) {
            return 0
        }

        GLES20.glAttachShader(shaderProgram, vertexShader)
        GLES20.glAttachShader(shaderProgram, fragmentShader)
        GLES20.glLinkProgram(shaderProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link error: ${GLES20.glGetProgramInfoLog(shaderProgram)}")
            GLES20.glDeleteProgram(shaderProgram)
            return 0
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return shaderProgram
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            return 0
        }

        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Shader compile error: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }
}

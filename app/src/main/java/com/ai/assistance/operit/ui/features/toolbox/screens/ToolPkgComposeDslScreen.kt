package com.ai.assistance.operit.ui.features.toolbox.screens

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.javascript.JsEngine
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslNode
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslParser
import com.ai.assistance.operit.core.tools.packTool.ToolPkgComposeDslRenderResult
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.util.LocaleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

private const val TAG = "ToolPkgComposeDslScreen"
internal fun normalizeToken(raw: String): String =
    raw.lowercase(Locale.ROOT)
        .replace("-", "")
        .replace("_", "")
        .trim()

private fun buildZeroArgGetterByToken(
    ownerClass: Class<*>,
    returnTypeMatcher: (Class<*>) -> Boolean
): Map<String, java.lang.reflect.Method> =
    ownerClass.methods
        .asSequence()
        .filter { method ->
            method.name.startsWith("get") &&
                method.parameterCount == 0 &&
                returnTypeMatcher(method.returnType)
        }
        .onEach { method -> method.isAccessible = true }
        .associateBy { method -> normalizeToken(method.name.removePrefix("get")) }

private val typographyGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(androidx.compose.material3.Typography::class.java) { returnType ->
        returnType == androidx.compose.ui.text.TextStyle::class.java
    }
}
private val horizontalAlignmentGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(Alignment::class.java) { returnType ->
        Alignment.Horizontal::class.java.isAssignableFrom(returnType)
    }
}
private val verticalAlignmentGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(Alignment::class.java) { returnType ->
        Alignment.Vertical::class.java.isAssignableFrom(returnType)
    }
}
private val boxAlignmentGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(Alignment::class.java) { returnType ->
        returnType == Alignment::class.java
    }
}
private val horizontalArrangementGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(Arrangement::class.java) { returnType ->
        Arrangement.Horizontal::class.java.isAssignableFrom(returnType)
    }
}
private val verticalArrangementGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(Arrangement::class.java) { returnType ->
        Arrangement.Vertical::class.java.isAssignableFrom(returnType)
    }
}
private val fontWeightGetterByToken: Map<String, java.lang.reflect.Method> by lazy {
    buildZeroArgGetterByToken(FontWeight.Companion::class.java) { returnType ->
        FontWeight::class.java.isAssignableFrom(returnType)
    }
}

private val colorSchemeFieldByToken: Map<String, java.lang.reflect.Field> by lazy {
    androidx.compose.material3.ColorScheme::class.java.declaredFields
        .onEach { it.isAccessible = true }
        .associateBy { field ->
            normalizeToken(field.name)
        }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun ToolPkgComposeDslToolScreen(
    navController: NavController,
    containerPackageName: String,
    uiModuleId: String,
    fallbackTitle: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val renderMutex = remember { Mutex() }

    val packageManager = remember {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    val jsEngine = remember(containerPackageName, uiModuleId) { JsEngine(context) }
    DisposableEffect(jsEngine) {
        onDispose { jsEngine.destroy() }
    }

    var script by remember(containerPackageName, uiModuleId) { mutableStateOf<String?>(null) }
    var scriptEntryPath by remember(containerPackageName, uiModuleId) { mutableStateOf<String?>(null) }
    var renderResult by remember(containerPackageName, uiModuleId) {
        mutableStateOf<ToolPkgComposeDslRenderResult?>(null)
    }
    var errorMessage by remember(containerPackageName, uiModuleId) { mutableStateOf<String?>(null) }
    var isLoading by remember(containerPackageName, uiModuleId) { mutableStateOf(true) }
    var isDispatching by remember(containerPackageName, uiModuleId) { mutableStateOf(false) }
    var dispatchingCount by remember(containerPackageName, uiModuleId) { mutableStateOf(0) }

    fun buildModuleSpec(entryPath: String?): Map<String, Any?> =
        mapOf(
            "id" to uiModuleId,
            "runtime" to "compose_dsl",
            "entry" to (entryPath ?: ""),
            "title" to fallbackTitle,
            "toolPkgId" to containerPackageName
        )

    fun dispatchAction(actionId: String, payload: Any? = null) {
        val normalizedActionId = actionId.trim()
        if (normalizedActionId.isBlank()) {
            return
        }

        dispatchingCount += 1
        isDispatching = dispatchingCount > 0

        val dispatched =
            jsEngine.dispatchComposeDslActionAsync(
                actionId = normalizedActionId,
                payload = payload,
                onIntermediateResult = { intermediateResult ->
                    val parsedIntermediate =
                        ToolPkgComposeDslParser.parseRenderResult(intermediateResult)
                    if (parsedIntermediate != null) {
                        renderResult = parsedIntermediate
                        errorMessage = null
                    }
                },
                onComplete = {
                    dispatchingCount = (dispatchingCount - 1).coerceAtLeast(0)
                    isDispatching = dispatchingCount > 0
                },
                onError = { error ->
                    errorMessage = "compose_dsl runtime error: $error"
                    AppLogger.e(
                        TAG,
                        "compose_dsl async action failed: actionId=$normalizedActionId, error=$error"
                    )
                }
            )

        if (!dispatched) {
            dispatchingCount = (dispatchingCount - 1).coerceAtLeast(0)
            isDispatching = dispatchingCount > 0
        }
    }

    suspend fun render() {
        var followUpActionId: String? = null
        renderMutex.withLock {
            try {
                isLoading = true
                dispatchingCount = 0
                isDispatching = false
                errorMessage = null

                val scriptText: String? =
                    if (script == null) {
                        val loaded =
                            withContext(Dispatchers.IO) {
                                Pair(
                                    packageManager.getToolPkgComposeDslScript(
                                        containerPackageName = containerPackageName,
                                        uiModuleId = uiModuleId
                                    ),
                                    packageManager.getToolPkgComposeDslEntryPath(
                                        containerPackageName = containerPackageName,
                                        uiModuleId = uiModuleId
                                    )
                                )
                            }
                        if (scriptEntryPath.isNullOrBlank() && !loaded.second.isNullOrBlank()) {
                            scriptEntryPath = loaded.second
                        }
                        loaded.first
                    } else {
                        script
                    }

                if (scriptText.isNullOrBlank()) {
                    renderResult = null
                    errorMessage =
                        "compose_dsl script not found: package=$containerPackageName, module=$uiModuleId"
                    return
                }
                if (script == null) {
                    script = scriptText
                }

                val rawResult =
                    withContext(Dispatchers.IO) {
                        val language = LocaleUtils.getCurrentLanguage(context).trim()
                        jsEngine.executeComposeDslScript(
                            script = scriptText,
                            runtimeOptions =
                                mapOf(
                                    "packageName" to containerPackageName,
                                    "toolPkgId" to containerPackageName,
                                    "uiModuleId" to uiModuleId,
                                    "__operit_package_lang" to
                                        (if (language.isNotBlank()) language else "zh"),
                                    "__operit_script_entry" to (scriptEntryPath ?: ""),
                                    "moduleSpec" to buildModuleSpec(scriptEntryPath),
                                    "state" to (renderResult?.state ?: emptyMap<String, Any?>()),
                                    "memo" to (renderResult?.memo ?: emptyMap<String, Any?>())
                                )
                        )
                    }

                val rawText = rawResult?.toString()?.trim().orEmpty()
                val parsed = ToolPkgComposeDslParser.parseRenderResult(rawResult)
                if (parsed == null) {
                    val normalizedError =
                        when {
                            rawText.startsWith("Error:", ignoreCase = true) -> rawText
                            rawText.isNotBlank() -> "Invalid compose_dsl result: $rawText"
                            else -> "Invalid compose_dsl result"
                        }
                    renderResult = null
                    errorMessage = normalizedError
                    AppLogger.e(TAG, normalizedError)
                    return
                }

                renderResult = parsed
                errorMessage = null

                followUpActionId =
                    ToolPkgComposeDslParser.extractActionId(parsed.tree.props["onLoad"])
                Unit
            } catch (e: Exception) {
                renderResult = null
                errorMessage = "compose_dsl runtime error: ${e.message}"
                AppLogger.e(TAG, "compose_dsl render failed", e)
            } finally {
                isLoading = false
            }
        }

        val onLoadActionId = followUpActionId
        if (!onLoadActionId.isNullOrBlank()) {
            dispatchAction(actionId = onLoadActionId, payload = null)
        }
    }

    LaunchedEffect(containerPackageName, uiModuleId) {
        scope.launch {
            render()
        }
    }

    CustomScaffold { paddingValues ->
        val rootNode = renderResult?.tree
        val useOuterScroll = rootNode?.type?.equals("LazyColumn", ignoreCase = true) != true
        val contentModifier =
            Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .let { modifier ->
                    if (useOuterScroll) {
                        modifier.verticalScroll(rememberScrollState())
                    } else {
                        modifier
                    }
                }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMessage != null -> {
                    Column(
                        modifier =
                            Modifier.align(Alignment.Center)
                                .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    render()
                                }
                            }
                        ) {
                            Text("Retry")
                        }
                    }
                }
                rootNode != null -> {
                    Box(modifier = contentModifier) {
                        renderComposeDslNode(
                            node = rootNode,
                            onAction = ::dispatchAction,
                            nodePath = "0"
                        )
                    }
                }
            }

            if (isDispatching) {
                LinearProgressIndicator(
                    modifier =
                        Modifier.align(Alignment.TopCenter)
                            .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun renderComposeDslNode(
    node: ToolPkgComposeDslNode,
    onAction: (String, Any?) -> Unit,
    nodePath: String
) {
    val renderer = composeDslGeneratedNodeRendererRegistry[normalizeToken(node.type)]
    if (renderer != null) {
        renderer(node, onAction, nodePath)
        return
    }
    Text(
        text = "Unsupported node: ${node.type}",
        style = MaterialTheme.typography.bodySmall
    )
}

internal typealias ComposeDslNodeRenderer =
    @Composable (ToolPkgComposeDslNode, (String, Any?) -> Unit, String) -> Unit


@Composable
internal fun applyCommonModifier(base: Modifier, props: Map<String, Any?>): Modifier {
    var modifier = base

    val explicitWidth = props.floatOrNull("width")
    if (explicitWidth != null) {
        modifier = modifier.width(explicitWidth.dp)
    }
    val explicitHeight = props.floatOrNull("height")
    if (explicitHeight != null) {
        modifier = modifier.height(explicitHeight.dp)
    }

    if (props.bool("fillMaxSize", false)) {
        modifier = modifier.fillMaxSize()
    } else if (props.bool("fillMaxWidth", false)) {
        modifier = modifier.fillMaxWidth()
    }

    val paddingValue = props["padding"]
    if (paddingValue is Map<*, *>) {
        val horizontal = (paddingValue["horizontal"] as? Number)?.toFloat()
        val vertical = (paddingValue["vertical"] as? Number)?.toFloat()
        if (horizontal != null || vertical != null) {
            modifier = modifier.padding(
                horizontal = (horizontal ?: 0f).dp,
                vertical = (vertical ?: 0f).dp
            )
        }
    } else {
        val allPadding = props.floatOrNull("padding")
        if (allPadding != null) {
            modifier = modifier.padding(allPadding.dp)
        } else {
            val horizontal = props.floatOrNull("paddingHorizontal")
            val vertical = props.floatOrNull("paddingVertical")
            if (horizontal != null || vertical != null) {
                modifier = modifier.padding(
                    horizontal = (horizontal ?: 0f).dp,
                    vertical = (vertical ?: 0f).dp
                )
            }
        }
    }

    modifier = applyProxyModifierOps(modifier, props["modifier"])

    return modifier
}

private data class ComposeDslModifierOp(
    val name: String,
    val args: List<Any?>
)

@Composable
private fun applyProxyModifierOps(base: Modifier, rawModifier: Any?): Modifier {
    val ops = extractModifierOps(rawModifier)
    if (ops.isEmpty()) {
        return base
    }
    var modifier = base
    ops.forEach { op ->
        modifier = applySingleModifierOp(modifier, op)
    }
    return modifier
}

private fun extractModifierOps(rawModifier: Any?): List<ComposeDslModifierOp> {
    val container = rawModifier as? Map<*, *> ?: return emptyList()
    val list = container["__modifierOps"] as? List<*> ?: return emptyList()
    return list.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val name = map["name"]?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            return@mapNotNull null
        }
        val args = (map["args"] as? List<*>)?.toList() ?: emptyList()
        ComposeDslModifierOp(name = name, args = args)
    }
}

@Composable
private fun applySingleModifierOp(modifier: Modifier, op: ComposeDslModifierOp): Modifier {
    val token = normalizeToken(op.name)
    return when (token) {
        "fillmaxsize" -> {
            val fraction = op.args.getOrNull(0).floatArg() ?: 1f
            modifier.fillMaxSize(fraction.coerceAtLeast(0f))
        }
        "fillmaxwidth" -> {
            val fraction = op.args.getOrNull(0).floatArg() ?: 1f
            modifier.fillMaxWidth(fraction.coerceAtLeast(0f))
        }
        "fillmaxheight" -> {
            val fraction = op.args.getOrNull(0).floatArg() ?: 1f
            modifier.fillMaxHeight(fraction.coerceAtLeast(0f))
        }
        "width" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.width(value.dp)
        }
        "height" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.height(value.dp)
        }
        "size" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.size(value.dp)
        }
        "padding" -> applyPaddingModifierOp(modifier, op.args)
        "offset" -> applyOffsetModifierOp(modifier, op.args)
        "aspectratio" -> {
            val ratio = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.aspectRatio(ratio, true)
        }
        "alpha" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.alpha(value)
        }
        "rotate" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.rotate(value)
        }
        "scale" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.scale(value)
        }
        "zindex" -> {
            val value = op.args.getOrNull(0).floatArg() ?: return modifier
            modifier.zIndex(value)
        }
        "background" -> {
            val color = colorFromModifierArg(op.args.getOrNull(0)) ?: return modifier
            val shape = shapeFromModifierArg(op.args.getOrNull(1))
            if (shape != null) {
                modifier.background(color = color, shape = shape)
            } else {
                modifier.background(color = color)
            }
        }
        "border" -> {
            val width = op.args.getOrNull(0).floatArg() ?: 1f
            val color = colorFromModifierArg(op.args.getOrNull(1)) ?: return modifier
            val shape = shapeFromModifierArg(op.args.getOrNull(2))
            if (shape != null) {
                modifier.border(width = width.dp, color = color, shape = shape)
            } else {
                modifier.border(width = width.dp, color = color)
            }
        }
        "clip" -> {
            val shape = shapeFromModifierArg(op.args.getOrNull(0)) ?: return modifier
            modifier.clip(shape)
        }
        else -> modifier
    }
}

private fun applyPaddingModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    if (args.isEmpty()) {
        return modifier
    }
    val first = args.firstOrNull()
    if (first is Map<*, *>) {
        val all = first["all"].floatArg()
        if (all != null) {
            return modifier.padding(all.dp)
        }
        val horizontal = first["horizontal"].floatArg() ?: 0f
        val vertical = first["vertical"].floatArg() ?: 0f
        val start = first["start"].floatArg()
        val top = first["top"].floatArg()
        val end = first["end"].floatArg()
        val bottom = first["bottom"].floatArg()
        return if (start != null || top != null || end != null || bottom != null) {
            modifier.padding(
                start = (start ?: 0f).dp,
                top = (top ?: 0f).dp,
                end = (end ?: 0f).dp,
                bottom = (bottom ?: 0f).dp
            )
        } else {
            modifier.padding(horizontal = horizontal.dp, vertical = vertical.dp)
        }
    }

    val firstNumber = first.floatArg()
    val secondNumber = args.getOrNull(1).floatArg()
    val thirdNumber = args.getOrNull(2).floatArg()
    val fourthNumber = args.getOrNull(3).floatArg()

    return when {
        firstNumber != null && secondNumber == null -> modifier.padding(firstNumber.dp)
        firstNumber != null && secondNumber != null && thirdNumber == null ->
            modifier.padding(horizontal = firstNumber.dp, vertical = secondNumber.dp)
        firstNumber != null && secondNumber != null && thirdNumber != null && fourthNumber != null ->
            modifier.padding(
                start = firstNumber.dp,
                top = secondNumber.dp,
                end = thirdNumber.dp,
                bottom = fourthNumber.dp
            )
        else -> modifier
    }
}

private fun applyOffsetModifierOp(modifier: Modifier, args: List<Any?>): Modifier {
    if (args.isEmpty()) {
        return modifier
    }
    val first = args.firstOrNull()
    if (first is Map<*, *>) {
        val x = first["x"].floatArg() ?: 0f
        val y = first["y"].floatArg() ?: 0f
        return modifier.offset(x.dp, y.dp)
    }
    val x = first.floatArg() ?: 0f
    val y = args.getOrNull(1).floatArg() ?: 0f
    return modifier.offset(x.dp, y.dp)
}

@Composable
private fun colorFromModifierArg(value: Any?): Color? {
    return when (value) {
        is Color -> value
        is Number -> Color(value.toLong().toULong())
        else -> value?.toString()?.let { token ->
            resolveColorToken(token) ?: try {
                Color(AndroidColor.parseColor(token))
            } catch (_: Exception) {
                null
            }
        }
    }
}

private fun shapeFromModifierArg(value: Any?): androidx.compose.ui.graphics.Shape? {
    if (value is Map<*, *>) {
        val cornerRadius = value["cornerRadius"].floatArg()
        if (cornerRadius != null) {
            return RoundedCornerShape(cornerRadius.dp)
        }
    }
    return null
}

private fun Any?.floatArg(): Float? {
    return when (this) {
        is Number -> this.toFloat()
        else -> this?.toString()?.toFloatOrNull()
    }
}

internal fun Map<String, Any?>.string(key: String, defaultValue: String = ""): String {
    return this[key]?.toString().orEmpty().ifBlank { defaultValue }
}

internal fun Map<String, Any?>.stringOrNull(key: String): String? {
    val value = this[key]?.toString()?.trim().orEmpty()
    return if (value.isBlank()) null else value
}

internal fun Map<String, Any?>.bool(key: String, defaultValue: Boolean): Boolean {
    val value = this[key] ?: return defaultValue
    return when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        else -> value.toString().equals("true", ignoreCase = true)
    }
}

internal fun Map<String, Any?>.int(key: String, defaultValue: Int): Int {
    val value = this[key] ?: return defaultValue
    return when (value) {
        is Number -> value.toInt()
        else -> value.toString().toIntOrNull() ?: defaultValue
    }
}

internal fun Map<String, Any?>.floatOrNull(key: String): Float? {
    val value = this[key] ?: return null
    return when (value) {
        is Number -> value.toFloat()
        else -> value.toString().toFloatOrNull()
    }
}

internal fun Map<String, Any?>.dp(key: String, defaultValue: Dp = 0.dp): Dp {
    return (floatOrNull(key) ?: defaultValue.value).dp
}

@Composable
internal fun Map<String, Any?>.textStyle(key: String): androidx.compose.ui.text.TextStyle {
    val typography = MaterialTheme.typography
    val token = normalizeToken(string(key))
    val getter = typographyGetterByToken[token]
    return (getter?.invoke(typography) as? androidx.compose.ui.text.TextStyle) ?: typography.bodyMedium
}

internal fun Map<String, Any?>.horizontalAlignment(key: String): Alignment.Horizontal {
    val token = normalizeToken(string(key))
    val getter =
        horizontalAlignmentGetterByToken[token]
            ?: horizontalAlignmentGetterByToken["${token}horizontally"]
    return (getter?.invoke(Alignment) as? Alignment.Horizontal) ?: Alignment.Start
}

internal fun Map<String, Any?>.verticalAlignment(key: String): Alignment.Vertical {
    val token = normalizeToken(string(key))
    val getter =
        verticalAlignmentGetterByToken[token]
            ?: verticalAlignmentGetterByToken["${token}vertically"]
            ?: verticalAlignmentGetterByToken[if (token == "end") "bottom" else token]
    return (getter?.invoke(Alignment) as? Alignment.Vertical) ?: Alignment.Top
}

internal fun Map<String, Any?>.boxAlignment(key: String): Alignment {
    val token = normalizeToken(string(key))
    val getter =
        boxAlignmentGetterByToken[token]
            ?: boxAlignmentGetterByToken[if (token == "end") "bottomend" else token]
    return (getter?.invoke(Alignment) as? Alignment) ?: Alignment.TopStart
}

internal fun Map<String, Any?>.horizontalArrangement(key: String, spacing: Dp): Arrangement.Horizontal {
    val token = normalizeToken(string(key))
    val getter = horizontalArrangementGetterByToken[token]
    return (getter?.invoke(Arrangement) as? Arrangement.Horizontal) ?: Arrangement.spacedBy(spacing)
}

internal fun Map<String, Any?>.verticalArrangement(key: String, spacing: Dp): Arrangement.Vertical {
    val token = normalizeToken(string(key))
    val getter =
        verticalArrangementGetterByToken[token]
            ?: verticalArrangementGetterByToken[if (token == "end") "bottom" else token]
    return (getter?.invoke(Arrangement) as? Arrangement.Vertical) ?: Arrangement.spacedBy(spacing)
}

internal fun Map<String, Any?>.fontWeightOrNull(key: String): FontWeight? {
    val token = normalizeToken(string(key))
    val getter =
        fontWeightGetterByToken[token]
            ?: fontWeightGetterByToken[token.replace("ultra", "extra")]
            ?: fontWeightGetterByToken[token.replace("demi", "semi")]
            ?: fontWeightGetterByToken[if (token == "regular") "normal" else token]
            ?: fontWeightGetterByToken[if (token == "heavy") "black" else token]
    return getter?.invoke(FontWeight.Companion) as? FontWeight
}

@Composable
internal fun Map<String, Any?>.colorOrNull(key: String): Color? {
    val raw = stringOrNull(key) ?: return null
    return resolveColorToken(raw)
}

@Composable
internal fun resolveColorToken(raw: String): Color? {
    val token =
        normalizeToken(raw)
    val scheme = MaterialTheme.colorScheme
    val schemeColor =
        colorSchemeFieldByToken[token]?.let { field ->
            when (field.type) {
                java.lang.Long.TYPE -> Color(field.getLong(scheme).toULong())
                java.lang.Long::class.java -> Color((field.get(scheme) as Long).toULong())
                else -> field.get(scheme) as? Color
            }
        }
    if (schemeColor != null) {
        return schemeColor
    }
    return try {
        Color(AndroidColor.parseColor(raw))
    } catch (_: Exception) {
        null
    }
}

internal fun iconFromName(name: String): ImageVector {
    val iconKey = name.trim()
    require(iconKey.isNotEmpty()) { "icon name is blank" }

    val pascalCaseName =
        iconKey
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString(separator = "") { segment ->
                segment.replaceFirstChar { it.uppercaseChar() }
            }

    require(pascalCaseName.isNotEmpty()) { "icon name is invalid: $name" }

    val iconKtClass = Class.forName("androidx.compose.material.icons.filled.${pascalCaseName}Kt")
    val getterMethod = iconKtClass.getMethod("get$pascalCaseName", Icons.Default::class.java)
    return getterMethod.invoke(null, Icons.Default) as ImageVector
}

@Composable
internal fun Map<String, Any?>.shapeOrNull(): androidx.compose.ui.graphics.Shape? {
    val shapeValue = this["shape"]
    if (shapeValue is Map<*, *>) {
        val cornerRadius = (shapeValue["cornerRadius"] as? Number)?.toFloat()
        if (cornerRadius != null) {
            return RoundedCornerShape(cornerRadius.dp)
        }
    }
    return null
}

@Composable
internal fun Map<String, Any?>.borderOrNull(): BorderStroke? {
    val borderValue = this["border"]
    if (borderValue is Map<*, *>) {
        val width = (borderValue["width"] as? Number)?.toFloat() ?: 1f
        val colorStr = borderValue["color"]?.toString()
        val alpha = (borderValue["alpha"] as? Number)?.toFloat() ?: 1f

        if (colorStr != null) {
            val color = resolveColorToken(colorStr) ?: MaterialTheme.colorScheme.outline
            return BorderStroke(width.dp, color.copy(alpha = alpha))
        }
    }
    return null
}

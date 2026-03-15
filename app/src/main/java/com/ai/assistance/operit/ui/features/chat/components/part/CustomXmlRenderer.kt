package com.ai.assistance.operit.ui.features.chat.components.part

import android.webkit.WebView
import android.webkit.WebSettings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.animations.SimpleAnimatedVisibility
import com.ai.assistance.operit.ui.common.markdown.DefaultXmlRenderer
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.common.markdown.XmlContentRenderer
import com.ai.assistance.operit.ui.common.markdown.XmlRenderPluginRegistry
import com.ai.assistance.operit.ui.common.rememberLocal
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream

/** 支持多种 XML 标签的自定义渲染器 包含高效的前缀检测，直接解析标签类型 */
class CustomXmlRenderer(
    private val showThinkingProcess: Boolean = true,
    private val showStatusTags: Boolean = true,
    private val enableDialogs: Boolean = true,  // 新增参数：是否启用弹窗功能，默认启用
    private val fallback: XmlContentRenderer = DefaultXmlRenderer()
) : XmlContentRenderer {
    // 定义渲染器能够处理的内置标签集合
    private val builtInTags =
            setOf("think", "thinking", "search", "tool", "status", "tool_result", "html", "mood", "font", "details", "detail")

    private data class ToolRequestRenderState(
        val rawToolName: String,
        val paramText: String,
        val displayToolName: String,
        val isLongContent: Boolean,
        val isClosed: Boolean,
    )

    private data class ToolResultRenderState(
        val toolName: String,
        val isSuccess: Boolean,
        val resultContent: String,
    )

    @Composable
    override fun RenderXmlContent(
        xmlContent: String,
        modifier: Modifier,
        textColor: Color,
        xmlStream: Stream<String>?,
        renderInstanceKey: Any?
    ) {
        val trimmedContent = xmlContent.trim()
        val tagName = extractTagName(trimmedContent)
        
        // 无障碍朗读描述：只朗读块类型
        val accessibilityDesc = when (tagName) {
            "tool" -> stringResource(R.string.tool_call_block)
            "tool_result" -> stringResource(R.string.tool_result_block)
            "think", "thinking" -> stringResource(R.string.thinking_process_block)
            "search" -> stringResource(R.string.search_content_block)
            "status" -> stringResource(R.string.status_info_block)
            "html" -> stringResource(R.string.html_content_block)
            "mood" -> stringResource(R.string.mood_tag_block)
            "font" -> stringResource(R.string.xml_block)
            "details", "detail" -> stringResource(R.string.xml_block)
            else -> stringResource(R.string.tool_call_block)
        }
        
        // 用 Box 包裹所有内容，添加无障碍描述
        if (tagName == "think" || tagName == "thinking") {
            Box(modifier = modifier) {
                RenderXmlContentInternal(trimmedContent, tagName, textColor, xmlStream, renderInstanceKey, Modifier)
            }
        } else {
            Box(modifier = modifier.semantics { contentDescription = accessibilityDesc }) {
                RenderXmlContentInternal(trimmedContent, tagName, textColor, xmlStream, renderInstanceKey, Modifier)
            }
        }
    }
    
    @Composable
    private fun RenderXmlContentInternal(
        trimmedContent: String,
        tagName: String?,
        textColor: Color,
        xmlStream: Stream<String>?,
        renderInstanceKey: Any?,
        modifier: Modifier
    ) {
        val shouldSkipHiddenThink =
            (tagName == "think" || tagName == "thinking") && !showThinkingProcess
        if (shouldSkipHiddenThink) {
            return
        }

        val shouldSkipHiddenStatus =
            if (tagName != "status") {
                false
            } else {
                val typeMatch = ChatMarkupRegex.typeAttr.find(trimmedContent)
                val statusType = typeMatch?.groupValues?.get(1)
                statusType in listOf("completion", "complete", "wait_for_user_need") && !showStatusTags
            }
        if (shouldSkipHiddenStatus) {
            return
        }

        // 如果无法识别为有效的XML标签，则交由默认渲染器处理
        if (tagName == null) {
            fallback.RenderXmlContent(trimmedContent, Modifier, textColor, xmlStream, renderInstanceKey)
            return
        }
        val resolvedTagName = tagName

        // 优先分发到已注册的 XML 渲染插件（例如 deepsearch 的 <plan>）
        val pluginHandled =
            XmlRenderPluginRegistry.RenderIfMatched(
                xmlContent = trimmedContent,
                tagName = resolvedTagName,
                modifier = modifier,
                textColor = textColor,
                xmlStream = xmlStream,
                renderInstanceKey = renderInstanceKey
            )
        if (pluginHandled) {
            return
        }

        // 根据新规则处理未闭合的标签
        val isClosed = isXmlFullyClosed(trimmedContent)
        if (!isClosed) {
            if (resolvedTagName in builtInTags && resolvedTagName != "tool" && resolvedTagName != "think" && resolvedTagName != "thinking" && resolvedTagName != "search") {
                // 是内置标签但未闭合，则不显示任何内容，等待其闭合
                return
            } else if (resolvedTagName !in builtInTags) {
                // 是未知标签且未闭合，则交由默认渲染器处理
                fallback.RenderXmlContent(trimmedContent, Modifier, textColor, xmlStream, renderInstanceKey)
                return
            }
        }

        // 标签已正确闭合，根据标签名分发到对应的渲染函数
        when (resolvedTagName) {
            "think" -> renderThinkContent(trimmedContent, Modifier, textColor, xmlStream)
            "thinking" -> renderThinkContent(trimmedContent, Modifier, textColor, xmlStream)
            "search" -> renderSearchContent(trimmedContent, Modifier, textColor)
            "tool" -> renderToolRequest(trimmedContent, Modifier, textColor)
            "tool_result" -> renderToolResult(trimmedContent, Modifier, textColor)
            "status" -> renderStatus(trimmedContent, Modifier, textColor)
            "html" -> renderHtmlContent(trimmedContent, Modifier, textColor)
            "mood" -> renderMoodTag(trimmedContent, Modifier, textColor)
            "font" -> FontTagRenderer.Render(trimmedContent, Modifier, textColor)
            "details", "detail" -> DetailsTagRenderer.Render(trimmedContent, Modifier, textColor, enableDialogs = enableDialogs)
            else -> fallback.RenderXmlContent(trimmedContent, Modifier, textColor, xmlStream, renderInstanceKey)
        }
    }

    /** 从XML字符串中提取第一个标签的名称。 例如: "<think>...</think>" -> "think" */
    private fun extractTagName(content: String): String? {
        return ChatMarkupRegex.normalizeToolLikeTagName(extractRawTagName(content))
    }

    private fun extractRawTagName(content: String): String? {
        return ChatMarkupRegex.extractOpeningTagName(content)
    }

    /** 检查XML标签是否完全闭合。 支持标准配对标签 (<tag>...</tag>) 和自闭合标签 (<tag/>)。 */
    private fun isXmlFullyClosed(content: String): Boolean {
        val tagName = extractRawTagName(content) ?: return false

        // 处理自闭合标签，例如 <status type="completion"/>
        if (content.endsWith("/>")) {
            return true
        }

        // 处理标准配对标签
        val closeTag = "</$tagName>"
        return content.contains(closeTag)
    }

    /** 从XML内容中提取纯文本内容 */
    private fun extractContentFromXml(content: String, tagName: String? = null): String {
        val rawTagName = extractRawTagName(content) ?: return content
        val effectiveTagName =
            if (tagName != null && ChatMarkupRegex.normalizeToolLikeTagName(rawTagName) != tagName) {
                tagName
            } else {
                rawTagName
            }
        val startTag = "<$effectiveTagName"
        val endTag = "</$effectiveTagName>"

        // 找到开始标签的结束位置
        val startTagEnd = content.indexOf('>', content.indexOf(startTag)) + 1
        val endIndex = content.lastIndexOf(endTag)

        return if (startTagEnd > 0 && endIndex > startTagEnd) {
            content.substring(startTagEnd, endIndex).trim()
        } else {
            // 如果无法正确提取，返回原始内容
            content
        }
    }

    /** 从工具调用XML提取参数内容 */
    private fun extractParamsFromTool(content: String): Map<String, String> {
        val params = mutableMapOf<String, String>()

        // 查找所有参数标签
        val matches = ChatMarkupRegex.toolParamPattern.findAll(content)

        for (match in matches) {
            val name = match.groupValues[1]
            val value = match.groupValues[2].trim()
            params[name] = value
        }

        return params
    }

    private fun resolveToolDisplayNameForRender(toolName: String, params: Map<String, String>): String {
        if (toolName != "package_proxy") {
            return toolName
        }

        val targetToolName = params["tool_name"]
            ?.replace("&quot;", "\"")
            ?.replace("&amp;", "&")
            ?.trim()
            .orEmpty()

        return if (targetToolName.isNotBlank()) targetToolName else toolName
    }

    /** 渲染 <search> 标签内容 (Google Search Grounding 来源) */
    @Composable
    private fun renderSearchContent(content: String, modifier: Modifier, textColor: Color) {
        val startTag = "<search>"
        val endTag = "</search>"
        val startIndex = content.indexOf(startTag) + startTag.length

        // 提取搜索来源内容
        val searchText =
                if (content.contains(endTag)) {
                    val endIndex = content.lastIndexOf(endTag)
                    content.substring(startIndex, endIndex).trim()
                } else {
                    // 没有结束标签，直接使用startIndex后的所有内容
                    content.substring(startIndex).trim()
                }

        var expanded by remember { mutableStateOf(false) }  // 默认收起

        Column(modifier = modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 4.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        expanded = !expanded
                    },
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                val rotation by
                        animateFloatAsState(
                                targetValue = if (expanded) 90f else 0f,
                                animationSpec = tween(durationMillis = 300),
                                label = "arrowRotation"
                        )

                Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (expanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation }
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                        text = stringResource(id = R.string.search_sources),  // 需要添加字符串资源
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(
                    visible = expanded,
                    enter = androidx.compose.animation.fadeIn(animationSpec = tween(200)),
                    exit = androidx.compose.animation.fadeOut(animationSpec = tween(200))
            ) {
                if (searchText.isNotBlank()) {
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 8.dp, start = 24.dp)
                    ) {
                        // 使用 Markdown 渲染器来渲染搜索来源（支持链接等格式）
                        com.ai.assistance.operit.ui.common.displays.MarkdownTextComposable(
                                text = searchText,
                                textColor = textColor.copy(alpha = 0.8f),
                                modifier = Modifier,
                                enableDialogs = enableDialogs
                        )
                    }
                }
            }
        }
    }

    /** 渲染 <think> 和 <thinking> 标签内容 */
    @Composable
    private fun renderThinkContent(
        content: String,
        modifier: Modifier,
        textColor: Color,
        xmlStream: Stream<String>?
    ) {
        val tagName =
            if (content.contains("<thinking")) "thinking" else "think"

        var expandThinkingProcess by rememberLocal(key = "expand_thinking_process_default", defaultValue = false)
        // 仅在"流仍然存在"且标签未闭合时，才判定为进行中。
        // 这样用户取消后（最终消息 contentStream = null）会自动按完成态折叠。
        val isThinkingInProgress = (xmlStream != null) && !isXmlFullyClosed(content)
        val thinkingTitleBaseColor = textColor.copy(alpha = 0.7f)
        val thinkingTitleModifier =
            if (isThinkingInProgress) {
                val titleHighlightTransition =
                    rememberInfiniteTransition(label = "thinkingTitleHighlight")
                val highlightShift by
                    titleHighlightTransition.animateFloat(
                        initialValue = -140f,
                        targetValue = 220f,
                        animationSpec =
                            infiniteRepeatable(
                                animation =
                                    tween(
                                        durationMillis = 1400,
                                        easing = LinearEasing
                                    ),
                                repeatMode = RepeatMode.Restart
                            ),
                        label = "thinkingTitleHighlightShift"
                    )
                val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
                Modifier.graphicsLayer(alpha = 0.99f).drawWithContent {
                    drawContent()
                    drawRect(
                        brush =
                            Brush.linearGradient(
                                colors =
                                    listOf(
                                        thinkingTitleBaseColor.copy(alpha = 0.35f),
                                        highlightColor,
                                        thinkingTitleBaseColor.copy(alpha = 0.35f)
                                    ),
                                start = Offset(highlightShift - 140f, 0f),
                                end = Offset(highlightShift + 140f, size.height)
                            ),
                        blendMode = BlendMode.SrcAtop
                    )
                }
            } else {
                Modifier
            }

        var expanded by remember { mutableStateOf(false) }
        var thinkExpandSession by remember { mutableIntStateOf(0) }
        var skipCollapseAnimationOnce by remember { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        var autoScrollEnabled by remember { mutableStateOf(true) }
        var userHasInteractedWithScroll by remember { mutableStateOf(false) }
        var isProgrammaticScroll by remember { mutableStateOf(false) }
        val thinkVisibilityState = remember { MutableTransitionState(false) }

        val accessibilityDesc = stringResource(R.string.thinking_process_block)

        // 使用LaunchedEffect来初始化和同步状态，避免在快速重组时状态被意外重置
        LaunchedEffect(isThinkingInProgress, expandThinkingProcess) {
            val targetExpanded = if (isThinkingInProgress) {
                // 思考过程中，状态由用户偏好决定
                expandThinkingProcess
            } else {
                // 思考结束后，总是折叠
                skipCollapseAnimationOnce = true
                false
            }
            if (targetExpanded && !expanded) {
                thinkExpandSession += 1
            }
            expanded = targetExpanded
        }

        LaunchedEffect(expanded, skipCollapseAnimationOnce) {
            if (!expanded && skipCollapseAnimationOnce) {
                // 仅跳过一次自动收起动画，随后恢复手动交互动画
                skipCollapseAnimationOnce = false
            }
        }

        LaunchedEffect(expanded) {
            thinkVisibilityState.targetState = expanded
        }

        LaunchedEffect(expanded) {
            if (expanded) {
                autoScrollEnabled = true
                userHasInteractedWithScroll = false
            }
        }

        LaunchedEffect(expanded, scrollState.isScrollInProgress) {
            if (!expanded) return@LaunchedEffect

            if (scrollState.isScrollInProgress) {
                if (!isProgrammaticScroll) {
                    userHasInteractedWithScroll = true
                }
                return@LaunchedEffect
            }

            // 只有用户真的滚动过之后，才根据是否在底部来切换“自动贴底”。
            // 否则在内容第一次溢出时 scrollState.value 仍为 0，会被误判为“不在底部”。
            if (userHasInteractedWithScroll && !isProgrammaticScroll) {
                val threshold = 80
                val atBottom = scrollState.value >= (scrollState.maxValue - threshold)
                autoScrollEnabled = atBottom
            }
        }

        val shouldComposeThinkBody =
            thinkVisibilityState.currentState || thinkVisibilityState.targetState
        val thinkText =
            if (shouldComposeThinkBody) {
                extractContentFromXml(content, tagName).trim()
            } else {
                ""
            }
        val thinkMarkdownStream =
            remember(shouldComposeThinkBody, thinkExpandSession, xmlStream, tagName) {
                if (!shouldComposeThinkBody || thinkExpandSession <= 0) {
                    null
                } else {
                    xmlStream?.let { createThinkMarkdownCharStream(it, tagName) }
                }
            }
        val useStreamingThinkMarkdown = shouldComposeThinkBody && isThinkingInProgress && (thinkMarkdownStream != null)

        LaunchedEffect(expanded, thinkText) {
            if (shouldComposeThinkBody && autoScrollEnabled) {
                isProgrammaticScroll = true
                try {
                    withFrameNanos { }
                    scrollState.scrollTo(scrollState.maxValue)
                } finally {
                    isProgrammaticScroll = false
                }
            }
        }

        Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 2.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (expanded && thinkText.isNotBlank()) {
                            "$accessibilityDesc\n$thinkText"
                        } else {
                            accessibilityDesc
                        }
                    }
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            // 用户手动交互时始终保留动画
                            skipCollapseAnimationOnce = false
                            val newExpandedValue = !expanded
                            if (newExpandedValue) {
                                thinkExpandSession += 1
                            }
                            expanded = newExpandedValue
                            if (isThinkingInProgress) {
                                expandThinkingProcess = newExpandedValue
                            }
                        },
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    val rotation by
                            animateFloatAsState(
                                    targetValue = if (expanded) 90f else 0f,
                                    animationSpec =
                                        if (skipCollapseAnimationOnce && !expanded) snap()
                                        else tween(durationMillis = 300),
                                    label = "arrowRotation"
                            )

                    Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (expanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                            text = stringResource(id = R.string.thinking_process),
                            style = MaterialTheme.typography.labelMedium,
                            color = thinkingTitleBaseColor,
                            modifier = thinkingTitleModifier
                    )
                }

                AnimatedVisibility(
                    visibleState = thinkVisibilityState,
                    enter =
                        androidx.compose.animation.expandVertically(
                            animationSpec = tween(durationMillis = 220),
                            expandFrom = Alignment.Top
                        ) + androidx.compose.animation.fadeIn(
                            animationSpec = tween(durationMillis = 220)
                        ),
                    exit =
                        if (skipCollapseAnimationOnce && !expanded) {
                            ExitTransition.None
                        } else {
                            androidx.compose.animation.shrinkVertically(
                                animationSpec = tween(durationMillis = 220),
                                shrinkTowards = Alignment.Top
                            ) + androidx.compose.animation.fadeOut(
                                animationSpec = tween(durationMillis = 220)
                            )
                        }
                ) {
                    if (shouldComposeThinkBody && (thinkText.isNotBlank() || thinkMarkdownStream != null)) {
                            val hierarchyLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                            Box(
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(top = 2.dp, bottom = 4.dp)
                                        .animateContentSize(
                                            animationSpec = tween(durationMillis = 240)
                                        )
                                        .heightIn(max = 300.dp)
                            ) {
                                Box(
                                    modifier =
                                        Modifier.matchParentSize()
                                            .padding(start = 10.dp, top = 1.dp, bottom = 1.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Box(
                                        modifier =
                                            Modifier.fillMaxHeight()
                                                .width(1.dp)
                                                .background(
                                                    brush =
                                                        Brush.verticalGradient(
                                                            colorStops =
                                                                arrayOf(
                                                                    0f to Color.Transparent,
                                                                    0.16f to hierarchyLineColor,
                                                                    0.84f to hierarchyLineColor,
                                                                    1f to Color.Transparent
                                                                )
                                                        )
                                                )
                                    )
                                }

                                Box(
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .verticalScroll(scrollState)
                                            .padding(start = 24.dp)
                                ) {
                                    if (useStreamingThinkMarkdown) {
                                            val baseTypography = MaterialTheme.typography
                                            val thinkTypography =
                                                remember(baseTypography) {
                                                    baseTypography.copy(
                                                        bodyMedium = baseTypography.bodySmall
                                                    )
                                                }
                                            MaterialTheme(
                                                colorScheme = MaterialTheme.colorScheme,
                                                typography = thinkTypography,
                                                shapes = MaterialTheme.shapes
                                            ) {
                                                StreamMarkdownRenderer(
                                                    markdownStream = thinkMarkdownStream!!,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textColor = textColor.copy(alpha = 0.6f),
                                                    backgroundColor = Color.Transparent,
                                                    enableDialogs = enableDialogs,
                                                    fillMaxWidth = true
                                                )
                                            }
                                    } else if (thinkText.isNotBlank()) {
                                            val baseTypography = MaterialTheme.typography
                                            val thinkTypography =
                                                remember(baseTypography) {
                                                    baseTypography.copy(
                                                        bodyMedium = baseTypography.bodySmall
                                                    )
                                                }
                                            MaterialTheme(
                                                colorScheme = MaterialTheme.colorScheme,
                                                typography = thinkTypography,
                                                shapes = MaterialTheme.shapes
                                            ) {
                                                StreamMarkdownRenderer(
                                                    content = thinkText,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textColor = textColor.copy(alpha = 0.6f),
                                                    backgroundColor = Color.Transparent,
                                                    enableDialogs = enableDialogs,
                                                    fillMaxWidth = true
                                                )
                                            }
                                    } else {
                                        Text(
                                            text = thinkText,
                                            color = textColor.copy(alpha = 0.6f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                    }
                }
            }
    }

    private fun createThinkMarkdownCharStream(
        xmlStream: Stream<String>,
        tagName: String
    ): Stream<Char> = stream {
        val endTag = "</$tagName>"
        var startTagClosed = false
        var reachedEndTag = false
        val tailBuffer = StringBuilder()

        xmlStream.collect { chunk ->
            chunk.forEach { ch ->
                if (reachedEndTag) return@forEach

                if (!startTagClosed) {
                    if (ch == '>') {
                        startTagClosed = true
                    }
                    return@forEach
                }

                tailBuffer.append(ch)

                while (tailBuffer.length > endTag.length) {
                    emit(tailBuffer[0])
                    tailBuffer.deleteCharAt(0)
                }

                if (tailBuffer.length == endTag.length && tailBuffer.toString() == endTag) {
                    tailBuffer.setLength(0)
                    reachedEndTag = true
                }
            }
        }

        if (!reachedEndTag && tailBuffer.isNotEmpty()) {
            tailBuffer.toString().forEach { emit(it) }
        }
    }

    /** 渲染标准工具请求标签 <tool name="..."><param name="param_name">param_value</param></tool> */
    @Composable
    private fun renderToolRequest(content: String, modifier: Modifier, textColor: Color) {
        val renderState =
            run {
                val nameRegex = "name=\"([^\"]+)\"".toRegex()
                val nameMatch = nameRegex.find(content)
                val rawToolName = nameMatch?.groupValues?.get(1) ?: "Unknown tool"
                val params = extractParamsFromTool(content)
                val paramText = extractContentFromXml(content, "tool").trim()
                val displayToolName = resolveToolDisplayNameForRender(rawToolName, params)
                val contentLengthThreshold = 200

                ToolRequestRenderState(
                    rawToolName = rawToolName,
                    paramText = paramText,
                    displayToolName = displayToolName,
                    isLongContent = paramText.length > contentLengthThreshold,
                    isClosed = isXmlFullyClosed(content),
                )
            }

        // 特殊处理 apply_file 工具
        if (renderState.displayToolName == "apply_file") {
            if (renderState.isClosed) {
                CompactToolDisplay(
                    toolName = renderState.rawToolName,
                    params = renderState.paramText,
                    textColor = textColor,
                    modifier = modifier,
                    enableDialog = enableDialogs
                )
            } else {
                DetailedToolDisplay(
                    toolName = renderState.rawToolName,
                    params = renderState.paramText,
                    textColor = textColor,
                    modifier = modifier,
                    enableDialog = enableDialogs
                )
            }
        } else {
            // 对于其他工具，保持原有逻辑
            if (renderState.isLongContent) {
                DetailedToolDisplay(
                        toolName = renderState.rawToolName,
                        params = renderState.paramText,
                        textColor = textColor,
                        modifier = modifier,
                        enableDialog = enableDialogs  // 传递弹窗启用状态
                )
            } else {
                CompactToolDisplay(
                        toolName = renderState.rawToolName,
                        params = renderState.paramText,
                        textColor = textColor,
                        modifier = modifier,
                        enableDialog = enableDialogs  // 传递弹窗启用状态
                )
            }
        }
    }

    /** 渲染工具结果标签 <tool_result name="..." status="..."><content>...</content></tool_result> */
    @Composable
    private fun renderToolResult(content: String, _modifier: Modifier, _textColor: Color) {
        val clipboardManager = LocalClipboardManager.current

        val renderState =
            run {
                val nameMatch = ChatMarkupRegex.nameAttr.find(content)
                val toolName = nameMatch?.groupValues?.get(1) ?: ""
                val statusMatch = ChatMarkupRegex.statusAttr.find(content)
                val status = statusMatch?.groupValues?.get(1) ?: "success"
                val contentMatch = ChatMarkupRegex.contentTag.find(content)
                val resultContent = contentMatch?.groupValues?.get(1)?.trim() ?: ""

                ToolResultRenderState(
                    toolName = toolName,
                    isSuccess = status.lowercase() == "success",
                    resultContent = resultContent,
                )
            }
        val toolName = renderState.toolName.ifBlank { stringResource(R.string.unknown_tool) }

        // 检查结果是否为 file-diff
        if (toolName == "apply_file" && renderState.isSuccess && renderState.resultContent.contains("<file-diff")) {
            val (path, details, unescapedDiffContent) =
                run {
                    val pathRegex = "<file-diff path=\"([^\"]+)\"".toRegex()
                    val detailsRegex = "details=\"([^\"]+)\"".toRegex()
                    val cdataRegex = "<!\\[CDATA\\[(.*?)\\]\\]>".toRegex(RegexOption.DOT_MATCHES_ALL)

                    val path = pathRegex.find(renderState.resultContent)?.groupValues?.get(1) ?: ""
                    val details = detailsRegex.find(renderState.resultContent)?.groupValues?.get(1) ?: ""
                    val diffContent = cdataRegex.find(renderState.resultContent)?.groupValues?.get(1)?.trim() ?: ""

                    Triple(
                        path,
                        details,
                        diffContent
                            .replace("<", "<")
                            .replace(">", ">")
                            .replace("&", "&"),
                    )
                }

            FileDiffDisplay(diff = FileDiff(path, unescapedDiffContent, details))
        } else {
            // 如果是错误状态，尝试提取错误信息
            val errorContent =
                    if (!renderState.isSuccess) {
                        val errorMatch = ChatMarkupRegex.errorTag.find(renderState.resultContent)
                        errorMatch?.groupValues?.get(1)?.trim() ?: renderState.resultContent
                    } else {
                        // 从结果中移除 file-diff 块（如果存在）
                        val fileDiffRegex = """<file-diff.*</file-diff>""".toRegex(RegexOption.DOT_MATCHES_ALL)
                        renderState.resultContent.replace(fileDiffRegex, "").trim()
                    }

            // 使用ToolResultDisplay组件显示结果
            ToolResultDisplay(
                    toolName = toolName,
                    result = errorContent,
                    isSuccess = renderState.isSuccess,
                    onCopyResult = {
                        if (errorContent.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(errorContent))
                        }
                    },
                    enableDialog = enableDialogs  // 传递弹窗启用状态
            )
        }
    }

    /** 渲染状态信息标签 <status type="..." tool="..." uuid="..." title="..." subtitle="...">...</status> */
    @Composable
    private fun renderStatus(content: String, modifier: Modifier, textColor: Color) {
        val (statusType, statusContent, statusText) =
            run {
                val typeMatch = ChatMarkupRegex.typeAttr.find(content)
                val statusType = typeMatch?.groupValues?.get(1) ?: "info"
                val statusContent =
                    if (statusType !in listOf("completion", "complete", "wait_for_user_need")) {
                        extractContentFromXml(content, "status")
                    } else {
                        ""
                    }
                val statusText =
                    when (statusType) {
                        "completion", "complete" -> "✓ Task completed"
                        "wait_for_user_need" -> "✓ Ready for further assistance"
                        "warning" ->
                            if (statusContent.isNotBlank()) statusContent
                            else "Warning: AI made a mistake"
                        else -> statusContent
                    }

                Triple(statusType, statusContent, statusText)
            }

        // 非工具相关的状态信息
        val bgColor =
                when (statusType) {
                    "completion", "complete" ->
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    "wait_for_user_need" ->
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    "warning" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                }

        val borderColor =
                when (statusType) {
                    "completion", "complete" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    "wait_for_user_need" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                    "warning" -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }

        Card(
                modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = bgColor),
                border = BorderStroke(width = 1.dp, color = borderColor),
                shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color =
                            when (statusType) {
                                "completion", "complete" -> MaterialTheme.colorScheme.primary
                                "wait_for_user_need" -> MaterialTheme.colorScheme.tertiary
                                "warning" -> MaterialTheme.colorScheme.error
                                else -> textColor
                            },
                    modifier = Modifier.padding(12.dp),
                    maxLines = if (statusType == "warning") 1 else Int.MAX_VALUE
            )
        }
    }

    /** 渲染 <html> 标签内容 - 使用WebView渲染，支持完整的HTML/CSS功能 */
    @Composable
    private fun renderHtmlContent(content: String, modifier: Modifier, textColor: Color) {
        // 提取html内部的HTML内容
        val htmlContent = extractContentFromXml(content, "html")
        
        // 提取class属性
        val classRegex = "class=\"([^\"]+)\"".toRegex()
        val classMatch = classRegex.find(content)
        val className = classMatch?.groupValues?.get(1)
        
        // 提取color属性 - 用于自定义卡片主题色
        val colorRegex = "color=\"([^\"]+)\"".toRegex()
        val colorMatch = colorRegex.find(content)
        val customColor = colorMatch?.groupValues?.get(1)
        
        // 如果内容不为空，则作为HTML渲染
        if (htmlContent.isNotBlank()) {
            val context = LocalContext.current
            
            // 应用内置样式
            val styledHtml = applyBuiltInStyles(htmlContent, className, customColor, textColor)
            
            // 构建完整的HTML文档
            val fullHtml = buildFullHtmlDocument(styledHtml, textColor)
            
            AndroidView(
                modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = false
                            domStorageEnabled = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = false
                            displayZoomControls = false
                        }
                        // 使用软件渲染层，这有助于解决在Compose中嵌入WebView时可能出现的渲染线程崩溃问题，
                        // 尤其是在涉及硬件加速的复杂视图层级中。
                        setLayerType(WebView.LAYER_TYPE_SOFTWARE, null)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(null, fullHtml, "text/html", "UTF-8", null)
                },
                onRelease = { webView ->
                    // 在视图被销毁时，明确调用destroy()以释放WebView资源，防止内存泄漏和崩溃。
                    webView.destroy()
                }
            )
        }
    }
    
    /**
     * 构建完整的HTML文档，包含CSS样式
     */
    private fun buildFullHtmlDocument(bodyContent: String, textColor: Color): String {
        val textColorHex = String.format("#%06X", 0xFFFFFF and textColor.toArgb())
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200" />
                <style>
                    * {
                        margin: 0;
                        padding: 0;
                        box-sizing: border-box;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
                        font-size: 13px;
                        line-height: 1.4;
                        color: $textColorHex;
                        padding: 0;
                        background: transparent;
                    }
                    .material-symbols-rounded {
                        font-family: 'Material Symbols Rounded';
                        font-weight: normal;
                        font-style: normal;
                        font-size: 20px;
                        display: inline-block;
                        line-height: 1;
                        text-transform: none;
                        letter-spacing: normal;
                        word-wrap: normal;
                        white-space: nowrap;
                        direction: ltr;
                        font-variation-settings: 'FILL' 1, 'wght' 400, 'GRAD' 0, 'opsz' 24;
                    }
                    h1, h2, h3, h4, h5, h6 {
                        margin: 2px 0 3px 0;
                        font-weight: 600;
                        line-height: 1.3;
                        color: inherit;
                    }
                    h1 { font-size: 15px; }
                    h2 { font-size: 14px; }
                    h3 { font-size: 13px; }
                    h4 { font-size: 13px; }
                    h5 { font-size: 12px; }
                    h6 { font-size: 12px; }
                    p {
                        margin: 2px 0;
                        font-size: 13px;
                    }
                    a {
                        color: #007AFF;
                        text-decoration: none;
                    }
                    a:hover {
                        text-decoration: underline;
                    }
                    strong, b {
                        font-weight: 600;
                    }
                </style>
            </head>
            <body>
                $bodyContent
            </body>
            </html>
        """.trimIndent()
    }
    
    /**
     * 应用内置样式到HTML内容
     * 支持的class类型：
     * - status-card: 状态卡片样式
     * - info-card: 信息卡片样式
     * - warning-card: 警告卡片样式
     * - success-card: 成功卡片样式
     * - metric-grid: 指标网格布局
     * - badge: 徽章样式
     * - progress-bar: 进度条
     * 
     * @param customColor 自定义颜色（十六进制，如 #FF2D55），会覆盖默认的卡片主题色
     */
    private fun applyBuiltInStyles(htmlContent: String, className: String?, customColor: String?, textColor: Color): String {
        // 如果没有指定class，直接返回原内容
        if (className == null) return htmlContent
        
        // 先处理内容中的内置组件（递归处理）
        var processedContent = processInlineComponents(htmlContent)
        
        // 根据不同的class应用不同的样式
        return when (className) {
            "status-card" -> applyStatusCardStyle(processedContent, customColor)
            "info-card" -> applyInfoCardStyle(processedContent, customColor)
            "warning-card" -> applyWarningCardStyle(processedContent, customColor)
            "success-card" -> applySuccessCardStyle(processedContent, customColor)
            "metric-grid" -> applyMetricGridStyle(processedContent)
            else -> processedContent
        }
    }
    
    /**
     * 处理内联组件标签，将自定义标签转换为带样式的HTML
     * 支持的组件：
     * - <metric label="xxx" value="yyy" />
     * - <badge type="xxx">text</badge>
     * - <progress value="80" />
     */
    private fun processInlineComponents(content: String): String {
        var result = content
        
        // 处理 <metric> 标签
        result = processMetricTags(result)
        
        // 处理 <badge> 标签
        result = processBadgeTags(result)
        
        // 处理 <progress> 标签
        result = processProgressTags(result)
        
        return result
    }
    
    /** 处理 <metric label="标签" value="值" icon="icon_name" color="#xxx" /> */
    private fun processMetricTags(content: String): String {
        val metricRegex = """<metric\s+label="([^"]+)"\s+value="([^"]+)"(?:\s+icon="([^"]+)")?(?:\s+color="([^"]+)")?\s*/>""".toRegex()
        return metricRegex.replace(content) { matchResult ->
            val label = matchResult.groupValues[1]
            val value = matchResult.groupValues[2]
            val iconName = matchResult.groupValues.getOrNull(3) ?: "analytics"
            val color = matchResult.groupValues.getOrNull(4) ?: "#007AFF"
            
            // 将十六进制颜色转换为 RGB 值，用于生成半透明背景
            val rgb = hexToRgb(color)
            val bgGradient = "linear-gradient(135deg, rgba($rgb, 0.08) 0%, rgba($rgb, 0.04) 100%)"
            val borderColor = "rgba($rgb, 0.15)"
            
            """
            <div style="
                display: inline-flex;
                align-items: center;
                gap: 4px;
                margin: 0 4px 4px 0;
                padding: 4px 8px;
                background: $bgGradient;
                border-radius: 8px;
                border: 1px solid $borderColor;
                min-width: 60px;
                box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);">
                <span class="material-symbols-rounded" style="font-size: 14px; color: $color;">$iconName</span>
                <div style="display: flex; flex-direction: column; gap: 0;">
                    <div style="font-size: 8px; color: rgba(120, 120, 128, 0.65); font-weight: 500; letter-spacing: 0.2px; text-transform: uppercase;">$label</div>
                    <div style="font-size: 11px; font-weight: 600; color: inherit; letter-spacing: -0.1px; line-height: 1.1;">$value</div>
                </div>
            </div>
            """.trimIndent()
        }
    }
    
    /** 将十六进制颜色 (#RRGGBB) 转换为 RGB 字符串 "r, g, b" */
    private fun hexToRgb(hex: String): String {
        val cleanHex = hex.removePrefix("#")
        return try {
            val r = cleanHex.substring(0, 2).toInt(16)
            val g = cleanHex.substring(2, 4).toInt(16)
            val b = cleanHex.substring(4, 6).toInt(16)
            "$r, $g, $b"
        } catch (e: Exception) {
            // 如果解析失败，默认返回蓝色
            "0, 122, 255"
        }
    }
    
    /** 处理 <badge type="success|info|warning|error" icon="icon_name">文本</badge> */
    private fun processBadgeTags(content: String): String {
        val badgeRegex = """<badge(?:\s+type="([^"]+)")?(?:\s+icon="([^"]+)")?>([^<]+)</badge>""".toRegex()
        return badgeRegex.replace(content) { matchResult ->
            val type = matchResult.groupValues.getOrNull(1) ?: "info"
            val iconName = matchResult.groupValues.getOrNull(2)
            val text = matchResult.groupValues[3]
            
            val (bgColor, textColor, borderColor) = when (type) {
                "success" -> Triple("rgba(52, 199, 89, 0.15)", "#34C759", "rgba(52, 199, 89, 0.3)")
                "warning" -> Triple("rgba(255, 159, 10, 0.15)", "#FF9F0A", "rgba(255, 159, 10, 0.3)")
                "error" -> Triple("rgba(255, 69, 58, 0.15)", "#FF453A", "rgba(255, 69, 58, 0.3)")
                else -> Triple("rgba(120, 120, 128, 0.12)", "rgba(120, 120, 128, 0.9)", "rgba(120, 120, 128, 0.25)")
            }
            
            val iconHtml = if (iconName != null) {
                """<span class="material-symbols-rounded" style="font-size: 11px; margin-right: 2px;">$iconName</span>"""
            } else ""
            
            """<span style="
                display: inline-flex;
                align-items: center;
                padding: 2px 6px;
                margin: 0 2px;
                background: $bgColor;
                color: $textColor;
                border: 1px solid $borderColor;
                border-radius: 5px;
                font-size: 10px;
                font-weight: 600;
                letter-spacing: 0.1px;
                box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
                ">$iconHtml$text</span>"""
        }
    }
    
    /** 处理 <progress value="80" label="能量" /> */
    private fun processProgressTags(content: String): String {
        val progressRegex = """<progress\s+value="([^"]+)"(?:\s+label="([^"]+)")?\s*/>""".toRegex()
        return progressRegex.replace(content) { matchResult ->
            val valueStr = matchResult.groupValues[1]
            val label = matchResult.groupValues.getOrNull(2) ?: ""
            val value = valueStr.toIntOrNull() ?: 0
            val clampedValue = value.coerceIn(0, 100)
            
            val barColor = when {
                clampedValue >= 80 -> "#34C759"
                clampedValue >= 50 -> "#007AFF"
                clampedValue >= 30 -> "#FF9F0A"
                else -> "#FF453A"
            }
            
            val labelHtml = if (label.isNotEmpty()) {
                """<div style="font-size: 8px; color: rgba(120, 120, 128, 0.65); margin-bottom: 2px; font-weight: 500;">$label</div>"""
            } else ""
            
            """
            <div style="margin: 3px 0;">
                $labelHtml
                <div style="
                    width: 100%;
                    height: 2px;
                    background-color: rgba(120, 120, 128, 0.1);
                    border-radius: 1px;
                    overflow: hidden;">
                    <div style="
                        width: ${clampedValue}%;
                        height: 100%;
                        background: $barColor;
                        border-radius: 1px;
                        transition: width 0.3s ease;"></div>
                </div>
                <div style="font-size: 8px; color: rgba(120, 120, 128, 0.55); margin-top: 1px; text-align: right; font-weight: 500;">$clampedValue%</div>
            </div>
            """.trimIndent()
        }
    }
    
    /** 状态卡片样式 - 现代渐变设计 */
    private fun applyStatusCardStyle(content: String, customColor: String? = null): String {
        // 如果提供了自定义颜色，使用自定义颜色；否则使用默认蓝紫渐变
        val rgb = if (customColor != null) hexToRgb(customColor) else null
        
        val bgGradient = if (rgb != null) {
            "linear-gradient(135deg, rgba($rgb, 0.1) 0%, rgba($rgb, 0.08) 100%)"
        } else {
            "linear-gradient(135deg, rgba(0, 122, 255, 0.1) 0%, rgba(88, 86, 214, 0.08) 100%)"
        }
        
        val borderColor = if (rgb != null) {
            "rgba($rgb, 0.2)"
        } else {
            "rgba(0, 122, 255, 0.2)"
        }
        
        val shadowColor = if (rgb != null) {
            "0 2px 8px rgba($rgb, 0.06), 0 1px 3px rgba(0, 0, 0, 0.04)"
        } else {
            "0 2px 8px rgba(0, 122, 255, 0.06), 0 1px 3px rgba(0, 0, 0, 0.04)"
        }
        
        return """
            <div style="
                background: $bgGradient;
                padding: 10px 12px;
                border-radius: 12px;
                margin: 4px 0;
                border: 1px solid $borderColor;
                box-shadow: $shadowColor;
                backdrop-filter: blur(8px);">
                $content
            </div>
        """.trimIndent()
    }
    
    /** 信息卡片样式 - 中性色调玻璃态 */
    private fun applyInfoCardStyle(content: String, customColor: String? = null): String {
        val rgb = if (customColor != null) hexToRgb(customColor) else "120, 120, 128"
        
        return """
            <div style="
                background: linear-gradient(135deg, rgba($rgb, 0.1) 0%, rgba($rgb, 0.06) 100%);
                padding: 10px 12px;
                border-radius: 12px;
                margin: 4px 0;
                border: 1px solid rgba($rgb, 0.18);
                box-shadow: 0 2px 8px rgba($rgb, 0.05), 0 1px 3px rgba(0, 0, 0, 0.03);
                backdrop-filter: blur(8px);">
                $content
            </div>
        """.trimIndent()
    }
    
    /** 警告卡片样式 - 橙色渐变 */
    private fun applyWarningCardStyle(content: String, customColor: String? = null): String {
        val rgb = if (customColor != null) hexToRgb(customColor) else "255, 159, 10"
        
        return """
            <div style="
                background: linear-gradient(135deg, rgba($rgb, 0.12) 0%, rgba($rgb, 0.08) 100%);
                padding: 10px 12px;
                border-radius: 12px;
                margin: 4px 0;
                border: 1px solid rgba($rgb, 0.25);
                box-shadow: 0 2px 8px rgba($rgb, 0.08), 0 1px 3px rgba(0, 0, 0, 0.04);
                backdrop-filter: blur(8px);">
                $content
            </div>
        """.trimIndent()
    }
    
    /** 成功卡片样式 - 绿色渐变 */
    private fun applySuccessCardStyle(content: String, customColor: String? = null): String {
        val rgb = if (customColor != null) hexToRgb(customColor) else "52, 199, 89"
        
        return """
            <div style="
                background: linear-gradient(135deg, rgba($rgb, 0.12) 0%, rgba($rgb, 0.08) 100%);
                padding: 10px 12px;
                border-radius: 12px;
                margin: 4px 0;
                border: 1px solid rgba($rgb, 0.25);
                box-shadow: 0 2px 8px rgba($rgb, 0.08), 0 1px 3px rgba(0, 0, 0, 0.04);
                backdrop-filter: blur(8px);">
                $content
            </div>
        """.trimIndent()
    }
    
    /** 指标网格样式 - 用于展示多个指标 */
    private fun applyMetricGridStyle(content: String): String {
        return """
            <div style="
                display: flex;
                flex-wrap: wrap;
                gap: 0;
                margin: 0 -4px;">
                $content
            </div>
        """.trimIndent()
    }

    /** 
     * 渲染 <mood> 标签 - 这是一个虚拟形象动画触发器，不应在聊天界面显示
     * 
     * mood标签的格式: <mood>happy</mood>、<mood>angry</mood>、
     * 或用户在助手配置里自定义的任意 trigger key（例如 <mood>sleepy</mood>）。
     * 
     * 注意：此标签不会在UI中渲染任何内容。虚拟形象的情感控制
     * 是在虚拟形象控制器中通过 trigger / emotion 映射实现的。
     */
    @Composable
    private fun renderMoodTag(_content: String, _modifier: Modifier, _textColor: Color) {
        // mood标签不显示任何内容
        // 它只是作为一个标记存在于文本中，供虚拟形象系统解析
        // 实际的情感触发由 AvatarEmotionManager.analyzeEmotion() 处理
    }
}

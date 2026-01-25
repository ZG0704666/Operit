package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.common.markdown.MarkdownGroupedItem
import com.ai.assistance.operit.ui.common.markdown.MarkdownNodeGrouper
import com.ai.assistance.operit.ui.common.markdown.XmlContentRenderer
import com.ai.assistance.operit.util.ChatMarkupRegex
import com.ai.assistance.operit.util.markdown.MarkdownNodeStable
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType

class ThinkToolsXmlNodeGrouper(
    private val showThinkingProcess: Boolean
) : MarkdownNodeGrouper {

    override fun group(nodes: List<MarkdownNodeStable>, rendererId: String): List<MarkdownGroupedItem> {
        if (!showThinkingProcess) {
            return nodes.indices.map { MarkdownGroupedItem.Single(it) }
        }

        val out = ArrayList<MarkdownGroupedItem>(nodes.size)
        var i = 0
        while (i < nodes.size) {
            val node = nodes[i]
            if (node.type != MarkdownProcessorType.XML_BLOCK) {
                out.add(MarkdownGroupedItem.Single(i))
                i++
                continue
            }

            val tag = extractXmlTagName(node.content)
            if (tag != "think" && tag != "thinking") {
                out.add(MarkdownGroupedItem.Single(i))
                i++
                continue
            }

            var j = i + 1
            var toolCount = 0
            while (j < nodes.size) {
                val next = nodes[j]
                // 允许 think 与 tool/tool_result 之间出现纯空白文本（通常是换行）
                if (next.type == MarkdownProcessorType.PLAIN_TEXT && next.content.isBlank()) {
                    j++
                    continue
                }
                if (next.type != MarkdownProcessorType.XML_BLOCK) break

                val nextTag = extractXmlTagName(next.content)
                val isThinkAgain = nextTag == "think" || nextTag == "thinking"
                val isToolRelated = nextTag == "tool" || nextTag == "tool_result"
                if (!isThinkAgain && !isToolRelated) break

                if (isToolRelated) {
                    val toolName = extractToolName(next.content)
                    if (!shouldGroupToolByName(toolName)) break

                    if (nextTag == "tool") toolCount++
                }

                j++
            }

            if (toolCount > 0) {
                out.add(
                    MarkdownGroupedItem.Group(
                        startIndex = i,
                        endIndexInclusive = j - 1,
                        stableKey = "think-tools-$i"
                    )
                )
                i = j
            } else {
                out.add(MarkdownGroupedItem.Single(i))
                i++
            }
        }

        return out
    }

    @Composable
    override fun RenderGroup(
        group: MarkdownGroupedItem.Group,
        nodes: List<MarkdownNodeStable>,
        rendererId: String,
        isVisible: Boolean,
        isLastNode: Boolean,
        modifier: Modifier,
        textColor: Color,
        onLinkClick: ((String) -> Unit)?,
        xmlRenderer: XmlContentRenderer,
        fillMaxWidth: Boolean
    ) {
        val alpha by animateFloatAsState(
            targetValue = if (isVisible) 1f else 0f,
            animationSpec = tween(durationMillis = 800),
            label = "fadeIn-think-tools-$rendererId"
        )

        val sliceEndExclusive = (group.endIndexInclusive + 1).coerceAtMost(nodes.size)
        val slice = if (group.startIndex in 0 until sliceEndExclusive) {
            nodes.subList(group.startIndex, sliceEndExclusive)
        } else {
            emptyList()
        }

        val toolCount = slice.count {
            it.type == MarkdownProcessorType.XML_BLOCK && extractXmlTagName(it.content) == "tool"
        }

        val isInProgress = slice.any {
            it.type == MarkdownProcessorType.XML_BLOCK && !isXmlFullyClosed(it.content)
        }

        var expanded by remember(rendererId, group.stableKey) { mutableStateOf(isInProgress || isLastNode) }
        var userOverride by remember(rendererId, group.stableKey) { mutableStateOf<Boolean?>(null) }
        val appearedKeys = remember(rendererId, group.stableKey) { mutableStateMapOf<String, Boolean>() }

        LaunchedEffect(isInProgress, isLastNode, userOverride) {
            if (userOverride != null) return@LaunchedEffect
            expanded = isInProgress || isLastNode
        }

        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 4.dp)
                    .graphicsLayer { this.alpha = alpha }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newExpanded = !expanded
                        expanded = newExpanded
                        userOverride = newExpanded
                    },
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 90f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "arrowRotation-think-tools-$rendererId"
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation }
                )

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = stringResource(
                        id = R.string.thinking_tools_group_title_with_count,
                        toolCount
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(top = 4.dp, bottom = 8.dp, start = 24.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        slice.forEachIndexed { idx, node ->
                            val absoluteIndex = group.startIndex + idx
                            val innerKey = "think-tools-$rendererId-${group.stableKey}-$absoluteIndex"
                            androidx.compose.runtime.key(innerKey) {
                                if (node.type == MarkdownProcessorType.XML_BLOCK) {
                                    val alreadyAppeared = appearedKeys[innerKey] == true
                                    var itemVisible by remember(innerKey, alreadyAppeared) {
                                        mutableStateOf(alreadyAppeared)
                                    }

                                    LaunchedEffect(innerKey) {
                                        if (!alreadyAppeared) {
                                            itemVisible = true
                                            appearedKeys[innerKey] = true
                                        }
                                    }

                                    val itemAlpha by animateFloatAsState(
                                        targetValue = if (itemVisible) 1f else 0f,
                                        animationSpec = tween(durationMillis = 800),
                                        label = "fadeIn-$innerKey"
                                    )

                                    xmlRenderer.RenderXmlContent(
                                        xmlContent = node.content,
                                        modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = itemAlpha },
                                        textColor = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun extractXmlTagName(xml: String): String? {
    val openTagRegex = "<([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
    return openTagRegex.find(xml.trim())?.groupValues?.getOrNull(1)
}

private fun extractToolName(xml: String): String? {
    val nameMatch = ChatMarkupRegex.nameAttr.find(xml)
    return nameMatch?.groupValues?.getOrNull(1)
}

private fun isXmlFullyClosed(xml: String): Boolean {
    val tagName = extractXmlTagName(xml) ?: return false
    val trimmed = xml.trim()
    if (trimmed.endsWith("/>") || trimmed.startsWith("<$tagName") && trimmed.endsWith("/>") ) {
        return true
    }
    return trimmed.contains("</$tagName>")
}

private fun shouldGroupToolByName(toolName: String?): Boolean {
    val n = toolName?.trim()?.lowercase() ?: return true
    return n != "apply_file" && n != "delete_file"
}

@file:Suppress(
    "FunctionNaming",
    "ktlint:standard:function-naming",
    "LongMethod",
)

package com.clibeats.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clibeats.presentation.theme.CliBeatsBorder
import com.clibeats.presentation.theme.CliBeatsTextSecondary
import com.clibeats.presentation.theme.LocalAccentColor
import kotlin.math.max
import kotlin.math.roundToInt

private val BORDER_WIDTH = 1.dp
private val TITLE_START_INSET = 12.dp
private val TITLE_HORIZONTAL_GAP = 6.dp
private val CONTENT_TOP_SPACING = 6.dp
private val DEFAULT_HORIZONTAL_PADDING = 12.dp
private val DEFAULT_BOTTOM_PADDING = 12.dp

private class BorderTitleBounds {
    var hasTitle: Boolean = false
    var titleLeftPx: Float = 0f
    var titleRightPx: Float = 0f
    var topBorderYPx: Float = 0f
}

/**
 * Terminal-style bordered container whose optional [title] sits directly
 * inside a geometric gap in the top border stroke.
 */
@Suppress("LongParameterList", "CyclomaticComplexMethod")
@Composable
fun TuiBlock(
    modifier: Modifier = Modifier,
    title: String? = null,
    isActive: Boolean = false,
    containerColor: Color = Color.Transparent,
    contentPadding: PaddingValues =
        PaddingValues(
            start = DEFAULT_HORIZONTAL_PADDING,
            end = DEFAULT_HORIZONTAL_PADDING,
            top = CONTENT_TOP_SPACING,
            bottom = DEFAULT_BOTTOM_PADDING,
        ),
    content: @Composable () -> Unit,
) {
    val accent = LocalAccentColor.current
    val borderColor = if (isActive) accent else CliBeatsBorder
    val titleColor = if (isActive) accent else CliBeatsTextSecondary

    val density = LocalDensity.current
    val strokePx = with(density) { BORDER_WIDTH.toPx() }
    val titleInsetPx = with(density) { TITLE_START_INSET.toPx() }
    val titleGapPx = with(density) { TITLE_HORIZONTAL_GAP.toPx() }
    val bounds = remember { BorderTitleBounds() }

    Layout(
        modifier =
            modifier.drawBehind {
                val halfStroke = strokePx / 2f
                val left = halfStroke
                val right = size.width - halfStroke
                val top = bounds.topBorderYPx.coerceAtLeast(halfStroke)
                val bottom = size.height - halfStroke

                if (containerColor != Color.Transparent) {
                    drawRect(
                        color = containerColor,
                        topLeft = Offset(left, top),
                        size = Size(right - left, (bottom - top).coerceAtLeast(0f)),
                    )
                }

                val stroke =
                    Stroke(
                        width = strokePx,
                        cap = StrokeCap.Square,
                        join = StrokeJoin.Miter,
                    )

                if (bounds.hasTitle) {
                    val gapStart = (bounds.titleLeftPx - titleGapPx).coerceAtLeast(left)
                    val gapEnd = (bounds.titleRightPx + titleGapPx).coerceAtMost(right)

                    val path =
                        Path().apply {
                            moveTo(gapEnd, top)
                            lineTo(right, top)
                            lineTo(right, bottom)
                            lineTo(left, bottom)
                            lineTo(left, top)
                            lineTo(gapStart, top)
                        }
                    drawPath(path = path, color = borderColor, style = stroke)
                } else {
                    drawRect(
                        color = borderColor,
                        topLeft = Offset(left, top),
                        size = Size(right - left, (bottom - top).coerceAtLeast(0f)),
                        style = stroke,
                    )
                }
            },
        content = {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        },
    ) { measurables, constraints ->
        val hasTitle = !title.isNullOrBlank()
        val titlePlaceable =
            if (hasTitle) {
                val maxTitleWidth =
                    if (constraints.hasBoundedWidth) {
                        (constraints.maxWidth - (titleInsetPx * 2f).roundToInt()).coerceAtLeast(0)
                    } else {
                        constraints.maxWidth
                    }
                measurables[0].measure(
                    constraints.copy(
                        minWidth = 0,
                        minHeight = 0,
                        maxWidth = maxTitleWidth,
                    ),
                )
            } else {
                null
            }

        val contentMeasurable = if (hasTitle) measurables[1] else measurables[0]
        val contentTopOffset = titlePlaceable?.height ?: 0
        val contentMaxHeight =
            if (constraints.hasBoundedHeight) {
                (constraints.maxHeight - contentTopOffset).coerceAtLeast(0)
            } else {
                constraints.maxHeight
            }
        val contentMinHeight = (constraints.minHeight - contentTopOffset).coerceAtLeast(0)

        // Boxes span the full width they're given (like the original TuiBlock), so short content such as
        // the accent swatches or the About lines still gets a full-width frame.
        val fullWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else null
        val contentPlaceable =
            contentMeasurable.measure(
                constraints.copy(
                    minWidth = fullWidth ?: constraints.minWidth,
                    minHeight = contentMinHeight,
                    maxHeight = contentMaxHeight,
                ),
            )

        val minWidthForTitle =
            if (titlePlaceable != null) {
                titlePlaceable.width + (titleInsetPx * 2f).roundToInt()
            } else {
                0
            }
        val totalWidth =
            (fullWidth ?: max(contentPlaceable.width, minWidthForTitle))
                .coerceIn(constraints.minWidth, constraints.maxWidth)
        val totalHeight =
            (contentTopOffset + contentPlaceable.height)
                .coerceIn(constraints.minHeight, constraints.maxHeight)

        bounds.hasTitle = titlePlaceable != null
        if (titlePlaceable != null) {
            bounds.titleLeftPx = titleInsetPx
            bounds.titleRightPx = titleInsetPx + titlePlaceable.width
            bounds.topBorderYPx = titlePlaceable.height / 2f
        } else {
            bounds.topBorderYPx = strokePx / 2f
        }

        layout(totalWidth, totalHeight) {
            titlePlaceable?.placeRelative(x = titleInsetPx.roundToInt(), y = 0)
            contentPlaceable.placeRelative(x = 0, y = contentTopOffset)
        }
    }
}

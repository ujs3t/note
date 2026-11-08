package com.midad.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * مجموعة أيقونات مِداد — نفس مسارات SVG للويب (24×24، سماكة 1.8، أطراف دائرية)
 */

private data class IconDef(val stroke: List<String>, val fill: List<String> = emptyList())

private val ICONS = mapOf(
    "plus" to IconDef(listOf("M12 5v14M5 12h14")),
    "search" to IconDef(listOf("M11 4a7 7 0 1 1 0 14 7 7 0 0 1 0-14Z", "M20.5 20.5 16.7 16.7")),
    "dots" to IconDef(
        emptyList(), listOf(
            "M12 3.3a1.7 1.7 0 1 0 0 3.4 1.7 1.7 0 0 0 0-3.4Z",
            "M12 10.3a1.7 1.7 0 1 0 0 3.4 1.7 1.7 0 0 0 0-3.4Z",
            "M12 17.3a1.7 1.7 0 1 0 0 3.4 1.7 1.7 0 0 0 0-3.4Z"
        )
    ),
    "back" to IconDef(listOf("M9.5 5l7 7-7 7")),
    "undo" to IconDef(listOf("M9.5 14.5 4.5 9.5l5-5", "M4.5 9.5h10a5.5 5.5 0 0 1 0 11h-3")),
    "redo" to IconDef(listOf("M14.5 14.5l5-5-5-5", "M19.5 9.5h-10a5.5 5.5 0 0 0 0 11h3")),
    "x" to IconDef(listOf("M6 6l12 12M18 6L6 18")),
    "copy" to IconDef(
        listOf(
            "M11.5 9h6A2.5 2.5 0 0 1 20 11.5v6a2.5 2.5 0 0 1-2.5 2.5h-6A2.5 2.5 0 0 1 9 17.5v-6A2.5 2.5 0 0 1 11.5 9Z",
            "M5 15V6a2 2 0 0 1 2-2h9"
        )
    ),
    "check" to IconDef(listOf("M4.5 12.5l5 5 10-11")),
    "clock" to IconDef(listOf("M12 3.5a8.5 8.5 0 1 1 0 17 8.5 8.5 0 0 1 0-17Z", "M12 7.5V12l3 2.5")),
    "code" to IconDef(listOf("M8.5 7 4 12l4.5 5M15.5 7 20 12l-4.5 5")),
    "mic" to IconDef(listOf("M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3Z", "M5.5 11a6.5 6.5 0 0 0 13 0", "M12 17.5V21")),
    "image" to IconDef(
        listOf(
            "M6.5 4.5h11a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3h-11a3 3 0 0 1-3-3v-9a3 3 0 0 1 3-3Z",
            "M9 8.2a1.8 1.8 0 1 1 0 3.6 1.8 1.8 0 0 1 0-3.6Z",
            "M4.5 17.5l4.5-4.5 3.5 3.5 3-3 4 4"
        )
    ),
    "todo" to IconDef(listOf("M8 4h8a4 4 0 0 1 4 4v8a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V8a4 4 0 0 1 4-4Z", "M8.5 12.2l2.4 2.4 4.8-5.2")),
    "trash" to IconDef(listOf("M4.5 7h15M9.5 7V5h5v2M7 7l1 13h8l1-13M10 11v5M14 11v5")),
    "play" to IconDef(emptyList(), listOf("M8 5.5v13l10-6.5z")),
    "pause" to IconDef(
        emptyList(), listOf(
            "M8.2 5h1a1.2 1.2 0 0 1 1.2 1.2v11.6A1.2 1.2 0 0 1 9.2 19h-1a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 8.2 5Z",
            "M14.8 5h1a1.2 1.2 0 0 1 1.2 1.2v11.6A1.2 1.2 0 0 1 15.8 19h-1a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 14.8 5Z"
        )
    ),
    "reset" to IconDef(listOf("M4.5 5.5v5h5", "M5.3 14.5a7.5 7.5 0 1 0 1.2-7L4.5 10.5")),
    "note" to IconDef(listOf("M7 3.5h7l5 5v12a1.5 1.5 0 0 1-1.5 1.5H7A1.5 1.5 0 0 1 5.5 20.5V5A1.5 1.5 0 0 1 7 3.5z", "M14 3.5v5h5M9 13h7M9 16.5h5")),
    "tasks" to IconDef(listOf("M4.5 6.5l2 2 3-3.5M4.5 16l2 2 3-3.5M12.5 7h7M12.5 16.5h7")),
    "selectAll" to IconDef(listOf("M4.5 6.5l2 2 3.2-3.7M4.5 15.5l2 2 3.2-3.7M12.5 7.5h7M12.5 16.5h7")),
    "arrowUp" to IconDef(listOf("M12 19V5M6 11l6-6 6 6")),
    "arrowDown" to IconDef(listOf("M12 5v14M6 13l6 6 6-6"))
)

private val parsedCache = HashMap<String, Pair<List<Path>, List<Path>>>()

private fun parseIcon(name: String): Pair<List<Path>, List<Path>> {
    parsedCache[name]?.let { return it }
    val def = ICONS[name] ?: ICONS.getValue("plus")
    val strokes = def.stroke.map { d -> PathParser().parsePathString(d).toPath() }
    val fills = def.fill.map { d -> PathParser().parsePathString(d).toPath() }
    val pair = strokes to fills
    parsedCache[name] = pair
    return pair
}

private fun DrawScope.drawIconPaths(name: String, color: Color) {
    val (strokes, fills) = parseIcon(name)
    scale(size.minDimension / 24f, pivot = Offset.Zero) {
        for (p in strokes)
            drawPath(p, color, style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        for (p in fills)
            drawPath(p, color)
    }
}

/** أيقونة مِداد — نفس شكل أيقونات الويب تمامًا */
@Composable
fun MidadIcon(
    name: String,
    size: Dp = 20.dp,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.size(size)) {
        drawIconPaths(name, tint)
    }
}

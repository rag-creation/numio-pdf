package com.rr.numio.pdf.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons on a 24×24 grid with round ends, tinted by Icon(tint = …). */
object PIcons {
    private fun stroke(name: String, vararg paths: String, width: Float = 1.8f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    addPathNodes(it),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private fun fill(name: String, vararg paths: String) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
        }.build()

    val Back = stroke("Back", "M15 5l-7 7l7 7", width = 2f)
    val Close = stroke("Close", "M6 6l12 12M18 6L6 18")
    val Search = stroke("Search", "M11 4a7 7 0 1 1 0 14a7 7 0 1 1 0 -14z", "M20 20l-3.5-3.5")
    val Plus = stroke("Plus", "M12 5v14M5 12h14", width = 2.2f)
    val Share = stroke("Share", "M12 15V4M8 8l4-4l4 4", "M5 13v5.5A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5V13")
    val Star = stroke("Star", "M12 3.5l2.6 5.4l5.9.8l-4.3 4.1l1 5.8L12 16.8l-5.2 2.8l1-5.8l-4.3-4.1l5.9-.8z", width = 1.6f)
    val StarFilled = fill("StarFilled", "M12 3.5l2.6 5.4l5.9.8l-4.3 4.1l1 5.8L12 16.8l-5.2 2.8l1-5.8l-4.3-4.1l5.9-.8z")
    val More = fill(
        "More",
        "M12 4.5a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0 -3.2z",
        "M12 10.4a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0 -3.2z",
        "M12 16.3a1.6 1.6 0 1 1 0 3.2a1.6 1.6 0 1 1 0 -3.2z",
    )
    val Page = stroke("Page", "M7 3.5h7l4.5 4.5v11a1.5 1.5 0 0 1 -1.5 1.5h-10a1.5 1.5 0 0 1 -1.5 -1.5v-14a1.5 1.5 0 0 1 1.5 -1.5z", "M14 3.5V8h4.5")
    val Copy = stroke(
        "Copy",
        "M9.5 8.5h9a1.5 1.5 0 0 1 1.5 1.5v9a1.5 1.5 0 0 1 -1.5 1.5h-9a1.5 1.5 0 0 1 -1.5 -1.5v-9a1.5 1.5 0 0 1 1.5 -1.5z",
        "M16 8.5V5.5A1.5 1.5 0 0 0 14.5 4h-9A1.5 1.5 0 0 0 4 5.5v9A1.5 1.5 0 0 0 5.5 16h2.5",
    )
    val SelectAll = stroke(
        "SelectAll",
        "M4 7V5.5A1.5 1.5 0 0 1 5.5 4H7M17 4h1.5A1.5 1.5 0 0 1 20 5.5V7M20 17v1.5a1.5 1.5 0 0 1 -1.5 1.5H17M7 20H5.5A1.5 1.5 0 0 1 4 18.5V17",
        "M8 9h8M8 12h8M8 15h5",
    )
    val Highlight = stroke(
        "Highlight",
        "M9 11l-5 5v4h4l5-5",
        "M9 11l6-6l4 4l-6 6z",
        "M13 20h7",
    )
    val Underline = stroke("Underline", "M7 4v7a5 5 0 0 0 10 0V4", "M5 20h14")
    val Undo = stroke("Undo", "M9 14L4 9l5-5", "M4 9h11a5 5 0 0 1 0 10h-3")
    val Bin = stroke("Bin", "M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13")
    val Lock = stroke("Lock", "M6.5 10.5h11a1.5 1.5 0 0 1 1.5 1.5v7a1.5 1.5 0 0 1 -1.5 1.5h-11a1.5 1.5 0 0 1 -1.5 -1.5v-7a1.5 1.5 0 0 1 1.5 -1.5z", "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5")
}

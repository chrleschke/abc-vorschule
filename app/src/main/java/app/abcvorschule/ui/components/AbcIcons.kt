package app.abcvorschule.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.StarGoldDeep

@Composable
fun IconChevronLeft(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = size.toPx() * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.toPx() * 0.65f, size.toPx() * 0.2f)
            lineTo(size.toPx() * 0.35f, size.toPx() * 0.5f)
            lineTo(size.toPx() * 0.65f, size.toPx() * 0.8f)
        }
        drawPath(path, color = tint, style = stroke)
    }
}

@Composable
fun IconChevronRight(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = size.toPx() * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(size.toPx() * 0.35f, size.toPx() * 0.2f)
            lineTo(size.toPx() * 0.65f, size.toPx() * 0.5f)
            lineTo(size.toPx() * 0.35f, size.toPx() * 0.8f)
        }
        drawPath(path, color = tint, style = stroke)
    }
}

@Composable
fun IconClose(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = size.toPx() * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val a = Path().apply {
            moveTo(size.toPx() * 0.25f, size.toPx() * 0.25f)
            lineTo(size.toPx() * 0.75f, size.toPx() * 0.75f)
        }
        val b = Path().apply {
            moveTo(size.toPx() * 0.75f, size.toPx() * 0.25f)
            lineTo(size.toPx() * 0.25f, size.toPx() * 0.75f)
        }
        drawPath(a, color = tint, style = stroke)
        drawPath(b, color = tint, style = stroke)
    }
}

@Composable
fun IconSpeaker(
    tint: Color,
    speaking: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Canvas(modifier.size(size)) {
        val w = size.toPx()
        val body = Path().apply {
            moveTo(w * 0.18f, w * 0.38f)
            lineTo(w * 0.38f, w * 0.38f)
            lineTo(w * 0.58f, w * 0.22f)
            lineTo(w * 0.58f, w * 0.78f)
            lineTo(w * 0.38f, w * 0.62f)
            lineTo(w * 0.18f, w * 0.62f)
            close()
        }
        drawPath(body, color = tint)
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        drawArc(
            color = tint,
            startAngle = -35f,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(w * 0.52f, w * 0.28f),
            size = androidx.compose.ui.geometry.Size(w * 0.28f, w * 0.44f),
            style = stroke,
        )
        if (speaking) {
            drawArc(
                color = tint,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.62f, w * 0.18f),
                size = androidx.compose.ui.geometry.Size(w * 0.32f, w * 0.64f),
                style = stroke,
            )
        }
    }
}

@Composable
fun IconStar(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    /**
     * Kontur des Sternpfads: StarGold allein liegt auf Cream nur bei ~1.85:1 und
     * unterschreitet die 3:1-Grenze für UI-Glyphen. Der Tiefton-Stroke gibt dem
     * Glyph diese Grenze, ohne die Füllfarbe (und damit den "Belohnung"-Ton) zu
     * verändern. Aufrufer, die bewusst einen andersfarbigen Stern wollen, können
     * outline überschreiben (z. B. auf tint, um eine unifarbene Silhouette zu
     * behalten).
     */
    outline: Color = StarGoldDeep,
) {
    Canvas(modifier.size(size)) {
        val w = size.toPx()
        val cx = w / 2f
        val cy = w / 2f
        val outerR = w * 0.5f
        val innerR = outerR * 0.42f
        val points = 5
        val path = Path().apply {
            for (i in 0 until points * 2) {
                val radius = if (i % 2 == 0) outerR else innerR
                val angle = (Math.PI / points * i) - Math.PI / 2
                val x = cx + (radius * kotlin.math.cos(angle)).toFloat()
                val y = cy + (radius * kotlin.math.sin(angle)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path, color = tint)
        drawPath(
            path,
            color = outline,
            style = Stroke(width = w / 12f, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun IconUnlock(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    Canvas(modifier.size(size)) {
        val w = size.toPx()
        val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.22f, w * 0.42f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, w * 0.4f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
            style = stroke,
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.3f, w * 0.12f),
            size = androidx.compose.ui.geometry.Size(w * 0.4f, w * 0.4f),
            style = stroke,
        )
    }
}

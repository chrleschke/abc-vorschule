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

/**
 * Zurück-Pfeil im Material-Stil: Schaft plus Spitze, nicht der nackte Chevron —
 * das Navigations-Icon einer Top App Bar ist ein Pfeil, der Chevron gehört an
 * die Rundennavigation.
 */
@Composable
fun IconArrowBack(
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = size.toPx() * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val shaft = Path().apply {
            moveTo(size.toPx() * 0.82f, size.toPx() * 0.5f)
            lineTo(size.toPx() * 0.2f, size.toPx() * 0.5f)
        }
        val head = Path().apply {
            moveTo(size.toPx() * 0.45f, size.toPx() * 0.24f)
            lineTo(size.toPx() * 0.19f, size.toPx() * 0.5f)
            lineTo(size.toPx() * 0.45f, size.toPx() * 0.76f)
        }
        drawPath(shaft, color = tint, style = stroke)
        drawPath(head, color = tint, style = stroke)
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
        val strokeWidth = w / 12f
        // The stroke is centered on the path, so it grows outward by half its
        // width at every point — most visibly at the star's outer tips, which
        // otherwise sit exactly on the canvas edge (outerR = w * 0.5f) and get
        // clipped by whatever sits outside this Composable's bounds (Compose
        // does not clip Canvas drawing to its own size). Insetting both radii
        // keeps fill + stroke entirely within the declared size.
        val inset = strokeWidth / 2f
        val outerR = w * 0.5f - inset
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
            style = Stroke(width = strokeWidth, join = StrokeJoin.Round),
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
        // Offener Bügel: links im Korpus verankert, rechtes Ende angehoben — ein
        // symmetrisch geschlossener Bügel läse sich als zugesperrtes Schloss und
        // würde die Botschaft des Auflösen-Buttons invertieren.
        drawLine(
            color = tint,
            start = Offset(w * 0.30f, w * 0.44f),
            end = Offset(w * 0.30f, w * 0.28f),
            strokeWidth = w * 0.1f,
            cap = StrokeCap.Round,
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.30f, w * 0.10f),
            size = androidx.compose.ui.geometry.Size(w * 0.36f, w * 0.36f),
            style = stroke,
        )
    }
}

/**
 * Geschlossenes Vorhängeschloss für gesperrte Pfad-Schilder. Vektor statt des
 * 🔒-Emojis: Emojis rendern herstellerabhängig (meist goldgelb) und kollidieren
 * damit farblich mit der StarGold-Belohnungsrolle direkt neben echten Sternen —
 * und §10 verlangt für UI-Chrome ohnehin Vektor/ASCII.
 */
@Composable
fun IconLock(
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
        // Beide Bügel-Beine tauchen in den Korpus ein — eindeutig zugesperrt.
        drawLine(
            color = tint,
            start = Offset(w * 0.32f, w * 0.44f),
            end = Offset(w * 0.32f, w * 0.30f),
            strokeWidth = w * 0.1f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.68f, w * 0.44f),
            end = Offset(w * 0.68f, w * 0.30f),
            strokeWidth = w * 0.1f,
            cap = StrokeCap.Round,
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.32f, w * 0.12f),
            size = androidx.compose.ui.geometry.Size(w * 0.36f, w * 0.36f),
            style = stroke,
        )
    }
}

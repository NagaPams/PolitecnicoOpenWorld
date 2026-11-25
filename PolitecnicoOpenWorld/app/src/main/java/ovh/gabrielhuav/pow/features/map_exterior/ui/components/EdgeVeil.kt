package ovh.gabrielhuav.pow.features.map_exterior.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.hypot

/**
 * Velo de noche, lluvia o niebla con degradado hacia los bordes (t34): el centro (donde está el jugador) se oscurece
 * menos que las esquinas, en vez de una capa plana que aplana todos los colores. [alpha] es la opacidad en el borde;
 * en el centro es [centerStrength] veces esa. El degradado se arma una vez por tamaño; el alpha cambia sin recrearlo.
 */
@Composable
fun EdgeVeil(color: Color, alpha: Float, modifier: Modifier = Modifier, centerStrength: Float = 0.45f) {
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val brush = Brush.radialGradient(
                    0f to color.copy(alpha = centerStrength),
                    1f to color,
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = hypot(size.width, size.height) / 2f,
                )
                onDrawBehind { drawRect(brush, alpha = alpha.coerceIn(0f, 1f)) }
            },
    )
}

/** Rayas de lluvia que caen en diagonal; sin asignaciones por cuadro (solo Offset y Color, que son valores). */
@Composable
fun RainStreaks(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rain")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart),
        label = "rainProgress",
    )
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        for (i in 0 until RAIN_STREAKS) {
            val x = ((i * 97) % 100) / 100f * size.width
            val phase = ((i * 37) % 100) / 100f
            val y = ((t + phase) % 1f) * size.height
            drawLine(RAIN_COLOR, Offset(x, y), Offset(x - 7f, y + 24f), strokeWidth = 2f)
        }
    }
}

private const val RAIN_STREAKS = 46
private val RAIN_COLOR = Color(0x55C8D8E8)

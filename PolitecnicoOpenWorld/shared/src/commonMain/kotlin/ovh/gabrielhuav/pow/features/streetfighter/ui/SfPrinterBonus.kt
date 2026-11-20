package ovh.gabrielhuav.pow.features.streetfighter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfConstants
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfPrinterBonus
import ovh.gabrielhuav.pow.shared.recursos.*
import ovh.gabrielhuav.pow.ui.components.PowButton
import kotlin.math.sin

/** Renderer sin decodificación ni lógica de daño. Atlas con ancla idéntica por celda. */
internal fun DrawScope.drawPrinterBonus(ctx: SceneCtx, bonus: SfPrinterBonus,
    images: Map<String, ImageBitmap>, now: Long,
) {
    val sheet = images["printer_bonus.webp"] ?: return
    val age = now - bonus.lastHitMs
    val shake = if (age in 0L..160L) sin(age.toFloat() * .2f) * 3f else 0f
    val frame = if (bonus.health == SfPrinterBonus.MAX_HEALTH && !bonus.finished && now / 250L % 2L == 0L) 1 else bonus.damageFrame
    val x = ctx.ox + (SfPrinterBonus.X - 64f - ctx.camX + shake) * ctx.scale
    val y = ctx.oy + (SfConstants.STAGE_FLOOR - 124f - ctx.camY) * ctx.scale
    drawImage(sheet, srcOffset = IntOffset(frame % 4 * 256, frame / 4 * 256),
        srcSize = IntSize(256, 256), dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize((128f * ctx.scale).toInt(), (128f * ctx.scale).toInt()), filterQuality = FilterQuality.None)
    val particles = images["printer_particles.webp"] ?: return
    if (age in 0L..650L) {
        val t = age / 1000f
        repeat(6) { i ->
            val cell = (bonus.hits + i) % 12
            val px = SfPrinterBonus.X + (i - 2.5f) * 85f * t
            val py = SfConstants.STAGE_FLOOR - 65f - (100f + i * 12f) * t + 220f * t * t
            drawImage(particles, srcOffset = IntOffset(cell % 4 * 64, cell / 4 * 64),
                srcSize = IntSize(64, 64),
                dstOffset = IntOffset((ctx.ox + (px - ctx.camX) * ctx.scale).toInt(),
                    (ctx.oy + (py - ctx.camY) * ctx.scale).toInt()),
                dstSize = IntSize((15 * ctx.scale).toInt(), (15 * ctx.scale).toInt()), filterQuality = FilterQuality.None)
        }
        val smoke = (age / 165L).toInt().coerceIn(0, 3)
        drawImage(particles, srcOffset = IntOffset(smoke * 64, 192), srcSize = IntSize(64,64),
            dstOffset = IntOffset((x + 30f * ctx.scale).toInt(), (y + 60f * ctx.scale).toInt()),
            dstSize = IntSize((64 * ctx.scale).toInt(), (64 * ctx.scale).toInt()), filterQuality = FilterQuality.None)
    }
}

@Composable
internal fun BoxScope.PrinterBonusOverlay(bonus: SfPrinterBonus, onContinue: () -> Unit) {
    Column(Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
        .background(Color(0xCC101722)).padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(Res.string.sf_printer_title), color = Color(0xFFFFD54F), fontWeight = FontWeight.Black, fontSize = 19.sp)
        Text(stringResource(Res.string.sf_printer_score, bonus.seconds, bonus.score), color = Color.White)
        Text(stringResource(Res.string.sf_printer_health, bonus.health * 100 / SfPrinterBonus.MAX_HEALTH), color = Color(0xFF90CAF9))
    }
    if (bonus.introMs > 0L || bonus.finished) {
        Column(Modifier.align(Alignment.Center).background(Color(0xE6101722)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(if (!bonus.finished) Res.string.sf_printer_hint else
                if (bonus.destroyed) Res.string.sf_printer_destroyed else Res.string.sf_printer_timeout),
                color = Color.White, fontWeight = FontWeight.Bold)
            if (bonus.finished) {
                Spacer(Modifier.height(12.dp))
                PowButton(text = stringResource(Res.string.sf_printer_continue), onClick = onContinue)
            }
        }
    }
}

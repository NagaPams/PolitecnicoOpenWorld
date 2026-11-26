package ovh.gabrielhuav.pow.features.map_exterior.ui.iso

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import ovh.gabrielhuav.pow.domain.models.interior.InteriorIso
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction

// Personajes y zombis con las hojas ISOMÉTRICAS dentro de un interior generado (t82): la misma arte que el mundo exterior,
// con la dirección de pantalla sacada del rumbo en la cuadrícula del interior. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Arte isométrica cargada para los interiores (hojas del jugador y de los zombis). */
class IsoInteriorArt internal constructor(internal val art: IsoActorArt, internal val playerFrames: State<Array<ImageBitmap?>?>)

@Composable
fun rememberIsoInteriorArt(): IsoInteriorArt {
    val scope = rememberCoroutineScope()
    val art = remember { IsoActorArt(scope) }
    val frames = produceState<Array<ImageBitmap?>?>(null) { value = loadIsoPlayerSprites() }
    return remember(art, frames) { IsoInteriorArt(art, frames) }
}

private const val SPRITE_ASPECT = 193f / 249f

@Composable
private fun SpriteCanvas(frame: ImageBitmap?, mirror: Boolean, heightPx: Float, alpha: Float, healthFraction: Float, modifier: Modifier) {
    val density = LocalDensity.current
    val widthPx = heightPx * SPRITE_ASPECT
    Canvas(modifier.size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })) {
        if (frame != null) {
            val dst = IntSize(size.width.toInt(), size.height.toInt())
            val src = IntSize(frame.width, frame.height)
            if (mirror) scale(-1f, 1f, pivot = Offset(size.width / 2f, size.height / 2f)) { drawImage(frame, IntOffset.Zero, src, IntOffset.Zero, dst, alpha) }
            else drawImage(frame, IntOffset.Zero, src, IntOffset.Zero, dst, alpha)
        }
        if (healthFraction in 0f..0.999f) {
            drawRect(Color(0xAA000000), Offset(size.width * 0.2f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.6f, 5f))
            drawRect(Color(0xFFE53935), Offset(size.width * 0.2f, 0f), androidx.compose.ui.geometry.Size(size.width * 0.6f * healthFraction, 5f))
        }
    }
}

private val STEP = intArrayOf(0, 1, 2, 1)

/** Jugador isométrico: [aimDx]/[aimDy] es su dirección en la cuadrícula del interior (columna, fila). Los pies quedan abajo al centro. */
@Composable
fun IsoInteriorPlayer(art: IsoInteriorArt, action: PlayerAction, aimDx: Float, aimDy: Float, heightPx: Float, alpha: Float, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(action != PlayerAction.IDLE) {
        if (action != PlayerAction.IDLE) while (true) withFrameMillis { now = it }
    }
    val sector = InteriorIso.spriteSector(aimDx, aimDy)
    val slot = when (action) {
        PlayerAction.IDLE -> 0
        PlayerAction.WALK -> 1 + STEP[((now / 110) % 4).toInt()]
        PlayerAction.RUN -> 4 + STEP[((now / 85) % 4).toInt()]
        PlayerAction.SPECIAL -> 7
    }
    val frame = art.playerFrames.value?.get(ISO_SECTOR_ROW[sector] * ISO_SLOT_FOLDER.size + slot)
    SpriteCanvas(frame, sector >= 5, heightPx, alpha, -1f, modifier)
}

/**
 * Zombi isométrico: su dirección y si camina salen de cómo cambia su posición ([worldX], [worldY] en píxeles del mundo del
 * interior; [cellPx] es el tamaño de una celda).
 */
@Composable
fun IsoInteriorZombie(
    art: IsoInteriorArt, id: String, worldX: Float, worldY: Float, cellPx: Float, health: Float, maxHealth: Float,
    dying: Boolean, heightPx: Float, modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { now = it } }
    // Mismas coordenadas que el mundo exterior: este = -fila, norte = -columna (ver InteriorIso.spriteSector).
    val lat = -(worldX / cellPx) / METERS_PER_DEGREE_INTERIOR
    val lon = -(worldY / cellPx) / METERS_PER_DEGREE_INTERIOR
    val track = art.art.track(id, lat, lon, now)
    val skin = isoSkinFor(NpcType.ZOMBIE, id)
    val frame = skin?.let { art.art.frame(it, track, now) }
    val fraction = if (maxHealth > 0f) (health / maxHealth).coerceIn(0f, 1f) else -1f
    SpriteCanvas(frame, IsoActorArt.mirrored(track.sector), heightPx, if (dying) 0.35f else 1f, if (dying) -1f else fraction, modifier)
}

private const val METERS_PER_DEGREE_INTERIOR = 111_320.0

package ovh.gabrielhuav.pow.features.map_exterior.ui.iso

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import ovh.gabrielhuav.pow.domain.models.map.FootprintKind

// Trama de suelo (h22/t50): el relleno plano de parques, estacionamientos y canchas lleva una trama procedural (sin assets)
// que se recorta al polígono: motas de pasto, rayas de cajones y franjas de césped segado. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Trama ya construida para un suelo: el trazo se dibuja recortado al polígono. */
internal class IsoGroundPattern(val path: Path, val color: Color, val strokeMeters: Float)

private const val MAX_MARKS = 300

/** Trama del suelo [kind] dentro de [bounds] (píxeles de la cocción), o null si ese tipo no lleva. [seed] fija el azar. */
internal fun isoGroundPattern(kind: FootprintKind, bounds: Rect, ppm: Float, seed: Int): IsoGroundPattern? {
    if (ppm <= 0f || bounds.width <= 0f || bounds.height <= 0f) return null
    return when (kind) {
        FootprintKind.PARK -> tufts(bounds, ppm, seed, Color(0x553F5A3C))
        FootprintKind.PARKING -> stalls(bounds, ppm, Color(0x66F2F2EA))
        FootprintKind.PITCH -> stripes(bounds, ppm, Color(0x22FFFFFF))
        else -> null
    }
}

/** Escala el paso para no pasar de [MAX_MARKS] marcas en un suelo enorme. */
private fun fitStep(bounds: Rect, step: Float): Float {
    val marks = (bounds.width / step) * (bounds.height / step)
    return if (marks <= MAX_MARKS) step else step * kotlin.math.sqrt(marks / MAX_MARKS)
}

private fun hash(x: Int, y: Int, seed: Int): Int {
    var h = x * 374761393 + y * 668265263 + seed * 1274126177
    h = (h xor (h ushr 13)) * 1274126177
    return h xor (h ushr 16)
}

private fun tufts(bounds: Rect, ppm: Float, seed: Int, color: Color): IsoGroundPattern {
    val step = fitStep(bounds, 2.6f * ppm)
    val path = Path()
    var iy = 0
    var y = bounds.top
    while (y < bounds.bottom) {
        var ix = 0
        var x = bounds.left
        while (x < bounds.right) {
            val h = hash(ix, iy, seed)
            val px = x + ((h and 0xFF) / 255f) * step
            val py = y + (((h shr 8) and 0xFF) / 255f) * step
            val s = 0.35f * ppm * (0.7f + ((h shr 16) and 0x7) / 10f)
            path.moveTo(px - s, py - s * 0.6f); path.lineTo(px, py); path.lineTo(px + s, py - s * 0.6f)
            ix++; x += step
        }
        iy++; y += step
    }
    return IsoGroundPattern(path, color, 0.12f)
}

private fun stalls(bounds: Rect, ppm: Float, color: Color): IsoGroundPattern {
    val step = fitStep(bounds, 2.6f * ppm)
    val path = Path()
    var x = bounds.left
    while (x < bounds.right + bounds.height) {
        path.moveTo(x, bounds.top); path.lineTo(x - bounds.height * 0.5f, bounds.bottom)
        x += step
    }
    return IsoGroundPattern(path, color, 0.12f)
}

private fun stripes(bounds: Rect, ppm: Float, color: Color): IsoGroundPattern {
    val step = fitStep(bounds, 6f * ppm)
    val path = Path()
    var y = bounds.top
    while (y < bounds.bottom) {
        path.moveTo(bounds.left, y + step * 0.25f); path.lineTo(bounds.right, y + step * 0.25f)
        y += step
    }
    return IsoGroundPattern(path, color, step / ppm * 0.5f)
}

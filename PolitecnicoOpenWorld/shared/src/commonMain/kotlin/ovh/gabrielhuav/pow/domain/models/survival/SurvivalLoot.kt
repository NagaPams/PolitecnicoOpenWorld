package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.floor

// Botín suelto del mundo (modo supervivencia): puntos deterministas sobre una cuadrícula, sin guardar nada.
// Cada celda decide por un hash si trae botín y de qué tipo; el ViewModel los ancla a la calle y recuerda cuáles
// se recogieron (con enfriamiento). Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Tipo de botín. Los valores nuevos van AL FINAL. */
enum class LootKind { PIZZA, REFRESCO, SUERO, TABLONES, CONTENEDOR }

data class LootPoint(val id: String, val kind: LootKind, val latitude: Double, val longitude: Double)

object SurvivalLoot {
    /** Lado de la celda en grados (~40 m). */
    const val CELL_DEGREES = 0.00036
    /** Celdas a cada lado del jugador que se consideran (7×7 ≈ 280 m). */
    const val RADIUS_CELLS = 3
    const val ID_PREFIX = "survival_loot_"
    /** Una de cada [DENSITY] celdas trae botín. */
    private const val DENSITY = 5
    private const val ROLL_RANGE = 100
    // Pesos (de 100): comida y bebida abundan, el suero es raro.
    private const val PIZZA_UPTO = 32
    private const val REFRESCO_UPTO = 64
    private const val TABLONES_UPTO = 84
    private const val SUERO_UPTO = 92

    fun cellOf(degrees: Double): Int = floor(degrees / CELL_DEGREES).toInt()

    fun kindAt(cellLat: Int, cellLon: Int): LootKind? {
        val h = hash(cellLat, cellLon)
        if (h % DENSITY != 0) return null
        val roll = (h / DENSITY) % ROLL_RANGE
        return when {
            roll < PIZZA_UPTO -> LootKind.PIZZA
            roll < REFRESCO_UPTO -> LootKind.REFRESCO
            roll < TABLONES_UPTO -> LootKind.TABLONES
            roll < SUERO_UPTO -> LootKind.SUERO
            else -> LootKind.CONTENEDOR
        }
    }

    /** Puntos de botín en las celdas alrededor de ([latitude], [longitude]); el id es estable por celda. */
    fun around(latitude: Double, longitude: Double): List<LootPoint> {
        val centerLat = cellOf(latitude)
        val centerLon = cellOf(longitude)
        val out = ArrayList<LootPoint>()
        for (cy in centerLat - RADIUS_CELLS..centerLat + RADIUS_CELLS) {
            for (cx in centerLon - RADIUS_CELLS..centerLon + RADIUS_CELLS) {
                val kind = kindAt(cy, cx) ?: continue
                out.add(LootPoint("$ID_PREFIX${cy}_$cx", kind, (cy + 0.5) * CELL_DEGREES, (cx + 0.5) * CELL_DEGREES))
            }
        }
        return out
    }

    /** Mezcla de enteros estable entre plataformas (no usa `hashCode`), siempre ≥ 0. */
    private fun hash(a: Int, b: Int): Int {
        var h = a * 73856093 xor b * 19349663
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        h *= 0x297a2d39
        h = h xor (h ushr 15)
        return h and Int.MAX_VALUE
    }
}

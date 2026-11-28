package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.floor

// Botín suelto del mundo (modo supervivencia): puntos deterministas sobre una cuadrícula, sin guardar nada.
// Cada celda decide por un hash si trae botín y de qué tipo; el ViewModel los ancla a la calle y recuerda cuáles
// se recogieron (con enfriamiento). Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Tipo de botín. Los valores nuevos van AL FINAL. */
enum class LootKind { PIZZA, REFRESCO, SUERO, TABLONES, CONTENEDOR, ARMA_BLANCA, ARMA_FUEGO, MUNICION, GASOLINA, VENDA, EDIFICIO, FOGATA, TRAMPA, COCHE, HUERTO }

/** [variant] (0..9) decide qué arma o munición exacta es, estable por celda. */
data class LootPoint(val id: String, val kind: LootKind, val latitude: Double, val longitude: Double, val variant: Int = 0)

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
    private const val PIZZA_UPTO = 30
    private const val REFRESCO_UPTO = 60
    private const val TABLONES_UPTO = 75
    private const val SUERO_UPTO = 83
    private const val VENDA_UPTO = 86
    // El resto: 3 % contenedor, 4 % gasolina, 4 % munición, 1 % arma blanca, 2 % arma de fuego.
    private const val CONTENEDOR_UPTO = 89
    private const val GASOLINA_UPTO = 93
    private const val MUNICION_UPTO = 97
    private const val ARMA_BLANCA_UPTO = 98
    private const val VARIANTS = 10

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
            roll < VENDA_UPTO -> LootKind.VENDA
            roll < CONTENEDOR_UPTO -> LootKind.CONTENEDOR
            roll < GASOLINA_UPTO -> LootKind.GASOLINA
            roll < MUNICION_UPTO -> LootKind.MUNICION
            roll < ARMA_BLANCA_UPTO -> LootKind.ARMA_BLANCA
            else -> LootKind.ARMA_FUEGO
        }
    }

    /** Arma exacta de un punto de armas; la munición usa [variant] para elegir entre balas y cartuchos. */
    fun weaponFor(kind: LootKind, variant: Int): WeaponKind? = when (kind) {
        LootKind.ARMA_BLANCA -> when (variant % 3) { 0 -> WeaponKind.PALO; 1 -> WeaponKind.BATE; else -> WeaponKind.CUCHILLO }
        LootKind.ARMA_FUEGO -> if (variant < 7) WeaponKind.PISTOLA else WeaponKind.ESCOPETA
        else -> null
    }

    /** Munición de un punto de munición: balas casi siempre, cartuchos una de cada cuatro (y cuántas). */
    fun ammoFor(variant: Int): Pair<AmmoKind, Int> =
        if (variant < 7) AmmoKind.BALAS to 6 else AmmoKind.CARTUCHOS to 4

    /** Puntos de botín en las celdas alrededor de ([latitude], [longitude]); el id es estable por celda. */
    fun around(latitude: Double, longitude: Double): List<LootPoint> {
        val centerLat = cellOf(latitude)
        val centerLon = cellOf(longitude)
        val out = ArrayList<LootPoint>()
        for (cy in centerLat - RADIUS_CELLS..centerLat + RADIUS_CELLS) {
            for (cx in centerLon - RADIUS_CELLS..centerLon + RADIUS_CELLS) {
                val kind = kindAt(cy, cx) ?: continue
                val variant = (hash(cy, cx) / (DENSITY * ROLL_RANGE)) % VARIANTS
                out.add(LootPoint("$ID_PREFIX${cy}_$cx", kind, (cy + 0.5) * CELL_DEGREES, (cx + 0.5) * CELL_DEGREES, variant))
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

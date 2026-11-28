package ovh.gabrielhuav.pow.domain.models.survival

// Barril de lluvia (t87, paso 7): se arma con tablones, junta agua mientras llueve y se bebe de él. Vive en memoria como el
// huerto. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object BarrelRules {
    /** Tablones que cuesta armar un barril. */
    const val BOARD_COST = 2
    /** Barriles a la vez (el más viejo se pierde al armar otro). */
    const val MAX_BARRELS = 3
    /** Tragos que caben en un barril. */
    const val MAX_LEVEL = 4.0
    /** Tragos que junta por segundo de lluvia (uno cada 20 s). */
    const val FILL_PER_SECOND = 0.05

    fun canBuild(boards: Int): Boolean = boards >= BOARD_COST

    /** Nivel tras [seconds] segundos: solo sube con lluvia y nunca pasa de [MAX_LEVEL]. */
    fun fill(level: Double, weather: Weather, seconds: Double): Double =
        if (weather == Weather.RAIN) (level + FILL_PER_SECOND * seconds).coerceIn(0.0, MAX_LEVEL) else level

    /** Tragos enteros disponibles. */
    fun sips(level: Double): Int = level.toInt()

    /** Nivel tras un trago (no baja de 0). */
    fun afterSip(level: Double): Double = (level - 1.0).coerceAtLeast(0.0)

    fun check(level: Double, thirst: Double): TapResult = when {
        sips(level) < 1 -> TapResult.NO_WATER
        thirst >= WaterRules.NOT_THIRSTY_AT -> TapResult.NOT_THIRSTY
        else -> TapResult.DRANK
    }
}

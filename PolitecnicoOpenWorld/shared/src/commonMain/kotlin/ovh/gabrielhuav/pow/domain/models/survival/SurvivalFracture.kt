package ovh.gabrielhuav.pow.domain.models.survival

// Fracturas por choque fuerte (t10): caminar más lento un rato; se curan solas con el tiempo de supervivencia
// activa o con un botiquín. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object FractureRules {
    /** Fracción de la velocidad máxima a partir de la cual un choque puede fracturar. */
    const val MIN_SPEED_FRACTION = 0.7
    const val CHANCE = 0.4
    /** Tiempo de supervivencia activa que tarda en curarse sola. */
    const val DURATION_MS = 8L * 60_000L
    const val WALK_FACTOR = 0.6

    fun fracturesOnCrash(speed: Double, maxSpeed: Double, roll: Double): Boolean {
        if (maxSpeed <= 0.0 || !speed.isFinite()) return false
        return kotlin.math.abs(speed) / maxSpeed >= MIN_SPEED_FRACTION && roll < CHANCE
    }

    /** Instante (en ms de supervivencia activa) en que sana una fractura que ocurre en [activeMs]. */
    fun healsAt(activeMs: Long): Long = activeMs + DURATION_MS

    fun isFractured(activeMs: Long, healsAtMs: Long): Boolean = healsAtMs > activeMs

    /** Multiplicador de la velocidad al caminar. */
    fun speedFactor(activeMs: Long, healsAtMs: Long): Double = if (isFractured(activeMs, healsAtMs)) WALK_FACTOR else 1.0
}

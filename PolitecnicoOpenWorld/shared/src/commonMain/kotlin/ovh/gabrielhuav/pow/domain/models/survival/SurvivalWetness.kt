package ovh.gabrielhuav.pow.domain.models.survival

// Ropa mojada (t11): la lluvia empapa al jugador a la intemperie; se seca solo con el tiempo y más rápido dentro de un
// coche. Empapado suma un nivel de frío. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object WetnessRules {
    const val MAX = 100.0

    /** Desde este valor la ropa está empapada y cuenta como un nivel de frío. */
    const val SOAKED_AT = 50.0

    private const val RAIN_PER_SECOND = 1.5
    private const val FOG_PER_SECOND = 0.3
    private const val COAT_WET_FACTOR = 0.5
    private const val DRY_PER_SECOND = 0.4
    private const val DRY_SHELTERED_PER_SECOND = 1.5

    /** Humedad tras [seconds]: moja con lluvia o niebla a la intemperie (menos con abrigo); si no, se seca. */
    fun step(wet: Double, weather: Weather, coat: Boolean, sheltered: Boolean, seconds: Double): Double {
        val rate = when {
            sheltered -> -DRY_SHELTERED_PER_SECOND
            weather == Weather.RAIN -> RAIN_PER_SECOND * (if (coat) COAT_WET_FACTOR else 1.0)
            weather == Weather.FOG -> FOG_PER_SECOND * (if (coat) COAT_WET_FACTOR else 1.0)
            else -> -DRY_PER_SECOND
        }
        return (wet + rate * seconds).coerceIn(0.0, MAX)
    }

    fun isSoaked(wet: Double): Boolean = wet >= SOAKED_AT

    /** Gravedad 0..4 del moodle de ropa mojada. */
    fun level(wet: Double): Int = when {
        wet >= 80.0 -> 4
        wet >= 60.0 -> 3
        wet >= 40.0 -> 2
        wet >= 20.0 -> 1
        else -> 0
    }

    fun sanitized(wet: Double): Double = if (wet.isNaN()) 0.0 else wet.coerceIn(0.0, MAX)
}

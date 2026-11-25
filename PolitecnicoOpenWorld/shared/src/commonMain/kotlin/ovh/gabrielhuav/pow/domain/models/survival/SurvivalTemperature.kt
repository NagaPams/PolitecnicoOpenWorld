package ovh.gabrielhuav.pow.domain.models.survival

// Temperatura (t12/t13): sin ropa que abrigue, la noche, la lluvia y la niebla dan frío y cansan más. Reglas puras.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object TemperatureRules {
    const val MAX_COLD = 2
    const val ENERGY_DRAIN_PER_LEVEL = 0.15

    /** Frío 0 (templado), 1 (fresco) o 2 (helado): la noche suma uno y la lluvia o la niebla suman otro. */
    fun coldLevel(weather: Weather, night: Boolean): Int {
        val wet = if (weather == Weather.CLEAR) 0 else 1
        return ((if (night) 1 else 0) + wet).coerceAtMost(MAX_COLD)
    }

    /** Multiplicador del gasto de energía al moverse. */
    fun energyScale(weather: Weather, night: Boolean): Double =
        1.0 + ENERGY_DRAIN_PER_LEVEL * coldLevel(weather, night)
}

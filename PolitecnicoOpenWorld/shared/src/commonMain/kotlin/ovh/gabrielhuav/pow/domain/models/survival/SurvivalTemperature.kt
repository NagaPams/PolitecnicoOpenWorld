package ovh.gabrielhuav.pow.domain.models.survival

// Temperatura (t12/t13): sin ropa que abrigue, la noche, la lluvia y la niebla dan frío y cansan más. Reglas puras.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object TemperatureRules {
    const val MAX_COLD = 2
    const val ENERGY_DRAIN_PER_LEVEL = 0.15

    /**
     * Frío 0 (templado), 1 (fresco) o 2 (helado): la noche suma uno y la lluvia o la niebla suman otro. Un abrigo
     * ([coat]) y estar al abrigo dentro de un coche ([sheltered]) restan uno cada uno, sin bajar de 0. La ropa
     * empapada ([soaked], t11) suma otro, siempre con tope [MAX_COLD].
     */
    fun coldLevel(weather: Weather, night: Boolean, coat: Boolean = false, sheltered: Boolean = false, soaked: Boolean = false): Int {
        val damp = if (weather == Weather.CLEAR) 0 else 1
        val base = ((if (night) 1 else 0) + damp + (if (soaked) 1 else 0)).coerceAtMost(MAX_COLD)
        return (base - (if (coat) 1 else 0) - (if (sheltered) 1 else 0)).coerceAtLeast(0)
    }

    /** Multiplicador del gasto de energía al moverse. */
    fun energyScale(weather: Weather, night: Boolean, coat: Boolean = false, sheltered: Boolean = false, soaked: Boolean = false): Double =
        1.0 + ENERGY_DRAIN_PER_LEVEL * coldLevel(weather, night, coat, sheltered, soaked)
}

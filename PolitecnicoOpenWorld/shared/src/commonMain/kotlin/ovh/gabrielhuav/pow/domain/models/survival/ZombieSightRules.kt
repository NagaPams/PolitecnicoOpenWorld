package ovh.gabrielhuav.pow.domain.models.survival

/** t13: la niebla, la lluvia y la noche también le quitan vista a los zombis (misma idea que el radio del jugador). */
object ZombieSightRules {
    const val FOG_FACTOR = 0.6
    const val RAIN_FACTOR = 0.85
    const val NIGHT_FACTOR = 0.8

    /** Multiplicador (0..1) de la distancia a la que un zombi ve al jugador. Sin supervivencia siempre es 1. */
    fun factor(weather: Weather, night: Boolean): Double {
        val w = when (weather) {
            Weather.FOG -> FOG_FACTOR
            Weather.RAIN -> RAIN_FACTOR
            Weather.CLEAR -> 1.0
        }
        return if (night) w * NIGHT_FACTOR else w
    }
}

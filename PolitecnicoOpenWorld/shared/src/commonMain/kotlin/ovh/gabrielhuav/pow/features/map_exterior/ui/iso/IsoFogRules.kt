package ovh.gabrielhuav.pow.features.map_exterior.ui.iso

// Radio de la niebla de visión de la vista isométrica (t40/t58/t60). Regla pura para poder probarla sin dibujar.

internal object IsoFogRules {
    /** Radio mínimo en dp: aunque se aleje mucho el zoom queda un círculo visible alrededor del jugador. */
    const val MIN_RADIUS_DP = 40f

    /**
     * Radio en píxeles: la visión en metros por los píxeles por metro del zoom, limitada por arriba a
     * [maxFraction] del lado menor de la pantalla y por abajo a [MIN_RADIUS_DP] (sin pasar del tope).
     */
    fun radiusPx(visionMeters: Double, pixelsPerMeter: Double, scale: Float, density: Float, minDimension: Float, maxFraction: Float): Float {
        val max = (minDimension * maxFraction).coerceAtLeast(1f)
        val low = minOf(MIN_RADIUS_DP * density, max)
        return (visionMeters * pixelsPerMeter * scale).toFloat().coerceIn(low, max)
    }
}
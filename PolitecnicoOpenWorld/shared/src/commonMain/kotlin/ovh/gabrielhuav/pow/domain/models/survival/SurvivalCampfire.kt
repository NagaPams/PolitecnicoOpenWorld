package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.hypot

// Fogata (t14): se arma con tablones, arde unos minutos, da calor (como estar a cubierto: seca la ropa y quita un nivel
// de frío) y sirve de estufa al aire libre. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object CampfireRules {
    /** Lo que arde una fogata (ms de reloj real). */
    const val BURN_MS = 5L * 60L * 1000L
    /** A esta distancia (grados, ~8 m) o menos da calor. */
    const val WARM_DEG = 0.00007
    /** A esta distancia (grados, ~4 m) o menos se puede cocinar en ella. */
    const val COOK_DEG = 0.000036
    /** Se recuerdan a lo sumo tantas a la vez (cada una es un punto de botín más). */
    const val MAX_FIRES = 6

    fun isLit(nowMs: Long, untilMs: Long): Boolean = nowMs < untilMs

    fun litUntil(nowMs: Long): Long = nowMs + BURN_MS

    /** ¿La posición (lat, lon) está al calor de la fogata en (fireLat, fireLon)? */
    fun warms(lat: Double, lon: Double, fireLat: Double, fireLon: Double): Boolean =
        hypot(lat - fireLat, lon - fireLon) <= WARM_DEG
}

package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.hypot

// Trampa de latas (t09): una alarma que se arma con un tablón y un refresco; cuando un zombi pasa cerca suena y atrae a los
// zombis de la zona hacia ella (y avisa al jugador si la oye). Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object TrapRules {
    /** Un zombi a esta distancia (grados, ~3 m) o menos dispara la trampa. */
    const val TRIGGER_DEG = 0.000027
    /** Trampas puestas a la vez (las más viejas se pierden). */
    const val MAX_TRAPS = 8
    /** El jugador oye la trampa hasta esta distancia (grados, ~200 m). */
    const val HEARD_DEG = 0.0018
    const val BOARDS = 1
    const val SODAS = 1

    fun triggers(trapLat: Double, trapLon: Double, zombieLat: Double, zombieLon: Double): Boolean =
        hypot(trapLat - zombieLat, trapLon - zombieLon) <= TRIGGER_DEG

    fun heardBy(trapLat: Double, trapLon: Double, playerLat: Double, playerLon: Double): Boolean =
        hypot(trapLat - playerLat, trapLon - playerLon) <= HEARD_DEG
}

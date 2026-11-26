package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.hypot

// Policía que despeja zombis (t18): una patrulla o un policía a pie dispara al zombi que tiene cerca, y esa baja cuenta
// para la zona como si la hubiera hecho el jugador. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object PoliceSweepRules {
    /** Alcance del disparo de la policía (grados, ~25 m). */
    const val RANGE_DEG = 0.00022
    /** Cada unidad dispara a lo sumo una vez en este tiempo. */
    const val INTERVAL_MS = 3_000L
    /** Solo se simula la policía que el jugador tiene cerca (grados, ~170 m): fuera de eso no hay nada que ver. */
    const val NEAR_PLAYER_DEG = 0.0015

    fun ready(nowMs: Long, lastShotMs: Long): Boolean = nowMs - lastShotMs >= INTERVAL_MS

    fun inRange(unitLat: Double, unitLon: Double, zombieLat: Double, zombieLon: Double): Boolean =
        hypot(unitLat - zombieLat, unitLon - zombieLon) <= RANGE_DEG

    fun nearPlayer(unitLat: Double, unitLon: Double, playerLat: Double, playerLon: Double): Boolean =
        hypot(unitLat - playerLat, unitLon - playerLon) <= NEAR_PLAYER_DEG
}

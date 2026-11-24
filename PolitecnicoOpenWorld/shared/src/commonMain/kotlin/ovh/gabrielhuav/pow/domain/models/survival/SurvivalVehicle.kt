package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.abs

// Salud del coche en el modo supervivencia (t04): los choques contra paredes, edificios y otros coches la bajan según
// la velocidad; dañado a fondo el motor pierde fuerza y destruido deja de responder (como sin gasolina). Se repara con
// tablones. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object VehicleHealthRules {
    const val MAX = 100.0
    /** Daño de un choque contra pared o edificio a velocidad máxima. */
    const val WALL_DAMAGE_AT_TOP_SPEED = 45.0
    /** Daño de un choque contra otro coche a velocidad máxima. */
    const val CAR_DAMAGE_AT_TOP_SPEED = 28.0
    /** Por debajo de esta fracción de la velocidad máxima el golpe no cuenta (rozones y arranques contra la pared). */
    const val MIN_SPEED_FRACTION = 0.15
    /** Con menos salud que esto el coche no pasa de [DAMAGED_SPEED_FACTOR] de su velocidad máxima. */
    const val DAMAGED_BELOW = 35.0
    const val DAMAGED_SPEED_FACTOR = 0.6
    /** Lo que repara un tablón. */
    const val REPAIR_PER_BOARD = 25.0

    /** Salud tras un choque a [speed] (con signo) sobre [maxSpeed]; [againstWall] = pared o edificio, si no otro coche. */
    fun crash(health: Double, speed: Double, maxSpeed: Double, againstWall: Boolean): Double {
        if (maxSpeed <= 0.0) return health
        val fraction = (abs(speed) / maxSpeed).coerceIn(0.0, 1.0)
        if (fraction < MIN_SPEED_FRACTION) return health
        val top = if (againstWall) WALL_DAMAGE_AT_TOP_SPEED else CAR_DAMAGE_AT_TOP_SPEED
        return (health - fraction * top).coerceAtLeast(0.0)
    }

    fun isWrecked(health: Double): Boolean = health <= 0.0

    /** Multiplicador de la velocidad máxima según la salud. */
    fun speedFactor(health: Double): Double = when {
        isWrecked(health) -> 0.0
        health < DAMAGED_BELOW -> DAMAGED_SPEED_FACTOR
        else -> 1.0
    }

    fun repair(health: Double): Double = (health + REPAIR_PER_BOARD).coerceAtMost(MAX)

    fun sanitized(health: Double): Double = if (health.isNaN()) MAX else health.coerceIn(0.0, MAX)
}

package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.abs

// Salud del coche en el modo supervivencia (t04): los choques contra paredes, edificios y otros coches la bajan según
// la velocidad; dañado a fondo el motor pierde fuerza y destruido deja de responder (como sin gasolina). Se repara con
// tablones. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Puerta del coche (t05): subir lleva un momento y hace ruido; mientras tanto el coche no arranca. */
object CarDoorRules {
    const val LOCK_MS = 700L

    fun lockedUntil(nowMs: Long): Long = nowMs + LOCK_MS

    fun isLocked(nowMs: Long, lockedUntilMs: Long): Boolean = nowMs < lockedUntilMs
}

object VehicleHealthRules {
    const val MAX = 100.0
    /** Daño de un choque contra pared o edificio a velocidad máxima. */
    const val WALL_DAMAGE_AT_TOP_SPEED = 45.0
    /** Daño de un choque contra otro coche a velocidad máxima. */
    const val CAR_DAMAGE_AT_TOP_SPEED = 28.0
    /** Daño al coche por atropellar a un zombi a velocidad máxima (t04): menos que un choque, pero se acumula. */
    const val RUN_OVER_DAMAGE_AT_TOP_SPEED = 6.0
    /** Por debajo de esta fracción de la velocidad máxima el golpe no cuenta (rozones y arranques contra la pared). */
    const val MIN_SPEED_FRACTION = 0.15
    /** Con menos salud que esto el coche no pasa de [DAMAGED_SPEED_FACTOR] de su velocidad máxima. */
    const val DAMAGED_BELOW = 35.0
    const val DAMAGED_SPEED_FACTOR = 0.6
    /** Lo que repara un tablón. */
    const val REPAIR_PER_BOARD = 25.0
    /** Lo que repara una refacción (t87, paso 6): el doble que un tablón y sirve aunque el coche esté destruido. */
    const val REPAIR_PER_PART = 50.0

    /** Salud tras un choque a [speed] (con signo) sobre [maxSpeed]; [againstWall] = pared o edificio, si no otro coche. */
    fun crash(health: Double, speed: Double, maxSpeed: Double, againstWall: Boolean): Double {
        if (maxSpeed <= 0.0) return health
        val fraction = (abs(speed) / maxSpeed).coerceIn(0.0, 1.0)
        if (fraction < MIN_SPEED_FRACTION) return health
        val top = if (againstWall) WALL_DAMAGE_AT_TOP_SPEED else CAR_DAMAGE_AT_TOP_SPEED
        return (health - fraction * top).coerceAtLeast(0.0)
    }

    /** Salud tras atropellar a un zombi a [speed]; sin tope mínimo de velocidad (quien atropella ya va rápido). */
    fun runOver(health: Double, speed: Double, maxSpeed: Double): Double {
        if (maxSpeed <= 0.0 || !speed.isFinite()) return health
        val fraction = (abs(speed) / maxSpeed).coerceIn(0.0, 1.0)
        return (health - fraction * RUN_OVER_DAMAGE_AT_TOP_SPEED).coerceAtLeast(0.0)
    }

    fun isWrecked(health: Double): Boolean = health <= 0.0

    /** Multiplicador de la velocidad máxima según la salud. */
    fun speedFactor(health: Double): Double = when {
        isWrecked(health) -> 0.0
        health < DAMAGED_BELOW -> DAMAGED_SPEED_FACTOR
        else -> 1.0
    }

    fun repair(health: Double): Double = (health + REPAIR_PER_BOARD).coerceAtMost(MAX)

    fun repairWithPart(health: Double): Double = (health + REPAIR_PER_PART).coerceAtMost(MAX)

    fun sanitized(health: Double): Double = if (health.isNaN()) MAX else health.coerceIn(0.0, MAX)
}

/** t03: un coche ajeno que se aborda por primera vez viene con su propio estado (gasolina y salud), fijo según su id. */
/** Gasolina y salud propias de un coche (t03): se guardan al bajar y se recuperan al volver a subir. */
data class CarState(val fuel: Double, val health: Double)

object CarStateRules {
    /** Cuántos coches recordamos (los más recientes); el resto vuelve a un estado nuevo al subir. */
    const val MAX_STATES = 40

    /** [states] con [id] añadido al final (el más reciente) y recortado a [MAX_STATES]. */
    fun remember(states: Map<String, CarState>, id: String, state: CarState): Map<String, CarState> {
        val next = LinkedHashMap(states)
        next.remove(id)
        next[id] = CarState(FuelRules.sanitized(state.fuel), VehicleHealthRules.sanitized(state.health))
        while (next.size > MAX_STATES) next.remove(next.keys.first())
        return next
    }

    /** `id|gasolina|salud` separados por `;` (los ids son UUID: no llevan esos caracteres). */
    fun encode(states: Map<String, CarState>): String =
        states.entries.joinToString(";") { "${it.key}|${it.value.fuel}|${it.value.health}" }

    fun decode(text: String): Map<String, CarState> {
        val out = LinkedHashMap<String, CarState>()
        for (part in text.split(';')) {
            val f = part.split('|')
            if (f.size != 3 || f[0].isEmpty()) continue
            val fuel = f[1].toDoubleOrNull() ?: continue
            val health = f[2].toDoubleOrNull() ?: continue
            out[f[0]] = CarState(FuelRules.sanitized(fuel), VehicleHealthRules.sanitized(health))
        }
        while (out.size > MAX_STATES) out.remove(out.keys.first())
        return out
    }
}

object StrangerCarRules {
    const val MIN_FUEL = 15.0
    const val MAX_FUEL = 60.0
    const val MIN_HEALTH = 55.0

    private fun spread(seed: Int, salt: Int): Double {
        val mixed = (seed * 31 + salt) * -1640531527
        return ((mixed ushr 8) and 0xFFFF) / 65535.0
    }

    fun fuelFor(seed: Int): Double = MIN_FUEL + (MAX_FUEL - MIN_FUEL) * spread(seed, 1)

    fun healthFor(seed: Int): Double = MIN_HEALTH + (VehicleHealthRules.MAX - MIN_HEALTH) * spread(seed, 2)
}

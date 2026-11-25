package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.abs

// Gasolina del coche en el modo supervivencia (t03): un tanque que baja con la distancia recorrida y se rellena con
// bidones del botín. Sin gasolina el motor no responde al acelerador. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object FuelRules {
    const val MAX = 100.0
    const val START = 70.0
    /** Lo que repone un bidón. */
    const val CAN_FILL = 40.0
    const val MAX_CANS = 3
    /** Por paso del bucle a velocidad máxima (~30 pasos/s): un tanque lleno dura unos 3 min a tope. */
    const val BURN_PER_TICK_AT_TOP_SPEED = 0.018
    /** Si el tanque pasa de esto, un bidón recogido al volante se guarda en vez de gastarse. */
    const val USE_NOW_BELOW = 60.0

    fun isEmpty(fuel: Double): Boolean = fuel <= 0.0

    /** Tanque tras un paso a [speed] (con signo) sobre [maxSpeed]; nunca baja de 0. */
    fun burn(fuel: Double, speed: Double, maxSpeed: Double): Double {
        if (maxSpeed <= 0.0) return fuel
        val fraction = (abs(speed) / maxSpeed).coerceIn(0.0, 1.0)
        return (fuel - fraction * BURN_PER_TICK_AT_TOP_SPEED).coerceAtLeast(0.0)
    }

    fun refill(fuel: Double): Double = (fuel + CAN_FILL).coerceAtMost(MAX)

    /** Lo que surte una bomba por segundo con el coche parado junto a una gasolinera (t03). */
    const val PUMP_PER_SECOND = 8.0
    /** Distancia máxima (m) a una gasolinera para surtir. */
    const val PUMP_RADIUS_M = 30.0

    fun pump(fuel: Double, seconds: Double): Double = (fuel + PUMP_PER_SECOND * seconds).coerceIn(fuel, MAX)

    fun isFull(fuel: Double): Boolean = fuel >= MAX - 0.01

    fun sanitized(fuel: Double): Double = if (fuel.isNaN()) START else fuel.coerceIn(0.0, MAX)
}

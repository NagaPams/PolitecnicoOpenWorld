package ovh.gabrielhuav.pow.domain.models.survival

// Linterna (t25, pieza 3): se enciende y agranda de noche el radio que ve el jugador; gasta batería mientras está encendida y
// se recarga con pilas (botín). Complementa a la antorcha de pantalla (TorchRules). Reglas puras.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object FlashlightRules {
    const val MAX_CHARGE = 100.0
    /** Batería con la que arranca una partida nueva. */
    const val START_CHARGE = 60.0
    /** Batería que gasta por segundo encendida (una carga completa dura ~3 min 20 s). */
    const val DRAIN_PER_SECOND = 0.5
    /** Batería que devuelve cada pila. */
    const val BATTERY_CHARGE = 60.0
    /** Con la linterna encendida el radio de visión se multiplica por esto y su tope sube a [MAX_FRACTION_LIT] de la pantalla. */
    const val VISION_FACTOR = 1.7
    const val MAX_FRACTION_LIT = 0.6f

    fun canTurnOn(charge: Double): Boolean = charge > 0.0

    /** Batería tras [seconds]: solo baja encendida y no pasa de 0. */
    fun drain(charge: Double, on: Boolean, seconds: Double): Double =
        if (on) (charge - DRAIN_PER_SECOND * seconds).coerceAtLeast(0.0) else charge

    fun canSwap(batteries: Int, charge: Double): Boolean = batteries >= 1 && charge < MAX_CHARGE

    /** Batería tras gastar una pila. */
    fun swap(charge: Double): Double = (charge + BATTERY_CHARGE).coerceAtMost(MAX_CHARGE)

    fun visionFactor(lit: Boolean): Double = if (lit) VISION_FACTOR else 1.0

    fun sanitized(charge: Double): Double = if (charge.isNaN()) START_CHARGE else charge.coerceIn(0.0, MAX_CHARGE)
}

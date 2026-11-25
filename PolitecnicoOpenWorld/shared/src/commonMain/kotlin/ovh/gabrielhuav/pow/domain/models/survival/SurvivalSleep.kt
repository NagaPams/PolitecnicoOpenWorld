package ovh.gabrielhuav.pow.domain.models.survival

// Dormir (t12, modo supervivencia): a pie y sin zombis cerca el jugador se queda quieto unos segundos y recupera
// energía a cambio de hambre y sed (pasa tiempo "de juego"). Un zombi que se acerca o una mordida lo despiertan sin
// ganar nada. De noche rinde más. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

enum class SleepCheck { OK, TOO_RESTED, ZOMBIES_NEAR }

object SleepRules {
    /** Lo que dura dormir en tiempo real. */
    const val SLEEP_MS = 5000L
    /** Con tanta energía o más no da sueño. */
    const val TOO_RESTED_AT = 85.0
    /** Con un zombi más cerca que esto (m) no se puede empezar a dormir. */
    const val BLOCK_METERS = 40.0
    /** Un zombi a esta distancia (m) o menos despierta al jugador. */
    const val WAKE_METERS = 25.0
    private const val ENERGY_GAIN_NIGHT = 70.0
    private const val ENERGY_GAIN_DAY = 40.0
    /** t46: dormir equivale a pasar estas horas de juego; el costo sale de los ritmos de [SurvivalRules] (en reposo). */
    private const val SLEEP_GAME_HOURS = 3.0
    private const val HUNGER_COST = SurvivalRules.HUNGER_PER_DAY * SLEEP_GAME_HOURS / 24.0
    private const val THIRST_COST = SurvivalRules.THIRST_PER_DAY * SLEEP_GAME_HOURS / 24.0
    /** Dormir no deja el hambre ni la sed por debajo de esto (si ya estaban peor, no empeoran). */
    private const val FLOOR = 5.0

    /** [nearestZombieMeters] es `Double.MAX_VALUE` si no hay zombis. */
    fun check(energy: Double, nearestZombieMeters: Double): SleepCheck = when {
        energy >= TOO_RESTED_AT -> SleepCheck.TOO_RESTED
        nearestZombieMeters <= BLOCK_METERS -> SleepCheck.ZOMBIES_NEAR
        else -> SleepCheck.OK
    }

    fun shouldWake(nearestZombieMeters: Double): Boolean = nearestZombieMeters <= WAKE_METERS

    fun isSleeping(nowMs: Long, sleepUntilMs: Long): Boolean = nowMs < sleepUntilMs

    fun sleepUntil(nowMs: Long): Long = nowMs + SLEEP_MS

    /** Necesidades tras dormir una vez; nunca baja el hambre o la sed por debajo de [FLOOR] ni sube de 100. */
    fun rested(needs: SurvivalNeeds, night: Boolean): SurvivalNeeds = SurvivalNeeds(
        hunger = lowered(needs.hunger, HUNGER_COST),
        thirst = lowered(needs.thirst, THIRST_COST),
        energy = (needs.energy + if (night) ENERGY_GAIN_NIGHT else ENERGY_GAIN_DAY).coerceAtMost(100.0),
    )

    private fun lowered(value: Double, cost: Double): Double =
        maxOf(value - cost, minOf(value, FLOOR)).coerceIn(0.0, 100.0)
}

package ovh.gabrielhuav.pow.domain.models.survival

/** Valores de bienestar: 100 es completo y 0 es agotado. */
data class SurvivalNeeds(
    val hunger: Double = 100.0,
    val thirst: Double = 100.0,
    val energy: Double = 100.0,
)

enum class SurvivalActivity { IDLE, WALKING, RUNNING, DRIVING }
enum class NeedLevel { OK, LOW, CRITICAL }

/** Consumibles de la tienda del campus. Si se agregan más, van AL FINAL (viajan por nombre). */
enum class SurvivalItem {
    PIZZA, REFRESCO, SUERO, TABLONES;

    companion object {
        fun fromName(name: String): SurvivalItem? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

data class SurvivalConsumeResult(
    val needs: SurvivalNeeds,
    val infection: InfectionState,
    val healthBonus: Float,
    /** false si el consumible no cambiaría nada: no se cobra. */
    val effective: Boolean,
)

object SurvivalRules {
    private const val CRITICAL_THRESHOLD = 15.0
    private const val LOW_THRESHOLD = 35.0
    const val HEALTH_FLOOR = 10.0
    const val BITE_INFECTION_PROBABILITY = 0.25

    // t37: el ritmo se mide en DÍAS de juego (DayRules.DAY_MS), así cambiar la duración del día no desajusta la
    // supervivencia. En reposo el hambre dura 1,2 días, la sed 0,8 y la energía 1 día; correr triplica el gasto.
    private const val SECONDS_PER_DAY = DayRules.DAY_MS / 1000.0
    private const val HUNGER_RATE = 100.0 / (SECONDS_PER_DAY * 1.2)
    private const val THIRST_RATE = 100.0 / (SECONDS_PER_DAY * 0.8)
    private const val ENERGY_RATE = 100.0 / SECONDS_PER_DAY
    private const val REST_ENERGY_GAIN = 0.05
    private const val FOOD_RESTORE = 40.0
    private const val DRINK_RESTORE = 40.0
    private const val DRINK_ENERGY = 10.0
    private const val INFECTION_HEALTH_RATE = 0.02

    fun level(value: Double): NeedLevel = when {
        value <= CRITICAL_THRESHOLD -> NeedLevel.CRITICAL
        value <= LOW_THRESHOLD -> NeedLevel.LOW
        else -> NeedLevel.OK
    }

    /** [roll] es un número en 0..1; se inyecta para poder probarlo sin azar. */
    fun biteInfects(roll: Double): Boolean = roll < BITE_INFECTION_PROBABILITY

    fun priceOf(item: SurvivalItem): Int = when (item) {
        SurvivalItem.PIZZA -> 15
        SurvivalItem.REFRESCO -> 12
        SurvivalItem.SUERO -> 40
        SurvivalItem.TABLONES -> 20
    }

    fun consume(item: SurvivalItem, needs: SurvivalNeeds, infection: InfectionState): SurvivalConsumeResult =
        when (item) {
            SurvivalItem.PIZZA -> {
                val updated = needs.copy(hunger = (needs.hunger + FOOD_RESTORE).coerceAtMost(100.0))
                SurvivalConsumeResult(updated, infection, 20f, updated != needs)
            }
            SurvivalItem.REFRESCO -> {
                val updated = needs.copy(
                    thirst = (needs.thirst + DRINK_RESTORE).coerceAtMost(100.0),
                    energy = (needs.energy + DRINK_ENERGY).coerceAtMost(100.0),
                )
                SurvivalConsumeResult(updated, infection, 10f, updated != needs)
            }
            SurvivalItem.SUERO ->
                if (infection.infected) SurvivalConsumeResult(needs, infection.cure(), 0f, true)
                else SurvivalConsumeResult(needs, infection, 0f, false)
            // No se consume: se guarda como material (SurvivalManager.addBoards) para barricadar puertas.
            SurvivalItem.TABLONES -> SurvivalConsumeResult(needs, infection, 0f, false)
        }

    /**
     * Quieto (IDLE) la energía se RECUPERA (descansar); en cualquier otra actividad se gasta.
     * Con la infección sintomática se recupera menos y se gasta más.
     */
    fun advance(
        needs: SurvivalNeeds,
        activity: SurvivalActivity,
        night: Boolean,
        seconds: Double,
        infection: InfectionState? = null,
        thirstScale: Double = 1.0,
    ): SurvivalNeeds {
        if (!seconds.isFinite() || seconds <= 0.0) return needs
        val multiplier = when (activity) {
            SurvivalActivity.IDLE -> 1.0
            SurvivalActivity.WALKING -> 1.5
            SurvivalActivity.RUNNING -> 3.0
            SurvivalActivity.DRIVING -> 1.2
        }
        val sick = infection?.symptomatic == true
        val energy = if (activity == SurvivalActivity.IDLE) {
            val gain = REST_ENERGY_GAIN * (if (night) 0.8 else 1.0) * (if (sick) 0.5 else 1.0)
            needs.energy + seconds * gain
        } else {
            val drain = ENERGY_RATE * multiplier * (if (night) 1.25 else 1.0) * (if (sick) 1.5 else 1.0)
            needs.energy - seconds * drain
        }
        return SurvivalNeeds(
            hunger = (needs.hunger - seconds * HUNGER_RATE * multiplier).coerceIn(0.0, 100.0),
            thirst = (needs.thirst - seconds * THIRST_RATE * multiplier * thirstScale).coerceIn(0.0, 100.0),
            energy = energy.coerceIn(0.0, 100.0),
        )
    }

    /** Las necesidades críticas y la infección sintomática desgastan la vida, pero nunca por debajo del piso. */
    fun healthAfterNeedsDamage(
        health: Double,
        needs: SurvivalNeeds,
        seconds: Double,
        infection: InfectionState? = null,
    ): Double {
        if (!seconds.isFinite() || seconds <= 0.0 || health <= HEALTH_FLOOR) return health
        val criticalCount = (if (needs.hunger <= CRITICAL_THRESHOLD) 1 else 0) +
            (if (needs.thirst <= CRITICAL_THRESHOLD) 1 else 0) +
            (if (needs.energy <= CRITICAL_THRESHOLD) 1 else 0)
        val rate = criticalCount * 0.01 + (if (infection?.symptomatic == true) INFECTION_HEALTH_RATE else 0.0)
        if (rate == 0.0) return health
        return (health - seconds * rate).coerceAtLeast(HEALTH_FLOOR)
    }
}

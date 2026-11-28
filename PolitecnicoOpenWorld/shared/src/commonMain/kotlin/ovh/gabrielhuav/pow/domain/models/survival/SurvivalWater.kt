package ovh.gabrielhuav.pow.domain.models.survival

// Agua corriente (t14): los grifos de los interiores dan agua para beber hasta que la red se corta, unos días después
// del apagón (como en Project Zomboid, donde el agua y la luz se cortan tras un tiempo). Reglas puras.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Al final de la lista de valores: [DIRTY] = el grifo ya no tiene red, pero se juntó agua sucia en un recipiente. */
enum class TapResult { DRANK, NO_WATER, NOT_THIRSTY, DIRTY, WASHED }

object WaterRules {
    /** El agua se corta desde este día de supervivencia (la luz lo hace el día [BlackoutRules.FROM_DAY]). */
    const val CUT_FROM_DAY = 5
    /** Lo que repone beber una vez del grifo. */
    const val DRINK_RESTORE = 25.0
    /** Con la sed por encima de esto no se bebe. */
    const val NOT_THIRSTY_AT = 90.0

    fun running(day: Int): Boolean = day < CUT_FROM_DAY

    fun check(day: Int, thirst: Double): TapResult = when {
        !running(day) -> TapResult.NO_WATER
        thirst >= NOT_THIRSTY_AT -> TapResult.NOT_THIRSTY
        else -> TapResult.DRANK
    }

    /** Necesidades tras beber una vez del grifo (la sed sube hasta 100). */
    fun drink(needs: SurvivalNeeds): SurvivalNeeds = needs.copy(thirst = (needs.thirst + DRINK_RESTORE).coerceAtMost(100.0))
}

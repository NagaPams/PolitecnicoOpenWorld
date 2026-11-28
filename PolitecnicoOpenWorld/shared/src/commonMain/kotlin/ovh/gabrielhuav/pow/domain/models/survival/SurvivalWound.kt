package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.min

// Suciedad e infección local de la herida (t87, paso 2). Una herida abierta (sangrado) se ensucia; vendarla sobre una herida ya
// sucia deja la venda sucia y la herida sigue pudriéndose; al llegar al tope se infecta y quita vida. Desinfectar (antiséptico)
// la limpia y suturar (un botiquín) la cierra. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object WoundRules {
    const val MAX = 100.0
    /** Desde aquí la herida está sucia: una venda puesta encima también se ensucia y la herida sigue empeorando. */
    const val DIRTY_FROM = 40.0
    /** En este valor la herida se infecta y quita vida hasta limpiarla. */
    const val INFECTED_AT = 100.0

    /** Sube por segundo mientras la herida sigue abierta (sangrando). */
    private const val OPEN_PER_SECOND = 0.8
    /** Sube por segundo con la herida cerrada pero ya sucia (venda sucia). */
    private const val DIRTY_BANDAGE_PER_SECOND = 0.3
    /** Baja por segundo con la herida cerrada y limpia: el cuerpo se defiende. */
    private const val CLEAN_DECAY_PER_SECOND = 0.1
    /** Vida por segundo que quita una herida infectada. */
    const val INFECTION_HP_PER_SECOND = 0.4

    /** Lo que baja la suciedad al lavar la herida con agua limpia del grifo (el antiséptico la deja en 0). */
    const val WASH_AMOUNT = 40.0

    /** ¿Vale la pena lavar? Hay algo de suciedad. */
    fun canWash(dirt: Double): Boolean = dirt > 0.0

    fun wash(dirt: Double): Double = sanitized(dirt - WASH_AMOUNT)

    /** Hasta cuánto baja la suciedad al suturar. */
    const val SUTURE_CAP = 20.0

    /** Suciedad tras [seconds] segundos. */
    fun step(dirt: Double, bleeding: Boolean, seconds: Double): Double {
        if (!seconds.isFinite() || seconds <= 0.0) return sanitized(dirt)
        val rate = when {
            bleeding -> OPEN_PER_SECOND
            dirt >= DIRTY_FROM -> DIRTY_BANDAGE_PER_SECOND
            else -> -CLEAN_DECAY_PER_SECOND
        }
        return sanitized(dirt + rate * seconds)
    }

    fun isDirty(dirt: Double): Boolean = dirt >= DIRTY_FROM

    fun isInfected(dirt: Double): Boolean = dirt >= INFECTED_AT

    /** Vida tras [seconds] segundos con la herida en [dirt]; solo una herida infectada quita vida. */
    fun healthAfter(health: Double, dirt: Double, seconds: Double): Double {
        if (!isInfected(dirt) || !seconds.isFinite() || seconds <= 0.0 || health <= SurvivalRules.HEALTH_FLOOR) return health
        return (health - seconds * INFECTION_HP_PER_SECOND).coerceAtLeast(SurvivalRules.HEALTH_FLOOR)
    }

    /** ¿Se puede desinfectar? Hay antiséptico y la herida tiene algo de suciedad o sangra. */
    fun canDisinfect(pack: SurvivalPack, dirt: Double, bleeding: Boolean): Boolean = pack.antisepticos > 0 && (dirt > 0.0 || bleeding)

    /** ¿Se puede suturar? Hay botiquín y la herida sigue abierta. */
    fun canSuture(pack: SurvivalPack, bleeding: Boolean): Boolean = pack.botiquin > 0 && bleeding

    fun disinfect(pack: SurvivalPack): SurvivalPack = pack.copy(antisepticos = (pack.antisepticos - 1).coerceAtLeast(0))

    fun suture(pack: SurvivalPack): SurvivalPack = pack.copy(botiquin = (pack.botiquin - 1).coerceAtLeast(0))

    /** Suciedad tras suturar: se cierra la herida, pero lo que ya estaba sucio no se limpia del todo. */
    fun dirtAfterSuture(dirt: Double): Double = min(sanitized(dirt), SUTURE_CAP)

    fun sanitized(dirt: Double): Double = if (dirt.isFinite()) dirt.coerceIn(0.0, MAX) else 0.0
}

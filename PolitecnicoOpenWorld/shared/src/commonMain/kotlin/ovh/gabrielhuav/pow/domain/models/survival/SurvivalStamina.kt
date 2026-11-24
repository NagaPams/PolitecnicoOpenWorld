package ovh.gabrielhuav.pow.domain.models.survival

// Aliento (t11): correr lo gasta y, al agotarse, solo se camina hasta recuperar un mínimo. Puro y sin estado propio:
// el ViewModel guarda [StaminaState] y lo avanza en cada paso a pie. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

data class StaminaState(val value: Double = StaminaRules.MAX, val exhausted: Boolean = false)

object StaminaRules {
    const val MAX = 100.0
    /** Por paso de movimiento corriendo (~30 pasos/s): unos 9 s de carrera con el aliento lleno. */
    const val DRAIN_PER_STEP = 0.35
    /** Por paso de movimiento caminando. */
    const val WALK_REGEN_PER_STEP = 0.05
    /** Por segundo quieto (lo aplica el tick de 1 s). */
    const val IDLE_REGEN_PER_SECOND = 8.0
    /** Agotado, no se vuelve a correr hasta recuperar esto. */
    const val RECOVER_AT = 25.0

    /** Un paso a pie; [running] = el jugador mantiene el botón de correr. */
    fun step(state: StaminaState, running: Boolean): StaminaState {
        if (running && !state.exhausted) {
            val next = (state.value - DRAIN_PER_STEP).coerceAtLeast(0.0)
            return StaminaState(next, next <= 0.0)
        }
        val regen = (state.value + WALK_REGEN_PER_STEP).coerceAtMost(MAX)
        return StaminaState(regen, state.exhausted && regen < RECOVER_AT)
    }

    /** Descanso: [seconds] sin moverse. */
    fun rest(state: StaminaState, seconds: Double): StaminaState {
        if (!seconds.isFinite() || seconds <= 0.0) return state
        val next = (state.value + seconds * IDLE_REGEN_PER_SECOND).coerceAtMost(MAX)
        return StaminaState(next, state.exhausted && next < RECOVER_AT)
    }

    /** ¿Este paso corre de verdad? Solo si lo pide el jugador y no está agotado. */
    fun runsNow(state: StaminaState, running: Boolean): Boolean = running && !state.exhausted
}

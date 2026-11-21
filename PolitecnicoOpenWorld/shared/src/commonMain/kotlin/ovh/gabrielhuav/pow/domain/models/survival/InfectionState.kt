package ovh.gabrielhuav.pow.domain.models.survival

/** Tiempo de juego acumulado: el llamador lo detiene durante misiones y pausas. */
data class InfectionState(val infected: Boolean = false, val elapsedMs: Long = 0L) {
    val symptomatic: Boolean get() = infected && elapsedMs >= INCUBATION_MS

    fun startInfection(): InfectionState = if (infected) this else InfectionState(infected = true)

    fun advance(deltaMs: Long): InfectionState {
        if (!infected || deltaMs <= 0L || elapsedMs >= INCUBATION_MS) return this
        val remaining = INCUBATION_MS - elapsedMs.coerceAtLeast(0L)
        return copy(elapsedMs = elapsedMs.coerceAtLeast(0L) + deltaMs.coerceAtMost(remaining))
    }

    fun cure(): InfectionState = InfectionState()

    companion object {
        const val INCUBATION_MS = 600_000L
    }
}

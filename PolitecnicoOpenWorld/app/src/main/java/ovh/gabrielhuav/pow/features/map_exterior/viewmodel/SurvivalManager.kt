package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.map.DoorRuntime
import ovh.gabrielhuav.pow.domain.models.survival.InfectionState
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalActivity
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalConsumeResult
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalItem
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalNeeds
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalRules

data class SurvivalSubState(
    val enabled: Boolean = false,
    val needs: SurvivalNeeds = SurvivalNeeds(),
    val infection: InfectionState = InfectionState(),
    val boards: Int = 0,
    /** Solo puertas cuyo estado cambió respecto al esquema; clave `<huella>_e<arista>`. */
    val doors: Map<String, DoorRuntime> = emptyMap(),
)

internal const val MAX_BOARDS_CARRIED = 9

class SurvivalManager(enabled: Boolean = false) {
    private val mutableState = MutableStateFlow(SurvivalSubState(enabled = enabled))
    val state = mutableState.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        if (mutableState.value.enabled != enabled) mutableState.value = mutableState.value.copy(enabled = enabled)
    }

    fun resetForNewGame() {
        mutableState.value = SurvivalSubState(enabled = mutableState.value.enabled)
    }

    fun addBoards(count: Int) {
        mutableState.update { it.copy(boards = (it.boards + count).coerceIn(0, MAX_BOARDS_CARRIED)) }
    }

    fun setDoors(boards: Int, doors: Map<String, DoorRuntime>) {
        mutableState.update { it.copy(boards = boards.coerceIn(0, MAX_BOARDS_CARRIED), doors = doors) }
    }

    fun restore(needs: SurvivalNeeds, infected: Boolean, elapsedMs: Long, boards: Int = 0, doors: Map<String, DoorRuntime> = emptyMap()) {
        mutableState.value = SurvivalSubState(
            boards = boards.coerceIn(0, MAX_BOARDS_CARRIED),
            doors = doors,
            enabled = mutableState.value.enabled,
            needs = SurvivalNeeds(
                hunger = needs.hunger.coerceIn(0.0, 100.0),
                thirst = needs.thirst.coerceIn(0.0, 100.0),
                energy = needs.energy.coerceIn(0.0, 100.0),
            ),
            infection = InfectionState(infected, if (infected) elapsedMs.coerceIn(0, InfectionState.INCUBATION_MS) else 0),
        )
    }

    /** Inicia la infección si no existía. Devuelve true solo cuando empezó en esta llamada. */
    fun infect(): Boolean {
        var started = false
        mutableState.update { current ->
            started = !current.infection.infected
            if (started) current.copy(infection = current.infection.startInfection()) else current
        }
        return started
    }

    /** Aplica el consumible al estado actual (el llamador decide antes si cobra o no). */
    fun consume(item: SurvivalItem): SurvivalConsumeResult {
        var result = SurvivalConsumeResult(SurvivalNeeds(), InfectionState(), 0f, false)
        mutableState.update { current ->
            result = SurvivalRules.consume(item, current.needs, current.infection)
            current.copy(needs = result.needs, infection = result.infection)
        }
        return result
    }

    fun tick(
        deltaMs: Long,
        enabled: Boolean,
        paused: Boolean,
        activity: SurvivalActivity,
        night: Boolean,
        health: Float,
    ): Float {
        if (!enabled || paused || deltaMs <= 0) return health
        val current = mutableState.value
        val infection = current.infection.advance(deltaMs)
        val seconds = deltaMs / 1000.0
        val needs = SurvivalRules.advance(current.needs, activity, night, seconds, infection)
        mutableState.value = current.copy(needs = needs, infection = infection)
        return SurvivalRules.healthAfterNeedsDamage(health.toDouble(), needs, seconds, infection).toFloat()
    }
}

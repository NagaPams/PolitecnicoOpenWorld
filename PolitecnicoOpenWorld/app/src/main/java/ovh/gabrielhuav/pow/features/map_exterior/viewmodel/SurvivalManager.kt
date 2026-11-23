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
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalCrafting
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalPack
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalRules

data class SurvivalSubState(
    val enabled: Boolean = false,
    val needs: SurvivalNeeds = SurvivalNeeds(),
    val infection: InfectionState = InfectionState(),
    val boards: Int = 0,
    /** Solo puertas cuyo estado cambió respecto al esquema; clave `<huella>_e<arista>`. */
    val doors: Map<String, DoorRuntime> = emptyMap(),
    val pack: SurvivalPack = SurvivalPack(),
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

    fun setPack(pack: SurvivalPack) {
        mutableState.update { it.copy(pack = SurvivalCrafting.sanitized(pack)) }
    }

    fun restore(
        needs: SurvivalNeeds, infected: Boolean, elapsedMs: Long, boards: Int = 0,
        doors: Map<String, DoorRuntime> = emptyMap(), pack: SurvivalPack = SurvivalPack(),
    ) {
        mutableState.value = SurvivalSubState(
            pack = SurvivalCrafting.sanitized(pack),
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

    /**
     * Avanza necesidades e infección y devuelve el estado resultante, o null si no corre (apagado, en pausa o
     * sin tiempo). NO toca la vida: así el cálculo va FUERA del bloqueo de vida (h12) y solo el daño se aplica dentro.
     */
    fun advance(deltaMs: Long, enabled: Boolean, paused: Boolean, activity: SurvivalActivity, night: Boolean): SurvivalSubState? {
        if (!enabled || paused || deltaMs <= 0) return null
        val current = mutableState.value
        val infection = current.infection.advance(deltaMs)
        val needs = SurvivalRules.advance(current.needs, activity, night, deltaMs / 1000.0, infection)
        val next = current.copy(needs = needs, infection = infection)
        mutableState.value = next
        return next
    }

    /** Vida tras el desgaste de [state] durante [deltaMs] (puro: cabe dentro de `updateHealth`). */
    fun healthAfter(health: Float, state: SurvivalSubState, deltaMs: Long): Float =
        SurvivalRules.healthAfterNeedsDamage(health.toDouble(), state.needs, deltaMs / 1000.0, state.infection).toFloat()

    fun tick(
        deltaMs: Long,
        enabled: Boolean,
        paused: Boolean,
        activity: SurvivalActivity,
        night: Boolean,
        health: Float,
    ): Float {
        val next = advance(deltaMs, enabled, paused, activity, night) ?: return health
        return healthAfter(health, next, deltaMs)
    }
}

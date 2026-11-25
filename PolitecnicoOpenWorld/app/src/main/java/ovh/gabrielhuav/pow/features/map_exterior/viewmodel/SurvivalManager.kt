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
import ovh.gabrielhuav.pow.domain.models.survival.BleedingRules
import ovh.gabrielhuav.pow.domain.models.survival.FuelRules
import ovh.gabrielhuav.pow.domain.models.survival.StaminaRules
import ovh.gabrielhuav.pow.domain.models.survival.VehicleHealthRules
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalArsenal
import ovh.gabrielhuav.pow.domain.models.survival.WeaponRules
import ovh.gabrielhuav.pow.domain.models.survival.Weather
import ovh.gabrielhuav.pow.domain.models.survival.CraftProgress
import ovh.gabrielhuav.pow.domain.models.survival.Occupation
import ovh.gabrielhuav.pow.domain.models.survival.Skill
import ovh.gabrielhuav.pow.domain.models.survival.SkillRules
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalSkills
import ovh.gabrielhuav.pow.domain.models.survival.WeatherRules

data class SurvivalSubState(
    val enabled: Boolean = false,
    val needs: SurvivalNeeds = SurvivalNeeds(),
    val infection: InfectionState = InfectionState(),
    val boards: Int = 0,
    /** Solo puertas cuyo estado cambió respecto al esquema; clave `<huella>_e<arista>`. */
    val doors: Map<String, DoorRuntime> = emptyMap(),
    val pack: SurvivalPack = SurvivalPack(),
    val arsenal: SurvivalArsenal = SurvivalArsenal(),
    /** Gasolina del coche (0-100); la fuente es `WorldMapViewModel.fuelTank` y aquí se publica cada segundo. */
    val fuel: Double = FuelRules.START,
    val bleeding: Boolean = false,
    /** Aliento del jugador (0-100); la fuente es `WorldMapViewModel.stamina` y aquí se publica cada segundo. */
    val stamina: Double = StaminaRules.MAX,
    /** Salud del coche (0-100); la fuente es `WorldMapViewModel.vehicleHealth` y aquí se publica cada segundo. */
    val vehicleHealth: Double = VehicleHealthRules.MAX,
    /** Clima actual (t13): sale del reloj de juego, no se guarda. */
    val weather: Weather = Weather.CLEAR,
    /** XP de habilidades y oficio elegido (t15). */
    val skills: SurvivalSkills = SurvivalSkills(),
    val occupation: Occupation = Occupation.NINGUNO,
    /** Tiempo de supervivencia activa acumulado (ms); de aquí sale el día en que va el jugador. */
    val activeMs: Long = 0L,
    /** Crafteo en curso (t16); no se guarda. */
    val craft: CraftProgress? = null,
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

    fun setVehicleHealth(health: Double) {
        val value = VehicleHealthRules.sanitized(health)
        if (mutableState.value.vehicleHealth != value) mutableState.update { it.copy(vehicleHealth = value) }
    }

    fun setNeeds(needs: SurvivalNeeds) {
        mutableState.update { it.copy(needs = SurvivalNeeds(needs.hunger.coerceIn(0.0, 100.0), needs.thirst.coerceIn(0.0, 100.0), needs.energy.coerceIn(0.0, 100.0))) }
    }

    /** Suma XP a una habilidad; devuelve el nivel nuevo si subió de nivel. */
    fun gainSkill(skill: Skill, amount: Int): Int? {
        var levelUp: Int? = null
        mutableState.update { current ->
            val gain = SkillRules.gain(current.skills, skill, amount)
            levelUp = gain.levelUp
            current.copy(skills = gain.skills)
        }
        return levelUp
    }

    /** Elige oficio (una sola vez); false si ya había uno. */
    fun chooseOccupation(occupation: Occupation): Boolean {
        var chosen = false
        mutableState.update { current ->
            val skills = SkillRules.choose(current.occupation, occupation, current.skills)
            chosen = skills != null
            if (skills != null) current.copy(skills = skills, occupation = occupation) else current
        }
        return chosen
    }

    fun setCraft(craft: CraftProgress?) {
        mutableState.update { it.copy(craft = craft) }
    }

    fun setWeather(weather: Weather) {
        if (mutableState.value.weather != weather) mutableState.update { it.copy(weather = weather) }
    }

    fun addActiveMs(ms: Long) {
        if (ms > 0L) mutableState.update { it.copy(activeMs = it.activeMs + ms) }
    }

    fun setStamina(stamina: Double) {
        val value = stamina.coerceIn(0.0, StaminaRules.MAX)
        if (mutableState.value.stamina != value) mutableState.update { it.copy(stamina = value) }
    }

    /** Morir y reaparecer quita la infección y el sangrado (t32): no se revive para morir otra vez de inmediato. */
    fun clearConditions() {
        mutableState.update { it.copy(bleeding = false, infection = InfectionState()) }
    }

    fun setBleeding(bleeding: Boolean) {
        if (mutableState.value.bleeding != bleeding) mutableState.update { it.copy(bleeding = bleeding) }
    }

    fun setFuel(fuel: Double) {
        val value = FuelRules.sanitized(fuel)
        if (mutableState.value.fuel != value) mutableState.update { it.copy(fuel = value) }
    }

    fun setArsenal(arsenal: SurvivalArsenal) {
        mutableState.update { it.copy(arsenal = WeaponRules.sanitized(arsenal)) }
    }

    fun restore(
        needs: SurvivalNeeds, infected: Boolean, elapsedMs: Long, boards: Int = 0,
        doors: Map<String, DoorRuntime> = emptyMap(), pack: SurvivalPack = SurvivalPack(),
        arsenal: SurvivalArsenal = SurvivalArsenal(), fuel: Double = FuelRules.START,
        bleeding: Boolean = false, vehicleHealth: Double = VehicleHealthRules.MAX, activeMs: Long = 0L,
        skills: SurvivalSkills = SurvivalSkills(), occupation: Occupation = Occupation.NINGUNO,
    ) {
        mutableState.value = SurvivalSubState(
            arsenal = WeaponRules.sanitized(arsenal),
            fuel = FuelRules.sanitized(fuel),
            bleeding = bleeding,
            vehicleHealth = VehicleHealthRules.sanitized(vehicleHealth),
            activeMs = activeMs.coerceAtLeast(0L),
            skills = skills,
            occupation = occupation,
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
        val needs = SurvivalRules.advance(current.needs, activity, night, deltaMs / 1000.0, infection, WeatherRules.thirstFactor(current.weather))
        val next = current.copy(needs = needs, infection = infection)
        mutableState.value = next
        return next
    }

    /** Vida tras el desgaste de [state] durante [deltaMs] (puro: cabe dentro de `updateHealth`). */
    fun healthAfter(health: Float, state: SurvivalSubState, deltaMs: Long): Float {
        val afterNeeds = SurvivalRules.healthAfterNeedsDamage(health.toDouble(), state.needs, deltaMs / 1000.0, state.infection)
        val afterBleeding = if (state.bleeding) BleedingRules.healthAfter(afterNeeds, deltaMs / 1000.0) else afterNeeds
        return afterBleeding.toFloat()
    }

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

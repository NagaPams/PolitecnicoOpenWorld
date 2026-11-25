package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import ovh.gabrielhuav.pow.domain.models.ai.setSurvivalNoiseEnabled
import ovh.gabrielhuav.pow.domain.models.ai.queueSurvivalNoise
import ovh.gabrielhuav.pow.domain.models.survival.BleedingRules
import ovh.gabrielhuav.pow.domain.models.survival.ConditionSpeedRules
import ovh.gabrielhuav.pow.domain.models.survival.FractureRules
import ovh.gabrielhuav.pow.domain.models.survival.NeedEffectRules
import ovh.gabrielhuav.pow.domain.models.survival.HotwireRules
import ovh.gabrielhuav.pow.domain.models.survival.CarStateRules
import ovh.gabrielhuav.pow.domain.models.survival.FuelRules
import ovh.gabrielhuav.pow.domain.models.survival.SearchProfile
import ovh.gabrielhuav.pow.domain.models.survival.TemperatureRules
import ovh.gabrielhuav.pow.domain.models.survival.WetnessRules
import ovh.gabrielhuav.pow.domain.models.survival.StrangerCarRules
import ovh.gabrielhuav.pow.shared.recursos.survival_hotwire
import ovh.gabrielhuav.pow.shared.recursos.survival_hotwire_failed
import ovh.gabrielhuav.pow.domain.models.survival.CarDoorRules
import ovh.gabrielhuav.pow.domain.models.survival.GrabRules
import ovh.gabrielhuav.pow.domain.models.survival.NoiseEvent
import ovh.gabrielhuav.pow.domain.models.survival.BlackoutRules
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalCrafting
import ovh.gabrielhuav.pow.domain.models.survival.WalkRules
import ovh.gabrielhuav.pow.domain.models.survival.DayRules
import ovh.gabrielhuav.pow.domain.models.survival.AmmoKind
import ovh.gabrielhuav.pow.domain.models.survival.CraftInventory
import ovh.gabrielhuav.pow.domain.models.survival.CraftProgress
import ovh.gabrielhuav.pow.domain.models.survival.Recipe
import ovh.gabrielhuav.pow.domain.models.survival.RecipeRules
import ovh.gabrielhuav.pow.domain.models.survival.Occupation
import ovh.gabrielhuav.pow.domain.models.survival.Skill
import ovh.gabrielhuav.pow.domain.models.survival.ZoneKillRules
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.survival.SleepCheck
import ovh.gabrielhuav.pow.domain.models.survival.WeaponRules
import ovh.gabrielhuav.pow.domain.models.survival.Weather
import ovh.gabrielhuav.pow.domain.models.survival.WeatherRules
import ovh.gabrielhuav.pow.domain.models.survival.SleepRules
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.survival.StaminaRules
import ovh.gabrielhuav.pow.domain.models.survival.StaminaState
import ovh.gabrielhuav.pow.domain.models.survival.NoiseSource
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalActivity
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalItem
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalRules
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.survival_bitten
import ovh.gabrielhuav.pow.shared.recursos.survival_bleeding
import ovh.gabrielhuav.pow.shared.recursos.survival_grabbed
import ovh.gabrielhuav.pow.shared.recursos.survival_boards_bought
import ovh.gabrielhuav.pow.shared.recursos.survival_blackout_shop
import ovh.gabrielhuav.pow.shared.recursos.survival_blackout_started
import ovh.gabrielhuav.pow.shared.recursos.survival_boards_full
import ovh.gabrielhuav.pow.shared.recursos.survival_zone_cleared
import ovh.gabrielhuav.pow.shared.recursos.survival_craft_interrupted
import ovh.gabrielhuav.pow.shared.recursos.survival_craft_nailbat
import ovh.gabrielhuav.pow.shared.recursos.survival_craft_zombies
import ovh.gabrielhuav.pow.shared.recursos.survival_kit_crafted
import ovh.gabrielhuav.pow.shared.recursos.survival_occupation_chosen
import ovh.gabrielhuav.pow.shared.recursos.survival_cold
import ovh.gabrielhuav.pow.shared.recursos.survival_fuel_full
import ovh.gabrielhuav.pow.shared.recursos.survival_fuel_pumping
import ovh.gabrielhuav.pow.shared.recursos.survival_wet
import ovh.gabrielhuav.pow.shared.recursos.survival_fracture_healed
import ovh.gabrielhuav.pow.shared.recursos.survival_fuel_low
import ovh.gabrielhuav.pow.shared.recursos.survival_occupation_hint
import ovh.gabrielhuav.pow.shared.recursos.survival_splint_crafted
import ovh.gabrielhuav.pow.shared.recursos.survival_very_hungry
import ovh.gabrielhuav.pow.shared.recursos.survival_very_thirsty
import ovh.gabrielhuav.pow.shared.recursos.survival_very_tired
import ovh.gabrielhuav.pow.shared.recursos.survival_skill_carpinteria
import ovh.gabrielhuav.pow.shared.recursos.survival_skill_combate
import ovh.gabrielhuav.pow.shared.recursos.survival_skill_punteria
import ovh.gabrielhuav.pow.shared.recursos.survival_skill_up
import ovh.gabrielhuav.pow.shared.recursos.survival_item_used
import ovh.gabrielhuav.pow.shared.recursos.survival_no_money
import ovh.gabrielhuav.pow.shared.recursos.survival_not_needed
import ovh.gabrielhuav.pow.shared.recursos.survival_out_of_breath
import ovh.gabrielhuav.pow.shared.recursos.survival_sleep_done
import ovh.gabrielhuav.pow.shared.recursos.survival_sleep_rested
import ovh.gabrielhuav.pow.shared.recursos.survival_sleep_started
import ovh.gabrielhuav.pow.shared.recursos.survival_sleep_woken
import ovh.gabrielhuav.pow.shared.recursos.survival_sleep_zombies

internal fun survivalVisibilityPaused(mapVisible: Boolean, appResumed: Boolean): Boolean =
    !mapVisible || !appResumed

private fun WorldMapViewModel.survivalPaused(): Boolean {
    val s = _uiState.value
    return survivalVisibilityPaused(worldMapForeground, survivalAppResumed) ||
        !s.isRoadNetworkReady || !s.isMapReady || s.showWastedScreen ||
        currentInteriorRoomId != null || isCampaignEscortActive() || isMission1ChaseActive() ||
        isMission2StoryActive() || isMission3StoryActive() || isSideMissionStoryActive()
}

/**
 * Un paso a pie: ¿corre de verdad? Con supervivencia apagada es lo que pide el botón; encendida, correr gasta aliento
 * y agotado solo se camina (t11).
 */
internal fun WorldMapViewModel.playerRunsThisStep(): Boolean {
    val wantsRun = _uiState.value.isRunning
    if (!survivalManager.state.value.enabled) return wantsRun
    val before = staminaState
    staminaState = StaminaRules.step(before, wantsRun)
    staminaMovedThisSecond = true
    if (staminaState.exhausted && !before.exhausted) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_out_of_breath)) }
    }
    return StaminaRules.runsNow(before, wantsRun)
}

/** Puerta del coche (t05): con supervivencia, subir o bajar hace ruido de puerta y subir deja el coche parado un momento. */
/** t03: ¿falla el puente de este coche? Si sí, suena el motor y el jugador no sube (debe intentarlo otra vez). */
internal fun WorldMapViewModel.hotwireFails(firstTime: Boolean): Boolean {
    if (!survivalManager.state.value.enabled || !HotwireRules.fails(firstTime, Random.nextDouble())) return false
    emitSurvivalNoise(NoiseSource.ENGINE)
    viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_hotwire_failed)) }
    return true
}

internal fun WorldMapViewModel.onCarDoor(boarding: Boolean, firstTime: Boolean = false, carId: String? = null) {
    if (!survivalManager.state.value.enabled) return
    if (boarding) carDoorLockedUntilMs = CarDoorRules.lockedUntil(System.currentTimeMillis())
    if (boarding) HotwireRules.noisesOnBoard(firstTime).forEach { emitSurvivalNoise(it) } else emitSurvivalNoise(NoiseSource.DOOR)
    val remembered = if (boarding && carId != null) carStates[carId] else null
    if (remembered != null) {
        // t03: un coche que ya conducías conserva su gasolina y su salud, no las del último que usaste.
        fuelTank = remembered.fuel
        vehicleHealth = remembered.health
        fuelEmptyAnnounced = FuelRules.isEmpty(fuelTank)
        vehicleWreckAnnounced = false
        carStates = carStates - carId!!
    } else if (boarding && firstTime && carId != null) {
        // t03: el coche ajeno trae su propia gasolina y salud (fijas según su id), no las del anterior.
        fuelTank = StrangerCarRules.fuelFor(carId.hashCode())
        vehicleHealth = StrangerCarRules.healthFor(carId.hashCode())
        vehicleWreckAnnounced = false
    }
    if (boarding && firstTime) viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_hotwire)) }
}

/** Largo del paso a pie: caminar o correr (aliento) y más lento mientras un zombi te sujeta (t02). */
internal fun WorldMapViewModel.playerStepSize(): Double {
    val clock = System.currentTimeMillis()
    if (SleepRules.isSleeping(clock, sleepUntilMs) || RecipeRules.isCrafting(clock, craftUntilMs)) return 0.0
    val running = playerRunsThisStep()
    val sv = survivalManager.state.value
    val load = if (sv.enabled) SurvivalCrafting.speedFactor(SurvivalCrafting.weightOf(sv.pack)) * FractureRules.speedFactor(sv.activeMs, sv.fractureHealsAtMs) * ConditionSpeedRules.factor(sv.needs.energy, playerHealth.toDouble()) else 1.0
    return WalkRules.stepSize(running, load, GrabRules.speedFactor(System.currentTimeMillis(), grabbedUntilMs))
}

fun WorldMapViewModel.resetSurvivalForNewGame() {
    staminaState = StaminaState()
    grabbedUntilMs = 0L
    sleepUntilMs = 0L
    cancelCraft(announce = false)
    blackoutAnnounced = false
    promptQueue.clear()
    zoneKills = emptyMap()
    lastRegrowDay = 1
    npcAiManager.zoneKills = emptyMap()
    vehicleHealth = ovh.gabrielhuav.pow.domain.models.survival.VehicleHealthRules.MAX
    vehicleWreckAnnounced = false
    carStates = emptyMap()
    survivalManager.resetForNewGame()
    fuelTank = ovh.gabrielhuav.pow.domain.models.survival.FuelRules.START
    fuelEmptyAnnounced = false
    rebuildDoorCollisions()
    npcAiManager.setSurvivalNoiseEnabled(false)
    wasSurvivalNoiseActive = false
    lastSurvivalUpdateMs = 0L
}

internal fun WorldMapViewModel.emitSurvivalNoise(source: NoiseSource) {
    if (!survivalManager.state.value.enabled || !isServerDelegatedHost || survivalPaused()) return
    val state = _uiState.value
    val location = state.currentLocation ?: return
    npcAiManager.queueSurvivalNoise(NoiseEvent(location.latitude, location.longitude, source, state.nightAlpha > 0.05f, survivalManager.state.value.weather == Weather.RAIN))
}

/**
 * Aviso de supervivencia con cola y prioridad (t31): el de más prioridad se muestra al instante, los demás esperan su
 * turno y nada sale mientras el inventario está abierto (antes se perdía detrás del diálogo).
 */
internal fun WorldMapViewModel.showSurvivalPrompt(text: String, priority: Int = ovh.gabrielhuav.pow.domain.models.survival.PromptPriority.NORMAL) {
    displaySurvivalPrompt(promptQueue.offer(ovh.gabrielhuav.pow.domain.models.survival.QueuedPrompt(text, priority), SystemClock.elapsedRealtime(), _uiState.value.showWorldInventory))
}

/** Muestra el siguiente aviso en espera, si ya toca; se llama al terminar uno y al cerrar el inventario. */
internal fun WorldMapViewModel.drainSurvivalPrompts() {
    displaySurvivalPrompt(promptQueue.next(SystemClock.elapsedRealtime(), _uiState.value.showWorldInventory))
}

private fun WorldMapViewModel.displaySurvivalPrompt(prompt: ovh.gabrielhuav.pow.domain.models.survival.QueuedPrompt?) {
    if (prompt == null) return
    _uiState.update { it.copy(interactionPrompt = prompt.text) }
    viewModelScope.launch {
        delay(ovh.gabrielhuav.pow.domain.models.survival.PromptQueue.SHOW_MS)
        _uiState.update { if (it.interactionPrompt == prompt.text) it.copy(interactionPrompt = null) else it }
        drainSurvivalPrompts()
    }
}

/** Mordida de zombi: con modo supervivencia activo puede iniciar la infección (una sola vez). */
internal fun WorldMapViewModel.onZombieBite() {
    if (!survivalManager.state.value.enabled || survivalPaused()) return
    if (System.currentTimeMillis() >= grabbedUntilMs) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_grabbed), ovh.gabrielhuav.pow.domain.models.survival.PromptPriority.HIGH) }
    }
    grabbedUntilMs = GrabRules.grabbedUntil(System.currentTimeMillis())
    wakeUp(SleepRules.WAKE_METERS)
    cancelCraft(announce = true)
    if (BleedingRules.bleedsOnBite(Random.nextDouble()) && !survivalManager.state.value.bleeding) {
        survivalManager.setBleeding(true)
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_bleeding), ovh.gabrielhuav.pow.domain.models.survival.PromptPriority.HIGH) }
    }
    if (!SurvivalRules.biteInfects(Random.nextDouble())) return
    if (survivalManager.infect()) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_bitten), ovh.gabrielhuav.pow.domain.models.survival.PromptPriority.HIGH) }
    }
}

/** Avisa una vez cuando empieza el apagón; si el día vuelve a bajar (partida nueva o cargada) se rearma. */
private fun WorldMapViewModel.announceBlackout() {
    val dayNow = DayRules.daysSurvived(survivalManager.state.value.activeMs)
    if (dayNow > lastRegrowDay) {
        zoneKills = ZoneKillRules.regrow(zoneKills, dayNow - lastRegrowDay)
        npcAiManager.zoneKills = zoneKills
        lastRegrowDay = dayNow
    }
    val blackout = BlackoutRules.active(dayNow)
    if (blackout && !blackoutAnnounced) {
        blackoutAnnounced = true
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_blackout_started)) }
    } else if (!blackout) {
        blackoutAnnounced = false
    }
}

/** Un zombi murió a manos del jugador (t18): se anota en su zona y, al despejarla, se avisa. */
internal fun WorldMapViewModel.onZombieKilled(at: GeoPoint) {
    if (!survivalManager.state.value.enabled) return
    val result = ZoneKillRules.record(zoneKills, at.latitude, at.longitude)
    zoneKills = result.kills
    npcAiManager.zoneKills = result.kills
    if (result.justCleared) viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_zone_cleared)) }
}

/** Usar una habilidad (t15): suma XP y avisa al subir de nivel. Solo con supervivencia encendida. */
internal fun WorldMapViewModel.onSkillUse(skill: Skill, amount: Int = 1) {
    if (!survivalManager.state.value.enabled) return
    val level = survivalManager.gainSkill(skill, amount) ?: return
    viewModelScope.launch {
        val name = getString(
            when (skill) {
                Skill.COMBATE -> Res.string.survival_skill_combate
                Skill.PUNTERIA -> Res.string.survival_skill_punteria
                Skill.CARPINTERIA -> Res.string.survival_skill_carpinteria
            },
        )
        showSurvivalPrompt(getString(Res.string.survival_skill_up, name, level))
    }
}

/** Nivel actual de una habilidad (0 si el modo está apagado). */
internal fun WorldMapViewModel.skillLevel(skill: Skill): Int =
    if (survivalManager.state.value.enabled) ovh.gabrielhuav.pow.domain.models.survival.SkillRules.levelOf(survivalManager.state.value.skills, skill) else 0

/** Elige oficio desde el inventario (una vez): da un punto de partida y un extra pequeño. */
internal fun WorldMapViewModel.chooseSurvivalOccupation(occupation: Occupation) {
    if (!survivalManager.state.value.enabled || !survivalManager.chooseOccupation(occupation)) return
    when (occupation) {
        Occupation.SOLDADO -> survivalManager.setArsenal(WeaponRules.addAmmo(survivalManager.state.value.arsenal, AmmoKind.BALAS, 6))
        Occupation.CARPINTERO -> survivalManager.addBoards(2)
        else -> Unit
    }
    soundManager.playItem()
    viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_occupation_chosen)) }
}

/** Empieza una receta con tiempo: el jugador se queda quieto y los ingredientes se gastan solo al terminar (t16). */
internal fun WorldMapViewModel.startCraft(recipe: Recipe) {
    val state = survivalManager.state.value
    val now = System.currentTimeMillis()
    if (!state.enabled || survivalPaused() || _uiState.value.isDriving) return
    if (RecipeRules.isCrafting(now, craftUntilMs) || SleepRules.isSleeping(now, sleepUntilMs)) return
    if (!RecipeRules.canCraft(recipe, CraftInventory(state.pack, state.boards, state.arsenal))) return
    if (recipe == Recipe.FERULA && !FractureRules.isFractured(state.activeMs, state.fractureHealsAtMs)) return
    if (RecipeRules.shouldInterrupt(nearestZombieMeters())) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_craft_zombies)) }
        return
    }
    val duration = RecipeRules.durationMs(recipe, skillLevel(Skill.CARPINTERIA))
    craftUntilMs = now + duration
    survivalManager.setCraft(CraftProgress(recipe, now, duration))
    toggleWorldInventory(false)
    craftJob?.cancel()
    craftJob = viewModelScope.launch {
        delay(duration)
        finishCraft(recipe)
    }
}

private suspend fun WorldMapViewModel.finishCraft(recipe: Recipe) {
    craftUntilMs = 0L
    survivalManager.setCraft(null)
    val state = survivalManager.state.value
    val result = RecipeRules.apply(recipe, CraftInventory(state.pack, state.boards, state.arsenal)) ?: return
    survivalManager.setPack(result.pack)
    survivalManager.addBoards(result.boards - state.boards)
    survivalManager.setArsenal(result.arsenal)
    soundManager.playItem()
    if (recipe == Recipe.PALO_CLAVOS) onSkillUse(Skill.CARPINTERIA, 3)
    if (recipe == Recipe.FERULA) survivalManager.setFractureHealsAt(0L)
    showSurvivalPrompt(getString(when (recipe) {
        Recipe.BOTIQUIN -> Res.string.survival_kit_crafted
        Recipe.FERULA -> Res.string.survival_splint_crafted
        Recipe.PALO_CLAVOS -> Res.string.survival_craft_nailbat
    }))
}

/** Corta el crafteo sin gastar nada (zombi cerca, mordida o partida nueva). */
internal fun WorldMapViewModel.cancelCraft(announce: Boolean) {
    if (craftUntilMs == 0L && survivalManager.state.value.craft == null) return
    craftJob?.cancel()
    craftJob = null
    craftUntilMs = 0L
    survivalManager.setCraft(null)
    if (announce) viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_craft_interrupted)) }
}

/** Una vez por segundo: un zombi cerca interrumpe el crafteo. */
private fun WorldMapViewModel.updateCraft() {
    if (craftUntilMs != 0L && RecipeRules.shouldInterrupt(nearestZombieMeters())) cancelCraft(announce = true)
}

/** Distancia en metros al zombi vivo más cercano, o `Double.MAX_VALUE` si no hay. */
private fun WorldMapViewModel.nearestZombieMeters(): Double {
    val here = _uiState.value.currentLocation ?: return Double.MAX_VALUE
    var best = Double.MAX_VALUE
    for (npc in remoteEntities.values) {
        if (npc.type != NpcType.ZOMBIE || npc.health <= 0f) continue
        val d = here.distanceToAsDouble(npc.location)
        if (d < best) best = d
    }
    return best
}

/** Dormir (t12) desde el inventario: a pie, con energía baja y sin zombis cerca. */
internal fun WorldMapViewModel.startSleep() {
    val state = survivalManager.state.value
    val now = System.currentTimeMillis()
    if (!state.enabled || survivalPaused() || _uiState.value.isDriving || SleepRules.isSleeping(now, sleepUntilMs)) return
    when (SleepRules.check(state.needs.energy, nearestZombieMeters())) {
        SleepCheck.TOO_RESTED -> viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_sleep_rested)) }
        SleepCheck.ZOMBIES_NEAR -> viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_sleep_zombies)) }
        SleepCheck.OK -> {
            sleepUntilMs = SleepRules.sleepUntil(now)
            toggleWorldInventory(false)
            viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_sleep_started)) }
        }
    }
}

/** Corta el sueño sin ganar nada (un zombi cerca o una mordida); [nearestMeters] solo decide si hay que avisar. */
private fun WorldMapViewModel.wakeUp(nearestMeters: Double) {
    if (!SleepRules.isSleeping(System.currentTimeMillis(), sleepUntilMs)) return
    sleepUntilMs = 0L
    if (nearestMeters <= SleepRules.WAKE_METERS) viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_sleep_woken)) }
}

/** Una vez por segundo mientras duerme: lo despierta un zombi cerca o, al terminar, aplica el descanso. */
private fun WorldMapViewModel.updateSleep() {
    val now = System.currentTimeMillis()
    if (sleepUntilMs == 0L) return
    val nearest = nearestZombieMeters()
    if (SleepRules.shouldWake(nearest)) {
        wakeUp(nearest)
        return
    }
    if (SleepRules.isSleeping(now, sleepUntilMs)) return
    sleepUntilMs = 0L
    val sv = survivalManager.state.value
    val night = _uiState.value.nightAlpha > 0.05f
    survivalManager.setNeeds(SleepRules.rested(sv.needs, night, TemperatureRules.coldLevel(sv.weather, night, sv.coat, soaked = WetnessRules.isSoaked(sv.wetness))))
    viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_sleep_done)) }
}

/** Compra en la tienda del campus con modo supervivencia: cobra solo si el consumible sirve. */
internal fun WorldMapViewModel.buySurvivalItem(itemName: String) {
    val item = SurvivalItem.fromName(itemName) ?: return
    if (BlackoutRules.active(DayRules.daysSurvived(survivalManager.state.value.activeMs))) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_blackout_shop)) }
        return
    }
    val price = SurvivalRules.priceOf(item)
    if (item == SurvivalItem.TABLONES) {
        buyBoards(price)
        return
    }
    val current = survivalManager.state.value
    val preview = SurvivalRules.consume(item, current.needs, current.infection)
    when {
        !preview.effective -> viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_not_needed)) }
        _uiState.value.playerMoney < price -> viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_no_money)) }
        else -> {
            val result = survivalManager.consume(item)
            soundManager.playItem()
            if (result.healthBonus > 0f) heal(result.healthBonus)
            addMoney(-price)
            _uiState.update { it.copy(showVendorMenu = false) }
            viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_item_used)) }
        }
    }
}

/** Los tablones se guardan para barricadar; no se consumen. */
private fun WorldMapViewModel.buyBoards(price: Int) {
    when {
        survivalManager.state.value.boards >= MAX_BOARDS_CARRIED ->
            viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_boards_full)) }
        _uiState.value.playerMoney < price ->
            viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_no_money)) }
        else -> {
            survivalManager.addBoards(1)
            soundManager.playItem()
            addMoney(-price)
            viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_boards_bought)) }
        }
    }
}

/** Avisos de estado, una vez cada uno (t11): poca gasolina al volante, frío sin abrigo ni refugio y fractura sanada sola. */
private fun WorldMapViewModel.announceConditions(s: WorldMapState) {
    val sv = survivalManager.state.value
    if (lowFuelLatch.fire(s.isDriving && fuelTank > 0.0 && fuelTank < LOW_FUEL_NOTICE, fuelTank >= LOW_FUEL_NOTICE + 10.0)) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_fuel_low)) }
    }
    val cold = TemperatureRules.coldLevel(sv.weather, s.nightAlpha > 0.05f, sv.coat, s.isDriving, WetnessRules.isSoaked(sv.wetness))
    if (coldLatch.fire(cold >= 1, cold == 0)) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_cold)) }
    }
    if (wetLatch.fire(WetnessRules.isSoaked(sv.wetness), sv.wetness < WetnessRules.SOAKED_AT / 2)) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_wet)) }
    }
    // Necesidades críticas: un aviso al cruzar el umbral y otro solo tras recuperarse bien (30).
    if (hungerLatch.fire(sv.needs.hunger < NeedEffectRules.CRITICAL_BELOW, sv.needs.hunger >= 30.0)) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_very_hungry)) }
    }
    if (thirstLatch.fire(sv.needs.thirst < NeedEffectRules.CRITICAL_BELOW, sv.needs.thirst >= 30.0)) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_very_thirsty)) }
    }
    if (tiredLatch.fire(sv.needs.energy < NeedEffectRules.CRITICAL_BELOW, sv.needs.energy >= 30.0)) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_very_tired)) }
    }
    val fractured = FractureRules.isFractured(sv.activeMs, sv.fractureHealsAtMs)
    // fractureHealsAtMs = 0 es una cura con botiquín o férula (que ya tienen su propio aviso): solo se avisa la que sanó sola.
    if (wasFractured && !fractured && sv.fractureHealsAtMs != 0L) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_fracture_healed)) }
    }
    wasFractured = fractured
}

private const val LOW_FUEL_NOTICE = 15.0

/** t03: con el coche parado junto a una gasolinera el tanque se llena solo (gratis: el juego no cobra la gasolina). */
private fun WorldMapViewModel.updateFuelPump(s: WorldMapState, seconds: Double) {
    val here = s.currentLocation
    val atStation = s.isDriving && here != null && searchBuildings.orEmpty().any {
        it.profile == SearchProfile.GASOLINERA && distance(here, GeoPoint(it.latitude, it.longitude)) <= FuelRules.PUMP_RADIUS_M
    }
    val stopped = kotlin.math.abs(s.vehicleSpeed) <= MAX_SPEED * 0.05
    val pumping = atStation && stopped && !FuelRules.isFull(fuelTank)
    if (pumping) {
        fuelTank = FuelRules.pump(fuelTank, seconds)
        fuelEmptyAnnounced = false
    }
    if (pumpLatch.fire(pumping, !atStation)) viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_fuel_pumping)) }
    if (pumpFullLatch.fire(atStation && stopped && FuelRules.isFull(fuelTank) && pumpedHere, !atStation)) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_fuel_full)) }
    }
}

/** Reloj monotónico; durante una escena no se acumula tiempo de supervivencia. */
internal fun WorldMapViewModel.updateSurvivalTick() {
    val now = SystemClock.elapsedRealtime()
    if (now - lastSurvivalSettingCheckMs >= 1000L) {
        lastSurvivalSettingCheckMs = now
        survivalManager.setEnabled(settingsRepository.getSurvivalMode())
    }
    val s = _uiState.value
    val paused = survivalPaused()
    if (_survivalPaused.value != paused) _survivalPaused.value = paused
    val noiseActive = survivalManager.state.value.enabled && !paused && isServerDelegatedHost
    if (wasSurvivalNoiseActive != noiseActive) npcAiManager.setSurvivalNoiseEnabled(noiseActive)
    wasSurvivalNoiseActive = noiseActive
    npcAiManager.zombieVisionFactor = if (noiseActive) ovh.gabrielhuav.pow.domain.models.survival.ZombieSightRules.factor(survivalManager.state.value.weather, s.nightAlpha > 0.05f) else 1.0
    if (!survivalManager.state.value.enabled || paused) {
        lastSurvivalUpdateMs = 0L
        // Sin supervivencia o en pausa (misión, escena, interior) no hay botín que ver ni recoger.
        clearSurvivalLoot()
        return
    }
    if (lastSurvivalUpdateMs == 0L) {
        lastSurvivalUpdateMs = now
        // t15: al empezar el modo sin oficio, un aviso (una vez por sesión) recuerda que se elige en el inventario.
        if (!occupationHintShown && survivalManager.state.value.occupation == Occupation.NINGUNO) {
            occupationHintShown = true
            viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_occupation_hint)) }
        }
        return
    }
    val elapsed = now - lastSurvivalUpdateMs
    if (elapsed < 1000L) return
    lastSurvivalUpdateMs = now
    refreshSurvivalLoot()
    updateSleep()
    updateCraft()
    val clockNow = System.currentTimeMillis()
    survivalManager.setWeather(WeatherRules.at(clockNow / DAY_NIGHT_CYCLE_MS, currentGameHour(clockNow)))
    survivalManager.addActiveMs(elapsed.coerceAtMost(1000L))
    val dressed = survivalManager.state.value
    survivalManager.setWetness(WetnessRules.step(dressed.wetness, dressed.weather, dressed.coat, s.isDriving, elapsed.coerceAtMost(1000L) / 1000.0))
    announceBlackout()
    val activity = when {
        s.isDriving -> SurvivalActivity.DRIVING
        s.playerAction == PlayerAction.RUN -> SurvivalActivity.RUNNING
        s.playerAction == PlayerAction.WALK -> SurvivalActivity.WALKING
        else -> SurvivalActivity.IDLE
    }
    if (noiseActive) {
        when (activity) {
            SurvivalActivity.DRIVING -> emitSurvivalNoise(NoiseSource.ENGINE)
            SurvivalActivity.RUNNING -> emitSurvivalNoise(NoiseSource.RUNNING)
            SurvivalActivity.WALKING -> emitSurvivalNoise(NoiseSource.WALKING)
            SurvivalActivity.IDLE -> Unit
        }
    }
    announceConditions(s)
    updateFuelPump(s, elapsed.coerceAtMost(1000L) / 1000.0)
    updateBarricadeWear(elapsed.coerceAtMost(1000L) / 1000.0)
    survivalManager.setFuel(fuelTank)
    survivalManager.setVehicleHealth(vehicleHealth)
    if (!staminaMovedThisSecond) staminaState = StaminaRules.rest(staminaState, elapsed.coerceAtMost(1000L) / 1000.0 * NeedEffectRules.staminaRegenFactor(survivalManager.state.value.needs.thirst))
    staminaMovedThisSecond = false
    survivalManager.setStamina(staminaState.value)
    // El avance de necesidades corre fuera del bloqueo de vida; dentro solo se aplica el daño (h12).
    val deltaMs = elapsed.coerceAtMost(1000L)
    val advanced = survivalManager.advance(deltaMs, true, false, activity, s.nightAlpha > 0.05f) ?: return
    combatManager.updateHealth { health -> survivalManager.healthAfter(health, advanced, deltaMs) }
}

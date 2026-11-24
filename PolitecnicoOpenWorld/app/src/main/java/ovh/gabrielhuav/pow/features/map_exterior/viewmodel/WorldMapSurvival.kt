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
import ovh.gabrielhuav.pow.domain.models.survival.NoiseEvent
import ovh.gabrielhuav.pow.domain.models.survival.NoiseSource
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalActivity
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalItem
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalRules
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.survival_bitten
import ovh.gabrielhuav.pow.shared.recursos.survival_boards_bought
import ovh.gabrielhuav.pow.shared.recursos.survival_boards_full
import ovh.gabrielhuav.pow.shared.recursos.survival_item_used
import ovh.gabrielhuav.pow.shared.recursos.survival_no_money
import ovh.gabrielhuav.pow.shared.recursos.survival_not_needed

internal fun survivalVisibilityPaused(mapVisible: Boolean, appResumed: Boolean): Boolean =
    !mapVisible || !appResumed

private fun WorldMapViewModel.survivalPaused(): Boolean {
    val s = _uiState.value
    return survivalVisibilityPaused(worldMapForeground, survivalAppResumed) ||
        !s.isRoadNetworkReady || !s.isMapReady || s.showWastedScreen ||
        currentInteriorRoomId != null || isCampaignEscortActive() || isMission1ChaseActive() ||
        isMission2StoryActive() || isMission3StoryActive() || isSideMissionStoryActive()
}

fun WorldMapViewModel.resetSurvivalForNewGame() {
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
    npcAiManager.queueSurvivalNoise(NoiseEvent(location.latitude, location.longitude, source, state.nightAlpha > 0.05f))
}

internal fun WorldMapViewModel.showSurvivalPrompt(text: String) {
    _uiState.update { it.copy(interactionPrompt = text) }
    viewModelScope.launch {
        delay(3000L)
        _uiState.update { if (it.interactionPrompt == text) it.copy(interactionPrompt = null) else it }
    }
}

/** Mordida de zombi: con modo supervivencia activo puede iniciar la infección (una sola vez). */
internal fun WorldMapViewModel.onZombieBite() {
    if (!survivalManager.state.value.enabled || survivalPaused()) return
    if (!SurvivalRules.biteInfects(Random.nextDouble())) return
    if (survivalManager.infect()) {
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_bitten)) }
    }
}

/** Compra en la tienda del campus con modo supervivencia: cobra solo si el consumible sirve. */
internal fun WorldMapViewModel.buySurvivalItem(itemName: String) {
    val item = SurvivalItem.fromName(itemName) ?: return
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

/** Reloj monotónico; durante una escena no se acumula tiempo de supervivencia. */
internal fun WorldMapViewModel.updateSurvivalTick() {
    val now = SystemClock.elapsedRealtime()
    if (now - lastSurvivalSettingCheckMs >= 1000L) {
        lastSurvivalSettingCheckMs = now
        survivalManager.setEnabled(settingsRepository.getSurvivalMode())
    }
    val s = _uiState.value
    val paused = survivalPaused()
    val noiseActive = survivalManager.state.value.enabled && !paused && isServerDelegatedHost
    if (wasSurvivalNoiseActive != noiseActive) npcAiManager.setSurvivalNoiseEnabled(noiseActive)
    wasSurvivalNoiseActive = noiseActive
    if (!survivalManager.state.value.enabled || paused) {
        lastSurvivalUpdateMs = 0L
        // Sin supervivencia o en pausa (misión, escena, interior) no hay botín que ver ni recoger.
        clearSurvivalLoot()
        return
    }
    if (lastSurvivalUpdateMs == 0L) {
        lastSurvivalUpdateMs = now
        return
    }
    val elapsed = now - lastSurvivalUpdateMs
    if (elapsed < 1000L) return
    lastSurvivalUpdateMs = now
    refreshSurvivalLoot()
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
    updateBarricadeWear(elapsed.coerceAtMost(1000L) / 1000.0)
    survivalManager.setFuel(fuelTank)
    // El avance de necesidades corre fuera del bloqueo de vida; dentro solo se aplica el daño (h12).
    val deltaMs = elapsed.coerceAtMost(1000L)
    val advanced = survivalManager.advance(deltaMs, true, false, activity, s.nightAlpha > 0.05f) ?: return
    combatManager.updateHealth { health -> survivalManager.healthAfter(health, advanced, deltaMs) }
}

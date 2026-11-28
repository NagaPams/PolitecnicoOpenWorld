package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import ovh.gabrielhuav.pow.domain.models.map.DoorAction
import ovh.gabrielhuav.pow.domain.models.map.DoorRules
import ovh.gabrielhuav.pow.domain.models.map.DoorRuntime
import ovh.gabrielhuav.pow.domain.models.map.DoorState
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.survival.Skill
import ovh.gabrielhuav.pow.domain.models.survival.SkillRules
import ovh.gabrielhuav.pow.domain.models.map.cargarColisionesConHuellas
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.survival_door_barricaded
import ovh.gabrielhuav.pow.shared.recursos.survival_door_broken
import ovh.gabrielhuav.pow.shared.recursos.survival_door_closed
import ovh.gabrielhuav.pow.shared.recursos.survival_door_dismantled
import ovh.gabrielhuav.pow.shared.recursos.survival_door_opened
import ovh.gabrielhuav.pow.shared.recursos.survival_door_locked
import ovh.gabrielhuav.pow.shared.recursos.survival_door_unlocked

// Puertas de edificio en juego (t30): con el modo supervivencia, la X en una puerta abre un menú para
// abrir, cerrar, barricar (consume tablones) o desmontar; los zombis desgastan la barricada.
// El estado vive en SurvivalManager y la colisión se reconstruye al cambiar. Ver 04_MAP_EXTERIOR.md.

/** Recalcula los muros del campus con el estado actual de las puertas (E/S de assets: fuera del hilo de UI). */
internal fun WorldMapViewModel.rebuildDoorCollisions() {
    val doors = survivalManager.state.value.doors
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val config = withOuterCollisions(cargarColisionesConHuellas(doors))
            exteriorCollisions = config
            npcAiManager.setExteriorCollisions(config)
            withContext(Dispatchers.Main) { _uiState.update { it.copy(exteriorCollisions = config) } }
        } catch (e: Exception) {
            android.util.Log.e("Collisions", "No se pudieron reconstruir las puertas: ${e.message}")
        }
    }
}

/** Con supervivencia y sin pasar por el menú, la X en una puerta del esquema abre el menú; true = ya se atendió. */
internal fun WorldMapViewModel.interceptSpecDoor(collectibleId: String): Boolean {
    if (!survivalManager.state.value.enabled || doorEnterBypass) return false
    val doorId = collectibleId.removePrefix(SPEC_DOOR_ID_PREFIX)
    _uiState.update { it.copy(doorMenuId = doorId) }
    return true
}

internal fun WorldMapViewModel.closeDoorMenu() {
    _uiState.update { it.copy(doorMenuId = null) }
}

/** Estado efectivo de una puerta: el de la partida o, si nunca se tocó, abierta (el esquema trae una sola, OPEN). */
internal fun WorldMapViewModel.doorRuntime(doorId: String): DoorRuntime =
    survivalManager.state.value.doors[doorId] ?: DoorRuntime(DoorState.OPEN)

/** Poner o quitar el candado de una puerta (t87): poner gasta un candado; quitarlo lo devuelve. */
private fun WorldMapViewModel.performLockAction(action: DoorAction, doorId: String) {
    val current = survivalManager.state.value
    val result = DoorRules.applyLock(action, doorRuntime(doorId), current.pack.candados) ?: return
    survivalManager.setPack(current.pack.copy(candados = result.second))
    survivalManager.setDoors(current.boards, current.doors + (doorId to result.first))
    soundManager.playItem()
    rebuildDoorCollisions()
    viewModelScope.launch {
        showSurvivalPrompt(getString(if (action == DoorAction.LOCK) Res.string.survival_door_locked else Res.string.survival_door_unlocked))
    }
}

internal fun WorldMapViewModel.performDoorAction(action: DoorAction) {
    val doorId = _uiState.value.doorMenuId ?: return
    closeDoorMenu()
    if (action == DoorAction.ENTER) {
        doorEnterBypass = true
        try { handleInteraction() } finally { doorEnterBypass = false }
        return
    }
    val current = survivalManager.state.value
    if (action == DoorAction.LOCK || action == DoorAction.UNLOCK) {
        performLockAction(action, doorId)
        return
    }
    val result = DoorRules.apply(action, doorRuntime(doorId), current.boards) ?: return
    // Carpintería (t15): la barricada nueva aguanta más según el nivel, y construirla da XP.
    val built = if (action == DoorAction.BARRICADE) {
        onSkillUse(Skill.CARPINTERIA, 3)
        result.first.copy(health = result.first.health * SkillRules.barricadeFactor(skillLevel(Skill.CARPINTERIA)))
    } else result.first
    val doors = current.doors + (doorId to built)
    survivalManager.setDoors(result.second, doors)
    soundManager.playItem()
    rebuildDoorCollisions()
    viewModelScope.launch {
        showSurvivalPrompt(
            getString(
                when (action) {
                    DoorAction.OPEN -> Res.string.survival_door_opened
                    DoorAction.CLOSE -> Res.string.survival_door_closed
                    DoorAction.BARRICADE -> Res.string.survival_door_barricaded
                    else -> Res.string.survival_door_dismantled
                },
            ),
        )
    }
}

/**
 * Cada segundo: cada zombi pegado a una puerta barricada la desgasta; al romperse queda abierta, se
 * reconstruye la colisión y se avisa. Barato: solo recorre las puertas barricadas (casi siempre ninguna).
 */
internal fun WorldMapViewModel.updateBarricadeWear(seconds: Double) {
    val current = survivalManager.state.value
    if (current.doors.none { it.value.state == DoorState.BARRICADED || it.value.state == DoorState.LOCKED }) return
    val locations = _uiState.value.campusEntryDoors.associate { it.id to it.location }
    var changed = false
    var broke = false
    val updated = current.doors.toMutableMap()
    for ((id, door) in current.doors) {
        if (door.state != DoorState.BARRICADED && door.state != DoorState.LOCKED) continue
        val at = locations[id] ?: continue
        var zombies = 0
        for (npc in remoteEntities.values) {
            if (npc.type == NpcType.ZOMBIE && npc.health > 0f && at.distanceToAsDouble(npc.location) <= DoorRules.ZOMBIE_REACH_METERS) zombies++
        }
        if (zombies == 0) continue
        val next = DoorRules.damaged(door, zombies, seconds)
        updated[id] = next
        changed = true
        if (next.state != door.state) broke = true
    }
    if (!changed) return
    survivalManager.setDoors(current.boards, updated)
    if (broke) {
        rebuildDoorCollisions()
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_door_broken)) }
    }
}

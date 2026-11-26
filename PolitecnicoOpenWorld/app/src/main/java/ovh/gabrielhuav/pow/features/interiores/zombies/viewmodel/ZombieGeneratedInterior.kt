package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

import ovh.gabrielhuav.pow.domain.models.interior.FurnitureKind
import ovh.gabrielhuav.pow.domain.models.interior.InteriorLoot
import ovh.gabrielhuav.pow.domain.models.survival.SearchGain
import ovh.gabrielhuav.pow.domain.models.zombie.GeneratedInteriors
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.zinterior_bed_not_tired
import ovh.gabrielhuav.pow.shared.recursos.zinterior_bed_slept
import ovh.gabrielhuav.pow.shared.recursos.zinterior_furniture_cooldown
import ovh.gabrielhuav.pow.shared.recursos.zinterior_furniture_empty
import ovh.gabrielhuav.pow.shared.recursos.zinterior_furniture_found
import ovh.gabrielhuav.pow.shared.recursos.zinterior_furniture_nothing_useful
import kotlin.math.hypot

// Botín de los muebles de un interior procedural. El interior no conoce la mochila (vive en el mapa): el mapa deja aquí
// tres funciones antes de abrir la puerta. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Puente mapa → interior: aplica botín a la mochila y recuerda qué muebles ya se registraron (enfriamiento). */
object InteriorLootBridge {
    /** Da [gains] al jugador; true si entregó algo. */
    @Volatile var applyGains: ((List<SearchGain>) -> Boolean)? = null
    @Volatile var onCooldown: ((String) -> Boolean)? = null
    @Volatile var markTaken: ((String) -> Unit)? = null
    /** Duerme en una cama: true si descansó, false si no tenía sueño. */
    @Volatile var sleepInBed: (() -> Boolean)? = null
}

private const val FURNITURE_REACH_CELLS = 1.5f

/** X junto a un mueble: lo registra una vez por enfriamiento. Devuelve true si había un mueble al alcance. */
internal fun ZombieInteriorViewModel.searchNearbyFurniture(): Boolean {
    val room = currentRoom()
    val layout = room.generatedLayout ?: return false
    val spec = GeneratedInteriors.parse(room.id) ?: return false
    val s = _state.value
    val cx = s.playerX / GeneratedInteriors.CELL_PX
    val cy = s.playerY / GeneratedInteriors.CELL_PX
    var bestIndex = -1
    var bestDistance = FURNITURE_REACH_CELLS
    layout.furniture.forEachIndexed { index, f ->
        val nearestX = cx.coerceIn(f.col.toFloat(), (f.col + f.widthCells).toFloat())
        val nearestY = cy.coerceIn(f.row.toFloat(), (f.row + 1).toFloat())
        val d = hypot(cx - nearestX, cy - nearestY)
        if (d <= bestDistance) {
            bestDistance = d
            bestIndex = index
        }
    }
    if (bestIndex < 0) return false
    val furniture = layout.furniture[bestIndex]
    if (furniture.kind == FurnitureKind.CAMA) {
        val slept = InteriorLootBridge.sleepInBed?.invoke() == true
        showKeyRes(if (slept) Res.string.zinterior_bed_slept else Res.string.zinterior_bed_not_tired)
        return true
    }
    val key = InteriorLoot.furnitureKey(spec.buildingId, bestIndex)
    val bridge = InteriorLootBridge
    if (bridge.onCooldown?.invoke(key) == true) {
        showKeyRes(Res.string.zinterior_furniture_cooldown)
        return true
    }
    val gains = InteriorLoot.gainsFor(furniture.kind, layout.rooms[furniture.roomIndex].type, spec.buildingId.toInt())
    if (gains.isEmpty()) {
        bridge.markTaken?.invoke(key)
        showKeyRes(Res.string.zinterior_furniture_empty)
        return true
    }
    val gave = bridge.applyGains?.invoke(gains) == true
    if (gave) {
        soundManager.playItem()
        bridge.markTaken?.invoke(key)
        showKeyRes(Res.string.zinterior_furniture_found)
    } else {
        showKeyRes(Res.string.zinterior_furniture_nothing_useful)
    }
    return true
}

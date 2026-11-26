package ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel

import ovh.gabrielhuav.pow.domain.models.interior.FurnitureKind
import ovh.gabrielhuav.pow.domain.models.interior.InteriorLoot
import ovh.gabrielhuav.pow.domain.models.interior.WallSide
import ovh.gabrielhuav.pow.domain.models.interior.WindowAction
import ovh.gabrielhuav.pow.domain.models.interior.WindowRules
import ovh.gabrielhuav.pow.domain.models.interior.WindowState
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.survival.SearchGain
import ovh.gabrielhuav.pow.domain.models.zombie.GeneratedInteriors
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.zinterior_bed_not_tired
import ovh.gabrielhuav.pow.shared.recursos.zinterior_bed_slept
import ovh.gabrielhuav.pow.shared.recursos.zinterior_furniture_cooldown
import ovh.gabrielhuav.pow.shared.recursos.zinterior_window_boarded
import ovh.gabrielhuav.pow.shared.recursos.zinterior_window_broken
import ovh.gabrielhuav.pow.shared.recursos.zinterior_window_covered
import ovh.gabrielhuav.pow.shared.recursos.zinterior_window_need_board
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
    /** Rompe una ventana a mano: hace ruido y puede cortar (el mapa aplica el daño). */
    @Volatile var onWindowBroken: (() -> Unit)? = null
    /** Gasta [WindowRules.BOARDS_TO_COVER] tablones; false si no alcanzan. */
    @Volatile var spendBoards: ((Int) -> Boolean)? = null
    @Volatile var boardsCarried: (() -> Int)? = null

    /** Estado de las ventanas por clave (`WindowRules.key`): vive mientras la app, así un edificio sigue como se dejó. */
    val windowStates = java.util.concurrent.ConcurrentHashMap<String, WindowState>()
}

private const val FURNITURE_REACH_CELLS = 1.5f
private const val WINDOW_REACH_CELLS = 1.3f

/** Estado de la ventana [index] de la planta del edificio [buildingId]. */
internal fun windowStateOf(buildingId: Long, index: Int): WindowState =
    InteriorLootBridge.windowStates[WindowRules.key(buildingId, index)] ?: WindowState.CERRADA

/** X junto a una ventana: romperla (ruido, quizá un corte) o tapiarla con un tablón. true si había una al alcance. */
private fun ZombieInteriorViewModel.interactNearbyWindow(layout: ovh.gabrielhuav.pow.domain.models.interior.InteriorLayout, buildingId: Long, cx: Float, cy: Float): Boolean {
    var best = -1
    var bestDistance = WINDOW_REACH_CELLS
    layout.windows.forEachIndexed { index, w ->
        val (col, row) = when (w.side) {
            WallSide.NORTE -> w.index to 0
            WallSide.ESTE -> layout.cols - 1 to w.index
            WallSide.OESTE -> 0 to w.index
        }
        val d = hypot(cx - cx.coerceIn(col.toFloat(), col + 1f), cy - cy.coerceIn(row.toFloat(), row + 1f))
        if (d <= bestDistance) {
            bestDistance = d
            best = index
        }
    }
    if (best < 0) return false
    val key = WindowRules.key(buildingId, best)
    val state = windowStateOf(buildingId, best)
    when (val action = WindowRules.actionFor(state, InteriorLootBridge.boardsCarried?.invoke() ?: 0)) {
        WindowAction.ROMPER -> {
            InteriorLootBridge.windowStates[key] = WindowRules.after(state, action)
            InteriorLootBridge.onWindowBroken?.invoke()
            showKeyRes(Res.string.zinterior_window_broken)
        }
        WindowAction.TAPIAR -> {
            if (InteriorLootBridge.spendBoards?.invoke(WindowRules.BOARDS_TO_COVER) != true) return true
            InteriorLootBridge.windowStates[key] = WindowRules.after(state, action)
            showKeyRes(Res.string.zinterior_window_boarded)
        }
        WindowAction.NINGUNA -> showKeyRes(if (state == WindowState.ROTA) Res.string.zinterior_window_need_board else Res.string.zinterior_window_covered)
    }
    _state.update { it.copy(generatedRevision = it.generatedRevision + 1) }
    return true
}

/** X junto a un mueble: lo registra una vez por enfriamiento. Devuelve true si había un mueble al alcance. */
internal fun ZombieInteriorViewModel.searchNearbyFurniture(): Boolean {
    val room = currentRoom()
    val layout = room.generatedLayout ?: return false
    val spec = GeneratedInteriors.parse(room.id) ?: return false
    val s = _state.value
    val cx = s.playerX / GeneratedInteriors.CELL_PX
    val cy = s.playerY / GeneratedInteriors.CELL_PX
    if (interactNearbyWindow(layout, spec.buildingId, cx, cy)) return true
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

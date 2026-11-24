package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.survival.AmmoKind
import ovh.gabrielhuav.pow.domain.models.survival.BuildingSearchRules
import ovh.gabrielhuav.pow.domain.models.survival.SearchGain
import ovh.gabrielhuav.pow.domain.models.survival.FuelRules
import ovh.gabrielhuav.pow.domain.models.survival.LootKind
import ovh.gabrielhuav.pow.domain.models.survival.LootPoint
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalCrafting
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalItem
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalLoot
import ovh.gabrielhuav.pow.domain.models.survival.SurvivalRules
import ovh.gabrielhuav.pow.domain.models.survival.VehicleHealthRules
import ovh.gabrielhuav.pow.domain.models.survival.WeaponKind
import ovh.gabrielhuav.pow.domain.models.survival.WeaponRules
import ovh.gabrielhuav.pow.shared.recursos.survival_ammo_found
import ovh.gabrielhuav.pow.shared.recursos.survival_bandage_stored
import ovh.gabrielhuav.pow.shared.recursos.survival_car_repaired
import ovh.gabrielhuav.pow.shared.recursos.survival_car_wrecked
import ovh.gabrielhuav.pow.shared.recursos.survival_bandaged
import ovh.gabrielhuav.pow.shared.recursos.survival_fuel_empty
import ovh.gabrielhuav.pow.shared.recursos.survival_fuel_filled
import ovh.gabrielhuav.pow.shared.recursos.survival_fuel_stored
import ovh.gabrielhuav.pow.shared.recursos.survival_weapon_found
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.survival_boards_bought
import ovh.gabrielhuav.pow.shared.recursos.survival_boards_full
import ovh.gabrielhuav.pow.shared.recursos.survival_building_searched
import ovh.gabrielhuav.pow.shared.recursos.survival_container_looted
import ovh.gabrielhuav.pow.shared.recursos.survival_item_stored
import ovh.gabrielhuav.pow.shared.recursos.survival_item_used
import ovh.gabrielhuav.pow.shared.recursos.survival_kit_crafted
import ovh.gabrielhuav.pow.shared.recursos.survival_kit_used
import ovh.gabrielhuav.pow.shared.recursos.survival_not_needed

// Saqueo de consumibles sueltos (t09, modo supervivencia): los puntos salen de `SurvivalLoot` (determinista, sin
// guardar), se anclan a la calle más cercana y se recogen con X; lo recogido tarda en reaparecer. Ver 04_MAP_EXTERIOR.md.

private const val LOOT_COOLDOWN_MS = 10 * 60 * 1000L
private const val CONTAINER_COOLDOWN_MS = 30 * 60 * 1000L
private const val CONTAINER_BULLETS = 2
private const val VEHICLE_CRASH_COOLDOWN_MS = 400L
/** Si no hay calle a menos de esto (grados, ~65 m) del centro de la celda, esa celda no trae botín. */
private const val LOOT_MAX_SNAP_DEGREES = 0.0006
/** Los edificios registrables se muestran a menos de esto (grados, ~110 m) del jugador. */
private const val BUILDING_VISIBLE_DEGREES = 0.001

private fun LootKind.survivalItem(): SurvivalItem = when (this) {
    LootKind.PIZZA -> SurvivalItem.PIZZA
    LootKind.REFRESCO -> SurvivalItem.REFRESCO
    LootKind.SUERO -> SurvivalItem.SUERO
    LootKind.TABLONES, LootKind.CONTENEDOR, LootKind.ARMA_BLANCA, LootKind.ARMA_FUEGO, LootKind.MUNICION,
    LootKind.GASOLINA, LootKind.VENDA, LootKind.EDIFICIO -> SurvivalItem.TABLONES
}

private fun cellKey(latitude: Double, longitude: Double): Long =
    (SurvivalLoot.cellOf(latitude).toLong() shl 32) or (SurvivalLoot.cellOf(longitude).toLong() and 0xFFFFFFFFL)

/** Una vez por segundo: recalcula los puntos al cambiar de celda o al reaparecer uno recogido. */
internal fun WorldMapViewModel.refreshSurvivalLoot() {
    val state = _uiState.value
    val location = state.currentLocation
    if (!survivalManager.state.value.enabled || location == null || !state.isRoadNetworkReady) {
        clearSurvivalLoot()
        return
    }
    ensureSearchBuildings()
    val now = SystemClock.elapsedRealtime()
    var respawned = false
    val iterator = lootTaken.entries.iterator()
    while (iterator.hasNext()) {
        if (iterator.next().value <= now) {
            iterator.remove()
            respawned = true
        }
    }
    val key = cellKey(location.latitude, location.longitude)
    if (key == lootCellKey && !respawned) return
    lootCellKey = key
    val anchored = ArrayList<LootPoint>()
    for (point in SurvivalLoot.around(location.latitude, location.longitude)) {
        if (lootTaken.containsKey(point.id)) continue
        val spot = getNearestPointOnNetwork(GeoPoint(point.latitude, point.longitude))
        if (distance(spot, GeoPoint(point.latitude, point.longitude)) > LOOT_MAX_SNAP_DEGREES) continue
        anchored.add(point.copy(latitude = spot.latitude, longitude = spot.longitude))
    }
    searchBuildings?.let { buildings ->
        for (b in buildings) {
            if (abs(b.latitude - location.latitude) > BUILDING_VISIBLE_DEGREES || abs(b.longitude - location.longitude) > BUILDING_VISIBLE_DEGREES) continue
            val id = "${BuildingSearchRules.ID_PREFIX}${b.id}"
            if (lootTaken.containsKey(id)) continue
            anchored.add(LootPoint(id, LootKind.EDIFICIO, b.latitude, b.longitude, b.profile.ordinal))
        }
    }
    publishLoot(anchored)
}

/** Calcula una vez los edificios registrables de las huellas (las pide al cargador si aún no están). */
private fun WorldMapViewModel.ensureSearchBuildings() {
    if (searchBuildings != null) return
    val footprints = _uiState.value.campusFootprints
    if (footprints.isEmpty()) {
        if (!buildingFootprintsAsked) {
            buildingFootprintsAsked = true
            ensureCampusFootprintsLoaded()
        }
        return
    }
    searchBuildings = BuildingSearchRules.searchable(footprints)
    lootCellKey = Long.MIN_VALUE
}

internal fun WorldMapViewModel.clearSurvivalLoot() {
    lootCellKey = Long.MIN_VALUE
    if (_uiState.value.survivalLoot.isNotEmpty() || lootCollectibles.isNotEmpty()) publishLoot(emptyList())
}

private fun WorldMapViewModel.publishLoot(points: List<LootPoint>) {
    lootCollectibles = points.map {
        ActiveCollectible(id = it.id, name = it.kind.name, description = "Botín", assetPath = "", latitude = it.latitude, longitude = it.longitude)
    }
    _uiState.update { it.copy(survivalLoot = points) }
}

/** X junto a un punto de botín: aplica el consumible o guarda el tablón; si no sirve, no se gasta. */
internal fun WorldMapViewModel.pickUpSurvivalLoot(id: String) {
    val point = _uiState.value.survivalLoot.firstOrNull { it.id == id } ?: return
    val item = point.kind.survivalItem()
    val current = survivalManager.state.value
    var taken = false
    val message = when {
        point.kind == LootKind.ARMA_BLANCA || point.kind == LootKind.ARMA_FUEGO -> {
            val weapon = SurvivalLoot.weaponFor(point.kind, point.variant)
            if (weapon != null) {
                survivalManager.setArsenal(WeaponRules.pickUp(current.arsenal, weapon))
                taken = true
            }
            Res.string.survival_weapon_found
        }
        point.kind == LootKind.VENDA -> {
            if (current.bleeding) {
                survivalManager.setBleeding(false)
                taken = true
                Res.string.survival_bandaged
            } else if (current.pack.vendas < SurvivalCrafting.MAX_PER_ITEM) {
                survivalManager.setPack(current.pack.copy(vendas = current.pack.vendas + 1))
                taken = true
                Res.string.survival_bandage_stored
            } else {
                Res.string.survival_not_needed
            }
        }
        point.kind == LootKind.GASOLINA -> {
            val driving = _uiState.value.isDriving
            if (driving && fuelTank < FuelRules.USE_NOW_BELOW) {
                fuelTank = FuelRules.refill(fuelTank)
                fuelEmptyAnnounced = false
                taken = true
                Res.string.survival_fuel_filled
            } else if (current.pack.bidones < FuelRules.MAX_CANS) {
                survivalManager.setPack(current.pack.copy(bidones = current.pack.bidones + 1))
                taken = true
                Res.string.survival_fuel_stored
            } else {
                Res.string.survival_not_needed
            }
        }
        point.kind == LootKind.MUNICION -> {
            val (ammo, count) = SurvivalLoot.ammoFor(point.variant)
            if (current.arsenal.ammoOf(ammo) >= WeaponRules.MAX_AMMO) {
                Res.string.survival_not_needed
            } else {
                survivalManager.setArsenal(WeaponRules.addAmmo(current.arsenal, ammo, count))
                taken = true
                Res.string.survival_ammo_found
            }
        }
        point.kind == LootKind.EDIFICIO -> {
            taken = searchBuilding(point)
            if (taken) Res.string.survival_building_searched else Res.string.survival_not_needed
        }
        point.kind == LootKind.CONTENEDOR -> {
            taken = lootContainer()
            if (taken) Res.string.survival_container_looted else Res.string.survival_not_needed
        }
        item == SurvivalItem.TABLONES -> if (current.boards >= MAX_BOARDS_CARRIED) {
            Res.string.survival_boards_full
        } else {
            survivalManager.addBoards(1)
            taken = true
            Res.string.survival_boards_bought
        }
        !SurvivalCrafting.worthUsingNow(item, current.needs, current.infection) -> {
            // Lo que no hace falta ahora (comida/bebida) se guarda en la mochila para craftear; el suero no.
            val stored = SurvivalCrafting.store(current.pack, item)
            if (stored != null) {
                survivalManager.setPack(stored)
                taken = true
                Res.string.survival_item_stored
            } else {
                Res.string.survival_not_needed
            }
        }
        else -> {
            val result = survivalManager.consume(item)
            if (result.healthBonus > 0f) heal(result.healthBonus)
            taken = true
            Res.string.survival_item_used
        }
    }
    if (taken) {
        soundManager.playItem()
        lootTaken[id] = SystemClock.elapsedRealtime() + when (point.kind) { LootKind.CONTENEDOR -> CONTAINER_COOLDOWN_MS; LootKind.EDIFICIO -> BuildingSearchRules.COOLDOWN_MS; else -> LOOT_COOLDOWN_MS }
        collectiblesManager.clearNearby()
        lootCellKey = Long.MIN_VALUE
        refreshSurvivalLoot()
    }
    viewModelScope.launch { showSurvivalPrompt(getString(message)) }
}

/**
 * Un contenedor da un tablón (si hay lugar) y TODO lo que sirva ahora de comida, bebida y suero; false si no
 * habría servido de nada (mochila llena y sin necesidades), para no gastarlo.
 */
private fun WorldMapViewModel.lootContainer(): Boolean {
    var gave = false
    if (survivalManager.state.value.boards < MAX_BOARDS_CARRIED) {
        survivalManager.addBoards(1)
        gave = true
    }
    val arsenal = survivalManager.state.value.arsenal
    if (arsenal.balas < WeaponRules.MAX_AMMO) {
        survivalManager.setArsenal(WeaponRules.addAmmo(arsenal, AmmoKind.BALAS, CONTAINER_BULLETS))
        gave = true
    }
    for (item in arrayOf(SurvivalItem.PIZZA, SurvivalItem.REFRESCO, SurvivalItem.SUERO)) {
        if (grantConsumable(item)) gave = true
    }
    return gave
}

/** Da un consumible: se usa si hace falta ahora y, si no, se guarda en la mochila; false si no cupo. */
private fun WorldMapViewModel.grantConsumable(item: SurvivalItem): Boolean {
    val state = survivalManager.state.value
    if (!SurvivalCrafting.worthUsingNow(item, state.needs, state.infection)) {
        val stored = SurvivalCrafting.store(state.pack, item) ?: return false
        survivalManager.setPack(stored)
        return true
    }
    val result = survivalManager.consume(item)
    if (result.healthBonus > 0f) heal(result.healthBonus)
    return true
}

/** Registra un edificio (t06): da lo que corresponde a su tipo de lugar; false si no cupo nada (no se gasta). */
private fun WorldMapViewModel.searchBuilding(point: LootPoint): Boolean {
    val profile = BuildingSearchRules.profileOf(point.variant)
    val buildingId = point.id.removePrefix(BuildingSearchRules.ID_PREFIX).toLongOrNull() ?: return false
    var gave = false
    for (gain in BuildingSearchRules.gainsFor(profile, buildingId)) {
        val current = survivalManager.state.value
        when (gain) {
            SearchGain.PIZZA -> if (grantConsumable(SurvivalItem.PIZZA)) gave = true
            SearchGain.REFRESCO -> if (grantConsumable(SurvivalItem.REFRESCO)) gave = true
            SearchGain.SUERO -> if (grantConsumable(SurvivalItem.SUERO)) gave = true
            SearchGain.TABLON -> if (current.boards < MAX_BOARDS_CARRIED) {
                survivalManager.addBoards(1)
                gave = true
            }
            SearchGain.VENDA -> if (current.bleeding) {
                survivalManager.setBleeding(false)
                gave = true
            } else if (current.pack.vendas < SurvivalCrafting.MAX_PER_ITEM) {
                survivalManager.setPack(current.pack.copy(vendas = current.pack.vendas + 1))
                gave = true
            }
            SearchGain.BIDON -> if (current.pack.bidones < FuelRules.MAX_CANS) {
                survivalManager.setPack(current.pack.copy(bidones = current.pack.bidones + 1))
                gave = true
            }
            SearchGain.BALAS -> if (current.arsenal.balas < WeaponRules.MAX_AMMO) {
                survivalManager.setArsenal(WeaponRules.addAmmo(current.arsenal, AmmoKind.BALAS, CONTAINER_BULLETS))
                gave = true
            }
        }
    }
    return gave
}

/** Usa una venda guardada: detiene el sangrado. */
internal fun WorldMapViewModel.useBandage() {
    val state = survivalManager.state.value
    if (state.pack.vendas <= 0 || !state.bleeding) return
    survivalManager.setPack(state.pack.copy(vendas = state.pack.vendas - 1))
    survivalManager.setBleeding(false)
    soundManager.playItem()
    viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_bandaged)) }
}

/** Un choque a [speed]: baja la salud del coche (con enfriamiento) y avisa una vez al destruirse. */
internal fun WorldMapViewModel.damageVehicle(speed: Double, againstWall: Boolean) {
    val now = SystemClock.elapsedRealtime()
    if (now - lastVehicleCrashMs < VEHICLE_CRASH_COOLDOWN_MS) return
    val after = VehicleHealthRules.crash(vehicleHealth, speed, MAX_SPEED, againstWall)
    if (after == vehicleHealth) return
    lastVehicleCrashMs = now
    vehicleHealth = after
    if (VehicleHealthRules.isWrecked(after) && !vehicleWreckAnnounced) {
        vehicleWreckAnnounced = true
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_car_wrecked)) }
    }
}

/** Repara el coche con un tablón (desde el inventario): +[VehicleHealthRules.REPAIR_PER_BOARD] % de salud. */
internal fun WorldMapViewModel.repairVehicle() {
    val state = survivalManager.state.value
    if (state.boards <= 0 || vehicleHealth >= VehicleHealthRules.MAX) return
    survivalManager.addBoards(-1)
    vehicleHealth = (VehicleHealthRules.repair(vehicleHealth) + ovh.gabrielhuav.pow.domain.models.survival.SkillRules.repairBonus(skillLevel(ovh.gabrielhuav.pow.domain.models.survival.Skill.CARPINTERIA))).coerceAtMost(VehicleHealthRules.MAX)
    onSkillUse(ovh.gabrielhuav.pow.domain.models.survival.Skill.CARPINTERIA, 2)
    vehicleWreckAnnounced = false
    survivalManager.setVehicleHealth(vehicleHealth)
    soundManager.playItem()
    viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_car_repaired)) }
}

/** Un paso del bucle de conducción: baja el tanque y avisa una vez al quedarse sin gasolina. */
internal fun WorldMapViewModel.burnFuel(speed: Double) {
    fuelTank = FuelRules.burn(fuelTank, speed, MAX_SPEED)
    if (FuelRules.isEmpty(fuelTank) && !fuelEmptyAnnounced) {
        fuelEmptyAnnounced = true
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_fuel_empty)) }
    }
}

/** Usa un bidón guardado (desde el inventario): +[FuelRules.CAN_FILL] % de tanque. */
internal fun WorldMapViewModel.useFuelCan() {
    val pack = survivalManager.state.value.pack
    if (pack.bidones <= 0 || fuelTank >= FuelRules.MAX) return
    survivalManager.setPack(pack.copy(bidones = pack.bidones - 1))
    fuelTank = FuelRules.refill(fuelTank)
    fuelEmptyAnnounced = false
    survivalManager.setFuel(fuelTank)
    soundManager.playItem()
    viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_fuel_filled)) }
}

/** Equipa un arma que el jugador ya tiene (desde el inventario). */
internal fun WorldMapViewModel.equipSurvivalWeapon(kind: WeaponKind) {
    val equipped = WeaponRules.equip(survivalManager.state.value.arsenal, kind) ?: return
    survivalManager.setArsenal(equipped)
    soundManager.playItem()
}

/** Crafteo desde el inventario: 2 refrescos + 1 pizza guardados = 1 botiquín. */
internal fun WorldMapViewModel.craftSurvivalKit() {
    val crafted = SurvivalCrafting.craftKit(survivalManager.state.value.pack) ?: return
    survivalManager.setPack(crafted)
    soundManager.playItem()
    viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_kit_crafted)) }
}

/** Usa un botiquín guardado: cura [SurvivalCrafting.KIT_HEAL] de vida. */
internal fun WorldMapViewModel.useSurvivalKit() {
    val used = SurvivalCrafting.useKit(survivalManager.state.value.pack) ?: return
    survivalManager.setPack(used)
    survivalManager.setBleeding(false)
    soundManager.playItem()
    heal(SurvivalCrafting.KIT_HEAL)
    viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_kit_used)) }
}

package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.map.NpcNavState
import ovh.gabrielhuav.pow.domain.models.map.NpcType
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
import ovh.gabrielhuav.pow.domain.models.survival.TrunkRules
import ovh.gabrielhuav.pow.domain.models.interior.InteriorLoot
import ovh.gabrielhuav.pow.domain.models.interior.WindowRules
import ovh.gabrielhuav.pow.domain.models.survival.BailoutRules
import ovh.gabrielhuav.pow.domain.models.survival.SleepRules
import ovh.gabrielhuav.pow.domain.models.survival.TapResult
import ovh.gabrielhuav.pow.domain.models.survival.WaterRules
import ovh.gabrielhuav.pow.domain.models.zombie.GeneratedInteriors
import ovh.gabrielhuav.pow.features.interiores.zombies.viewmodel.InteriorLootBridge
import ovh.gabrielhuav.pow.domain.models.survival.CampfireRules
import ovh.gabrielhuav.pow.domain.models.survival.GroupRules
import ovh.gabrielhuav.pow.domain.models.survival.CookResult
import ovh.gabrielhuav.pow.shared.recursos.survival_trap_dismantled
import ovh.gabrielhuav.pow.shared.recursos.survival_trunk_empty
import ovh.gabrielhuav.pow.shared.recursos.survival_trunk_searched
import ovh.gabrielhuav.pow.shared.recursos.zinterior_stove_boiled
import ovh.gabrielhuav.pow.shared.recursos.zinterior_stove_cooked
import ovh.gabrielhuav.pow.shared.recursos.zinterior_stove_cooked_raw
import ovh.gabrielhuav.pow.shared.recursos.zinterior_stove_no_food
import ovh.gabrielhuav.pow.shared.recursos.zinterior_stove_not_hungry
import ovh.gabrielhuav.pow.shared.recursos.zinterior_stove_not_thirsty
import ovh.gabrielhuav.pow.domain.models.survival.CookRules
import ovh.gabrielhuav.pow.domain.models.survival.CrashInjuryRules
import ovh.gabrielhuav.pow.domain.models.survival.DayRules
import ovh.gabrielhuav.pow.domain.models.survival.NoiseSource
import ovh.gabrielhuav.pow.domain.models.survival.FractureRules
import ovh.gabrielhuav.pow.domain.models.survival.VehicleHealthRules
import ovh.gabrielhuav.pow.domain.models.survival.WeaponKind
import ovh.gabrielhuav.pow.domain.models.survival.WeaponRules
import ovh.gabrielhuav.pow.shared.recursos.survival_ammo_found
import ovh.gabrielhuav.pow.shared.recursos.survival_bandage_stored
import ovh.gabrielhuav.pow.shared.recursos.survival_car_repaired
import ovh.gabrielhuav.pow.shared.recursos.survival_coat_found
import ovh.gabrielhuav.pow.shared.recursos.survival_bail_hit
import ovh.gabrielhuav.pow.shared.recursos.survival_bleeding
import ovh.gabrielhuav.pow.shared.recursos.survival_fracture
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
import ovh.gabrielhuav.pow.shared.recursos.survival_pack_full

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

/** Prefijo del id de las fogatas como punto de botín (la X sobre una cocina la comida). */
internal const val LOOT_FIRE_PREFIX = "fogata_"
internal const val LOOT_TRAP_PREFIX = "trampa_"

private fun LootKind.survivalItem(): SurvivalItem = when (this) {
    LootKind.PIZZA -> SurvivalItem.PIZZA
    LootKind.REFRESCO -> SurvivalItem.REFRESCO
    LootKind.SUERO -> SurvivalItem.SUERO
    LootKind.TABLONES, LootKind.CONTENEDOR, LootKind.ARMA_BLANCA, LootKind.ARMA_FUEGO, LootKind.MUNICION,
    LootKind.GASOLINA, LootKind.VENDA, LootKind.EDIFICIO, LootKind.FOGATA, LootKind.TRAMPA, LootKind.COCHE -> SurvivalItem.TABLONES
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
    // Cajuelas de los coches estacionados cercanos (cooldown por coche en lootTaken).
    for (npc in remoteEntities.values) {
        if (npc.type != NpcType.CAR || npc.navState != NpcNavState.PARKED || !npc.displayName.isNullOrEmpty()) continue
        if (abs(npc.location.latitude - location.latitude) > BUILDING_VISIBLE_DEGREES || abs(npc.location.longitude - location.longitude) > BUILDING_VISIBLE_DEGREES) continue
        val id = TrunkRules.key(npc.id)
        if (lootTaken.containsKey(id)) continue
        anchored.add(LootPoint(id, LootKind.COCHE, npc.location.latitude, npc.location.longitude))
    }
    for ((id, pos) in traps) {
        if (abs(pos.first - location.latitude) > BUILDING_VISIBLE_DEGREES || abs(pos.second - location.longitude) > BUILDING_VISIBLE_DEGREES) continue
        anchored.add(LootPoint(id, LootKind.TRAMPA, pos.first, pos.second))
    }
    for ((id, fire) in campfires) {
        if (abs(fire.first - location.latitude) > BUILDING_VISIBLE_DEGREES || abs(fire.second - location.longitude) > BUILDING_VISIBLE_DEGREES) continue
        anchored.add(LootPoint(id, LootKind.FOGATA, fire.first, fire.second))
    }
    publishLoot(anchored)
}

/** Calcula una vez los edificios registrables de las huellas (las pide al cargador si aún no están). */
private fun WorldMapViewModel.ensureSearchBuildings() {
    if (searchBuildings != null) return
    // Solo las huellas del campus: los edificios exteriores descargados (t27) no son registrables.
    val footprints = campusBaseFootprints.ifEmpty { _uiState.value.campusFootprints }
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
            } else if (SurvivalCrafting.canAddVenda(current.pack)) {
                survivalManager.setPack(current.pack.copy(vendas = current.pack.vendas + 1))
                taken = true
                Res.string.survival_bandage_stored
            } else {
                Res.string.survival_pack_full
            }
        }
        point.kind == LootKind.GASOLINA -> {
            val driving = _uiState.value.isDriving
            if (driving && fuelTank < FuelRules.USE_NOW_BELOW) {
                fuelTank = FuelRules.refill(fuelTank)
                fuelEmptyAnnounced = false
                taken = true
                Res.string.survival_fuel_filled
            } else if (SurvivalCrafting.canAddBidon(current.pack)) {
                survivalManager.setPack(current.pack.copy(bidones = current.pack.bidones + 1))
                taken = true
                Res.string.survival_fuel_stored
            } else {
                Res.string.survival_pack_full
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
        point.kind == LootKind.COCHE -> {
            // Cajuela de un coche estacionado: lo que hay depende de su id.
            val gains = TrunkRules.gainsFor(point.id.removePrefix(TrunkRules.ID_PREFIX))
            if (gains.isEmpty()) {
                taken = true
                Res.string.survival_trunk_empty
            } else {
                val outcome = giveSearchGains(gains)
                taken = outcome == SearchOutcome.GAVE
                when (outcome) {
                    SearchOutcome.GAVE -> Res.string.survival_trunk_searched
                    SearchOutcome.WEIGHT -> Res.string.survival_pack_full
                    SearchOutcome.EMPTY -> Res.string.survival_not_needed
                }
            }
        }
        point.kind == LootKind.TRAMPA -> {
            // X sobre una trampa la desarma y devuelve el tablón.
            if (traps.remove(point.id) != null) {
                survivalManager.addBoards(1)
                taken = false
                lootCellKey = Long.MIN_VALUE
                Res.string.survival_trap_dismantled
            } else Res.string.survival_not_needed
        }
        point.kind == LootKind.FOGATA -> {
            // X junto a la fogata cocina lo que lleves (sin gastar la fogata, que se apaga sola).
            val near = campfires[point.id]?.let { CampfireRules.warms(_uiState.value.currentLocation?.latitude ?: 0.0, _uiState.value.currentLocation?.longitude ?: 0.0, it.first, it.second) } == true
            if (near) stoveMessage(cookMeal()) else Res.string.survival_not_needed
        }
        point.kind == LootKind.EDIFICIO -> {
            // Con supervivencia el edificio se abre: se entra a su interior procedural y ahí se registran los muebles.
            if (enterBuildingInterior(point)) return
            val outcome = searchBuilding(point)
            taken = outcome == SearchOutcome.GAVE
            when (outcome) {
                SearchOutcome.GAVE -> Res.string.survival_building_searched
                SearchOutcome.WEIGHT -> Res.string.survival_pack_full
                SearchOutcome.EMPTY -> Res.string.survival_not_needed
            }
        }
        point.kind == LootKind.CONTENEDOR -> {
            taken = lootContainer()
            if (taken) Res.string.survival_container_looted else emptyHandedMessage(current)
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
        lootTaken[id] = SystemClock.elapsedRealtime() + when (point.kind) { LootKind.CONTENEDOR -> CONTAINER_COOLDOWN_MS; LootKind.EDIFICIO -> BuildingSearchRules.COOLDOWN_MS; LootKind.COCHE -> TrunkRules.COOLDOWN_MS; else -> LOOT_COOLDOWN_MS }
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

/** t68: resultado de registrar un edificio; WEIGHT = algo no cupo por el peso (no por el tope del tipo). */
internal enum class SearchOutcome { GAVE, EMPTY, WEIGHT }

/** t68: si no se recogió nada y la mochila ya no admite un bidón, el motivo es el peso y no que no hiciera falta. */
private fun emptyHandedMessage(state: SurvivalSubState): org.jetbrains.compose.resources.StringResource =
    if (SurvivalCrafting.isHeavy(state.pack)) Res.string.survival_pack_full else Res.string.survival_not_needed

/** Registra un edificio (t06): da lo que corresponde a su tipo de lugar; false si no cupo nada (no se gasta). */
private fun WorldMapViewModel.searchBuilding(point: LootPoint): SearchOutcome {
    val profile = BuildingSearchRules.profileOf(point.variant)
    val buildingId = point.id.removePrefix(BuildingSearchRules.ID_PREFIX).toLongOrNull() ?: return SearchOutcome.EMPTY
    return giveSearchGains(BuildingSearchRules.gainsFor(profile, buildingId))
}

/** Entra al interior procedural de un edificio registrable; false si no se pudo (el registro de afuera sigue valiendo). */
private fun WorldMapViewModel.enterBuildingInterior(point: LootPoint): Boolean {
    if (!survivalManager.state.value.enabled) return false
    val buildingId = point.id.removePrefix(BuildingSearchRules.ID_PREFIX).toLongOrNull() ?: return false
    val building = searchBuildings?.firstOrNull { it.id == buildingId } ?: return false
    InteriorLootBridge.applyGains = { gains -> giveSearchGains(gains) == SearchOutcome.GAVE }
    InteriorLootBridge.onCooldown = { key -> (lootTaken[key] ?: 0L) > SystemClock.elapsedRealtime() }
    InteriorLootBridge.sleepInBed = { sleepInBed() }
    InteriorLootBridge.drinkFromTap = { drinkFromTap() }
    InteriorLootBridge.cookMeal = { cookMeal() }
    InteriorLootBridge.joinGroup = { if (GroupRules.canJoin(groupSize)) { groupSize++; true } else false }
    InteriorLootBridge.currentDay = { DayRules.daysSurvived(survivalManager.state.value.activeMs) }
    InteriorLootBridge.cansCarried = { survivalManager.state.value.pack.bidones }
    InteriorLootBridge.spendCan = { val pack = survivalManager.state.value.pack; if (pack.bidones > 0) { survivalManager.setPack(pack.copy(bidones = pack.bidones - 1)); true } else false }
    InteriorLootBridge.boardsCarried = { survivalManager.state.value.boards }
    InteriorLootBridge.spendBoards = { n -> if (survivalManager.state.value.boards >= n) { survivalManager.addBoards(-n); true } else false }
    InteriorLootBridge.platesCarried = { survivalManager.state.value.pack.chapas }
    InteriorLootBridge.spendPlate = { val pack = survivalManager.state.value.pack; if (pack.chapas > 0) { survivalManager.setPack(pack.copy(chapas = pack.chapas - 1)); true } else false }
    InteriorLootBridge.onWindowBroken = { breakWindowByHand() }
    InteriorLootBridge.markTaken = { key -> lootTaken[key] = SystemClock.elapsedRealtime() + InteriorLoot.COOLDOWN_MS }
    val roomId = GeneratedInteriors.idFor(building.id, building.profile, building.widthM, building.depthM)
    transitTeleportManager.beginEscomDoorFade("interiores_zombies?startRoom=$roomId")
    return true
}

/** Aviso de lo que pasó al cocinar (el mismo texto en la estufa de un interior y en una fogata). */
private fun stoveMessage(result: CookResult): org.jetbrains.compose.resources.StringResource = when (result) {
    CookResult.COOKED -> Res.string.zinterior_stove_cooked
    CookResult.COOKED_RAW -> Res.string.zinterior_stove_cooked_raw
    CookResult.BOILED -> Res.string.zinterior_stove_boiled
    CookResult.NOT_THIRSTY -> Res.string.zinterior_stove_not_thirsty
    CookResult.NOT_HUNGRY -> Res.string.zinterior_stove_not_hungry
    CookResult.NO_FOOD -> Res.string.zinterior_stove_no_food
}

/** Cocinar en la estufa (t16): gasta una comida de la mochila y alimenta más que comerla fría. */
private fun WorldMapViewModel.cookMeal(): CookResult {
    val sv = survivalManager.state.value
    val result = CookRules.check(sv.pack, sv.needs)
    if (result == CookResult.COOKED || result == CookResult.COOKED_RAW || result == CookResult.BOILED) {
        val (pack, needs) = CookRules.cook(sv.pack, sv.needs, result)
        survivalManager.setPack(pack)
        survivalManager.setNeeds(needs)
    }
    return result
}

/** Beber de un grifo (t14): hay agua hasta el día [WaterRules.CUT_FROM_DAY]; después el grifo está seco. */
private fun WorldMapViewModel.drinkFromTap(): TapResult {
    val sv = survivalManager.state.value
    val result = WaterRules.check(DayRules.daysSurvived(sv.activeMs), sv.needs.thirst)
    if (result == TapResult.DRANK) survivalManager.setNeeds(WaterRules.drink(sv.needs))
    // Sin red, el grifo todavía suelta un poco de agua turbia: se junta en la mochila y se hierve en la estufa.
    if (result == TapResult.NO_WATER && SurvivalCrafting.canAddSucia(sv.pack)) {
        survivalManager.setPack(sv.pack.copy(sucia = sv.pack.sucia + 1))
        return TapResult.DIRTY
    }
    return result
}

/** Romper una ventana a mano (t07): hace ruido de cristales y puede cortar (algo de vida y sangrado). */
private fun WorldMapViewModel.breakWindowByHand() {
    emitSurvivalNoise(NoiseSource.CRISTAL)
    if (!WindowRules.cutsOnBreak(kotlin.random.Random.nextDouble())) return
    combatManager.updateHealth { h -> CrashInjuryRules.healthAfter(h.toDouble(), WindowRules.CUT_DAMAGE).toFloat() }
    survivalManager.setBleeding(true)
    viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_bleeding), ovh.gabrielhuav.pow.domain.models.survival.PromptPriority.HIGH) }
}

/** Dormir en una cama (t80): recupera energía a cambio de hambre y sed, y la ropa se seca; false si no hay sueño. */
private fun WorldMapViewModel.sleepInBed(): Boolean {
    val sv = survivalManager.state.value
    if (sv.needs.energy >= SleepRules.TOO_RESTED_AT) return false
    survivalManager.setNeeds(SleepRules.restedInBed(sv.needs, _uiState.value.nightAlpha > 0.05f))
    survivalManager.setWetness(0.0)
    return true
}

/** Da lo de [gains] a la mochila (y cura el sangrado con la venda); lo que no cabe no se gasta. */
internal fun WorldMapViewModel.giveSearchGains(gains: List<SearchGain>): SearchOutcome {
    var gave = false
    var weightBlocked = false
    for (gain in gains) {
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
            } else if (SurvivalCrafting.canAddVenda(current.pack)) {
                survivalManager.setPack(current.pack.copy(vendas = current.pack.vendas + 1))
                gave = true
            } else if (SurvivalCrafting.vendaBlockedByWeight(current.pack)) {
                weightBlocked = true
            }
            SearchGain.BIDON -> if (SurvivalCrafting.canAddBidon(current.pack)) {
                survivalManager.setPack(current.pack.copy(bidones = current.pack.bidones + 1))
                gave = true
            } else if (SurvivalCrafting.bidonBlockedByWeight(current.pack)) {
                weightBlocked = true
            }
            SearchGain.CRUDA -> if (SurvivalCrafting.canAddCruda(current.pack)) {
                survivalManager.setPack(current.pack.copy(cruda = current.pack.cruda + 1))
                gave = true
            }
            SearchGain.CHAPA -> if (SurvivalCrafting.canAddChapa(current.pack)) {
                survivalManager.setPack(current.pack.copy(chapas = current.pack.chapas + 1))
                gave = true
            }
            SearchGain.ABRIGO -> if (!current.coat) {
                survivalManager.setCoat(true)
                gave = true
                viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_coat_found)) }
            }
            SearchGain.BALAS -> if (current.arsenal.balas < WeaponRules.MAX_AMMO) {
                survivalManager.setArsenal(WeaponRules.addAmmo(current.arsenal, AmmoKind.BALAS, CONTAINER_BULLETS))
                gave = true
            }
        }
    }
    return when {
        gave -> SearchOutcome.GAVE
        weightBlocked -> SearchOutcome.WEIGHT
        else -> SearchOutcome.EMPTY
    }
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
    if (survivalManager.state.value.enabled) {
        // t04: un choque fuerte también lastima al jugador (nunca por debajo del piso de vida del modo).
        val hurt = CrashInjuryRules.damage(speed, MAX_SPEED, againstWall)
        if (hurt > 0.0) combatManager.updateHealth { h -> CrashInjuryRules.healthAfter(h.toDouble(), hurt).toFloat() }
    }
    if (survivalManager.state.value.enabled && FractureRules.fracturesOnCrash(speed, MAX_SPEED, kotlin.random.Random.nextDouble())) {
        val st = survivalManager.state.value
        if (!FractureRules.isFractured(st.activeMs, st.fractureHealsAtMs)) {
            survivalManager.setFractureHealsAt(FractureRules.healsAt(st.activeMs))
            viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_fracture), ovh.gabrielhuav.pow.domain.models.survival.PromptPriority.HIGH) }
        }
    }
    if (VehicleHealthRules.isWrecked(after) && !vehicleWreckAnnounced) {
        vehicleWreckAnnounced = true
        viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_car_wrecked)) }
    }
}

/** t10: bajar de un coche en movimiento lastima según la velocidad: golpe (vida), corte (sangrado) o caída (fractura). */
internal fun WorldMapViewModel.applyBailoutInjury(speed: Double) {
    if (!survivalManager.state.value.enabled) return
    val injury = BailoutRules.injuryOnBailout(speed, MAX_SPEED, kotlin.random.Random.nextDouble()) ?: return
    combatManager.updateHealth { h -> CrashInjuryRules.healthAfter(h.toDouble(), injury.damage).toFloat() }
    when {
        BailoutRules.causesBleeding(injury.kind) -> {
            survivalManager.setBleeding(true)
            viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_bleeding), ovh.gabrielhuav.pow.domain.models.survival.PromptPriority.HIGH) }
        }
        BailoutRules.causesFracture(injury.kind) -> {
            val st = survivalManager.state.value
            if (!FractureRules.isFractured(st.activeMs, st.fractureHealsAtMs)) survivalManager.setFractureHealsAt(FractureRules.healsAt(st.activeMs))
            viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_fracture), ovh.gabrielhuav.pow.domain.models.survival.PromptPriority.HIGH) }
        }
        else -> viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_bail_hit)) }
    }
}

/** Atropellar a un zombi abolla el coche un poco (t04); sin enfriamiento porque cada zombi muerto cuenta una vez. */
internal fun WorldMapViewModel.damageVehicleRunOver(speed: Double) {
    if (!survivalManager.state.value.enabled) return
    val after = VehicleHealthRules.runOver(vehicleHealth, speed, MAX_SPEED)
    if (after == vehicleHealth) return
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
    startCraft(ovh.gabrielhuav.pow.domain.models.survival.Recipe.BOTIQUIN)
}

/** Usa un botiquín guardado: cura [SurvivalCrafting.KIT_HEAL] de vida. */
internal fun WorldMapViewModel.useSurvivalKit() {
    val used = SurvivalCrafting.useKit(survivalManager.state.value.pack) ?: return
    survivalManager.setPack(used)
    survivalManager.setBleeding(false)
    survivalManager.setFractureHealsAt(0L)
    soundManager.playItem()
    heal(SurvivalCrafting.KIT_HEAL)
    viewModelScope.launch { showSurvivalPrompt(getString(Res.string.survival_kit_used)) }
}

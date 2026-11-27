package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.domain.models.map.CampusFootprintsFile
import ovh.gabrielhuav.pow.domain.models.map.OuterBuildingRules
import ovh.gabrielhuav.pow.domain.models.map.RUTA_HUELLAS_CAMPUS
import ovh.gabrielhuav.pow.domain.models.map.cargarEsquemaEdificios
import ovh.gabrielhuav.pow.domain.models.map.toFootprints
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import org.jetbrains.compose.resources.getString
import ovh.gabrielhuav.pow.shared.recursos.Res
import ovh.gabrielhuav.pow.shared.recursos.survival_no_building_data

// Prototipo del mapa cenital estilizado (estilo Chinatown Wars): carga las huellas de OSM del
// campus y activa/desactiva la capa Compose `CampusFootprintLayer`. Ver 04_MAP_EXTERIOR.md.

/** Activa o apaga la capa; la primera vez carga el archivo en IO. */
internal fun WorldMapViewModel.setCampusFootprints(show: Boolean) {
    if (!show) {
        _uiState.update { it.copy(showCampusFootprints = false) }
        return
    }
    if (_uiState.value.campusFootprints.isNotEmpty()) {
        _uiState.update { it.copy(showCampusFootprints = true) }
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val file = PowJson.decodeFromString<CampusFootprintsFile>(PowAssets.texto(RUTA_HUELLAS_CAMPUS))
            val footprints = file.toFootprints(cargarEsquemaEdificios())
            withContext(Dispatchers.Main) {
                campusBaseFootprints = footprints
                _uiState.update { it.copy(campusFootprints = footprints, showCampusFootprints = true) }
            }
            Log.d("Footprints", "cargadas ${footprints.size} huellas")
        } catch (e: Exception) {
            Log.e("Footprints", "Error: ${e.message}")
        }
    }
}

/** Carga las huellas para el renderer isométrico sin activar la capa cenital de depuración. */
internal fun WorldMapViewModel.ensureCampusFootprintsLoaded() {
    if (_uiState.value.campusFootprints.isNotEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val file = PowJson.decodeFromString<CampusFootprintsFile>(PowAssets.texto(RUTA_HUELLAS_CAMPUS))
            val footprints = file.toFootprints(cargarEsquemaEdificios())
            withContext(Dispatchers.Main) {
                campusBaseFootprints = footprints
                _uiState.update { it.copy(campusFootprints = footprints) }
            }
        } catch (e: Exception) {
            Log.e("Footprints", "Error al cargar huellas isométricas: ${e.message}")
        }
    }
}

/** La colisión del campus más la de los edificios exteriores ya descargados (t42). */
internal fun WorldMapViewModel.withOuterCollisions(
    config: ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig,
): ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig {
    val outer = outerCollisionPolygons
    return if (outer.isEmpty()) config else config.copy(polygons = config.polygons + outer)
}

/**
 * Edificios fuera del campus para la vista isométrica (t27): cuando el jugador se aleja ~220 m del último centro
 * descargado, pide a Overpass los edificios cercanos y los suma a las huellas del campus. Si falla la red espera 1 min.
 * Solo corre con el mapa isométrico y con las huellas del campus ya cargadas.
 */
internal fun WorldMapViewModel.updateOuterBuildingsTick(location: ovh.gabrielhuav.pow.domain.models.geo.GeoPoint) {
    if (_uiState.value.mapProvider != MapProvider.ISOMETRIC || campusBaseFootprints.isEmpty()) return
    if (outerFetching) return
    val now = android.os.SystemClock.elapsedRealtime()
    if (now < outerRetryAtMs) return
    if (!OuterBuildingRules.shouldFetch(outerFetchLat, outerFetchLon, location.latitude, location.longitude)) return
    outerFetching = true
    // Privacidad y caché: a los servidores públicos solo se les manda la posición redondeada a ~110 m.
    val lat = kotlin.math.round(location.latitude * 1000.0) / 1000.0
    val lon = kotlin.math.round(location.longitude * 1000.0) / 1000.0
    viewModelScope.launch(Dispatchers.IO) {
        try {
            // t38: primero el disco (zona ya visitada, sin red); si no hay, Overpass y se guarda.
            val cacheDir = java.io.File(getApplication<android.app.Application>().cacheDir, "outer_buildings")
            val cached = OuterBuildingsDiskCache.read(cacheDir, lat, lon)
                ?: OuterBuildingRules.nearby(outerPack, lat, lon, OuterBuildingRules.RADIUS_METERS).takeIf { it.isNotEmpty() }
            // Sin red, un archivo caducado sirve más que ningún edificio (auditoría t35–t39).
            val fetched = cached ?: overpassRepository.fetchBuildings(lat, lon, OuterBuildingRules.RADIUS_METERS)
                ?: OuterBuildingsDiskCache.read(cacheDir, lat, lon, ignoreAge = true)
            if (cached == null && fetched != null) OuterBuildingsDiskCache.write(cacheDir, lat, lon, fetched)
            if (fetched == null) {
                outerRetryAtMs = android.os.SystemClock.elapsedRealtime() + OuterBuildingRules.RETRY_AFTER_MS
                Log.w("Footprints", "Edificios exteriores: sin respuesta de Overpass")
                // El modo supervivencia lo posee SurvivalManager: `_uiState.value.survivalMode` siempre es false (ver 09 §1).
                if (!outerNoDataShown && survivalManager.state.value.enabled) {
                    outerNoDataShown = true
                    withContext(Dispatchers.Main) { showSurvivalPrompt(getString(Res.string.survival_no_building_data)) }
                }
            } else {
                outerNoDataShown = false
                val merged = OuterBuildingRules.merge(campusBaseFootprints, fetched, lat, lon)
                outerFetchLat = lat
                outerFetchLon = lon
                // t42: los edificios nuevos también bloquean (jugador, coches y NPC); se suman a la colisión actual del campus.
                outerCollisionPolygons = OuterBuildingRules.collisionPolygons(merged, campusBaseFootprints.size)
                exteriorCollisions?.let { current ->
                    val config = current.copy(polygons = current.polygons.filterNot { it.name.startsWith("OUT_") } + outerCollisionPolygons)
                    exteriorCollisions = config
                    npcAiManager.setExteriorCollisions(config)
                    withContext(Dispatchers.Main) { _uiState.update { it.copy(exteriorCollisions = config) } }
                }
                // t80: los edificios descargados también se pueden registrar y entrar (perfil por sus etiquetas de OSM).
                searchBuildings = ovh.gabrielhuav.pow.domain.models.survival.BuildingSearchRules.searchable(merged)
                lootCellKey = Long.MIN_VALUE
                withContext(Dispatchers.Main) { _uiState.update { it.copy(campusFootprints = merged) } }
                Log.i("Footprints", "Edificios exteriores: ${merged.size - campusBaseFootprints.size} de ${fetched.size} descargados")
            }
        } catch (e: Exception) {
            outerRetryAtMs = android.os.SystemClock.elapsedRealtime() + OuterBuildingRules.RETRY_AFTER_MS
            Log.e("Footprints", "Edificios exteriores: ${e.message}")
        } finally {
            outerFetching = false
        }
    }
}

package ovh.gabrielhuav.pow.features.map_exterior.viewmodel

import android.os.SystemClock
import kotlinx.coroutines.flow.update
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.NpcType

// Acción contextual (t20): el botón Y sube al coche cuando hay uno al alcance, pero nada lo indicaba. Aquí se calcula, a
// ~4 Hz y sin asignaciones, si hay un coche civil dentro de INTERACT_RADIUS; la interfaz muestra una pista junto a Y.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

internal fun WorldMapViewModel.updateContextActionTick(location: GeoPoint) {
    val now = SystemClock.elapsedRealtime()
    if (now - lastContextActionMs < CONTEXT_ACTION_INTERVAL_MS) return
    lastContextActionMs = now
    var near = false
    if (!_uiState.value.isDriving) {
        for (entry in remoteEntities.values) {
            if (entry.type == NpcType.CAR && distance(location, entry.location) <= INTERACT_RADIUS) {
                near = true
                break
            }
        }
    }
    if (_uiState.value.nearCar != near) _uiState.update { it.copy(nearCar = near) }
}

private const val CONTEXT_ACTION_INTERVAL_MS = 250L

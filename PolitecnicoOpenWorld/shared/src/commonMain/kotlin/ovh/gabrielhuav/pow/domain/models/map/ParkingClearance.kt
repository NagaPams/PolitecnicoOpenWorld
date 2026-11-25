package ovh.gabrielhuav.pow.domain.models.map

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint

// Zona libre de coches estacionados alrededor del jugador al aparecer o teletransportarse (t28): antes nacía dentro
// de un lote lleno y los coches tapaban los controles y no dejaban caminar. Los coches que ya estaban se quedan.

object ParkingClearance {
    /** Radio sin coches estacionados nuevos alrededor del jugador, en metros. */
    const val CLEAR_METERS = 12.0

    /** ¿Un coche estacionado en [car] caería dentro de la zona libre del jugador en [player]? */
    fun blocks(car: GeoPoint, player: GeoPoint): Boolean = car.distanceToAsDouble(player) < CLEAR_METERS
}

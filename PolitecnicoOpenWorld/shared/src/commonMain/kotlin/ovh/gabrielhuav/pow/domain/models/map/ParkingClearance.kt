package ovh.gabrielhuav.pow.domain.models.map

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint

// Zona libre de coches estacionados alrededor del jugador al aparecer o teletransportarse (t28): antes nacía dentro
// de un lote lleno y los coches tapaban los controles y no dejaban caminar. Los coches que ya estaban se quedan.

object ParkingClearance {
    /** Radio sin coches estacionados nuevos alrededor del jugador, en metros. */
    const val CLEAR_METERS = 12.0

    /** Hasta dónde se aclara el lote (la mitad de los lugares se queda vacía) para que no sea una pared de coches. */
    const val THIN_METERS = 30.0

    /** ¿Un coche estacionado en [car] caería dentro de la zona libre del jugador en [player]? */
    fun blocks(car: GeoPoint, player: GeoPoint): Boolean = car.distanceToAsDouble(player) < CLEAR_METERS

    /** Tirada estable (0..1) de un lugar: siempre la misma para el mismo punto, así el aclarado no se rellena con los repoblados. */
    fun slotRoll(slot: GeoPoint): Float {
        val h = (slot.latitude * 1_000_000.0).toLong() * 31L + (slot.longitude * 1_000_000.0).toLong()
        return ((h xor (h ushr 17)) and 0x7FFFFFFFL).toInt() % 1000 / 1000f
    }

    /**
     * ¿Se deja vacío este lugar? Dentro de [CLEAR_METERS] siempre; entre eso y [THIN_METERS] solo si [roll] (0..1,
     * lo da el que llama) cae en la primera mitad. Así el lote junto al jugador queda a medias (tope de coches
     * visibles al aparecer) y el resto sigue lleno.
     */
    fun skipSlot(car: GeoPoint, player: GeoPoint, roll: Float): Boolean {
        val d = car.distanceToAsDouble(player)
        return d < CLEAR_METERS || (d < THIN_METERS && roll < 0.5f)
    }
}

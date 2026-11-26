package ovh.gabrielhuav.pow.domain.models.interior

import ovh.gabrielhuav.pow.domain.models.survival.SearchGain

// Botín de los muebles de un interior procedural: cada mueble se registra una vez (con enfriamiento) y da una o dos cosas
// según su tipo y el de su habitación. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object InteriorLoot {
    /** Cuánto tarda un mueble en volver a tener algo (ms de reloj real, como el registro de edificios). */
    const val COOLDOWN_MS = 30L * 60L * 1000L

    /** Clave estable de un mueble: sirve de id del enfriamiento y no cambia entre partidas. */
    fun furnitureKey(buildingId: Long, index: Int): String = "mueble_${buildingId}_$index"

    /** Lo que da el mueble [kind] de una habitación [room]; vacío si ese mueble no guarda nada. */
    fun gainsFor(kind: FurnitureKind, room: RoomType, seed: Int): List<SearchGain> {
        val pick = hash(seed, kind.ordinal, room.ordinal)
        return when (kind) {
            FurnitureKind.ESTANTE -> when (room) {
                RoomType.TIENDA -> listOf(SearchGain.PIZZA, SearchGain.REFRESCO)
                RoomType.COCINA -> listOf(SearchGain.PIZZA)
                else -> listOf(if (pick % 3 == 0) SearchGain.BIDON else SearchGain.TABLON)
            }
            FurnitureKind.ARMARIO -> when (room) {
                RoomType.DORMITORIO -> listOf(SearchGain.ABRIGO, SearchGain.VENDA)
                RoomType.BANO, RoomType.CONSULTORIO -> listOf(SearchGain.VENDA, SearchGain.SUERO)
                else -> listOf(if (pick % 2 == 0) SearchGain.VENDA else SearchGain.TABLON)
            }
            FurnitureKind.MOSTRADOR -> listOf(SearchGain.REFRESCO, SearchGain.PIZZA)
            FurnitureKind.CAMILLA -> listOf(SearchGain.SUERO)
            FurnitureKind.COCINA -> listOf(if (pick % 2 == 0) SearchGain.PIZZA else SearchGain.REFRESCO)
            FurnitureKind.MESA -> if (pick % 3 == 0) listOf(SearchGain.REFRESCO) else emptyList()
            FurnitureKind.CAMA, FurnitureKind.GRIFO, FurnitureKind.INODORO, FurnitureKind.BANCO -> emptyList()
        }
    }

    /** Mezcla de enteros estable entre plataformas (no usa `hashCode`), siempre ≥ 0. */
    private fun hash(a: Int, b: Int, c: Int): Int {
        var h = a * 73856093 xor b * 19349663 xor c * 83492791
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        h *= 0x297a2d39
        h = h xor (h ushr 15)
        return h and Int.MAX_VALUE
    }
}

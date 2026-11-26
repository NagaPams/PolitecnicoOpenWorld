package ovh.gabrielhuav.pow.domain.models.survival

// Migración de hordas (t18): cada día una horda se pone en marcha hacia una zona vecina (cuadrícula de ~200 m de
// [ZoneKillRules]) que no esté despejada; cuando el jugador entra a esa zona la horda aparece y se avisa. Reglas puras.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object HordeRules {
    /** Hasta cuántas zonas (cuadros de ~200 m) de distancia se puede mandar la horda. */
    const val MAX_RING = 3

    fun cellKey(latCell: Int, lonCell: Int): Long = (latCell.toLong() shl 32) xor (lonCell.toLong() and 0xFFFFFFFFL)

    fun latCellOf(key: Long): Int = (key shr 32).toInt()

    fun lonCellOf(key: Long): Int = key.toInt()

    /**
     * Zona a la que migra la horda del día [day] desde la zona [playerKey]: una de las que están a 1..[MAX_RING] cuadros y
     * no están despejadas, elegida de forma estable por día; null si todas lo están.
     */
    fun targetFor(day: Int, playerKey: Long, kills: Map<Long, Int>): Long? {
        val lat = latCellOf(playerKey)
        val lon = lonCellOf(playerKey)
        val candidates = ArrayList<Long>()
        for (dLat in -MAX_RING..MAX_RING) for (dLon in -MAX_RING..MAX_RING) {
            if (dLat == 0 && dLon == 0) continue
            val key = cellKey(lat + dLat, lon + dLon)
            if ((kills[key] ?: 0) < ZoneKillRules.CLEARED_AT) candidates.add(key)
        }
        if (candidates.isEmpty()) return null
        return candidates[mix(day, candidates.size) % candidates.size]
    }

    fun isInZone(key: Long, latitude: Double, longitude: Double): Boolean = ZoneKillRules.keyOf(latitude, longitude) == key

    /** Mezcla de enteros estable entre plataformas (no usa `hashCode`), siempre ≥ 0. */
    private fun mix(a: Int, b: Int): Int {
        var h = a * 73856093 xor b * 19349663
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        h *= 0x297a2d39
        h = h xor (h ushr 15)
        return h and Int.MAX_VALUE
    }
}

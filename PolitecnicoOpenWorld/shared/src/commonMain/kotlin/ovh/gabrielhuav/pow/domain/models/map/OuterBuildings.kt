package ovh.gabrielhuav.pow.domain.models.map

// Edificios fuera del campus para la vista isométrica (t27): se descargan de OSM por zonas y se suman a las huellas
// empaquetadas del campus. Reglas puras aquí; la descarga vive en OverpassRepository y el ViewModel decide cuándo.

object OuterBuildingRules {
    /** Radio de descarga alrededor del jugador. */
    const val RADIUS_METERS = 450
    /** Cuánto debe alejarse el jugador (grados, ~220 m) del último centro descargado para pedir otra zona. */
    const val REFETCH_DEGREES = 0.002
    /** Máximo de edificios exteriores dibujados (cada uno es una capa gráfica; el equipo objetivo es de gama baja). */
    const val MAX_BUILDINGS = 220
    /** Espera tras un fallo de red antes de reintentar. */
    const val RETRY_AFTER_MS = 60_000L

    /**
     * Suma los edificios descargados a las huellas del campus: descarta los que ya están (mismo id), solo deja
     * edificios (no suelo) y se queda con los [MAX_BUILDINGS] más cercanos a ([lat], [lon]).
     */
    fun merge(base: List<CampusFootprint>, fetched: List<FootprintWay>, lat: Double, lon: Double): List<CampusFootprint> {
        if (fetched.isEmpty()) return base
        val known = HashSet<Long>(base.size * 2)
        for (fp in base) known.add(fp.id)
        val outer = CampusFootprintsFile(ways = fetched.filter { it.id !in known }).toFootprints()
            .filter { it.kind == FootprintKind.BUILDING }
        if (outer.isEmpty()) return base
        val nearest = outer.sortedBy { fp ->
            val p = fp.points[0]
            val dLat = p.latitude - lat
            val dLon = p.longitude - lon
            dLat * dLat + dLon * dLon
        }.take(MAX_BUILDINGS)
        return base + nearest
    }

    /** ¿Toca pedir otra zona? [lastLat]/[lastLon] nulos = nunca se ha pedido. */
    fun shouldFetch(lastLat: Double?, lastLon: Double?, lat: Double, lon: Double): Boolean {
        if (lastLat == null || lastLon == null) return true
        return kotlin.math.abs(lat - lastLat) >= REFETCH_DEGREES || kotlin.math.abs(lon - lastLon) >= REFETCH_DEGREES
    }
}

package ovh.gabrielhuav.pow.domain.models.map

// Edificios fuera del campus para la vista isométrica (t27): se descargan de OSM por zonas y se suman a las huellas
// empaquetadas del campus. Reglas puras aquí; la descarga vive en OverpassRepository y el ViewModel decide cuándo.

/** Paquete opcional de edificios incluido en la app (t51): si el archivo no existe se ignora y todo sigue como antes. */
const val RUTA_PAQUETE_EDIFICIOS = "CONFIG/outer_buildings_pack.json"

/** Lee el paquete de edificios; vacío si no está o está dañado (nunca lanza). Bloqueante: llamarlo fuera del hilo de UI. */
fun cargarPaqueteEdificios(): List<FootprintWay> = try {
    if (!ovh.gabrielhuav.pow.platform.assets.PowAssets.existe(RUTA_PAQUETE_EDIFICIOS)) emptyList()
    else ovh.gabrielhuav.pow.data.json.PowJson.decodeFromString<CampusFootprintsFile>(
        ovh.gabrielhuav.pow.platform.assets.PowAssets.texto(RUTA_PAQUETE_EDIFICIOS),
    ).ways
} catch (e: Exception) {
    emptyList()
}

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

    /**
     * t42: colisión maciza para los edificios que [merge] añadió después de las primeras [baseSize] huellas (los del
     * campus ya tienen la suya). Quien ya esté dentro de uno sale caminando libre (resolveCirclePath).
     */
    fun collisionPolygons(merged: List<CampusFootprint>, baseSize: Int): List<CollisionPolygon> =
        if (merged.size <= baseSize) emptyList() else merged.subList(baseSize, merged.size).map { fp ->
            CollisionPolygon(name = "OUT_${fp.id}", nodes = fp.points.map { GeoNode(it.latitude, it.longitude) })
        }

    /** t51: edificios del [pack] cuyo primer punto cae a [radiusMeters] o menos de ([lat], [lon]). */
    fun nearby(pack: List<FootprintWay>, lat: Double, lon: Double, radiusMeters: Int): List<FootprintWay> {
        if (pack.isEmpty()) return pack
        val dLat = radiusMeters / 111_320.0
        val dLon = radiusMeters / (111_320.0 * kotlin.math.cos(lat * kotlin.math.PI / 180.0))
        return pack.filter { way ->
            val p = way.pts.firstOrNull() ?: return@filter false
            p.size >= 2 && kotlin.math.abs(p[0] - lat) <= dLat && kotlin.math.abs(p[1] - lon) <= dLon
        }
    }

    /** ¿Toca pedir otra zona? [lastLat]/[lastLon] nulos = nunca se ha pedido. */
    fun shouldFetch(lastLat: Double?, lastLon: Double?, lat: Double, lon: Double): Boolean {
        if (lastLat == null || lastLon == null) return true
        return kotlin.math.abs(lat - lastLat) >= REFETCH_DEGREES || kotlin.math.abs(lon - lastLon) >= REFETCH_DEGREES
    }
}

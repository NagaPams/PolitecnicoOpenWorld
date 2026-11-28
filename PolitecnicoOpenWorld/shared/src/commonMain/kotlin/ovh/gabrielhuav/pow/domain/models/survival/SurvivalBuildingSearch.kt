package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.CampusFootprint
import ovh.gabrielhuav.pow.domain.models.map.FootprintKind
import ovh.gabrielhuav.pow.domain.models.map.edgeLengthMeters

// Registrar edificios (t06, modo supervivencia): las huellas de OSM sin puerta de entrada se pueden registrar
// desde afuera con X, una vez cada 30 min. Qué sale depende del tipo de lugar. Solo hay datos de edificios en el
// recuadro del campus (`escom_osm_footprints.json`); fuera de él no hay nada que registrar. Ver 04_MAP_EXTERIOR.md.

/** Tipo de lugar según su nombre en OSM. Los valores nuevos van AL FINAL. */
enum class SearchProfile { TIENDA, GASOLINERA, CLINICA, ESCUELA, ESTABLO, GENERICO, CASA, TALLER }

/** Qué puede dar un registro. */
enum class SearchGain { PIZZA, REFRESCO, SUERO, TABLON, VENDA, BIDON, BALAS, ABRIGO, CRUDA, CHAPA, LLAVE, LIBRO }

/** Un edificio registrable con el punto (afuera de su pared) donde se registra. */
data class SearchableBuilding(
    val id: Long,
    val profile: SearchProfile,
    val latitude: Double,
    val longitude: Double,
    /** Ancho y fondo aproximados de la huella (m): de ahí sale la planta del interior procedural. */
    val widthM: Double = 12.0,
    val depthM: Double = 10.0,
)

object BuildingSearchRules {
    const val ID_PREFIX = SurvivalLoot.ID_PREFIX + "b_"
    const val COOLDOWN_MS = 30 * 60 * 1000L
    /** Cuánto se separa el punto de registro de la pared, hacia afuera. */
    const val OUTSIDE_METERS = 2.0
    /** Los techos dibujados a mano del campus (`source=art`, ids 9 000 000..9 000 099) no son lugares reales. */
    private val ART_IDS = 9_000_000L..9_000_099L
    private const val MIN_EDGE_METERS = 3.0
    private const val METERS_PER_DEGREE = 111_320.0

    fun profileOf(ordinal: Int): SearchProfile = SearchProfile.entries.getOrElse(ordinal) { SearchProfile.GENERICO }

    /** El nombre manda; si no dice nada, el tipo de lugar sale de las etiquetas de OSM [amenity] y [building]. */
    fun profileFor(name: String, amenity: String = "", building: String = ""): SearchProfile {
        val byName = profileByName(name)
        if (byName != SearchProfile.GENERICO) return byName
        return when {
            amenity == "fuel" -> SearchProfile.GASOLINERA
            amenity in CLINIC_AMENITIES -> SearchProfile.CLINICA
            amenity in SHOP_AMENITIES -> SearchProfile.TIENDA
            amenity in SCHOOL_AMENITIES || building in SCHOOL_BUILDINGS -> SearchProfile.ESCUELA
            building == "stable" -> SearchProfile.ESTABLO
            building in HOME_BUILDINGS -> SearchProfile.CASA
            building in WORKSHOP_BUILDINGS -> SearchProfile.TALLER
            else -> SearchProfile.GENERICO
        }
    }

    private val CLINIC_AMENITIES = setOf("doctors", "hospital", "clinic", "pharmacy", "dentist")
    private val SHOP_AMENITIES = setOf("cafe", "restaurant", "fast_food", "marketplace", "supermarket", "bar")
    private val SCHOOL_AMENITIES = setOf("school", "university", "college")
    private val SCHOOL_BUILDINGS = setOf("school", "university")
    private val HOME_BUILDINGS = setOf("apartments", "house", "residential", "dormitory")
    private val WORKSHOP_BUILDINGS = setOf("industrial", "warehouse", "garage", "garages")

    private fun profileByName(name: String): SearchProfile {
        val n = name.lowercase()
        return when {
            "plaza" in n || "torres" in n || "tienda" in n -> SearchProfile.TIENDA
            "g500" in n || "gasolin" in n -> SearchProfile.GASOLINERA
            "transfusi" in n || "hospital" in n || "clinica" in n || "clínica" in n -> SearchProfile.CLINICA
            "establo" in n -> SearchProfile.ESTABLO
            "centro" in n || "escuela" in n || "universidad" in n || "coordinaci" in n -> SearchProfile.ESCUELA
            else -> SearchProfile.GENERICO
        }
    }

    /** Qué da cada lugar; el genérico varía por [id] para que no todos sean iguales. */
    fun gainsFor(profile: SearchProfile, id: Long): List<SearchGain> = when (profile) {
        SearchProfile.TIENDA -> listOf(SearchGain.PIZZA, SearchGain.REFRESCO, SearchGain.REFRESCO, SearchGain.ABRIGO)
        SearchProfile.GASOLINERA -> listOf(SearchGain.BIDON, SearchGain.REFRESCO, SearchGain.LLAVE)
        SearchProfile.CLINICA -> listOf(SearchGain.VENDA, SearchGain.VENDA, SearchGain.SUERO)
        SearchProfile.ESCUELA -> listOf(SearchGain.TABLON, SearchGain.TABLON, SearchGain.PIZZA, SearchGain.LIBRO)
        SearchProfile.ESTABLO -> listOf(SearchGain.TABLON, SearchGain.VENDA)
        SearchProfile.CASA -> listOf(SearchGain.PIZZA, SearchGain.ABRIGO, SearchGain.VENDA, SearchGain.LLAVE, SearchGain.LIBRO)
        SearchProfile.TALLER -> listOf(SearchGain.TABLON, SearchGain.TABLON, SearchGain.BIDON, SearchGain.CHAPA, SearchGain.LLAVE)
        SearchProfile.GENERICO -> listOf(
            SearchGain.TABLON,
            when (abs(id % 4).toInt()) { 0 -> SearchGain.PIZZA; 1 -> SearchGain.REFRESCO; 2 -> SearchGain.BALAS; else -> SearchGain.ABRIGO },
        )
    }

    /**
     * Edificios y techos reales sin puerta de entrada (los del campus con puerta ya se entran por ella). El punto
     * de registro es el medio de la pared más larga, [OUTSIDE_METERS] hacia afuera.
     */
    fun searchable(footprints: List<CampusFootprint>): List<SearchableBuilding> {
        val out = ArrayList<SearchableBuilding>()
        for (fp in footprints) {
            if ((fp.kind != FootprintKind.BUILDING && fp.kind != FootprintKind.ROOF) || fp.doors.isNotEmpty() || fp.id in ART_IDS) continue
            val point = outsidePoint(fp.points) ?: continue
            val minLat = fp.points.minOf { it.latitude }
            val maxLat = fp.points.maxOf { it.latitude }
            val minLon = fp.points.minOf { it.longitude }
            val maxLon = fp.points.maxOf { it.longitude }
            val lonScale = cos((minLat + maxLat) / 2.0 * PI_OVER_180)
            val spanX = (maxLon - minLon) * METERS_PER_DEGREE * lonScale
            val spanY = (maxLat - minLat) * METERS_PER_DEGREE
            out.add(SearchableBuilding(fp.id, profileFor(fp.name, fp.amenity, fp.buildingType), point.latitude, point.longitude, maxOf(spanX, spanY), minOf(spanX, spanY)))
        }
        return out
    }

    private fun outsidePoint(points: List<GeoPoint>): GeoPoint? {
        val n = points.size
        if (n < 3) return null
        var best = -1
        var bestLength = MIN_EDGE_METERS
        for (k in 0 until n) {
            val length = edgeLengthMeters(points[k], points[(k + 1) % n])
            if (length > bestLength) {
                bestLength = length
                best = k
            }
        }
        if (best < 0) return null
        val a = points[best]
        val b = points[(best + 1) % n]
        val midLat = (a.latitude + b.latitude) / 2.0
        val midLon = (a.longitude + b.longitude) / 2.0
        val centerLat = points.sumOf { it.latitude } / n
        val centerLon = points.sumOf { it.longitude } / n
        // Se aleja del centro del edificio, en metros (la longitud mide menos grados por metro con la latitud).
        val lonScale = cos(midLat * PI_OVER_180)
        val dy = (midLat - centerLat) * METERS_PER_DEGREE
        val dx = (midLon - centerLon) * METERS_PER_DEGREE * lonScale
        val norm = sqrt(dx * dx + dy * dy)
        if (norm < 1e-6) return GeoPoint(midLat, midLon)
        return GeoPoint(
            midLat + dy / norm * OUTSIDE_METERS / METERS_PER_DEGREE,
            midLon + dx / norm * OUTSIDE_METERS / (METERS_PER_DEGREE * lonScale),
        )
    }

    private const val PI_OVER_180 = 0.017453292519943295
}

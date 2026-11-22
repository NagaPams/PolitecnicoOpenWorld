package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlinx.serialization.Serializable
import ovh.gabrielhuav.pow.data.json.PowJson
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.log.powLog

// Esquema de edificio del campus: lo que OSM no sabe (niveles, material, color, puertas, ventanas).
// La geometría sigue siendo la de OSM (`escom_osm_footprints.json`); este archivo se une por `id`.
// Dibujo (IsoWorldView) y colisión por bordes salen de aquí. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Dónde vive el JSON de edificios. */
const val RUTA_EDIFICIOS_CAMPUS = "CONFIG/escom_buildings_spec.json"

/** Material de fachada. Los valores nuevos van AL FINAL. */
@Serializable
enum class BuildingMaterial { DEFAULT, BRICK, CONCRETE, PLASTER }

/** Estado de una puerta. Los valores nuevos van AL FINAL. */
@Serializable
enum class DoorState { OPEN, CLOSED, BARRICADED }

/**
 * Puerta en la arista [edge] (del vértice `edge` al siguiente), centrada en la fracción [t] de su largo.
 * [entry] es la ruta de interior que dispara (vacío = ninguna).
 */
@Serializable
data class DoorSpec(
    val edge: Int = 0,
    val t: Double = 0.5,
    val widthM: Double = 2.0,
    val state: DoorState = DoorState.OPEN,
    val entry: String = "",
)

/** Ventanas procedurales: una cada [spacingM] metros de pared y por nivel; 0 = sin ventanas. */
@Serializable
data class WindowSpec(val spacingM: Double = 3.0)

@Serializable
data class BuildingSpec(
    val id: Long,
    /** 0 = usar la altura deducida de OSM. */
    val levels: Int = 0,
    val material: BuildingMaterial = BuildingMaterial.DEFAULT,
    /** `#RRGGBB` del techo; vacío = el del material. */
    val roofColor: String = "",
    val windows: WindowSpec = WindowSpec(),
    val doors: List<DoorSpec> = emptyList(),
)

@Serializable
data class BuildingSpecFile(
    val version: Int = 1,
    val buildings: List<BuildingSpec> = emptyList(),
) {
    private val byId: Map<Long, BuildingSpec> by lazy { buildings.associateBy { it.id } }

    fun specFor(id: Long): BuildingSpec? = byId[id]
}

private const val METERS_PER_DEGREE = 111_320.0

/** Largo real de una arista en metros (plano local; suficiente para edificios). */
fun edgeLengthMeters(a: GeoPoint, b: GeoPoint): Double {
    val north = (b.latitude - a.latitude) * METERS_PER_DEGREE
    val east = (b.longitude - a.longitude) * METERS_PER_DEGREE * cos(a.latitude * PI / 180.0)
    return hypot(north, east)
}

/** `#RRGGBB` → ARGB opaco, o null si no es válido. */
fun parseColorHex(hex: String): Long? {
    if (hex.length != 7 || hex[0] != '#') return null
    val rgb = hex.substring(1).toLongOrNull(16) ?: return null
    return 0xFF000000L or rgb
}

/** Carga el esquema; si falta o está roto devuelve uno vacío (nunca lanza). Bloqueante: fuera del hilo de UI. */
fun cargarEsquemaEdificios(): BuildingSpecFile = try {
    PowJson.decodeFromString<BuildingSpecFile>(PowAssets.texto(RUTA_EDIFICIOS_CAMPUS))
} catch (e: Exception) {
    powLog("BuildingSpec", "Sin esquema de edificios ($RUTA_EDIFICIOS_CAMPUS): ${e::class.simpleName}: ${e.message}")
    BuildingSpecFile()
}

/** Puerta de edificio que lleva a un interior: [id] estable (`<huella>_e<arista>`), [entry] es la ruta de navegación. */
data class EntryDoor(val id: String, val entry: String, val location: GeoPoint)

/** Centro de cada puerta con `entry` (las de `entry` vacío no llevan a ningún interior). */
fun List<CampusFootprint>.entryDoors(): List<EntryDoor> = flatMap { fp ->
    val n = fp.points.size
    if (n < 2) return@flatMap emptyList<EntryDoor>()
    fp.doors.filter { it.entry.isNotEmpty() && it.edge in 0 until n }.map { door ->
        val a = fp.points[door.edge]
        val b = fp.points[(door.edge + 1) % n]
        EntryDoor(
            id = "${fp.id}_e${door.edge}",
            entry = door.entry,
            location = GeoPoint(
                a.latitude + (b.latitude - a.latitude) * door.t,
                a.longitude + (b.longitude - a.longitude) * door.t,
            ),
        )
    }
}

/** Puertas con interior del campus; vacío si faltan las huellas o el esquema (nunca lanza). Bloqueante. */
fun cargarPuertasDeEntrada(): List<EntryDoor> = try {
    PowJson.decodeFromString<CampusFootprintsFile>(PowAssets.texto(RUTA_HUELLAS_CAMPUS))
        .toFootprints(cargarEsquemaEdificios()).entryDoors()
} catch (e: Exception) {
    powLog("BuildingSpec", "Sin puertas de entrada: ${e::class.simpleName}: ${e.message}")
    emptyList()
}

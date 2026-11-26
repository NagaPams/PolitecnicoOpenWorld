package ovh.gabrielhuav.pow.features.map_exterior.ui.iso

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.PI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.map.isoScreenSector
import ovh.gabrielhuav.pow.domain.models.map.isoSpriteHeading
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.imagen.decodificarReducido

// Arte isométrico de NPC, zombis, policía y vehículos (hojas cortadas con tools/slice_iso_actor.py).
// Los personajes cargan SOLO idle y caminar (4 cuadros x 5 direcciones) bajo demanda; los vehículos, sus 5 vistas.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

private const val SKIN_REDUCTION = 2
private const val VEHICLE_REDUCTION = 2
private const val MAX_RESIDENT_SKINS = 16
private const val MAX_RESIDENT_RUN_SKINS = 6
private const val TRACK_CAPACITY = 600
private const val MOVE_EPSILON_METERS = 0.04
private const val METERS_PER_DEGREE = 111_320.0
private const val TRACK_IDLE_MS = 350L

/** Cuadros que se cargan por dirección: idle + 3 de caminar. */
internal const val SKIN_SLOTS = 4

/** Dirección de la fila de la hoja (S, SE, E, NE, N) para cada sector de pantalla (0 = N ... 7 = NW). */
private val SECTOR_ROW = intArrayOf(4, 3, 2, 1, 0, 1, 2, 3)
private val DIRS = arrayOf("s", "se", "e", "ne", "n")
private val STEP_ORDER = intArrayOf(1, 2, 3, 2)

private val PERSON_SKINS = arrayOf(
    "NPC/Iso/student_red_jacket", "NPC/Iso/student_woman", "NPC/Iso/professor", "NPC/Iso/vendor",
    "NPC/Iso/woman_athlete", "NPC/Iso/woman_braids", "NPC/Iso/woman_glasses", "NPC/Iso/woman_lab", "NPC/Iso/woman_professor",
)
private val ZOMBIE_SKINS = arrayOf(
    "ZOMBIE/Iso/zombie_01_chamarra_roja", "ZOMBIE/Iso/zombie_02_turquesa", "ZOMBIE/Iso/zombie_03_polo",
    "ZOMBIE/Iso/zombie_04_bata_lila", "ZOMBIE/Iso/zombie_05_profesor", "ZOMBIE/Iso/zombie_06_guardia",
    "ZOMBIE/Iso/zombie_07_deportista", "ZOMBIE/Iso/zombie_08_vendedor", "ZOMBIE/Iso/zombie_09_profesora",
    "ZOMBIE/Iso/zombie_10_mantenimiento",
)
private const val COP_SKIN = "POLICE/Iso/police_officer"
// Skins añadidas sin quitar las anteriores (t22, arte provisional generado por código; ver tools/make_iso_derived_sheets.py):
// paramédico y Prankedy como peatones raros, granaderos como parte de la policía a pie y el gato del campus.
private const val PARAMEDIC_SKIN = "NPC/Iso/paramedico"
private const val PRANKEDY_SKIN = "NPC/Iso/prankedy"
private const val CAT_SKIN = "NPC/Iso/cat"
private val RIOT_COP_SKINS = arrayOf("POLICE/Iso/granadero_hombre", "POLICE/Iso/granadero_mujer")

/** Vehículos isométricos: carpeta y largo en metros con el que se dibuja el lienzo (ancho del sprite). */
internal enum class IsoVehicle(val folder: String, val drawMeters: Float, val tintable: Boolean) {
    SEDAN("sedan", 5.2f, true),
    PICKUP("pickup", 5.6f, true),
    VAN("van", 5.8f, true),
    BUS("bus", 11.5f, true),
    BOX_TRUCK("box_truck", 7.8f, true),
    SEMI_TRACTOR("semi_tractor", 6.8f, true),
    POLICE("police", 5.2f, false),
}

/** Cinco vistas de un vehículo: N, NE, E, SE, S (las del oeste salen espejando). */
internal class IsoVehicleViews(val frames: Array<ImageBitmap?>)

/** Elige el sprite según el modelo del coche del juego; los camiones y el autobús se reparten por id. */
internal fun isoVehicleFor(type: NpcType?, model: CarModel, id: String): IsoVehicle = when {
    type == NpcType.POLICE_CAR -> IsoVehicle.POLICE
    model == CarModel.VAN -> IsoVehicle.VAN
    model == CarModel.SUV -> IsoVehicle.PICKUP
    id.isNotEmpty() && model == CarModel.WAGON -> when (id.hashCode().mod(4)) {
        0 -> IsoVehicle.BOX_TRUCK
        1 -> IsoVehicle.SEMI_TRACTOR
        2 -> IsoVehicle.BUS
        else -> IsoVehicle.SEDAN
    }
    else -> IsoVehicle.SEDAN
}

/** Carpeta de la skin de un personaje (estable por id) o null si el tipo no usa hojas. */
internal fun isoSkinFor(type: NpcType?, id: String): String? = when (type) {
    NpcType.PERSON -> personSkin(id)
    NpcType.ZOMBIE -> ZOMBIE_SKINS[id.hashCode().mod(ZOMBIE_SKINS.size)]
    NpcType.POLICE_COP -> copSkin(id)
    NpcType.CAT -> CAT_SKIN
    else -> null
}

/** Peatón: 1 de cada 24 es paramédico o Prankedy; el resto conserva el reparto de siempre por id. */
internal fun personSkin(id: String): String = when ((id.hashCode() ushr 7).mod(24)) {
    0 -> PARAMEDIC_SKIN
    1 -> PRANKEDY_SKIN
    else -> PERSON_SKINS[id.hashCode().mod(PERSON_SKINS.size)]
}

/** Policía a pie: 1 de cada 3 es granadero (hombre o mujer); el resto, el oficial de siempre. */
internal fun copSkin(id: String): String {
    val h = (id.hashCode() ushr 5).mod(6)
    return if (h < 2) RIOT_COP_SKINS[h] else COP_SKIN
}

/** Última posición y sector de pantalla de un actor, para elegir dirección y animar sin que el modelo traiga rumbo. */
internal class IsoTrack {
    var lat = 0.0
    var lon = 0.0
    var sector = 4
    var movingUntilMs = 0L
    var anchorMs = 0L
    var lastSeenMs = 0L
    var speedMps = 0f
    /** Cuadro del ciclo (0..): avanza según la velocidad, sin saltos al cambiar de ritmo. */
    var phase = 0f
}

internal class IsoActorArt(private val scope: CoroutineScope) {
    private val skins = LinkedHashMap<String, Array<ImageBitmap?>>()
    private val loading = HashSet<String>()
    private val tracks = HashMap<String, IsoTrack>()
    private val runSkins = LinkedHashMap<String, Array<ImageBitmap?>>()
    private val runLoading = HashSet<String>()
    private val vehicles = HashMap<IsoVehicle, IsoVehicleViews>()
    private var vehiclesRequested = false

    /** Cuadros de una skin (20 = 5 direcciones x 4), o null mientras se cargan en segundo plano. */
    fun skinFrames(skin: String): Array<ImageBitmap?>? {
        skins[skin]?.let { return it }
        if (loading.add(skin)) {
            scope.launch {
                val frames = withContext(Dispatchers.Default) { loadSkin(skin) }
                if (skins.size >= MAX_RESIDENT_SKINS) skins.remove(skins.keys.first())
                skins[skin] = frames
                loading.remove(skin)
            }
        }
        return null
    }

    /** Cuadros de correr (mismo índice que [skinFrames], sin idle), cargados solo cuando alguien de esa skin corre. */
    private fun runFrames(skin: String): Array<ImageBitmap?>? {
        runSkins[skin]?.let { return it }
        if (runLoading.add(skin)) {
            scope.launch {
                val frames = withContext(Dispatchers.Default) { loadRun(skin) }
                if (runSkins.size >= MAX_RESIDENT_RUN_SKINS) runSkins.remove(runSkins.keys.first())
                runSkins[skin] = frames
                runLoading.remove(skin)
            }
        }
        return null
    }

    /** Cuadro a dibujar de [skin] para el actor [t]: idle, caminar o correr según su velocidad real. */
    fun frame(skin: String, t: IsoTrack, nowMs: Long): ImageBitmap? {
        val walk = skinFrames(skin) ?: return null
        val moving = nowMs < t.movingUntilMs
        val step = STEP_ORDER[t.phase.toInt() and 3]
        val index = SECTOR_ROW[t.sector] * SKIN_SLOTS + if (moving) step else 0
        if (moving && IsoGait.isRunning(t.speedMps)) runFrames(skin)?.get(index)?.let { return it }
        return walk[index]
    }

    fun vehicleViews(vehicle: IsoVehicle): IsoVehicleViews? {
        if (!vehiclesRequested) {
            vehiclesRequested = true
            scope.launch {
                for (v in IsoVehicle.entries) {
                    val frames = withContext(Dispatchers.Default) { loadVehicle(v) }
                    vehicles[v] = IsoVehicleViews(frames)
                }
            }
        }
        return vehicles[vehicle]
    }

    /** Actualiza el rumbo y el movimiento del actor desde su posición actual. */
    fun track(id: String, lat: Double, lon: Double, nowMs: Long): IsoTrack {
        var t = tracks[id]
        if (t == null) {
            if (tracks.size >= TRACK_CAPACITY) tracks.clear()
            t = IsoTrack().also { it.lat = lat; it.lon = lon; it.anchorMs = nowMs; it.lastSeenMs = nowMs }
            tracks[id] = t
            return t
        }
        val dt = (nowMs - t.lastSeenMs).coerceIn(0L, 100L)
        t.lastSeenMs = nowMs
        val north = (lat - t.lat) * METERS_PER_DEGREE
        val east = (lon - t.lon) * METERS_PER_DEGREE * cos(lat * PI / 180.0)
        val moved = hypot(north, east)
        if (moved >= MOVE_EPSILON_METERS) {
            t.movingUntilMs = nowMs + TRACK_IDLE_MS
            // Los empujones chicos (separación entre cuerpos) mueven el cuerpo, pero no giran al actor ni cuentan como velocidad.
            if (moved >= IsoGait.HEADING_MIN_METERS) {
                val headingDeg = (atan2(east, north) * 180.0 / PI + 360.0) % 360.0
                t.sector = isoScreenSector(isoSpriteHeading(headingDeg))
                val seconds = (nowMs - t.anchorMs) / 1000f
                if (seconds > 0f) t.speedMps = IsoGait.smooth(t.speedMps, (moved / seconds).toFloat())
                t.anchorMs = nowMs
                t.lat = lat
                t.lon = lon
            }
        } else if (nowMs >= t.movingUntilMs) {
            t.speedMps = 0f
            t.anchorMs = nowMs
        }
        if (nowMs < t.movingUntilMs) t.phase += dt / IsoGait.stepMs(t.speedMps.coerceAtLeast(1.0f))
        return t
    }

    companion object {
        /** Índice del cuadro: fila de dirección x SKIN_SLOTS + (0 = idle, 1..3 = caminar). */
        fun frameIndex(sector: Int, moving: Boolean, elapsedMs: Long): Int =
            SECTOR_ROW[sector] * SKIN_SLOTS + if (moving) STEP_ORDER[((elapsedMs / 130) % 4).toInt()] else 0

        fun mirrored(sector: Int): Boolean = sector >= 5
    }
}

private fun loadSkin(skin: String): Array<ImageBitmap?> {
    val out = arrayOfNulls<ImageBitmap>(DIRS.size * SKIN_SLOTS)
    for (d in DIRS.indices) {
        for (slot in 0 until SKIN_SLOTS) {
            val path = if (slot == 0) "SPRITES/$skin/Idle/iso_${DIRS[d]}_i_1.webp"
            else "SPRITES/$skin/Walk/iso_${DIRS[d]}_w_$slot.webp"
            out[d * SKIN_SLOTS + slot] = runCatching { decodificarReducido(PowAssets.bytes(path), SKIN_REDUCTION) }.getOrNull()
        }
    }
    return out
}

private fun loadRun(skin: String): Array<ImageBitmap?> {
    val out = arrayOfNulls<ImageBitmap>(DIRS.size * SKIN_SLOTS)
    for (d in DIRS.indices) {
        for (slot in 1 until SKIN_SLOTS) {
            out[d * SKIN_SLOTS + slot] = runCatching { decodificarReducido(PowAssets.bytes("SPRITES/$skin/Run/iso_${DIRS[d]}_r_$slot.webp"), SKIN_REDUCTION) }.getOrNull()
        }
    }
    return out
}

private val VEHICLE_VIEW_NAMES = arrayOf("n", "ne", "e", "se", "s")

private fun loadVehicle(vehicle: IsoVehicle): Array<ImageBitmap?> = Array(VEHICLE_VIEW_NAMES.size) { i ->
    runCatching {
        decodificarReducido(PowAssets.bytes("SPRITES/VEHICLES/Iso/${vehicle.folder}/${VEHICLE_VIEW_NAMES[i]}.webp"), VEHICLE_REDUCTION)
    }.getOrNull()
}

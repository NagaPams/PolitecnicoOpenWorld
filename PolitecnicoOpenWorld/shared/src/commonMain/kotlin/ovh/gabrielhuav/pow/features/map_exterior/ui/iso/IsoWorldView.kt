package ovh.gabrielhuav.pow.features.map_exterior.ui.iso

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ovh.gabrielhuav.pow.features.map_exterior.ui.components.PlayerAction
import ovh.gabrielhuav.pow.platform.assets.PowAssets
import ovh.gabrielhuav.pow.platform.imagen.decodificarReducido
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.SmokeRules
import ovh.gabrielhuav.pow.domain.models.map.VehicleDepthRules
import ovh.gabrielhuav.pow.domain.models.map.VehicleSpriteAnchor
import ovh.gabrielhuav.pow.domain.models.map.bodyDimensions
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.survival.LootKind
import ovh.gabrielhuav.pow.domain.models.survival.LootPoint
import ovh.gabrielhuav.pow.domain.models.map.BuildingMaterial
import ovh.gabrielhuav.pow.domain.models.map.CampusFootprint
import ovh.gabrielhuav.pow.domain.models.map.edgeLengthMeters
import ovh.gabrielhuav.pow.domain.models.map.FootprintKind
import ovh.gabrielhuav.pow.domain.models.map.IsoProjection
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.CarModel
import ovh.gabrielhuav.pow.domain.models.map.CarVariantRules
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.map.isoPointInPolygon
import ovh.gabrielhuav.pow.domain.models.map.isoScreenSector
import ovh.gabrielhuav.pow.domain.models.map.isoWorldAngleToScreenHeading
import ovh.gabrielhuav.pow.domain.models.map.isoWallFacesCamera
import ovh.gabrielhuav.pow.domain.models.map.isoSpriteHeading
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.time.TimeSource

private const val ISO_BAKE_ZOOM = 18.0
private const val ISO_LAYER_MARGIN = 8f
private const val ISO_ROOF_HIDDEN_ALPHA = 0.1f
private const val ISO_ROOF_FADE_STEP = 0.12f
private val ISO_GROUND = Color(0xFF65745E)
private val ISO_ROAD = Color(0xFF8D908A)
private val ISO_ROAD_CASING = Color(0xFF3B3E3A)
/** Los zombis se tiñen de verde enfermizo para no confundirse con los estudiantes (t32). */
private val ISO_ZOMBIE_TINT = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
    0.72f, 0.20f, 0f, 0f, 0f,
    0.05f, 1.00f, 0.05f, 0f, 14f,
    0f, 0.10f, 0.55f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)))
private const val ISO_THREAT_METERS = 12.0
private const val ISO_THREAT_MAX = 8
private val ISO_WALKWAY = Color(0xFFA49F8D)
private val ISO_ROOF = Color(0xFFCDBF9B)
private val ISO_ROOF_EDGE = Color(0xFF4E4637)
private val ISO_WALL_LIGHT = Color(0xFF8A7A62)
private val ISO_WALL_DARK = Color(0xFF574E40)
private val ISO_PATCH_DARK = Color(0x1A1B2A18)
private val ISO_PATCH_LIGHT = Color(0x14C8DCB0)
private val ISO_SHADOW = Color(0x47101510)
private const val ISO_SHADOW_MAX_DX = 16f
private const val ISO_SHADOW_MAX_DY = 7f
private val ISO_FOG = Color(0x6E222A33)
// t40: fuera del radio de visión la niebla es casi opaca y cubre toda la pantalla (el degradado se queda en este color).
private val ISO_FOG_EDGE = Color(0xF2141A22)
private val ISO_WINDOW = Color(0xFF2C3A47)
private val ISO_DOOR = Color(0xFF241C14)
private const val ISO_LEVEL_METERS = 3.2
private const val ISO_WINDOW_WIDTH_M = 1.0
private const val ISO_WINDOW_BASE_M = 1.0
private const val ISO_WINDOW_HEIGHT_M = 1.3
private const val ISO_DOOR_HEIGHT_M = 2.2
private const val ISO_MIN_PERSON_PX = 44f
private const val ISO_METERS_PER_DEGREE_LAT = 111_320.0
private const val ISO_METERS_PER_DEGREE_LON = 104_800.0

/** El gato usa la misma hoja de personaje: se dibuja a ~1/4 del alto de una persona. */
private const val ISO_CAT_SIZE = 0.42f
private const val ISO_MAX_SPRITE_BOOST = 2.5f

// Hoja del jugador al estándar POW (tools/slice_iso_player.py): 5 direcciones x 8 cuadros.
private const val ISO_PLAYER_PATH = "SPRITES/PLAYER/EstudianteIso/"
private const val ISO_PLAYER_REDUCTION = 2
private const val ISO_PLAYER_CANVAS_METERS = 2.3f
private const val ISO_PLAYER_FOOT_FRACTION = 0.036f
private val ISO_PLAYER_DIRS = arrayOf("s", "se", "e", "ne", "n")
internal val ISO_SLOT_FOLDER = arrayOf("Idle", "Walk", "Walk", "Walk", "Run", "Run", "Run", "Special")
private val ISO_SLOT_LETTER = arrayOf("i", "w", "w", "w", "r", "r", "r", "s")
private val ISO_SLOT_NUMBER = intArrayOf(1, 1, 2, 3, 1, 2, 3, 1)
internal val ISO_SECTOR_ROW = intArrayOf(4, 3, 2, 1, 0, 1, 2, 3)
private val ISO_STEP_ORDER = intArrayOf(0, 1, 2, 1)

internal suspend fun loadIsoPlayerSprites(): Array<ImageBitmap?> = withContext(Dispatchers.Default) {
    val slots = ISO_SLOT_FOLDER.size
    val out = arrayOfNulls<ImageBitmap>(ISO_PLAYER_DIRS.size * slots)
    for (d in ISO_PLAYER_DIRS.indices) {
        for (slot in 0 until slots) {
            val path = "$ISO_PLAYER_PATH${ISO_SLOT_FOLDER[slot]}/iso_${ISO_PLAYER_DIRS[d]}_${ISO_SLOT_LETTER[slot]}_${ISO_SLOT_NUMBER[slot]}.webp"
            out[d * slots + slot] = runCatching { decodificarReducido(PowAssets.bytes(path), ISO_PLAYER_REDUCTION) }.getOrNull()
        }
    }
    out
}

/** Tonos de una fachada: techo, pared iluminada y pared en sombra. */
private class IsoTones(val roof: Color, val light: Color, val dark: Color)

private val ISO_TONES_BRICK = IsoTones(Color(0xFFC7A48A), Color(0xFF9A5F48), Color(0xFF6B3F31))
private val ISO_TONES_CONCRETE = IsoTones(Color(0xFFC9CBCB), Color(0xFF8E9296), Color(0xFF5D6165))
private val ISO_TONES_PLASTER = IsoTones(Color(0xFFD9CDB3), Color(0xFFB8A98C), Color(0xFF857964))
private val ISO_TONES_DEFAULT = IsoTones(ISO_ROOF, ISO_WALL_LIGHT, ISO_WALL_DARK)

private fun isoTones(material: BuildingMaterial): IsoTones = when (material) {
    BuildingMaterial.BRICK -> ISO_TONES_BRICK
    BuildingMaterial.CONCRETE -> ISO_TONES_CONCRETE
    BuildingMaterial.PLASTER -> ISO_TONES_PLASTER
    BuildingMaterial.DEFAULT -> ISO_TONES_DEFAULT
}

/** Cuadrilátero sobre la pared i→j entre las fracciones [t0, t1] y las alturas [h0, h1] en píxeles. */
private fun isoWallQuad(
    xs: FloatArray, ys: FloatArray, i: Int, j: Int, left: Float, top: Float,
    t0: Float, t1: Float, h0: Float, h1: Float,
): Path {
    val ax = xs[i] + (xs[j] - xs[i]) * t0 - left
    val ay = ys[i] + (ys[j] - ys[i]) * t0 - top
    val bx = xs[i] + (xs[j] - xs[i]) * t1 - left
    val by = ys[i] + (ys[j] - ys[i]) * t1 - top
    return Path().apply {
        moveTo(ax, ay - h0)
        lineTo(bx, by - h0)
        lineTo(bx, by - h1)
        lineTo(ax, ay - h1)
        close()
    }
}

private data class IsoGroundPath(val path: Path, val bounds: Rect, val color: Color, val edge: Color, val edgeStroke: Stroke, val pattern: IsoGroundPattern? = null, val patternStroke: Stroke? = null)
private data class IsoRoadPath(val path: Path, val bounds: Rect, val stroke: Stroke, val color: Color, val casing: Stroke)
private class IsoBuildingLayer(
    val layer: GraphicsLayer,
    val xs: FloatArray,
    val ys: FloatArray,
    var alpha: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val depth: Double,
    val id: Long,
)

/** Dueño de las GraphicsLayer: las libera al reconstruir el caché y al salir de la composición. */
private class IsoLayerPool(private val context: GraphicsContext) : RememberObserver {
    private val layers = ArrayList<GraphicsLayer>()

    fun track(layer: GraphicsLayer): GraphicsLayer { layers.add(layer); return layer }

    fun releaseAll() {
        for (layer in layers) context.releaseGraphicsLayer(layer)
        layers.clear()
    }

    override fun onRemembered() = Unit
    override fun onForgotten() = releaseAll()
    override fun onAbandoned() = releaseAll()
}

private const val ISO_TINT_CACHE_MAX = 48
private const val ISO_TINT_GAIN = 1.6f
private val isoTintCache = HashMap<Int, ColorFilter>()

/** Tiñe el sprite conservando su sombreado: la luminancia del sprite se reparte por el color del coche. */
private fun isoCarTint(color: Int): ColorFilter {
    val key = (color and 0xF0F0F0) or 0xFF000000.toInt()
    isoTintCache[key]?.let { return it }
    if (isoTintCache.size >= ISO_TINT_CACHE_MAX) isoTintCache.clear()
    val r = ((key shr 16) and 0xFF) / 255f * ISO_TINT_GAIN
    val g = ((key shr 8) and 0xFF) / 255f * ISO_TINT_GAIN
    val b = (key and 0xFF) / 255f * ISO_TINT_GAIN
    val filter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        0.299f * r, 0.587f * r, 0.114f * r, 0f, 0f,
        0.299f * g, 0.587f * g, 0.114f * g, 0f, 0f,
        0.299f * b, 0.587f * b, 0.114f * b, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
    isoTintCache[key] = filter
    return filter
}

/**
 * Prototipo de vista del campus: recibe datos, no ViewModel. Los trazos y las capas de cada
 * edificio se preparan a un zoom fijo; al mover la cámara solo se trasladan y escalan.
 */
@Composable
fun IsoWorldView(
    center: GeoPoint?,
    zoom: Double,
    roads: List<MapWay>,
    footprints: List<CampusFootprint>,
    npcs: List<Npc>,
    collectibles: List<ActiveCollectible>,
    objective: GeoPoint?,
    destination: GeoPoint?,
    route: List<GeoPoint>,
    missionRoute: List<GeoPoint>,
    isDriving: Boolean,
    vehicleHeading: Float,
    fogVisionMeters: Double,
    /** Tope del radio de la niebla como fracción del lado menor de la pantalla (t60: más amplio al conducir). */
    fogMaxFraction: Float = 0.40f,
    fogStrength: Float = 1f,
    /** t21: sin adornos (manchas de suelo) cuando hay poca memoria. */
    lowDetail: Boolean = false,
    modifier: Modifier = Modifier,
    playerMoveAngle: Float? = null,
    playerAction: PlayerAction = PlayerAction.IDLE,
    vehicleColor: Int? = null,
    lootPoints: List<LootPoint> = emptyList(),
    /** Salud del coche del jugador (0-100): con poca sale humo del motor (t04). */
    vehicleHealth: Double = 100.0,
    /** Modelo, id y piel de patrulla del coche del jugador: el camión, el autobús y la patrulla se dibujan como son. */
    vehicleModel: CarModel? = null,
    vehicleId: String? = null,
    vehiclePolice: Boolean = false,
) {
    if (center == null) return
    val graphicsContext = LocalGraphicsContext.current
    val playerFrames by produceState<Array<ImageBitmap?>?>(null) { value = loadIsoPlayerSprites() }
    val artScope = rememberCoroutineScope()
    val art = remember { IsoActorArt(artScope) }
    val centerState by rememberUpdatedState(center)
    val zoomState by rememberUpdatedState(zoom)
    val npcState by rememberUpdatedState(npcs)
    val collectibleState by rememberUpdatedState(collectibles)
    val lootState by rememberUpdatedState(lootPoints)
    val objectiveState by rememberUpdatedState(objective)
    val destinationState by rememberUpdatedState(destination)
    val routeState by rememberUpdatedState(route)
    val missionRouteState by rememberUpdatedState(missionRoute)
    val drivingState by rememberUpdatedState(isDriving)
    val vehicleHeadingState by rememberUpdatedState(vehicleHeading)
    val lowDetailState by rememberUpdatedState(lowDetail)
    val moveAngleState by rememberUpdatedState(playerMoveAngle)
    val actionState by rememberUpdatedState(playerAction)
    val vehicleColorState by rememberUpdatedState(vehicleColor)
    val vehicleHealthState by rememberUpdatedState(vehicleHealth)
    val playerVehicle = isoVehicleFor(if (vehiclePolice) NpcType.POLICE_CAR else NpcType.CAR, vehicleModel ?: CarModel.SEDAN, vehicleId.orEmpty())
    val layerPool = remember { IsoLayerPool(graphicsContext) }
    val density = LocalDensity.current.density.toDouble()
    val reference = remember(footprints) { footprints.firstOrNull()?.points?.firstOrNull() ?: center }

    Box(modifier.fillMaxSize().clipToBounds().drawWithCache {
        layerPool.releaseAll()
        val projection = IsoProjection.forZoom(reference, ISO_BAKE_ZOOM, density, 0.0, 0.0)
        val grounds = ArrayList<IsoGroundPath>()
        val buildings = ArrayList<IsoBuildingLayer>()
        for (footprint in footprints) {
            val points = footprint.points
            if (points.size < 3) continue
            val xs = FloatArray(points.size)
            val ys = FloatArray(points.size)
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            var frontDepth = Double.POSITIVE_INFINITY
            for (i in points.indices) {
                val point = points[i]
                xs[i] = projection.projectX(point.latitude, point.longitude).toFloat()
                ys[i] = projection.projectY(point.latitude, point.longitude).toFloat()
                minX = minOf(minX, xs[i]); maxX = maxOf(maxX, xs[i])
                minY = minOf(minY, ys[i]); maxY = maxOf(maxY, ys[i])
                frontDepth = minOf(frontDepth, projection.depth(point))
            }
            val kind = footprint.kind
            if (kind != FootprintKind.BUILDING && kind != FootprintKind.ROOF) {
                val path = isoPolygon(xs, ys, 0f, 0f, 0f)
                val color = when (kind) {
                    FootprintKind.PARK -> Color(0xFF72956B)
                    FootprintKind.PARKING -> Color(0xFF91918B)
                    FootprintKind.PITCH -> Color(0xFF668F70)
                    else -> ISO_GROUND
                }
                // t50: cada tipo de suelo lleva su borde: bordillo oscuro (parque y estacionamiento) o línea blanca (cancha).
                val ppmBake = projection.pixelsPerMeter.toFloat()
                val (edge, edgeMeters) = when (kind) {
                    FootprintKind.PITCH -> Color(0xCCF2F2EA) to 0.25f
                    FootprintKind.PARKING -> Color(0x99444440) to 0.45f
                    else -> Color(0x883F5A3C) to 0.45f
                }
                val groundBounds = path.getBounds()
                val pattern = isoGroundPattern(kind, groundBounds, ppmBake, footprint.id.hashCode())
                grounds.add(IsoGroundPath(path, groundBounds, color, edge, Stroke(width = edgeMeters * ppmBake), pattern, pattern?.let { Stroke(width = it.strokeMeters * ppmBake) }))
                continue
            }
            val rise = (footprint.heightMeters.coerceAtLeast(0.0) * projection.pixelsPerMeter).toFloat()
            val left = floor(minX - ISO_LAYER_MARGIN)
            val top = floor(minY - rise - ISO_LAYER_MARGIN)
            // t43: sombra suave a la derecha y abajo del edificio (dentro de la misma capa, crece con la altura).
            val shadowDx = (rise * 0.3f).coerceAtMost(ISO_SHADOW_MAX_DX)
            val shadowDy = (rise * 0.12f).coerceAtMost(ISO_SHADOW_MAX_DY)
            val right = ceil(maxX + ISO_LAYER_MARGIN + shadowDx)
            val bottom = ceil(maxY + ISO_LAYER_MARGIN + shadowDy)
            val width = (right - left).toInt().coerceAtLeast(1)
            val height = (bottom - top).toInt().coerceAtLeast(1)
            val roof = isoPolygon(xs, ys, left, top, rise)
            var winding = 0.0
            for (i in points.indices) {
                val j = (i + 1) % points.size
                winding += xs[i].toDouble() * ys[j] - xs[j].toDouble() * ys[i]
            }
            val tones = isoTones(footprint.material)
            val walls = ArrayList<Pair<Path, Color>>()
            val details = ArrayList<Pair<Path, Color>>()
            for (i in points.indices) {
                val j = (i + 1) % points.size
                if (!isoWallFacesCamera(xs[i].toDouble(), xs[j].toDouble(), winding)) continue
                val path = Path().apply {
                    moveTo(xs[i] - left, ys[i] - top)
                    lineTo(xs[j] - left, ys[j] - top)
                    lineTo(xs[j] - left, ys[j] - top - rise)
                    lineTo(xs[i] - left, ys[i] - top - rise)
                    close()
                }
                walls.add(path to if (ys[j] > ys[i]) tones.light else tones.dark)
                val lengthM = edgeLengthMeters(points[i], points[j])
                val ppm = projection.pixelsPerMeter.toFloat()
                if (footprint.windowSpacingM > 0.0 && lengthM > 2.0 * ISO_WINDOW_WIDTH_M) {
                    val count = (lengthM / footprint.windowSpacingM).toInt().coerceAtLeast(1)
                    val levels = (footprint.heightMeters / ISO_LEVEL_METERS).toInt().coerceAtLeast(1)
                    val half = (ISO_WINDOW_WIDTH_M / lengthM / 2.0).toFloat()
                    for (level in 0 until levels) {
                        val h0 = ((level * ISO_LEVEL_METERS + ISO_WINDOW_BASE_M) * ppm).toFloat()
                        val h1 = h0 + (ISO_WINDOW_HEIGHT_M * ppm).toFloat()
                        for (k in 0 until count) {
                            val center = (k + 0.5f) / count
                            details.add(isoWallQuad(xs, ys, i, j, left, top, center - half, center + half, h0, h1) to ISO_WINDOW)
                        }
                    }
                }
                for (door in footprint.doors) {
                    if (door.edge != i || lengthM <= 0.0) continue
                    val halfDoor = (door.widthM / lengthM / 2.0).toFloat()
                    val c = door.t.toFloat()
                    details.add(isoWallQuad(xs, ys, i, j, left, top, c - halfDoor, c + halfDoor, 0f,
                        (ISO_DOOR_HEIGHT_M * ppm).toFloat()) to ISO_DOOR)
                }
            }
            val layer = layerPool.track(obtainGraphicsLayer())
            layer.compositingStrategy = CompositingStrategy.ModulateAlpha
            val shadow = isoPolygon(xs, ys, left - shadowDx, top - shadowDy, 0f)
            layer.record(this, layoutDirection, IntSize(width, height)) {
                drawPath(shadow, ISO_SHADOW)
                for ((wall, color) in walls) drawPath(wall, color)
                for ((detail, color) in details) drawPath(detail, color)
                drawPath(roof, footprint.roofColor?.let { Color(it.toInt()) } ?: tones.roof)
                drawPath(roof, ISO_ROOF_EDGE, style = Stroke(width = 1.5f))
            }
            buildings.add(IsoBuildingLayer(layer, xs, ys, 1f, left, top, right, bottom, frontDepth, footprint.id))
        }
        buildings.sortWith(compareByDescending<IsoBuildingLayer> { it.depth }.thenBy { it.id })

        val bakedRoads = ArrayList<IsoRoadPath>()
        for (road in roads) {
            if (road.nodes.size < 2 || (!road.isForCars && !road.isForPeople)) continue
            val path = Path()
            for ((i, node) in road.nodes.withIndex()) {
                val x = projection.projectX(node.lat, node.lon).toFloat()
                val y = projection.projectY(node.lat, node.lon).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val widthMeters = if (road.isForCars) 6.0 else 2.0
            val stroke = Stroke(width = (widthMeters * projection.pixelsPerMeter).toFloat(), cap = StrokeCap.Round)
            val casing = Stroke(width = ((widthMeters + 1.2) * projection.pixelsPerMeter).toFloat(), cap = StrokeCap.Round)
            bakedRoads.add(IsoRoadPath(path, path.getBounds(), stroke, if (road.isForCars) ISO_ROAD else ISO_WALKWAY, casing))
        }
        val objectiveArrowOutline = Stroke(width = 3f)
        val threatDx = FloatArray(ISO_THREAT_MAX)
        val threatDy = FloatArray(ISO_THREAT_MAX)
        val objectiveArrow = Path().apply {
            moveTo(10f, 0f); lineTo(-8f, -9f); lineTo(-3f, 0f); lineTo(-8f, 9f); close()
        }
        var fogBrush: Brush? = null
        var cachedFogW = -1f
        var cachedFogH = -1f
        val actors = IsoActors(512)
        var cachedFogRadius = -1f
        val clock = TimeSource.Monotonic.markNow()
        var playerFrame: ImageBitmap? = null
        var playerMirror = false

        onDrawBehind {
            drawRect(ISO_GROUND)
            val player = centerState
            val scale = 2.0.pow(zoomState - ISO_BAKE_ZOOM).toFloat()
            val playerX = projection.projectX(player.latitude, player.longitude).toFloat()
            val playerY = projection.projectY(player.latitude, player.longitude).toFloat()
            val tx = size.width / 2f - playerX * scale
            val ty = size.height / 2f - playerY * scale
            val left = -tx / scale; val top = -ty / scale
            val right = (size.width - tx) / scale; val bottom = (size.height - ty) / scale
            // t50: manchas de pasto/tierra repartidas por celdas de ~3 m (fijas en el mundo, sin asignaciones); con zoom
            // muy lejano las celdas serían de pocos píxeles y se omiten.
            val patchCell = (IsoPatchRules.CELL_METERS * projection.pixelsPerMeter).toFloat()
            val ix0 = floor(left / patchCell).toInt(); val ix1 = floor(right / patchCell).toInt()
            val iy0 = floor(top / patchCell).toInt(); val iy1 = floor(bottom / patchCell).toInt()
            // Auditoría: además del mínimo en píxeles, un tope de celdas visibles para no dibujar miles de óvalos con zoom lejano.
            if (!lowDetailState && IsoPatchRules.shouldDraw(patchCell * scale, (ix1 - ix0 + 1).toLong() * (iy1 - iy0 + 1))) {
                val patchW = patchCell * scale * 0.55f
                val patchH = patchW * 0.5f
                for (iy in iy0..iy1) for (ix in ix0..ix1) {
                    if (!IsoPatchRules.hasPatch(ix, iy)) continue
                    val jx = IsoPatchRules.jitterX(ix, iy)
                    val jy = IsoPatchRules.jitterY(ix, iy)
                    drawOval(
                        if (IsoPatchRules.isDark(ix, iy)) ISO_PATCH_DARK else ISO_PATCH_LIGHT,
                        Offset((ix + jx) * patchCell * scale + tx, (iy + jy) * patchCell * scale + ty),
                        Size(patchW, patchH),
                    )
                }
            }
            actors.clear()
            val sector = moveAngleState?.let { isoScreenSector(isoWorldAngleToScreenHeading(it.toDouble())) } ?: 4
            val elapsed = clock.elapsedNow().inWholeMilliseconds
            val slot = when (actionState) {
                PlayerAction.IDLE -> 0
                PlayerAction.WALK -> 1 + ISO_STEP_ORDER[((elapsed / 110) % 4).toInt()]
                PlayerAction.RUN -> 4 + ISO_STEP_ORDER[((elapsed / 85) % 4).toInt()]
                PlayerAction.SPECIAL -> 7
            }
            playerFrame = playerFrames?.get(ISO_SECTOR_ROW[sector] * ISO_SLOT_FOLDER.size + slot)
            playerMirror = sector >= 5
            var playerCar: ImageBitmap? = null
            var playerCarMirror = false
            if (drivingState) {
                val sedan = art.vehicleViews(playerVehicle)
                val carDirection = isoVehicleDirection(vehicleHeadingState)
                playerCar = sedan?.frames?.get(carDirection.first)
                playerCarMirror = carDirection.second
            }
            val playerDepth = projection.depth(player)
            var playerCarY = playerY
            if (playerCar != null) {
                val body = CarVariantRules.dimensionsFor(vehicleModel ?: CarModel.SEDAN, vehicleId.orEmpty())
                val widthPx = projection.pixelsPerMeter.toFloat() * playerVehicle.drawMeters
                playerCarY += VehicleSpriteAnchor.groundDropPx(
                    body.lengthMeters, body.widthMeters, vehicleHeadingState.toDouble(),
                    art.vehicleViews(playerVehicle)?.bottoms?.get(isoVehicleDirection(vehicleHeadingState).first) ?: 1f,
                    widthPx * playerCar.height / playerCar.width, projection.pixelsPerMeter,
                )
            }
            actors.insert(playerX, playerCarY, playerDepth, null, "", vehicleHeadingState, vehicleColorState ?: -1,
                playerCar, playerCarMirror, playerVehicle.drawMeters, playerVehicle.tintable)
            var threatCount = 0
            val currentNpcs = npcState
            for (i in currentNpcs.indices) {
                val npc = currentNpcs[i]
                val x = projection.projectX(npc.location.latitude, npc.location.longitude).toFloat()
                val y = projection.projectY(npc.location.latitude, npc.location.longitude).toFloat()
                if (x < left - 20f || x > right + 20f || y < top - 20f || y > bottom + 20f) continue
                val heading = (npc.rotationAngle + 90f) % 360f
                var sprite: ImageBitmap? = null
                var mirror = false
                var meters = 0f
                var tintable = false
                var drawY = y
                val skin = isoSkinFor(npc.type, npc.id)
                if (skin != null) {
                    val track = art.track(npc.id, npc.location.latitude, npc.location.longitude, elapsed)
                    sprite = art.frame(skin, track, elapsed)
                    mirror = IsoActorArt.mirrored(track.sector)
                } else if (npc.type == NpcType.CAR || npc.type == NpcType.POLICE_CAR) {
                    val vehicle = isoVehicleFor(npc.type, npc.carModel, npc.id)
                    val direction = isoVehicleDirection(heading)
                    sprite = art.vehicleViews(vehicle)?.frames?.get(direction.first)
                    mirror = direction.second
                    meters = vehicle.drawMeters
                    tintable = vehicle.tintable
                    if (sprite != null) {
                        // El sprite se ancla por su fila inferior a la esquina más baja de la caja de colisión (ver VehicleSpriteAnchor).
                        val body = npc.bodyDimensions
                        val widthPx = projection.pixelsPerMeter.toFloat() * meters
                        drawY += VehicleSpriteAnchor.groundDropPx(
                            body.lengthMeters, body.widthMeters, heading.toDouble(),
                            art.vehicleViews(vehicle)?.bottoms?.get(direction.first) ?: 1f,
                            widthPx * sprite.height / sprite.width, projection.pixelsPerMeter,
                        )
                    }
                }
                if (npc.type == NpcType.ZOMBIE && threatCount < ISO_THREAT_MAX) {
                    val dxm = (npc.location.longitude - player.longitude) * 104800.0
                    val dym = (npc.location.latitude - player.latitude) * 111320.0
                    if (dxm * dxm + dym * dym < ISO_THREAT_METERS * ISO_THREAT_METERS) {
                        threatDx[threatCount] = (x - playerX) * scale
                        threatDy[threatCount] = (y - playerY) * scale
                        threatCount++
                    }
                }
                var depthKey = projection.depth(npc.location)
                if (meters > 0f) {
                    // Vehículo largo: se ordena con el punto de su eje más cercano en profundidad al jugador (ver VehicleDepthRules).
                    val eastToPlayer = (player.longitude - npc.location.longitude) * ISO_METERS_PER_DEGREE_LON
                    val northToPlayer = (player.latitude - npc.location.latitude) * ISO_METERS_PER_DEGREE_LAT
                    if (eastToPlayer * eastToPlayer + northToPlayer * northToPlayer < VehicleDepthRules.NEAR_METERS * VehicleDepthRules.NEAR_METERS) {
                        depthKey = VehicleDepthRules.sortKey(
                            depthKey, VehicleDepthRules.halfSpan(npc.bodyDimensions.lengthMeters, heading.toDouble()), playerDepth,
                            VehicleDepthRules.lateralDepth(eastToPlayer, northToPlayer, heading.toDouble()),
                        )
                    }
                }
                actors.insert(x, drawY, depthKey, npc.type, npc.id, heading, npc.carColor, sprite, mirror, meters, tintable)
            }
            val ppm = projection.pixelsPerMeter.toFloat()
            val personBoost = (ISO_MIN_PERSON_PX * density.toFloat() / (ppm * scale * 2.05f)).coerceIn(1f, ISO_MAX_SPRITE_BOOST)
            withTransform({ translate(tx, ty); scale(scale, scale, pivot = Offset.Zero) }) {
                for (ground in grounds) {
                    if (isoOnScreen(ground.bounds, left, top, right, bottom)) {
                        drawPath(ground.path, ground.color)
                        ground.pattern?.let { pattern -> clipPath(ground.path) { drawPath(pattern.path, pattern.color, style = ground.patternStroke ?: ground.edgeStroke) } }
                        drawPath(ground.path, ground.edge, style = ground.edgeStroke)
                    }
                }
                for (road in bakedRoads) {
                    if (isoOnScreen(road.bounds, left, top, right, bottom, road.casing.width / 2f)) {
                        drawPath(road.path, ISO_ROAD_CASING, style = road.casing)
                    }
                }
                for (road in bakedRoads) {
                    if (isoOnScreen(road.bounds, left, top, right, bottom, road.stroke.width / 2f)) {
                        drawPath(road.path, road.color, style = road.stroke)
                    }
                }
                drawIsoRoute(routeState, projection, Color(0xFF64A9EC))
                drawIsoRoute(missionRouteState, projection, Color(0xFFE05550))
                val currentCollectibles = collectibleState
                for (i in currentCollectibles.indices) {
                    val item = currentCollectibles[i]
                    val x = projection.projectX(item.latitude, item.longitude).toFloat()
                    val y = projection.projectY(item.latitude, item.longitude).toFloat()
                    if (x >= left && x <= right && y >= top && y <= bottom) {
                        drawCircle(Color(0xFFFFD35A), (projection.pixelsPerMeter * 0.65).toFloat(), Offset(x, y))
                    }
                }
                val currentLoot = lootState
                for (i in currentLoot.indices) {
                    val loot = currentLoot[i]
                    val x = projection.projectX(loot.latitude, loot.longitude).toFloat()
                    val y = projection.projectY(loot.latitude, loot.longitude).toFloat()
                    if (x >= left && x <= right && y >= top && y <= bottom) {
                        val r = (projection.pixelsPerMeter * 0.5).toFloat()
                        drawCircle(Color(0xCC101010), r * 1.35f, Offset(x, y))
                        drawCircle(isoLootColor(loot.kind), r, Offset(x, y))
                    }
                }
                var actorIndex = 0
                for (building in buildings) {
                    while (actorIndex < actors.size && actors.depth[actorIndex] > building.depth) {
                        drawIsoActor(actors, actorIndex++, projection.pixelsPerMeter.toFloat(), drivingState, playerMirror, personBoost, playerFrame)
                    }
                    if (building.right >= left && building.left <= right && building.bottom >= top && building.top <= bottom) {
                        val target = if (isoPointInPolygon(playerX, playerY, building.xs, building.ys)) ISO_ROOF_HIDDEN_ALPHA else 1f
                        building.alpha = when {
                            building.alpha < target -> minOf(target, building.alpha + ISO_ROOF_FADE_STEP)
                            building.alpha > target -> maxOf(target, building.alpha - ISO_ROOF_FADE_STEP)
                            else -> target
                        }
                        building.layer.alpha = building.alpha
                        translate(building.left, building.top) { drawLayer(building.layer) }
                    }
                }
                while (actorIndex < actors.size) {
                    drawIsoActor(actors, actorIndex++, projection.pixelsPerMeter.toFloat(), drivingState, playerMirror, personBoost, playerFrame)
                }
                if (IsoDebugOverlay.collisionBoxes) drawIsoCollisionBoxes(
                    projection, npcState, drivingState, player, vehicleHeadingState,
                    ovh.gabrielhuav.pow.domain.models.map.CarVariantRules.dimensionsFor(vehicleModel ?: CarModel.SEDAN, vehicleId.orEmpty()),
                )
                if (drivingState && SmokeRules.smokes(vehicleHealthState)) {
                    // Humo del motor: bocanadas grises que suben y se ensanchan (sin asignaciones: solo círculos).
                    val ppm = projection.pixelsPerMeter.toFloat()
                    for (i in 0 until SmokeRules.PUFFS) {
                        val phase = SmokeRules.phase(elapsed, i)
                        drawCircle(
                            Color(0xFF9AA0A6).copy(alpha = SmokeRules.alpha(phase, vehicleHealthState)),
                            ppm * SmokeRules.radiusMeters(phase),
                            Offset(playerX + ppm * SmokeRules.driftMeters(elapsed, i, phase), playerY - ppm * SmokeRules.riseMeters(phase)),
                        )
                    }
                }
            }
            val radius = IsoFogRules.radiusPx(fogVisionMeters, projection.pixelsPerMeter, scale, density.toFloat(), size.minDimension, fogMaxFraction)
            // t34: viñeta radial suave en vez de un óvalo de borde duro; el degradado se arma solo si cambia el radio o el tamaño.
            if (radius != cachedFogRadius || size.width != cachedFogW || size.height != cachedFogH) {
                fogBrush = Brush.radialGradient(
                    0.0f to Color.Transparent, 0.55f to Color.Transparent, 0.8f to ISO_FOG, 1.0f to ISO_FOG_EDGE,
                    center = Offset(size.width / 2f, size.height / 2f), radius = radius * 1.35f,
                )
                cachedFogRadius = radius; cachedFogW = size.width; cachedFogH = size.height
            }
            fogBrush?.let { brush ->
                withTransform({ scale(1f, 0.5f, pivot = Offset(size.width / 2f, size.height / 2f)) }) {
                    drawRect(brush, topLeft = Offset(0f, -size.height / 2f), size = Size(size.width, size.height * 2f), alpha = fogStrength)
                }
            }
            // Indicador de amenaza (t32): una flecha roja alrededor del jugador hacia cada zombi a menos de ~12 m.
            for (i in 0 until threatCount) {
                val angle = atan2(threatDy[i].toDouble(), threatDx[i].toDouble()) * 180.0 / PI
                val d = density.toFloat()
                withTransform({ translate(size.width / 2f, size.height / 2f); rotate(angle.toFloat(), Offset.Zero); translate(58f * d, 0f); scale(d, d, Offset.Zero) }) {
                    drawPath(objectiveArrow, Color(0xFF1A0808), style = objectiveArrowOutline)
                    drawPath(objectiveArrow, Color(0xFFE53935))
                }
            }
            destinationState?.let { target ->
                val x = (projection.projectX(target.latitude, target.longitude) * scale + tx).toFloat()
                    .coerceIn(16f, (size.width - 16f).coerceAtLeast(16f))
                val y = (projection.projectY(target.latitude, target.longitude) * scale + ty).toFloat()
                    .coerceIn(16f, (size.height - 16f).coerceAtLeast(16f))
                drawCircle(Color(0xFF64A9EC), 7f * density.toFloat(), Offset(x, y))
            }
            objectiveState?.let { target ->
                // Objetivo (t27): aro grande con borde claro; si cae fuera de la pantalla, una flecha en el borde que
                // apunta hacia él (antes era un punto de 9 dp que se perdía en el vacío).
                val rawX = (projection.projectX(target.latitude, target.longitude) * scale + tx).toFloat()
                val rawY = (projection.projectY(target.latitude, target.longitude) * scale + ty).toFloat()
                val x = rawX.coerceIn(24f * density.toFloat(), (size.width - 24f * density.toFloat()).coerceAtLeast(24f))
                // La franja de arriba la ocupan la barra de vida y el widget del objetivo: el marcador baja a [OBJECTIVE_TOP_INSET_DP].
                val y = rawY.coerceIn(OBJECTIVE_TOP_INSET_DP * density.toFloat(), (size.height - 24f * density.toFloat()).coerceAtLeast(OBJECTIVE_TOP_INSET_DP))
                val d = density.toFloat()
                drawCircle(Color(0xFF1A1208), 15f * d, Offset(x, y))
                drawCircle(Color(0xFFFFD35A), 12f * d, Offset(x, y))
                drawCircle(Color(0xFF552B16), 5f * d, Offset(x, y))
                if (x != rawX || y != rawY) {
                    val angle = atan2((rawY - size.height / 2f).toDouble(), (rawX - size.width / 2f).toDouble()) * 180.0 / PI
                    withTransform({ translate(x, y); rotate(angle.toFloat(), Offset.Zero); translate(22f * d, 0f); scale(d, d, Offset.Zero) }) {
                        drawPath(objectiveArrow, Color(0xFF1A1208), style = objectiveArrowOutline)
                        drawPath(objectiveArrow, Color(0xFFFFD35A))
                    }
                }
            }
        }
    })
}

/** Margen superior (dp) del marcador de objetivo en el borde: deja libre la barra de vida (t54). */
private const val OBJECTIVE_TOP_INSET_DP = 60f

private fun isoPolygon(xs: FloatArray, ys: FloatArray, left: Float, top: Float, rise: Float): Path = Path().apply {
    moveTo(xs[0] - left, ys[0] - top - rise)
    for (i in 1 until xs.size) lineTo(xs[i] - left, ys[i] - top - rise)
    close()
}

private fun isoOnScreen(bounds: Rect, left: Float, top: Float, right: Float, bottom: Float, margin: Float = 0f): Boolean =
    bounds.right + margin >= left && bounds.left - margin <= right &&
        bounds.bottom + margin >= top && bounds.top - margin <= bottom

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIsoPlayer(x: Float, y: Float, pixelsPerMeter: Float) {
    val width = pixelsPerMeter * 0.8f
    val height = pixelsPerMeter * 1.7f
    drawOval(Color(0x66000000), Offset(x - width / 2f, y - width / 6f), Size(width, width / 3f))
    drawRect(Color(0xFF344A65), Offset(x - width / 2f, y - height), Size(width, height))
    drawCircle(Color(0xFFD8B58F), width * 0.38f, Offset(x, y - height))
}

/** Arreglos fijos para ordenar los actores visibles sin crear listas por fotograma. */
private class IsoActors(private val capacity: Int) {
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    val depth = DoubleArray(capacity)
    val heading = FloatArray(capacity)
    val colors = IntArray(capacity)
    val sprites = arrayOfNulls<ImageBitmap>(capacity)
    val mirrors = BooleanArray(capacity)
    val vehicleMeters = FloatArray(capacity)
    val tintable = BooleanArray(capacity)
    val types = arrayOfNulls<NpcType>(capacity)
    private val ids = arrayOfNulls<String>(capacity)
    var size: Int = 0
        private set

    fun clear() { size = 0 }

    fun insert(
        px: Float, py: Float, value: Double, type: NpcType?, id: String, worldHeading: Float, color: Int = -1,
        sprite: ImageBitmap? = null, mirror: Boolean = false, meters: Float = 0f, tint: Boolean = false,
    ) {
        if (size == capacity) return
        var index = size
        while (index > 0 && (depth[index - 1] < value ||
                (depth[index - 1] == value && (ids[index - 1] ?: "") > id))) {
            x[index] = x[index - 1]; y[index] = y[index - 1]; depth[index] = depth[index - 1]
            types[index] = types[index - 1]; ids[index] = ids[index - 1]; heading[index] = heading[index - 1]
            colors[index] = colors[index - 1]; sprites[index] = sprites[index - 1]; mirrors[index] = mirrors[index - 1]
            vehicleMeters[index] = vehicleMeters[index - 1]; tintable[index] = tintable[index - 1]
            index--
        }
        x[index] = px; y[index] = py; depth[index] = value; types[index] = type; ids[index] = id; heading[index] = worldHeading; colors[index] = color
        sprites[index] = sprite; mirrors[index] = mirror; vehicleMeters[index] = meters; tintable[index] = tint
        size++
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIsoActor(
    actors: IsoActors, index: Int, pixelsPerMeter: Float, playerDriving: Boolean, playerMirror: Boolean, personBoost: Float,
    playerFrame: ImageBitmap?,
) {
    val x = actors.x[index]
    val y = actors.y[index]
    val type = actors.types[index]
    if (type == null && !playerDriving && playerFrame != null) {
        drawIsoPlayerFrame(playerFrame, x, y, pixelsPerMeter * personBoost, playerMirror)
        return
    }
    val sprite = actors.sprites[index]
    val isVehicle = actors.vehicleMeters[index] > 0f
    if (sprite != null) {
        if (isVehicle) {
            val width = (pixelsPerMeter * actors.vehicleMeters[index]).toInt().coerceAtLeast(1)
            val height = (width.toFloat() * sprite.height / sprite.width).toInt().coerceAtLeast(1)
            val tint = if (actors.tintable[index]) isoCarTint(actors.colors[index]) else null
            withTransform({ if (actors.mirrors[index]) scale(-1f, 1f, pivot = Offset(x, y)) }) {
                drawIsoCarSprite(sprite, x, y, width, height, tint)
            }
        } else {
            drawIsoPlayerFrame(sprite, x, y, pixelsPerMeter * personBoost * (if (type == NpcType.CAT) ISO_CAT_SIZE else 1f), actors.mirrors[index], if (type == NpcType.ZOMBIE) ISO_ZOMBIE_TINT else null)
        }
        return
    }
    if (type == null && !playerDriving) {
        drawIsoPlayer(x, y, pixelsPerMeter)
        return
    }
    val isCar = type == NpcType.CAR || type == NpcType.POLICE_CAR || (type == null && playerDriving)
    val width = pixelsPerMeter * if (isCar) 2.0f else 0.7f
    val height = pixelsPerMeter * if (isCar) 2.1f else 1.5f
    val color = when (type) {
        null -> Color(0xFF344A65)
        NpcType.ZOMBIE -> Color(0xFF769A66)
        NpcType.POLICE_COP, NpcType.POLICE_CAR -> Color(0xFF526B94)
        NpcType.CAR -> Color(0xFF9B7566)
        NpcType.CAT -> Color(0xFFB19A83)
        NpcType.PERSON -> Color(0xFFBEA58F)
    }
    drawOval(Color(0x66000000), Offset(x - width / 2f, y - width / 6f), Size(width, width / 3f))
    if (isCar) {
        val angle = isoSpriteHeading(actors.heading[index].toDouble()).toFloat()
        rotate(angle, pivot = Offset(x, y)) {
            drawRect(color, Offset(x - width / 2f, y - height / 2f), Size(width, height))
            drawRect(Color(0xFFBAC6C4), Offset(x - width / 4f, y - height / 2f), Size(width / 2f, height / 5f))
        }
    } else {
        drawRect(color, Offset(x - width / 2f, y - height), Size(width, height))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIsoPlayerFrame(
    frame: ImageBitmap, x: Float, y: Float, pixelsPerMeter: Float, mirror: Boolean, tint: ColorFilter? = null,
) {
    val height = (pixelsPerMeter * ISO_PLAYER_CANVAS_METERS).toInt().coerceAtLeast(1)
    val width = (height.toFloat() * frame.width / frame.height).toInt().coerceAtLeast(1)
    withTransform({ if (mirror) scale(-1f, 1f, pivot = Offset(x, y)) }) {
        drawImage(
            image = frame,
            srcSize = IntSize(frame.width, frame.height),
            dstOffset = IntOffset((x - width / 2f).toInt(), (y - height * (1f - ISO_PLAYER_FOOT_FRACTION)).toInt()),
            dstSize = IntSize(width, height),
            filterQuality = FilterQuality.Low,
            colorFilter = tint,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIsoCarSprite(
    sprite: ImageBitmap, x: Float, y: Float, width: Int, height: Int, tint: ColorFilter?,
) {
    drawImage(
        image = sprite, srcSize = IntSize(sprite.width, sprite.height),
        dstOffset = IntOffset((x - width / 2f).toInt(), (y - height / 2f).toInt()),
        dstSize = IntSize(width, height), filterQuality = FilterQuality.None, colorFilter = tint,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIsoRoute(
    points: List<GeoPoint>, projection: IsoProjection, color: Color,
) {
    if (points.size < 2) return
    var previousX = projection.projectX(points[0].latitude, points[0].longitude).toFloat()
    var previousY = projection.projectY(points[0].latitude, points[0].longitude).toFloat()
    for (i in 1 until points.size) {
        val x = projection.projectX(points[i].latitude, points[i].longitude).toFloat()
        val y = projection.projectY(points[i].latitude, points[i].longitude).toFloat()
        drawLine(color, Offset(previousX, previousY), Offset(x, y),
            strokeWidth = (projection.pixelsPerMeter * 1.2).toFloat(), cap = StrokeCap.Round)
        previousX = x; previousY = y
    }
}

/** Vista (0 = N, 1 = NE, 2 = E, 3 = SE, 4 = S) y si hay que espejarla, para un rumbo de mundo en grados. */
internal fun isoVehicleDirection(worldHeadingDegrees: Float): Pair<Int, Boolean> {
    val sector = isoScreenSector(isoSpriteHeading(worldHeadingDegrees.toDouble()))
    val view = when (sector) {
        0 -> 0
        1, 7 -> 1
        2, 6 -> 2
        3, 5 -> 3
        else -> 4
    }
    return view to (sector > 4)
}

/** Color del marcador de botín: pizza naranja, refresco azul, tablones café y suero verde. */
private fun isoLootColor(kind: LootKind): Color = when (kind) {
    LootKind.PIZZA -> Color(0xFFFF9A3C)
    LootKind.REFRESCO -> Color(0xFF4FA8FF)
    LootKind.TABLONES -> Color(0xFFB07A4A)
    LootKind.SUERO -> Color(0xFF5BE08A)
    LootKind.CONTENEDOR -> Color(0xFFE6C34A)
    LootKind.ARMA_BLANCA -> Color(0xFFD9D9D9)
    LootKind.ARMA_FUEGO -> Color(0xFFFF4D4D)
    LootKind.MUNICION -> Color(0xFFFFD84D)
    LootKind.GASOLINA -> Color(0xFFB36BFF)
    LootKind.VENDA -> Color(0xFFFF8FA3)
    LootKind.EDIFICIO -> Color(0xFF7FD6C8)
    LootKind.FOGATA -> Color(0xFFFF6A00)
    LootKind.TRAMPA -> Color(0xFF9E9E9E)
    LootKind.COCHE -> Color(0xFF90A4AE)
}

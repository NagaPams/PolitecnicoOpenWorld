package ovh.gabrielhuav.pow.features.map_exterior.ui.iso

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import ovh.gabrielhuav.pow.platform.imagen.rememberImagenDeAsset
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.ActiveCollectible
import ovh.gabrielhuav.pow.domain.models.map.CampusFootprint
import ovh.gabrielhuav.pow.domain.models.map.FootprintKind
import ovh.gabrielhuav.pow.domain.models.map.IsoProjection
import ovh.gabrielhuav.pow.domain.models.map.MapWay
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.NpcType
import ovh.gabrielhuav.pow.domain.models.map.isoPointInPolygon
import ovh.gabrielhuav.pow.domain.models.map.isoScreenSector
import ovh.gabrielhuav.pow.domain.models.map.isoWorldAngleToScreenHeading
import ovh.gabrielhuav.pow.domain.models.map.isoWallFacesCamera
import ovh.gabrielhuav.pow.domain.models.map.isoSpriteHeading
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.time.TimeSource

private const val ISO_BAKE_ZOOM = 18.0
private const val ISO_LAYER_MARGIN = 8f
private const val ISO_ROOF_HIDDEN_ALPHA = 0.1f
private const val ISO_ROOF_FADE_STEP = 0.12f
private val ISO_GROUND = Color(0xFF65745E)
private val ISO_ROAD = Color(0xFF797C76)
private val ISO_WALKWAY = Color(0xFFA49F8D)
private val ISO_ROOF = Color(0xFFCDBF9B)
private val ISO_ROOF_EDGE = Color(0xFF4E4637)
private val ISO_WALL_LIGHT = Color(0xFF8A7A62)
private val ISO_WALL_DARK = Color(0xFF574E40)
private val ISO_FOG = Color(0x6E222A33)
private const val ISO_MIN_PERSON_PX = 44f
private const val ISO_MAX_SPRITE_BOOST = 2.5f

// Hoja del jugador al estándar POW (tools/slice_iso_player.py): 5 direcciones x 8 cuadros.
private const val ISO_PLAYER_PATH = "SPRITES/PLAYER/EstudianteIso/"
private const val ISO_PLAYER_REDUCTION = 2
private const val ISO_PLAYER_CANVAS_METERS = 2.3f
private const val ISO_PLAYER_FOOT_FRACTION = 0.036f
private val ISO_PLAYER_DIRS = arrayOf("s", "se", "e", "ne", "n")
private val ISO_SLOT_FOLDER = arrayOf("Idle", "Walk", "Walk", "Walk", "Run", "Run", "Run", "Special")
private val ISO_SLOT_LETTER = arrayOf("i", "w", "w", "w", "r", "r", "r", "s")
private val ISO_SLOT_NUMBER = intArrayOf(1, 1, 2, 3, 1, 2, 3, 1)
private val ISO_SECTOR_ROW = intArrayOf(4, 3, 2, 1, 0, 1, 2, 3)
private val ISO_STEP_ORDER = intArrayOf(0, 1, 2, 1)

private suspend fun loadIsoPlayerSprites(): Array<ImageBitmap?> = withContext(Dispatchers.Default) {
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

private data class IsoGroundPath(val path: Path, val bounds: Rect, val color: Color)
private data class IsoRoadPath(val path: Path, val bounds: Rect, val stroke: Stroke, val color: Color)
private data class IsoCarSprites(
    val north: ImageBitmap?, val northEast: ImageBitmap?, val east: ImageBitmap?,
    val southEast: ImageBitmap?, val south: ImageBitmap?,
)
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
private const val ISO_TINT_GAIN = 2.2f
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
    modifier: Modifier = Modifier,
    playerMoveAngle: Float? = null,
    playerAction: PlayerAction = PlayerAction.IDLE,
    vehicleColor: Int? = null,
) {
    if (center == null) return
    val graphicsContext = LocalGraphicsContext.current
    val playerFrames by produceState<Array<ImageBitmap?>?>(null) { value = loadIsoPlayerSprites() }
    val zombieSprite = rememberImagenDeAsset("ISOMETRIC/zombie_idle_se_v1.png", reduccion = 8)
    val carNorth = rememberImagenDeAsset("ISOMETRIC/car_n_v1.png", reduccion = 8)
    val carNorthEast = rememberImagenDeAsset("ISOMETRIC/car_ne_v1.png", reduccion = 8)
    val carEast = rememberImagenDeAsset("ISOMETRIC/car_e_v1.png", reduccion = 8)
    val carSouthEast = rememberImagenDeAsset("ISOMETRIC/car_se_v1.png", reduccion = 8)
    val carSouth = rememberImagenDeAsset("ISOMETRIC/car_s_v1.png", reduccion = 8)
    val carSprites = remember(carNorth, carNorthEast, carEast, carSouthEast, carSouth) {
        IsoCarSprites(carNorth, carNorthEast, carEast, carSouthEast, carSouth)
    }
    val centerState by rememberUpdatedState(center)
    val zoomState by rememberUpdatedState(zoom)
    val npcState by rememberUpdatedState(npcs)
    val collectibleState by rememberUpdatedState(collectibles)
    val objectiveState by rememberUpdatedState(objective)
    val destinationState by rememberUpdatedState(destination)
    val routeState by rememberUpdatedState(route)
    val missionRouteState by rememberUpdatedState(missionRoute)
    val drivingState by rememberUpdatedState(isDriving)
    val vehicleHeadingState by rememberUpdatedState(vehicleHeading)
    val moveAngleState by rememberUpdatedState(playerMoveAngle)
    val actionState by rememberUpdatedState(playerAction)
    val vehicleColorState by rememberUpdatedState(vehicleColor)
    val layerPool = remember { IsoLayerPool(graphicsContext) }
    val density = LocalDensity.current.density.toDouble()
    val reference = remember(footprints) { footprints.firstOrNull()?.points?.firstOrNull() ?: center }

    Box(modifier.fillMaxSize().drawWithCache {
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
                grounds.add(IsoGroundPath(path, path.getBounds(), color))
                continue
            }
            val rise = (footprint.heightMeters.coerceAtLeast(0.0) * projection.pixelsPerMeter).toFloat()
            val left = floor(minX - ISO_LAYER_MARGIN)
            val top = floor(minY - rise - ISO_LAYER_MARGIN)
            val right = ceil(maxX + ISO_LAYER_MARGIN)
            val bottom = ceil(maxY + ISO_LAYER_MARGIN)
            val width = (right - left).toInt().coerceAtLeast(1)
            val height = (bottom - top).toInt().coerceAtLeast(1)
            val roof = isoPolygon(xs, ys, left, top, rise)
            var winding = 0.0
            for (i in points.indices) {
                val j = (i + 1) % points.size
                winding += xs[i].toDouble() * ys[j] - xs[j].toDouble() * ys[i]
            }
            val walls = ArrayList<Pair<Path, Color>>()
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
                walls.add(path to if (ys[j] > ys[i]) ISO_WALL_LIGHT else ISO_WALL_DARK)
            }
            val layer = layerPool.track(obtainGraphicsLayer())
            layer.compositingStrategy = CompositingStrategy.ModulateAlpha
            layer.record(this, layoutDirection, IntSize(width, height)) {
                for ((wall, color) in walls) drawPath(wall, color)
                drawPath(roof, ISO_ROOF)
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
            bakedRoads.add(IsoRoadPath(path, path.getBounds(), stroke, if (road.isForCars) ISO_ROAD else ISO_WALKWAY))
        }
        val fog = Path().apply { fillType = PathFillType.EvenOdd }
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
            actors.insert(playerX, playerY, projection.depth(player), null, "", vehicleHeadingState, vehicleColorState ?: -1)
            val currentNpcs = npcState
            for (i in currentNpcs.indices) {
                val npc = currentNpcs[i]
                val x = projection.projectX(npc.location.latitude, npc.location.longitude).toFloat()
                val y = projection.projectY(npc.location.latitude, npc.location.longitude).toFloat()
                if (x < left - 20f || x > right + 20f || y < top - 20f || y > bottom + 20f) continue
                actors.insert(x, y, projection.depth(npc.location), npc.type, npc.id, (npc.rotationAngle + 90f) % 360f, npc.carColor)
            }
            val ppm = projection.pixelsPerMeter.toFloat()
            val personBoost = (ISO_MIN_PERSON_PX * density.toFloat() / (ppm * scale * 2.05f)).coerceIn(1f, ISO_MAX_SPRITE_BOOST)
            withTransform({ translate(tx, ty); scale(scale, scale, pivot = Offset.Zero) }) {
                for (ground in grounds) {
                    if (isoOnScreen(ground.bounds, left, top, right, bottom)) drawPath(ground.path, ground.color)
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
                var actorIndex = 0
                for (building in buildings) {
                    while (actorIndex < actors.size && actors.depth[actorIndex] > building.depth) {
                        drawIsoActor(actors, actorIndex++, projection.pixelsPerMeter.toFloat(), drivingState, playerMirror, personBoost, playerFrame, zombieSprite, carSprites)
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
                    drawIsoActor(actors, actorIndex++, projection.pixelsPerMeter.toFloat(), drivingState, playerMirror, personBoost, playerFrame, zombieSprite, carSprites)
                }
            }
            val maxRadius = (size.minDimension * 0.40f).coerceAtLeast(1f)
            val radius = (fogVisionMeters * projection.pixelsPerMeter * scale).toFloat()
                .coerceIn(minOf(40f * density.toFloat(), maxRadius), maxRadius)
            if (radius != cachedFogRadius) {
                fog.reset()
                fog.fillType = PathFillType.EvenOdd
                fog.addRect(Rect(0f, 0f, size.width, size.height))
                fog.addOval(Rect(size.width / 2f - radius, size.height / 2f - radius / 2f,
                    size.width / 2f + radius, size.height / 2f + radius / 2f))
                cachedFogRadius = radius
            }
            drawPath(fog, ISO_FOG)
            destinationState?.let { target ->
                val x = (projection.projectX(target.latitude, target.longitude) * scale + tx).toFloat()
                    .coerceIn(16f, (size.width - 16f).coerceAtLeast(16f))
                val y = (projection.projectY(target.latitude, target.longitude) * scale + ty).toFloat()
                    .coerceIn(16f, (size.height - 16f).coerceAtLeast(16f))
                drawCircle(Color(0xFF64A9EC), 7f * density.toFloat(), Offset(x, y))
            }
            objectiveState?.let { target ->
                val x = (projection.projectX(target.latitude, target.longitude) * scale + tx).toFloat()
                    .coerceIn(16f, (size.width - 16f).coerceAtLeast(16f))
                val y = (projection.projectY(target.latitude, target.longitude) * scale + ty).toFloat()
                    .coerceIn(16f, (size.height - 16f).coerceAtLeast(16f))
                drawCircle(Color(0xFFFFD35A), 9f * density.toFloat(), Offset(x, y))
                drawCircle(Color(0xFF552B16), 4f * density.toFloat(), Offset(x, y))
            }
        }
    })
}

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
    val types = arrayOfNulls<NpcType>(capacity)
    private val ids = arrayOfNulls<String>(capacity)
    var size: Int = 0
        private set

    fun clear() { size = 0 }

    fun insert(px: Float, py: Float, value: Double, type: NpcType?, id: String, worldHeading: Float, color: Int = -1) {
        if (size == capacity) return
        var index = size
        while (index > 0 && (depth[index - 1] < value ||
                (depth[index - 1] == value && (ids[index - 1] ?: "") > id))) {
            x[index] = x[index - 1]; y[index] = y[index - 1]; depth[index] = depth[index - 1]
            types[index] = types[index - 1]; ids[index] = ids[index - 1]; heading[index] = heading[index - 1]
            colors[index] = colors[index - 1]
            index--
        }
        x[index] = px; y[index] = py; depth[index] = value; types[index] = type; ids[index] = id; heading[index] = worldHeading; colors[index] = color
        size++
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIsoActor(
    actors: IsoActors, index: Int, pixelsPerMeter: Float, playerDriving: Boolean, playerMirror: Boolean, personBoost: Float,
    playerFrame: ImageBitmap?, zombieSprite: ImageBitmap?, carSprites: IsoCarSprites,
) {
    val x = actors.x[index]
    val y = actors.y[index]
    val type = actors.types[index]
    if (type == null && !playerDriving && playerFrame != null) {
        drawIsoPlayerFrame(playerFrame, x, y, pixelsPerMeter * personBoost, playerMirror)
        return
    }
    val sprite = when {
        type == NpcType.ZOMBIE -> zombieSprite
        else -> null
    }
    if (sprite != null) {
        val height = (pixelsPerMeter * 2.05f * personBoost).toInt().coerceAtLeast(1)
        val width = (height.toFloat() * sprite.width / sprite.height).toInt().coerceAtLeast(1)
        val facingRight = isoSpriteHeading(actors.heading[index].toDouble()) in 0.0..180.0
        withTransform({ if (!facingRight) scale(-1f, 1f, pivot = Offset(x, y)) }) {
            drawImage(
                image = sprite,
                srcSize = IntSize(sprite.width, sprite.height),
                dstOffset = IntOffset((x - width / 2f).toInt(), (y - height).toInt()),
                dstSize = IntSize(width, height),
                filterQuality = FilterQuality.None,
            )
        }
        return
    }
    if (type == null && !playerDriving) {
        drawIsoPlayer(x, y, pixelsPerMeter)
        return
    }
    val isCar = type == NpcType.CAR || type == NpcType.POLICE_CAR || (type == null && playerDriving)
    if (isCar && type != NpcType.POLICE_CAR) {
        val direction = ((isoSpriteHeading(actors.heading[index].toDouble()) + 22.5) / 45.0).toInt() % 8
        val carSprite = when (direction) {
            0 -> carSprites.north
            1, 7 -> carSprites.northEast
            2, 6 -> carSprites.east
            3, 5 -> carSprites.southEast
            else -> carSprites.south
        }
        if (carSprite != null) {
            val width = (pixelsPerMeter * 5.2f).toInt().coerceAtLeast(1)
            val height = (width.toFloat() * carSprite.height / carSprite.width).toInt().coerceAtLeast(1)
            val tint = isoCarTint(actors.colors[index])
            if (direction > 4) {
                withTransform({ scale(-1f, 1f, pivot = Offset(x, y)) }) {
                    drawIsoCarSprite(carSprite, x, y, width, height, tint)
                }
            } else {
                drawIsoCarSprite(carSprite, x, y, width, height, tint)
            }
            return
        }
    }
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
    frame: ImageBitmap, x: Float, y: Float, pixelsPerMeter: Float, mirror: Boolean,
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
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIsoCarSprite(
    sprite: ImageBitmap, x: Float, y: Float, width: Int, height: Int, tint: ColorFilter,
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

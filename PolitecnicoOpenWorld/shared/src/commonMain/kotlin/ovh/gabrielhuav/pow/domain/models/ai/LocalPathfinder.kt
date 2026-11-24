package ovh.gabrielhuav.pow.domain.models.ai

import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.ExteriorCollisionsConfig
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// Pathfinding LOCAL para zombis y policías cazadores atorados (t17): A* sobre una cuadrícula de 1 m que cubre solo
// la zona entre el NPC y su objetivo, con las bardas y edificios de las colisiones estáticas como obstáculos. Es caro
// (cada arista consulta las paredes), así que solo se llama cuando el seguimiento de pared no sale de una esquina y
// con límite de tiempo por NPC. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

internal object LocalPathfinder {
    const val CELL_METERS = 1.0
    const val MARGIN_METERS = 14.0
    const val TARGET_CELLS_PER_SIDE = 50.0
    /** Más lejos que esto (incluido el margen) no se busca camino. */
    const val MAX_EXTENT_METERS = 200.0
    const val MAX_EXPANSIONS = 900
    private const val METERS_PER_DEGREE_LAT = 111_320.0
    private const val DIAGONAL = 1.4142135623730951
    private val NEIGHBORS_X = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
    private val NEIGHBORS_Y = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)

    /** Camino de [from] a [to] como puntos cada ~1 m (sin incluir [from]); null si no hay o el área es demasiado grande. */
    fun find(config: ExteriorCollisionsConfig, from: GeoPoint, to: GeoPoint): List<GeoPoint>? {
        val lonMetersPerDegree = METERS_PER_DEGREE_LAT * cos(from.latitude * kotlin.math.PI / 180.0)
        val minLat = min(from.latitude, to.latitude) - MARGIN_METERS / METERS_PER_DEGREE_LAT
        val minLon = min(from.longitude, to.longitude) - MARGIN_METERS / lonMetersPerDegree
        val maxLat = max(from.latitude, to.latitude) + MARGIN_METERS / METERS_PER_DEGREE_LAT
        val maxLon = max(from.longitude, to.longitude) + MARGIN_METERS / lonMetersPerDegree
        val extentLat = (maxLat - minLat) * METERS_PER_DEGREE_LAT
        val extentLon = (maxLon - minLon) * lonMetersPerDegree
        if (max(extentLat, extentLon) > MAX_EXTENT_METERS) return null
        // Celdas de 1 m hasta ~TARGET_CELLS_PER_SIDE de lado; más lejos se agrandan para que la búsqueda alcance.
        val cellMeters = max(CELL_METERS, max(extentLat, extentLon) / TARGET_CELLS_PER_SIDE)
        val height = (extentLat / cellMeters).toInt() + 1
        val width = (extentLon / cellMeters).toInt() + 1

        fun cellX(lon: Double) = floor((lon - minLon) * lonMetersPerDegree / cellMeters).toInt().coerceIn(0, width - 1)
        fun cellY(lat: Double) = floor((lat - minLat) * METERS_PER_DEGREE_LAT / cellMeters).toInt().coerceIn(0, height - 1)
        fun centerLat(y: Int) = minLat + (y + 0.5) * cellMeters / METERS_PER_DEGREE_LAT
        fun centerLon(x: Int) = minLon + (x + 0.5) * cellMeters / lonMetersPerDegree

        val startX = cellX(from.longitude)
        val startY = cellY(from.latitude)
        val goalX = cellX(to.longitude)
        val goalY = cellY(to.latitude)
        val total = width * height
        val cost = DoubleArray(total) { Double.MAX_VALUE }
        val parent = IntArray(total) { -1 }
        val closed = BooleanArray(total)
        val heap = MinHeap(MAX_EXPANSIONS * 8 + 16)

        val start = startY * width + startX
        val goal = goalY * width + goalX
        cost[start] = 0.0
        heap.push(octile(startX, startY, goalX, goalY), start)
        var expansions = 0
        while (heap.size > 0 && expansions < MAX_EXPANSIONS) {
            val current = heap.pop()
            if (closed[current]) continue
            closed[current] = true
            if (current == goal) return rebuild(parent, current, width, start, ::centerLat, ::centerLon)
            expansions++
            val cx = current % width
            val cy = current / width
            val aLat = centerLat(cy)
            val aLon = centerLon(cx)
            for (k in 0 until 8) {
                val nx = cx + NEIGHBORS_X[k]
                val ny = cy + NEIGHBORS_Y[k]
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                val next = ny * width + nx
                if (closed[next]) continue
                val step = if (k < 4) 1.0 else DIAGONAL
                val newCost = cost[current] + step
                if (newCost >= cost[next]) continue
                if (config.segmentBlocked(GeoPoint(aLat, aLon), GeoPoint(centerLat(ny), centerLon(nx)))) continue
                cost[next] = newCost
                parent[next] = current
                if (heap.size < heap.capacity) heap.push(newCost + octile(nx, ny, goalX, goalY), next)
            }
        }
        return null
    }

    private fun octile(x: Int, y: Int, gx: Int, gy: Int): Double {
        val dx = abs(x - gx)
        val dy = abs(y - gy)
        return (dx + dy) + (DIAGONAL - 2.0) * min(dx, dy)
    }

    private fun rebuild(
        parent: IntArray, goal: Int, width: Int, start: Int,
        centerLat: (Int) -> Double, centerLon: (Int) -> Double,
    ): List<GeoPoint> {
        val out = ArrayList<GeoPoint>()
        var node = goal
        while (node != -1 && node != start) {
            out.add(GeoPoint(centerLat(node / width), centerLon(node % width)))
            node = parent[node]
        }
        out.reverse()
        return out
    }

    /** Montículo binario mínimo con claves Double y valores Int; sin librerías (común a Android e iOS). */
    private class MinHeap(val capacity: Int) {
        private val keys = DoubleArray(capacity)
        private val values = IntArray(capacity)
        var size = 0
            private set

        fun push(key: Double, value: Int) {
            var i = size++
            keys[i] = key
            values[i] = value
            while (i > 0) {
                val parent = (i - 1) / 2
                if (keys[parent] <= keys[i]) break
                swap(i, parent)
                i = parent
            }
        }

        fun pop(): Int {
            val top = values[0]
            size--
            if (size > 0) {
                keys[0] = keys[size]
                values[0] = values[size]
                var i = 0
                while (true) {
                    val left = 2 * i + 1
                    val right = left + 1
                    var smallest = i
                    if (left < size && keys[left] < keys[smallest]) smallest = left
                    if (right < size && keys[right] < keys[smallest]) smallest = right
                    if (smallest == i) break
                    swap(i, smallest)
                    i = smallest
                }
            }
            return top
        }

        private fun swap(a: Int, b: Int) {
            val k = keys[a]; keys[a] = keys[b]; keys[b] = k
            val v = values[a]; values[a] = values[b]; values[b] = v
        }
    }
}

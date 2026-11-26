package ovh.gabrielhuav.pow.domain.models.interior

import ovh.gabrielhuav.pow.domain.models.survival.GroupRules
import ovh.gabrielhuav.pow.domain.models.survival.SearchProfile

// Interiores procedurales: a partir del id de un edificio de OSM, su tipo de lugar y su tamaño se genera, de forma
// determinista, una planta de celdas de 1 m con habitaciones, pasos interiores, ventanas y muebles. Reglas puras, sin
// Android. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Al final de la lista de valores: no insertar en medio (se guarda por nombre). */
enum class RoomType { SALA, COCINA, DORMITORIO, BANO, BODEGA, TIENDA, CONSULTORIO }

/** Al final de la lista de valores: no insertar en medio. */
enum class FurnitureKind { CAMA, MESA, ESTANTE, COCINA, GRIFO, INODORO, ARMARIO, MOSTRADOR, CAMILLA, BANCO, GENERADOR }

/** Lado del muro exterior donde va una ventana; la puerta de entrada siempre está al SUR. */
enum class WallSide { NORTE, ESTE, OESTE }

/** Habitación: celdas libres de [left]..[right] por [top]..[bottom] (ambos incluidos, sin contar los muros). */
data class InteriorRoom(val type: RoomType, val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Mueble pegado al muro norte de su habitación, de [widthCells] celdas de ancho y una de fondo. */
data class InteriorFurniture(val kind: FurnitureKind, val col: Int, val row: Int, val widthCells: Int, val roomIndex: Int)

/** Ventana en el muro exterior: [index] es la columna (NORTE) o la fila (ESTE y OESTE). */
data class InteriorWindow(val side: WallSide, val index: Int)

data class InteriorLayout(
    val cols: Int,
    val rows: Int,
    val rooms: List<InteriorRoom>,
    val furniture: List<InteriorFurniture>,
    val windows: List<InteriorWindow>,
    /** Columnas de los muros interiores (entre habitaciones). */
    val partitionCols: List<Int>,
    /** Celdas (col, fila) de los pasos abiertos en los muros interiores. */
    val doorways: List<Pair<Int, Int>>,
    /** Columna de la puerta de entrada, en la fila `rows - 1`. */
    val doorCol: Int,
    /** Celda (col, fila) de un superviviente rescatable, o null si el edificio no tiene (t05). */
    val survivorCell: Pair<Int, Int>? = null,
) {
    /** Muro (exterior o interior) sin paso: la puerta de entrada y los pasos interiores no cuentan. */
    fun isWall(col: Int, row: Int): Boolean {
        if (col < 0 || row < 0 || col >= cols || row >= rows) return true
        if (row == rows - 1 && col == doorCol) return false
        if (col == 0 || col == cols - 1 || row == 0 || row == rows - 1) return true
        return col in partitionCols && (col to row) !in doorways
    }

    fun furnitureAt(col: Int, row: Int): InteriorFurniture? =
        furniture.firstOrNull { it.row == row && col >= it.col && col < it.col + it.widthCells }

    /** Se puede pisar: ni muro ni mueble. */
    fun isWalkable(col: Int, row: Int): Boolean = !isWall(col, row) && furnitureAt(col, row) == null

    /** Una fila por celda: `#` muro, `^` mueble, `.` libre (el formato de `CollisionMatrix`). */
    fun toCollisionRows(): List<String> = List(rows) { r ->
        buildString(cols) {
            for (c in 0 until cols) append(if (isWall(c, r)) '#' else if (furnitureAt(c, r) != null) '^' else '.')
        }
    }
}

object InteriorGenerator {
    const val MIN_COLS = 8
    const val MAX_COLS = 24
    const val MIN_ROWS = 7
    const val MAX_ROWS = 16

    /** Planta del edificio [buildingId] de tipo [profile] y [widthM] × [depthM] metros (se acota a un rango jugable). */
    fun generate(buildingId: Long, profile: SearchProfile, widthM: Double, depthM: Double): InteriorLayout {
        val cols = (if (widthM.isFinite()) widthM.toInt() else MIN_COLS).coerceIn(MIN_COLS, MAX_COLS)
        val rows = (if (depthM.isFinite()) depthM.toInt() else MIN_ROWS).coerceIn(MIN_ROWS, MAX_ROWS)
        val seed = (buildingId xor (buildingId ushr 32)).toInt()
        val count = when {
            cols >= 18 -> 3
            cols >= 11 -> 2
            else -> 1
        }
        val partitions = (1 until count).map { cols * it / count }
        val types = roomTypes(profile)
        val rooms = ArrayList<InteriorRoom>(count)
        var left = 1
        for (i in 0 until count) {
            val right = if (i < count - 1) partitions[i] - 1 else cols - 2
            rooms.add(InteriorRoom(types[i % types.size], left, 1, right, rows - 2))
            left = right + 2
        }
        val doorways = partitions.mapIndexed { i, col -> col to (rows / 2 + hash(seed, i, 7) % (rows / 2 - 1)) }
        var doorCol = cols / 2
        if (doorCol in partitions) doorCol += 1
        val furniture = ArrayList<InteriorFurniture>()
        rooms.forEachIndexed { index, room -> placeFurniture(room, index, seed, furniture) }
        val windows = ArrayList<InteriorWindow>()
        val northWindows = 1 + cols / 8
        for (i in 0 until northWindows) {
            val col = 1 + hash(seed, i, 11) % (cols - 2)
            if (col !in partitions && windows.none { it.side == WallSide.NORTE && it.index == col }) windows.add(InteriorWindow(WallSide.NORTE, col))
        }
        windows.add(InteriorWindow(WallSide.ESTE, 1 + hash(seed, 0, 13) % (rows - 2)))
        windows.add(InteriorWindow(WallSide.OESTE, 1 + hash(seed, 1, 13) % (rows - 2)))
        // Un superviviente espera en el primer cuarto, lejos de los muebles (que van pegados al muro norte).
        val survivor = if (GroupRules.hasSurvivor(buildingId)) rooms[0].left + 1 to rows - 3 else null
        return InteriorLayout(cols, rows, rooms, furniture, windows, partitions, doorways, doorCol, survivor)
    }

    private fun roomTypes(profile: SearchProfile): List<RoomType> = when (profile) {
        SearchProfile.CASA -> listOf(RoomType.SALA, RoomType.COCINA, RoomType.DORMITORIO)
        SearchProfile.TIENDA -> listOf(RoomType.TIENDA, RoomType.BODEGA)
        SearchProfile.GASOLINERA -> listOf(RoomType.TIENDA, RoomType.BODEGA)
        SearchProfile.CLINICA -> listOf(RoomType.CONSULTORIO, RoomType.BANO, RoomType.SALA)
        SearchProfile.ESCUELA -> listOf(RoomType.SALA, RoomType.BODEGA, RoomType.BANO)
        SearchProfile.TALLER -> listOf(RoomType.BODEGA, RoomType.BODEGA)
        SearchProfile.ESTABLO -> listOf(RoomType.BODEGA)
        SearchProfile.GENERICO -> listOf(RoomType.SALA, RoomType.BODEGA, RoomType.COCINA)
    }

    private fun kindsFor(type: RoomType): List<FurnitureKind> = when (type) {
        RoomType.SALA -> listOf(FurnitureKind.MESA, FurnitureKind.BANCO, FurnitureKind.ARMARIO)
        RoomType.COCINA -> listOf(FurnitureKind.COCINA, FurnitureKind.GRIFO, FurnitureKind.ESTANTE)
        RoomType.DORMITORIO -> listOf(FurnitureKind.CAMA, FurnitureKind.ARMARIO)
        RoomType.BANO -> listOf(FurnitureKind.INODORO, FurnitureKind.GRIFO, FurnitureKind.ARMARIO)
        RoomType.BODEGA -> listOf(FurnitureKind.ESTANTE, FurnitureKind.GENERADOR, FurnitureKind.ESTANTE)
        RoomType.TIENDA -> listOf(FurnitureKind.MOSTRADOR, FurnitureKind.ESTANTE, FurnitureKind.ESTANTE)
        RoomType.CONSULTORIO -> listOf(FurnitureKind.CAMILLA, FurnitureKind.ARMARIO)
    }

    private fun widthOf(kind: FurnitureKind): Int = when (kind) {
        FurnitureKind.CAMA, FurnitureKind.MESA, FurnitureKind.MOSTRADOR, FurnitureKind.CAMILLA -> 2
        else -> 1
    }

    /** Muebles en fila contra el muro norte, con una celda de separación: así nunca cierran el paso hacia el sur. */
    private fun placeFurniture(room: InteriorRoom, roomIndex: Int, seed: Int, out: MutableList<InteriorFurniture>) {
        var col = room.left + hash(seed, roomIndex, 3) % 2
        for (kind in kindsFor(room.type)) {
            val w = widthOf(kind)
            if (col + w - 1 > room.right) break
            out.add(InteriorFurniture(kind, col, room.top, w, roomIndex))
            col += w + 1
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

package ovh.gabrielhuav.pow.features.map_exterior.ui.iso

// Manchas de suelo de la vista isométrica (t50): reparto determinista por celda del mundo. Reglas puras para probarlas.

internal object IsoPatchRules {
    /** Lado de la celda en metros. */
    const val CELL_METERS = 3f
    /** Con celdas más pequeñas que esto en pantalla no se dibujan manchas. */
    const val MIN_CELL_PX = 14f
    /** Tope de celdas visibles para no dibujar miles de óvalos con zoom lejano. */
    const val MAX_CELLS = 1200L

    private fun hash(ix: Int, iy: Int): Int = (ix * 73856093) xor (iy * 19349663)

    /** Una de cada cuatro celdas lleva mancha. */
    fun hasPatch(ix: Int, iy: Int): Boolean = hash(ix, iy) and 3 == 0

    /** Desplazamiento de la mancha dentro de su celda, en 0..1. */
    fun jitterX(ix: Int, iy: Int): Float = ((hash(ix, iy) ushr 4) and 15) / 15f
    fun jitterY(ix: Int, iy: Int): Float = ((hash(ix, iy) ushr 8) and 15) / 15f

    fun isDark(ix: Int, iy: Int): Boolean = hash(ix, iy) and 64 == 0

    /** ¿Se dibujan manchas con celdas de [cellPx] píxeles y [cells] celdas visibles? */
    fun shouldDraw(cellPx: Float, cells: Long): Boolean = cellPx >= MIN_CELL_PX && cells <= MAX_CELLS
}
package ovh.gabrielhuav.pow.domain.models.interior

import ovh.gabrielhuav.pow.domain.models.map.isoScreenSector
import ovh.gabrielhuav.pow.domain.models.map.isoSpriteHeading
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

// Proyección isométrica 2:1 de un interior procedural (t82): la cuadrícula (columna, fila) se ve como rombos, igual que el
// mundo exterior. Las constantes son píxeles de la imagen horneada del interior; el juego pone a los actores con las
// mismas fórmulas. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object InteriorIso {
    /** Medio ancho y medio alto de un rombo (2:1). */
    const val HALF_W = 40f
    const val HALF_H = 20f
    /** Espacio sobre la fila 0 para la altura de los muros del fondo. */
    const val TOP_PAD = 76f
    /** Píxeles de pantalla visibles de alto que se encuadran con zoom 1 (≈ 8 rombos). */
    const val VISIBLE_HEIGHT = 330f

    fun bitmapWidth(cols: Int, rows: Int): Float = (cols + rows) * HALF_W

    fun bitmapHeight(cols: Int, rows: Int): Float = (cols + rows) * HALF_H + TOP_PAD + 8f

    /** X en la imagen del punto (columna, fila) (flotantes: una celda mide 1). */
    fun x(col: Float, row: Float, rows: Int): Float = rows * HALF_W + (col - row) * HALF_W

    /** Y en la imagen del punto (columna, fila) a una altura [z] sobre el suelo (píxeles). */
    fun y(col: Float, row: Float, z: Float = 0f): Float = TOP_PAD + (col + row) * HALF_H - z

    /**
     * Sector de pantalla (0 = N, 1 = NE, ... 7 = NW) hacia el que mira quien se mueve con dirección ([dx] columnas, [dy] filas)
     * en la cuadrícula del interior: sirve para elegir la fila de las hojas isométricas de personajes.
     */
    fun spriteSector(dx: Float, dy: Float): Int {
        if (dx == 0f && dy == 0f) return 4
        // Las hojas usan este = -fila y norte = -columna (así la proyección del interior coincide con la del mundo exterior).
        val heading = (atan2((-dy).toDouble(), (-dx).toDouble()) * 180.0 / PI + 360.0) % 360.0
        return isoScreenSector(isoSpriteHeading(heading))
    }

    /**
     * Dirección del mundo (dx hacia las columnas, dy hacia las filas, normalizada) que corresponde a empujar el joystick
     * hacia ([screenX] a la derecha, [screenYDown] hacia abajo) de la pantalla. Arriba de la pantalla = hacia la esquina norte.
     */
    fun worldDirection(screenX: Float, screenYDown: Float): Pair<Float, Float> {
        val dx = (screenX + 2f * screenYDown) / 2f
        val dy = (2f * screenYDown - screenX) / 2f
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-6f) return 0f to 0f
        return dx / len to dy / len
    }
}

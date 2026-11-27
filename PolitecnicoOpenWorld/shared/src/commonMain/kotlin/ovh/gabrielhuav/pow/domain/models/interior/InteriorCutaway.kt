package ovh.gabrielhuav.pow.domain.models.interior

// Cutaway de los interiores isométricos (t84), la idea de Project Zomboid: los muros que quedan entre la cámara y un actor no lo
// ocultan, se dibujan semitransparentes; los muebles sí lo tapan. Reglas puras, sin Android.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object InteriorCutaway {
    /** Opacidad con que se redibuja un muro delante de un actor (0 = invisible, 1 = opaco). */
    const val WALL_ALPHA = 0.38f

    /** Opacidad con que se redibuja un mueble delante de un actor: tapa de verdad. */
    const val FURNITURE_ALPHA = 1f

    /**
     * ¿Un bloque con clave de profundidad [blockKey] queda DELANTE (más cerca de la cámara) de un actor con clave [actorKey]?
     * La clave es columna + fila de la celda; mayor = más cerca de la cámara.
     */
    fun isInFront(blockKey: Int, actorKey: Int): Boolean = blockKey > actorKey

    /** Opacidad con que se redibuja un bloque delante de un actor según sea muro o mueble. */
    fun alphaFor(isWall: Boolean): Float = if (isWall) WALL_ALPHA else FURNITURE_ALPHA
}

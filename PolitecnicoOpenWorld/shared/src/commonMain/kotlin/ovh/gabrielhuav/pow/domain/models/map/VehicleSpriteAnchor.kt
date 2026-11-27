package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Ancla vertical de los sprites de vehículo en la vista isométrica. El sprite se dibujaba centrado en el punto del suelo del
// vehículo, pero ese punto está en el centro de la HUELLA, no del dibujo (que incluye la altura de la carrocería): la caja
// de colisión quedaba ~1 m por encima de las ruedas y los pies del jugador parecían estar sobre las ventanas. Aquí se ancla
// la fila inferior opaca del sprite a la esquina más baja de la caja del cuerpo sólido. Reglas puras; ver
// README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object VehicleSpriteAnchor {
    /** Factor de la proyección 2:1: un metro de (este + norte) baja la pantalla `ppm * SCREEN_FACTOR` píxeles. */
    const val SCREEN_FACTOR = 0.35355339059327373

    /**
     * Desplazamiento vertical (píxeles, positivo = hacia abajo) que hay que sumar a la Y del punto del suelo para dibujar el
     * sprite centrado, de modo que su fila inferior opaca ([bottomFraction], 0..1 de la altura) caiga en la esquina más baja
     * de la caja de [lengthMeters] × [widthMeters] con rumbo [headingDegrees]; [spriteHeightPx] es la altura dibujada.
     */
    fun groundDropPx(
        lengthMeters: Double,
        widthMeters: Double,
        headingDegrees: Double,
        bottomFraction: Float,
        spriteHeightPx: Float,
        pixelsPerMeter: Double,
    ): Float {
        val h = headingDegrees * PI / 180.0
        val s = sin(h)
        val c = cos(h)
        val lowestCorner = pixelsPerMeter * SCREEN_FACTOR * (lengthMeters / 2.0 * abs(s + c) + widthMeters / 2.0 * abs(c - s))
        return (lowestCorner - (bottomFraction - 0.5f) * spriteHeightPx).toFloat()
    }
}

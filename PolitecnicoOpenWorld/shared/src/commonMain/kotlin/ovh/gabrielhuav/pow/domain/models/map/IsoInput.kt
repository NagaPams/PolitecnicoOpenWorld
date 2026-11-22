package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Convierte el ángulo del joystick (0 = derecha, positivo hacia arriba) al plano este/norte. */
fun isoInputToWorldAngle(screenAngle: Double): Double {
    val x = cos(screenAngle)
    val up = sin(screenAngle) * 2.0
    return atan2(up - x, up + x)
}

/** Ángulo de pantalla horario desde arriba para un rumbo de mundo horario desde el norte. */
fun isoSpriteHeading(worldHeadingDegrees: Double): Double {
    val heading = worldHeadingDegrees * PI / 180.0
    val east = sin(heading)
    val north = cos(heading)
    val screenX = east - north
    val screenUp = (east + north) / 2.0
    return (atan2(screenX, screenUp) * 180.0 / PI + 360.0) % 360.0
}

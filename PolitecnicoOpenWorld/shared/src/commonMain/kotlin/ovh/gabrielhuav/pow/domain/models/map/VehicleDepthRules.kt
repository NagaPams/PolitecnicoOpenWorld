package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Orden de dibujo de un vehículo LARGO respecto al jugador. El render ordena a los actores por la profundidad del centro;
// un autobús de 10 m en rumbo este/norte tiene sus puntas a ±3,5 de profundidad del centro, así que el jugador junto a una
// punta quedaba tapado o encima por error. Aquí el vehículo se ordena con el punto de su eje más cercano en profundidad al
// jugador. Reglas puras; ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object VehicleDepthRules {
    private val INV_SQRT_TWO = 1.0 / sqrt(2.0)
    /** Desempate cuando el jugador cae dentro del rango de profundidad del vehículo (en unidades de profundidad). */
    const val SIDE_EPSILON = 1e-3
    /** Solo se corrige con el jugador a menos de esto (m) del centro del vehículo; más lejos basta el centro. */
    const val NEAR_METERS = 14.0

    /** Mitad del rango de profundidad que cubre el eje del vehículo de [lengthMeters] con rumbo de mundo [headingDegrees]. */
    fun halfSpan(lengthMeters: Double, headingDegrees: Double): Double {
        val h = headingDegrees * PI / 180.0
        return lengthMeters / 2.0 * abs(sin(h) + cos(h)) * INV_SQRT_TWO
    }

    /**
     * Profundidad lateral del jugador respecto al eje: negativa = del lado más cercano a la cámara (se dibuja DESPUÉS).
     * ([eastMeters], [northMeters]) es la posición del jugador relativa al centro del vehículo.
     */
    fun lateralDepth(eastMeters: Double, northMeters: Double, headingDegrees: Double): Double {
        val h = headingDegrees * PI / 180.0
        val side = eastMeters * cos(h) - northMeters * sin(h)
        return side * (cos(h) - sin(h)) * INV_SQRT_TWO
    }

    /**
     * Clave de orden del vehículo (valores mayores quedan más lejos). Con el jugador fuera del rango del eje gana el extremo
     * más próximo; dentro del rango se ordena por el lado del eje en que está el jugador.
     */
    fun sortKey(centerDepth: Double, halfSpan: Double, playerDepth: Double, lateralDepth: Double): Double {
        val low = centerDepth - halfSpan
        val high = centerDepth + halfSpan
        if (playerDepth < low) return low
        if (playerDepth > high) return high
        return if (lateralDepth < 0.0) playerDepth + SIDE_EPSILON else playerDepth - SIDE_EPSILON
    }
}

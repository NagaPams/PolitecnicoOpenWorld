package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.abs
import kotlin.math.sqrt
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint

// Ajuste del coche del jugador a la calzada (antes inline en WorldMapViewModel.startGameLoop, t37): dentro del
// corredor se queda donde está; rozando el borde desliza a lo largo; apenas arrancando se jala suave hacia la
// calle; si se salió a velocidad, frena fuerte y se jala. No toca colisiones: la aduana va DESPUÉS.

data class RoadClampResult(val location: GeoPoint, val speed: Double)

object VehicleRoadClamp {
    /** Radio del corredor (grados, ~4,4 m); un poco mayor al rebasar o auto-centrar, para no verse fuera de la calle. */
    const val CORRIDOR_RADIUS = 0.00004
    const val CORRIDOR_RADIUS_WIDE = 0.000045
    const val SLIDE_ENTRY_FACTOR = 1.25
    const val SLIDE_BASE_KEEP = 0.85
    private const val LAUNCH_SPEED_FRACTION = 0.15
    private const val LAUNCH_DRAG = 0.95
    private const val OFF_ROAD_DRAG = 0.6
    private const val MAX_PULL = 0.3

    fun corridorRadius(dodging: Boolean, autoCentering: Boolean): Double =
        if (dodging || autoCentering) CORRIDOR_RADIUS_WIDE else CORRIDOR_RADIUS

    fun apply(
        from: GeoPoint,
        target: GeoPoint,
        nearestRoadPoint: GeoPoint,
        speed: Double,
        maxSpeed: Double,
        radius: Double,
    ): RoadClampResult {
        val distToRoad = dist(target, nearestRoadPoint)
        if (distToRoad <= radius) return RoadClampResult(target, speed)
        if (dist(from, nearestRoadPoint) <= radius * SLIDE_ENTRY_FACTOR) {
            // Venías por la calle y rozas el borde: desliza a lo largo (pared que desliza) en vez de clavarte.
            val slid = slideAlongCorridor(nearestRoadPoint, target, radius)
            val stepLen = dist(from, target)
            val kept = if (stepLen > 0.0) dist(from, slid) / stepLen else 1.0
            // Solo pierde velocidad de frente contra el borde; en ángulo rasante casi no pierde.
            return RoadClampResult(slid, speed * (SLIDE_BASE_KEEP + (1.0 - SLIDE_BASE_KEEP) * kept.coerceIn(0.0, 1.0)))
        }
        // Apenas arrancando: jala suave hacia la calzada; fuera de la calle a velocidad: frena fuerte y se jala
        // (antes te clavaba en `from`, un estado absorbente que solo se arreglaba bajándote y re-subiendo).
        val drag = if (abs(speed) < maxSpeed * LAUNCH_SPEED_FRACTION) LAUNCH_DRAG else OFF_ROAD_DRAG
        val pull = (radius / distToRoad).coerceAtMost(MAX_PULL)
        return RoadClampResult(
            GeoPoint(
                target.latitude + (nearestRoadPoint.latitude - target.latitude) * pull,
                target.longitude + (nearestRoadPoint.longitude - target.longitude) * pull,
            ),
            speed * drag,
        )
    }

    private fun dist(a: GeoPoint, b: GeoPoint): Double {
        val dLat = a.latitude - b.latitude
        val dLon = a.longitude - b.longitude
        return sqrt(dLat * dLat + dLon * dLon)
    }
}

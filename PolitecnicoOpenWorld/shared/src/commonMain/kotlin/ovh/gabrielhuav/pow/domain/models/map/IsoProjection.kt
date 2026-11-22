package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint

private val ISO_INV_SQRT_TWO = 1.0 / sqrt(2.0)
private val METERS_PER_DEGREE = GeoPoint.RADIUS_EARTH_METERS * PI / 180.0

/** Coordenada de pantalla en píxeles, con el eje Y creciendo hacia abajo. */
data class IsoScreenPoint(val x: Double, val y: Double)

/** Proyección local 2:1. La cámara y las posiciones geográficas siguen en coordenadas del mundo. */
class IsoProjection(
    val center: GeoPoint,
    val pixelsPerMeter: Double,
    val centerX: Double = 0.0,
    val centerY: Double = 0.0,
) {
    private val longitudeScale = cos(center.latitude * PI / 180.0)

    init {
        require(center.latitude.isFinite() && center.latitude in -90.0..90.0 && abs(longitudeScale) > 1e-9)
        require(center.longitude.isFinite() && pixelsPerMeter.isFinite() && pixelsPerMeter > 0.0)
        require(centerX.isFinite() && centerY.isFinite())
    }

    fun project(point: GeoPoint, heightMeters: Double = 0.0): IsoScreenPoint = IsoScreenPoint(
        x = projectX(point.latitude, point.longitude),
        y = projectY(point.latitude, point.longitude, heightMeters),
    )

    /** Proyección por componentes, sin crear objetos temporales en el bucle de dibujo. */
    fun projectX(latitude: Double, longitude: Double): Double {
        val north = (latitude - center.latitude) * METERS_PER_DEGREE
        val east = (longitude - center.longitude) * METERS_PER_DEGREE * longitudeScale
        return centerX + (east - north) * ISO_INV_SQRT_TWO * pixelsPerMeter
    }

    fun projectY(latitude: Double, longitude: Double, heightMeters: Double = 0.0): Double {
        val north = (latitude - center.latitude) * METERS_PER_DEGREE
        val east = (longitude - center.longitude) * METERS_PER_DEGREE * longitudeScale
        return centerY - (east + north) * ISO_INV_SQRT_TWO * pixelsPerMeter / 2.0 - heightMeters * pixelsPerMeter
    }

    /** Inversa exacta en el plano local para altura cero. */
    fun unprojectGround(x: Double, y: Double): GeoPoint {
        val horizontal = (x - centerX) / pixelsPerMeter
        val vertical = (centerY - y) * 2.0 / pixelsPerMeter
        val east = (horizontal + vertical) * ISO_INV_SQRT_TWO
        val north = (vertical - horizontal) * ISO_INV_SQRT_TWO
        return GeoPoint(
            latitude = center.latitude + north / METERS_PER_DEGREE,
            longitude = center.longitude + east / (METERS_PER_DEGREE * longitudeScale),
        )
    }

    /** Coordenada sin altura: valores mayores quedan más lejos de la cámara. */
    fun depth(point: GeoPoint): Double {
        val north = (point.latitude - center.latitude) * METERS_PER_DEGREE
        val east = (point.longitude - center.longitude) * METERS_PER_DEGREE * longitudeScale
        return (east + north) * ISO_INV_SQRT_TWO
    }

    companion object {
        /** Convierte la escala compartida en dp a píxeles físicos del Canvas. */
        fun forZoom(center: GeoPoint, zoom: Double, density: Double, centerX: Double, centerY: Double): IsoProjection {
            require(density.isFinite() && density > 0.0)
            return IsoProjection(center, density / metersPerDp(zoom, center.latitude), centerX, centerY)
        }
    }
}

package ovh.gabrielhuav.pow.features.map_exterior.ui.iso

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import ovh.gabrielhuav.pow.domain.models.geo.GeoPoint
import ovh.gabrielhuav.pow.domain.models.map.IsoProjection
import ovh.gabrielhuav.pow.domain.models.map.Npc
import ovh.gabrielhuav.pow.domain.models.map.VehicleDimensions
import ovh.gabrielhuav.pow.domain.models.map.bodyDimensions
import ovh.gabrielhuav.pow.domain.models.map.usesVehicleFootprint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Contorno de depuración de las cajas de colisión de los vehículos en la vista isométrica: sirve para calibrar el cuerpo sólido
// contra el sprite. Apagado por defecto; solo lo enciende un gancho de depuración local.

object IsoDebugOverlay {
    var collisionBoxes: Boolean = false
}

private const val OVERLAY_METERS_LAT = 111_320.0
private const val OVERLAY_METERS_LON = 104_800.0

internal fun DrawScope.drawIsoCollisionBoxes(
    projection: IsoProjection,
    npcs: List<Npc>,
    driving: Boolean,
    playerCenter: GeoPoint,
    playerHeading: Float,
    playerDimensions: VehicleDimensions,
) {
    for (npc in npcs) {
        if (!npc.type.usesVehicleFootprint()) continue
        drawBox(projection, npc.location, (npc.rotationAngle + 90f) % 360f, npc.bodyDimensions, Color(0xFFFF3B30))
    }
    if (driving) drawBox(projection, playerCenter, playerHeading, playerDimensions, Color(0xFF34C759))
}

private fun DrawScope.drawBox(projection: IsoProjection, center: GeoPoint, headingDegrees: Float, dims: VehicleDimensions, color: Color) {
    val h = headingDegrees * PI / 180.0
    val fwdN = cos(h); val fwdE = sin(h)
    val rightN = -fwdE; val rightE = fwdN
    val halfL = dims.lengthMeters / 2.0
    val halfW = dims.widthMeters / 2.0
    var firstX = 0f; var firstY = 0f; var prevX = 0f; var prevY = 0f
    for (i in 0 until 4) {
        val sl = if (i == 0 || i == 3) 1.0 else -1.0
        val sw = if (i < 2) 1.0 else -1.0
        val north = fwdN * halfL * sl + rightN * halfW * sw
        val east = fwdE * halfL * sl + rightE * halfW * sw
        val lat = center.latitude + north / OVERLAY_METERS_LAT
        val lon = center.longitude + east / OVERLAY_METERS_LON
        val x = projection.projectX(lat, lon).toFloat()
        val y = projection.projectY(lat, lon).toFloat()
        if (i == 0) { firstX = x; firstY = y } else drawLine(color, Offset(prevX, prevY), Offset(x, y), strokeWidth = 2f)
        prevX = x; prevY = y
    }
    drawLine(color, Offset(prevX, prevY), Offset(firstX, firstY), strokeWidth = 2f)
    val cx = projection.projectX(center.latitude, center.longitude).toFloat()
    val cy = projection.projectY(center.latitude, center.longitude).toFloat()
    drawCircle(color, 3f, Offset(cx, cy))
}

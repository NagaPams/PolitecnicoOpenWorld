package ovh.gabrielhuav.pow.domain.models.ai

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Cuándo un coche del tráfico frena por el jugador (a pie o en su coche) o cede el paso a un coche que cruza.
// Antes `carFollowScale` solo miraba coches con rumbo parecido, así que los NPC atravesaban al jugador y se
// cruzaban entre sí en las esquinas. Reglas puras; ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object CarYieldRules {
    private const val METERS_PER_DEG_LAT = 111_320.0
    private const val METERS_PER_DEG_LON = 104_800.0

    /** A esta distancia por delante del jugador el coche casi se detiene; hasta [PLAYER_FREE_M] frena poco a poco. */
    const val PLAYER_STOP_M = 5.0
    const val PLAYER_FREE_M = 18.0
    /** Ancho de carril a cada lado del eje del coche en el que el jugador cuenta como obstáculo. */
    const val LANE_HALF_WIDTH_M = 2.2
    const val STOPPED_SCALE = 0.05f

    /** Un coche que cruza (rumbo ~perpendicular) cuenta si está a esta distancia y dentro del cono delantero. */
    const val CROSS_RANGE_M = 9.0
    const val CROSS_CONE_DEG = 50.0
    const val CROSS_MIN_HEADING_DIFF = 55f
    const val CROSS_MAX_HEADING_DIFF = 125f
    const val CROSS_SCALE = 0.1f

    /**
     * Factor de velocidad (0..1) por el jugador en ([playerLat], [playerLon]) para un coche en ([carLat], [carLon])
     * que avanza hacia el ángulo [forwardRad] (el mismo `atan2(dLat, dLon)` del coche). 1 = vía libre.
     */
    fun playerScale(carLat: Double, carLon: Double, forwardRad: Double, playerLat: Double, playerLon: Double): Float {
        val ex = (playerLon - carLon) * METERS_PER_DEG_LON
        val ey = (playerLat - carLat) * METERS_PER_DEG_LAT
        var fx = cos(forwardRad) * METERS_PER_DEG_LON
        var fy = sin(forwardRad) * METERS_PER_DEG_LAT
        val len = sqrt(fx * fx + fy * fy)
        if (len == 0.0) return 1f
        fx /= len
        fy /= len
        val along = ex * fx + ey * fy
        if (along <= 0.0 || along > PLAYER_FREE_M) return 1f
        val lateral = abs(-ex * fy + ey * fx)
        if (lateral > LANE_HALF_WIDTH_M) return 1f
        if (along <= PLAYER_STOP_M) return STOPPED_SCALE
        return (STOPPED_SCALE + (1f - STOPPED_SCALE) * ((along - PLAYER_STOP_M) / (PLAYER_FREE_M - PLAYER_STOP_M)).toFloat()).coerceIn(STOPPED_SCALE, 1f)
    }

    /** `true` si [carId] debe ceder a un coche [otherId] que cruza por delante (cede el de id mayor: nunca se bloquean los dos). */
    fun yieldsToCrossing(
        carId: String, otherId: String, headingDiff: Float, carLat: Double, carLon: Double, forwardRad: Double, otherLat: Double, otherLon: Double,
    ): Boolean {
        if (headingDiff < CROSS_MIN_HEADING_DIFF || headingDiff > CROSS_MAX_HEADING_DIFF) return false
        if (carId <= otherId) return false
        val ex = (otherLon - carLon) * METERS_PER_DEG_LON
        val ey = (otherLat - carLat) * METERS_PER_DEG_LAT
        val dist = sqrt(ex * ex + ey * ey)
        if (dist > CROSS_RANGE_M || dist == 0.0) return false
        var fx = cos(forwardRad) * METERS_PER_DEG_LON
        var fy = sin(forwardRad) * METERS_PER_DEG_LAT
        val len = sqrt(fx * fx + fy * fy)
        if (len == 0.0) return false
        fx /= len
        fy /= len
        val cosAngle = (ex * fx + ey * fy) / dist
        return cosAngle >= cos(CROSS_CONE_DEG * kotlin.math.PI / 180.0)
    }
}

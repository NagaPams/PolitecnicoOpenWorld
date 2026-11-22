package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Esquive automático del coche del jugador ante el tráfico (antes inline en WorldMapViewModel.startGameLoop, t37).
// Acumulador reutilizable: sin allocations por tick. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

class VehicleDodge {
    /** Desplazamiento lateral resultante, en grados (ya con el tope por tick aplicado tras [finish]). */
    var offsetLat = 0.0
        private set
    var offsetLon = 0.0
        private set

    private var locLat = 0.0
    private var locLon = 0.0
    private var headLat = 0.0
    private var headLon = 0.0
    private var perpLat = 0.0
    private var perpLon = 0.0
    private var canDodge = false

    /**
     * Empieza un tick. Solo se esquiva yendo recto (sin giro brusco) a más del 9 % de la velocidad máxima: con
     * más velocidad o girando no hay esquive suave y el choque lo resuelve el atropello.
     */
    fun begin(lat: Double, lon: Double, headingRad: Double, absSpeed: Double, maxSpeed: Double, steeringSharply: Boolean) {
        locLat = lat
        locLon = lon
        headLat = cos(headingRad)
        headLon = sin(headingRad)
        perpLat = -headLon
        perpLon = headLat
        canDodge = absSpeed >= maxSpeed * MIN_SPEED_FRACTION && !steeringSharply
        offsetLat = 0.0
        offsetLon = 0.0
    }

    /** Considera un coche en ([carLat], [carLon]); solo empuja si está delante y dentro del radio de rebase. */
    fun consider(carLat: Double, carLon: Double) {
        if (!canDodge) return
        val vLat = carLat - locLat
        val vLon = carLon - locLon
        val dist = sqrt(vLat * vLat + vLon * vLon)
        if (dist >= OVERTAKE_RADIUS) return
        val inFront = vLat * headLat + vLon * headLon
        if (inFront <= 0) return
        val rightDist = vLat * perpLat + vLon * perpLon
        val dir = if (rightDist >= 0) -1.0 else 1.0
        val force = (OVERTAKE_RADIUS - dist) * FORCE_GAIN
        offsetLat += perpLat * dir * force
        offsetLon += perpLon * dir * force
    }

    /** Limita el desplazamiento por tick (con varios coches cerca las fuerzas se sumaban: ~1,6 m máx.). */
    fun finish() {
        val mag = sqrt(offsetLon * offsetLon + offsetLat * offsetLat)
        if (mag > MAX_PER_TICK) {
            val k = MAX_PER_TICK / mag
            offsetLon *= k
            offsetLat *= k
        }
    }

    val isDodging: Boolean get() = offsetLat != 0.0 || offsetLon != 0.0

    companion object {
        const val OVERTAKE_RADIUS = 0.00008
        const val MAX_PER_TICK = 0.0000015
        private const val MIN_SPEED_FRACTION = 0.09
        private const val FORCE_GAIN = 0.08
    }
}

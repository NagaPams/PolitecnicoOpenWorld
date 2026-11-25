package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.abs

// Heridas por tipo (t10): bajar de un coche en movimiento lastima según la velocidad y la suerte: un golpe solo quita
// vida, un corte además hace sangrar y una mala caída fractura. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Al final de la lista de valores: no insertar en medio. */
enum class InjuryKind { GOLPE, CORTE, CAIDA }

data class Injury(val kind: InjuryKind, val damage: Double)

object BailoutRules {
    /** Por debajo de esta fracción de la velocidad máxima bajar del coche no lastima. */
    const val MIN_SPEED_FRACTION = 0.25
    const val MIN_DAMAGE = 4.0
    const val MAX_DAMAGE = 30.0
    private const val FALL_FROM = 0.6
    private const val FALL_CHANCE = 0.3
    private const val CUT_FROM = 0.4
    private const val CUT_CHANCE = 0.65

    /** Herida al bajar a [speed] con la tirada [roll] (0..1), o null si la velocidad es baja o los datos no sirven. */
    fun injuryOnBailout(speed: Double, maxSpeed: Double, roll: Double): Injury? {
        if (maxSpeed <= 0.0 || !speed.isFinite()) return null
        val fraction = (abs(speed) / maxSpeed).coerceIn(0.0, 1.0)
        if (fraction < MIN_SPEED_FRACTION) return null
        val scale = (fraction - MIN_SPEED_FRACTION) / (1.0 - MIN_SPEED_FRACTION)
        val kind = when {
            fraction >= FALL_FROM && roll < FALL_CHANCE -> InjuryKind.CAIDA
            fraction >= CUT_FROM && roll < CUT_CHANCE -> InjuryKind.CORTE
            else -> InjuryKind.GOLPE
        }
        return Injury(kind, MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * scale)
    }

    fun causesBleeding(kind: InjuryKind): Boolean = kind == InjuryKind.CORTE

    fun causesFracture(kind: InjuryKind): Boolean = kind == InjuryKind.CAIDA
}

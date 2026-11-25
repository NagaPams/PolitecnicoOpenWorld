package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.abs

// Efectos del estado del cuerpo (modo supervivencia): un choque fuerte lastima al jugador y el cansancio o estar muy
// herido frenan el paso a pie. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object CrashInjuryRules {
    /** Por debajo de esta fracción de la velocidad máxima el choque no lastima. */
    const val MIN_SPEED_FRACTION = 0.5
    const val WALL_DAMAGE_AT_TOP_SPEED = 20.0
    const val CAR_DAMAGE_AT_TOP_SPEED = 12.0

    /** Vida que pierde el jugador en un choque a [speed]; 0 si es leve o los datos no sirven. */
    fun damage(speed: Double, maxSpeed: Double, againstWall: Boolean): Double {
        if (maxSpeed <= 0.0 || !speed.isFinite()) return 0.0
        val fraction = (abs(speed) / maxSpeed).coerceIn(0.0, 1.0)
        if (fraction < MIN_SPEED_FRACTION) return 0.0
        val scale = (fraction - MIN_SPEED_FRACTION) / (1.0 - MIN_SPEED_FRACTION)
        return scale * (if (againstWall) WALL_DAMAGE_AT_TOP_SPEED else CAR_DAMAGE_AT_TOP_SPEED)
    }

    /** Vida tras el choque; nunca baja del piso de vida del modo (si ya estaba por debajo, no empeora). */
    fun healthAfter(health: Double, damage: Double): Double =
        if (damage <= 0.0) health else maxOf(health - damage, minOf(health, SurvivalRules.HEALTH_FLOOR))
}

/** El hambre y la sed críticas también pasan factura: golpes más flojos y el aliento tarda más en volver. */
object NeedEffectRules {
    /** Por debajo de esto una necesidad ya cuenta como crítica (y avisa). */
    const val CRITICAL_BELOW = 15.0
    const val SEVERE_BELOW = 5.0

    /** Multiplicador del daño cuerpo a cuerpo según el hambre. */
    fun meleeFactor(hunger: Double): Double = when {
        hunger < SEVERE_BELOW -> 0.7
        hunger < CRITICAL_BELOW -> 0.85
        else -> 1.0
    }

    /** Multiplicador de lo que recupera el aliento al descansar según la sed. */
    fun staminaRegenFactor(thirst: Double): Double = when {
        thirst < SEVERE_BELOW -> 0.4
        thirst < CRITICAL_BELOW -> 0.6
        else -> 1.0
    }
}

object ConditionSpeedRules {
    const val TIRED_BELOW = 15.0
    const val EXHAUSTED_BELOW = 5.0
    const val TIRED_FACTOR = 0.85
    const val EXHAUSTED_FACTOR = 0.7
    const val HURT_BELOW = 25.0
    const val HURT_FACTOR = 0.9

    /** Multiplicador del paso a pie según la energía y la vida (0-100). */
    fun factor(energy: Double, health: Double): Double {
        val fatigue = when {
            energy < EXHAUSTED_BELOW -> EXHAUSTED_FACTOR
            energy < TIRED_BELOW -> TIRED_FACTOR
            else -> 1.0
        }
        return fatigue * (if (health < HURT_BELOW) HURT_FACTOR else 1.0)
    }
}

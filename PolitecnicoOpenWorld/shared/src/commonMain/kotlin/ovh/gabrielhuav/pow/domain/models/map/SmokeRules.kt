package ovh.gabrielhuav.pow.domain.models.map

import kotlin.math.sin

// Humo del motor del coche del jugador con poca salud (t04). Reglas puras del dibujo: cuándo sale, cuántas bocanadas hay y
// cómo suben, se ensanchan y se desvanecen según el tiempo. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object SmokeRules {
    /** Con esta salud (0-100) o menos el coche echa humo. */
    const val SMOKE_BELOW = 35.0
    const val PUFFS = 5
    private const val CYCLE_MS = 1400L
    private const val RISE_METERS = 2.2f
    private const val BASE_HEIGHT_METERS = 1.2f

    fun smokes(health: Double): Boolean = health < SMOKE_BELOW

    /** Fase (0..1) de la bocanada [index] en el instante [elapsedMs]: las bocanadas van desfasadas entre sí. */
    fun phase(elapsedMs: Long, index: Int): Float {
        val t = ((elapsedMs % CYCLE_MS).toFloat() / CYCLE_MS + index.toFloat() / PUFFS) % 1f
        return t
    }

    /** Altura (m) sobre el suelo: arranca en el cofre y sube. */
    fun riseMeters(phase: Float): Float = BASE_HEIGHT_METERS + RISE_METERS * phase

    fun radiusMeters(phase: Float): Float = 0.3f + 0.55f * phase

    /** Cuanto menos salud, más opaco; se desvanece al subir. */
    fun alpha(phase: Float, health: Double): Float {
        val density = (1.0 - health / SMOKE_BELOW).toFloat().coerceIn(0.15f, 1f)
        return ((1f - phase) * 0.95f * (0.5f + density * 0.5f)).coerceIn(0f, 0.85f)
    }

    /** Desvío lateral (m) de la bocanada: ondula con el tiempo y crece al subir. */
    fun driftMeters(elapsedMs: Long, index: Int, phase: Float): Float =
        sin(elapsedMs / 700f + index * 2.1f) * 0.35f * phase
}

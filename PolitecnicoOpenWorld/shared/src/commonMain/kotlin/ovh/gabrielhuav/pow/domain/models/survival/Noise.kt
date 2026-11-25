package ovh.gabrielhuav.pow.domain.models.survival

enum class NoiseSource { WALKING, RUNNING, MELEE, GUNSHOT, ENGINE, HORN, DOOR }

data class NoiseEvent(
    val latitude: Double, val longitude: Double, val source: NoiseSource, val night: Boolean,
    /** Con lluvia el ruido llega menos lejos (t13). */
    val rain: Boolean = false,
) {
    val radiusMeters: Double get() = NoiseRules.radiusFor(source, night) * (if (rain) WeatherRules.RAIN_NOISE_FACTOR else 1.0)
    /** Hasta dónde lo oyen los zombis (migración, t18): los ruidos fuertes llegan más lejos que su radio normal. */
    val hearingMeters: Double get() = radiusMeters * NoiseRules.migrationFactor(source)
}

object NoiseRules {
    /** Disparos y claxon atraen zombis de mucho más lejos; el resto, solo a su radio. */
    fun migrationFactor(source: NoiseSource): Double = when (source) {
        NoiseSource.GUNSHOT, NoiseSource.HORN -> 1.8
        else -> 1.0
    }

    fun radiusFor(source: NoiseSource, night: Boolean): Double {
        val radius = when (source) {
            NoiseSource.WALKING -> 12.0
            NoiseSource.RUNNING -> 30.0
            NoiseSource.MELEE -> 35.0
            NoiseSource.GUNSHOT -> 160.0
            NoiseSource.ENGINE -> 45.0
            NoiseSource.HORN -> 140.0
            NoiseSource.DOOR -> 25.0
        }
        return if (night) radius * 1.2 else radius
    }
}

/** Cola acotada: conserva los eventos existentes cuando se llena. */
class NoiseBus(capacity: Int) {
    init { require(capacity > 0) }
    private val events = arrayOfNulls<NoiseEvent>(capacity)
    private var head = 0
    private var count = 0

    fun offer(event: NoiseEvent): Boolean {
        if (count == events.size) return false
        events[(head + count) % events.size] = event
        count++
        return true
    }

    fun poll(): NoiseEvent? {
        if (count == 0) return null
        val event = events[head]
        events[head] = null
        head = (head + 1) % events.size
        count--
        return event
    }
}

/** t03: subirse por primera vez a un coche ajeno es "hacerle el puente" y suena más que abrir la puerta de uno ya tuyo. */
object HotwireRules {
    fun noisesOnBoard(firstTime: Boolean): List<NoiseSource> =
        if (firstTime) listOf(NoiseSource.DOOR, NoiseSource.ENGINE) else listOf(NoiseSource.DOOR)
}

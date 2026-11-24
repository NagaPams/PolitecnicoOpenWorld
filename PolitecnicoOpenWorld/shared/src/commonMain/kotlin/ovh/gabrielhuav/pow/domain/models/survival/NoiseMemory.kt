package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.cos
import kotlin.math.sqrt

/** Ventana acotada de sonidos recientes. El tiempo lo aporta el reloj monotónico del llamador. */
class NoiseMemory(capacity: Int, private val lifetimeMs: Long) {
    init {
        require(capacity > 0)
        require(lifetimeMs > 0)
    }

    private val events = arrayOfNulls<NoiseEvent>(capacity)
    private val timestamps = LongArray(capacity)
    private var head = 0
    private var count = 0

    private fun expire(nowMs: Long) {
        while (count > 0 && (nowMs < timestamps[head] || nowMs - timestamps[head] >= lifetimeMs)) {
            events[head] = null
            head = (head + 1) % events.size
            count--
        }
    }

    fun clear() {
        events.fill(null)
        head = 0
        count = 0
    }

    fun offer(event: NoiseEvent, nowMs: Long): Boolean {
        expire(nowMs)
        if (count == events.size) return false
        val index = (head + count) % events.size
        events[index] = event
        timestamps[index] = nowMs
        count++
        return true
    }

    fun lastStrong(minimumRadius: Double, nowMs: Long): NoiseEvent? {
        expire(nowMs)
        for (offset in count - 1 downTo 0) {
            val event = events[(head + offset) % events.size]
            if (event != null && event.radiusMeters >= minimumRadius) return event
        }
        return null
    }

    fun strongestAt(latitude: Double, longitude: Double, nowMs: Long): NoiseEvent? {
        expire(nowMs)
        val longitudeScale = cos(latitude * kotlin.math.PI / 180.0)
        var strongest: NoiseEvent? = null
        var best = 0.0
        for (offset in 0 until count) {
            val event = events[(head + offset) % events.size] ?: continue
            val north = (event.latitude - latitude) * 111_320.0
            val east = (event.longitude - longitude) * 111_320.0 * longitudeScale
            val strength = event.hearingMeters - sqrt(north * north + east * east)
            if (strength > best) {
                best = strength
                strongest = event
            }
        }
        return strongest
    }
}

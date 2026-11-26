package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.hypot

// Grupo de supervivientes (t05): a veces hay un superviviente en un interior; lo rescatas y viaja contigo (máximo 3, los
// asientos libres del coche). Cada uno bebe de tus provisiones, avisa de zombis cercanos y dispara a uno cada 10 s.
// Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object GroupRules {
    /** Asientos libres del coche: conductor y tres acompañantes. */
    const val MAX_MEMBERS = 3
    /** Cada acompañante sube la sed un 10 %. */
    const val THIRST_PER_MEMBER = 0.10
    /** Un zombi a esta distancia (m) o menos hace que el grupo avise. */
    const val LOOKOUT_METERS = 40.0
    /** Entre un aviso y el siguiente. */
    const val WARN_INTERVAL_MS = 30_000L
    /** Cada acompañante dispara a lo sumo una vez en este tiempo. */
    const val SHOT_INTERVAL_MS = 10_000L
    /** Alcance de su disparo (grados, ~30 m). */
    const val SHOT_RANGE_DEG = 0.00027
    /** Uno de cada [SURVIVOR_EVERY] edificios tiene un superviviente. */
    private const val SURVIVOR_EVERY = 4

    fun canJoin(members: Int): Boolean = members in 0 until MAX_MEMBERS

    fun thirstFactor(members: Int): Double = 1.0 + THIRST_PER_MEMBER * members.coerceIn(0, MAX_MEMBERS)

    fun shouldWarn(nearestZombieMeters: Double, nowMs: Long, lastWarnMs: Long): Boolean =
        nearestZombieMeters <= LOOKOUT_METERS && nowMs - lastWarnMs >= WARN_INTERVAL_MS

    fun canShoot(nowMs: Long, lastShotMs: Long): Boolean = nowMs - lastShotMs >= SHOT_INTERVAL_MS

    fun inShotRange(memberLat: Double, memberLon: Double, zombieLat: Double, zombieLon: Double): Boolean =
        hypot(memberLat - zombieLat, memberLon - zombieLon) <= SHOT_RANGE_DEG

    /** ¿El edificio [buildingId] tiene un superviviente? Estable por edificio. */
    fun hasSurvivor(buildingId: Long): Boolean = mix((buildingId xor (buildingId ushr 32)).toInt()) % SURVIVOR_EVERY == 0

    private fun mix(a: Int): Int {
        var h = a * 73856093
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        h *= 0x297a2d39
        h = h xor (h ushr 15)
        return h and Int.MAX_VALUE
    }
}

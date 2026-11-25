package ovh.gabrielhuav.pow.domain.models.survival

// Agarrón de zombi (t02): una mordida que conecta te sujeta un momento y frena tus pasos; un golpe que le das a un
// zombi te suelta. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Empujón (t02, como en Project Zomboid): aparta a los zombis pegados sin dañarlos, cuesta aliento y tiene enfriamiento. */
object ShoveRules {
    /** Radio (grados, ~5 m) en que un zombi cae dentro del empujón. */
    const val RADIUS_DEG = 0.000045
    /** Cuánto se aparta el zombi (grados, ~11 m). */
    const val KNOCKBACK_DEG = 0.0001
    const val COOLDOWN_MS = 2_000L
    const val STAMINA_COST = 20.0

    fun ready(nowMs: Long, lastShoveMs: Long): Boolean = nowMs - lastShoveMs >= COOLDOWN_MS

    /** Hace falta aliento y no estar agotado. */
    fun canAfford(stamina: StaminaState): Boolean = !stamina.exhausted && stamina.value >= STAMINA_COST

    /** Posición (lat, lon) del zombi tras el empujón, alejado del jugador; null si está fuera de alcance o encima. */
    fun pushedTo(zombieLat: Double, zombieLon: Double, playerLat: Double, playerLon: Double): Pair<Double, Double>? {
        val dLat = zombieLat - playerLat
        val dLon = zombieLon - playerLon
        val d = kotlin.math.sqrt(dLat * dLat + dLon * dLon)
        if (d > RADIUS_DEG || d < 1e-9) return null
        return Pair(zombieLat + dLat / d * KNOCKBACK_DEG, zombieLon + dLon / d * KNOCKBACK_DEG)
    }
}

object GrabRules {
    const val DURATION_MS = 1_500L
    const val SLOW_FACTOR = 0.4

    /** Hasta cuándo dura un agarrón que empieza en [nowMs]. */
    fun grabbedUntil(nowMs: Long): Long = nowMs + DURATION_MS

    /** Multiplicador de la velocidad a pie: lento mientras dure el agarrón, normal si no. */
    fun speedFactor(nowMs: Long, grabbedUntilMs: Long): Double = if (nowMs < grabbedUntilMs) SLOW_FACTOR else 1.0
}

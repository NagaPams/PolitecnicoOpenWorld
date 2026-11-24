package ovh.gabrielhuav.pow.domain.models.survival

// Agarrón de zombi (t02): una mordida que conecta te sujeta un momento y frena tus pasos; un golpe que le das a un
// zombi te suelta. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object GrabRules {
    const val DURATION_MS = 1_500L
    const val SLOW_FACTOR = 0.4

    /** Hasta cuándo dura un agarrón que empieza en [nowMs]. */
    fun grabbedUntil(nowMs: Long): Long = nowMs + DURATION_MS

    /** Multiplicador de la velocidad a pie: lento mientras dure el agarrón, normal si no. */
    fun speedFactor(nowMs: Long, grabbedUntilMs: Long): Double = if (nowMs < grabbedUntilMs) SLOW_FACTOR else 1.0
}

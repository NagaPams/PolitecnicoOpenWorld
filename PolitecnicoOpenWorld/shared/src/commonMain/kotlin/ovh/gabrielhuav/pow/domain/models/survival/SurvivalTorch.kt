package ovh.gabrielhuav.pow.domain.models.survival

// Antorcha (t87, paso 8): un tablón encendido alumbra unos minutos y aclara el velo de la noche (útil en el apagón).
// Solo memoria y de un solo uso, como las trampas. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object TorchRules {
    /** Tablones que gasta encender una antorcha. */
    const val BOARD_COST = 1
    /** Lo que arde (ms de reloj real). */
    const val BURN_MS = 3L * 60L * 1000L
    /** Con la antorcha encendida el velo nocturno se multiplica por esto. */
    const val VEIL_FACTOR = 0.45f

    fun canLight(boards: Int, lit: Boolean): Boolean = boards >= BOARD_COST && !lit

    fun isLit(nowMs: Long, untilMs: Long): Boolean = nowMs < untilMs

    /** Segundos que le quedan (0 si ya se apagó). */
    fun secondsLeft(nowMs: Long, untilMs: Long): Long = ((untilMs - nowMs).coerceAtLeast(0L) + 999L) / 1000L

    /** Alpha del velo con o sin antorcha; el día (alpha 0) sigue sin oscurecer. */
    fun veil(alpha: Float, lit: Boolean): Float = if (lit) alpha * VEIL_FACTOR else alpha
}

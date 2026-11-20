package ovh.gabrielhuav.pow.domain.models.streetfighter

/** Estado puro del primer bonus arcade. El reloj usa tiempo de juego y respeta la pausa. */
data class SfPrinterBonus(
    val remainingMs: Long = DURATION_MS,
    val introMs: Long = 2000L,
    val health: Int = MAX_HEALTH,
    val score: Int = 0,
    val hits: Int = 0,
    val lastHitMs: Long = -10000L,
) {
    val finished: Boolean get() = health <= 0 || remainingMs <= 0L
    val destroyed: Boolean get() = health <= 0
    val seconds: Int get() = ((remainingMs + 999L) / 1000L).toInt()
    val damageFrame: Int get() = when {
        health <= 0 -> 7
        health == MAX_HEALTH -> 0
        else -> (2 + (MAX_HEALTH - health) * 5 / MAX_HEALTH).coerceAtMost(6)
    }

    fun advance(ms: Long): SfPrinterBonus {
        if (finished || ms <= 0) return this
        val intro = minOf(introMs, ms)
        return copy(introMs = introMs - intro, remainingMs = (remainingMs - (ms - intro)).coerceAtLeast(0L))
    }

    fun hit(damage: Int, now: Long): SfPrinterBonus {
        if (finished || introMs > 0 || damage <= 0) return this
        val dealt = minOf(damage, health)
        val hp = health - dealt
        return copy(health = hp, hits = hits + 1, lastHitMs = now,
            score = score + dealt * 10 + if (hp == 0) 5000 + seconds * 100 else 0)
    }

    companion object {
        const val DURATION_MS = 30000L
        const val MAX_HEALTH = 500
        const val X = 720f
        val TARGET = SfBox(X - 48f, SfConstants.STAGE_FLOOR - 116f, 96f, 116f)
        fun followsVictory(step: Int): Boolean = step == 2
    }
}

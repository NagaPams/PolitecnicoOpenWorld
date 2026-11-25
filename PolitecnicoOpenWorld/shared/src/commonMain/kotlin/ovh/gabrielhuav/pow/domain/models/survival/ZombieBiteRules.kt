package ovh.gabrielhuav.pow.domain.models.survival

// Mordida de zombi a pie (t32): antes ~9 HP/s desde el primer contacto (tres zombis mataban en segundos). Ahora hay un
// aviso previo al primer contacto y un daño más lento, con un único cooldown global (por eso no crece con el número de
// zombis). Ver 04_MAP_EXTERIOR.md.

object ZombieBiteRules {
    /** Tiempo entre mordidas, sin importar cuántos zombis te rodeen. */
    const val INTERVAL_MS = 1_200L
    /** Daño de cada mordida (~4 HP/s en total). */
    const val DAMAGE = 5f
    /** Aviso previo: desde que un zombi te alcanza hasta la primera mordida. */
    const val WINDUP_MS = 600L
    /** Si pasa más de esto sin contacto, el siguiente contacto vuelve a tener aviso previo. */
    const val CONTACT_GAP_MS = 1_500L

    /** ¿Este contacto es nuevo (hay que dar aviso previo)? [sinceLastContactMs] = tiempo desde el contacto anterior. */
    fun startsNewContact(sinceLastContactMs: Long): Boolean = sinceLastContactMs > CONTACT_GAP_MS

    /** ¿Ya puede morder? Pasó el aviso previo y el cooldown global. */
    fun canBite(nowMs: Long, readyAtMs: Long, lastBiteMs: Long): Boolean =
        nowMs >= readyAtMs && nowMs - lastBiteMs >= INTERVAL_MS
}

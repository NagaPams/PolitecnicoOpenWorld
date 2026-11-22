package ovh.gabrielhuav.pow.domain.platform

// Cuándo avisar al jugador de memoria baja: lógica pura (la plataforma mide la RAM y muestra el aviso).
// Idea tomada de Zomdroid (aviso de memoria); regla y código propios.

/** Memoria disponible por debajo de la cual se avisa. */
const val LOW_MEMORY_THRESHOLD_MB = 300L

/** Tiempo mínimo entre dos avisos, para no saturar al jugador. */
const val LOW_MEMORY_WARN_COOLDOWN_MS = 30_000L

/** ¿Toca avisar? [lastWarnMs] es el instante del último aviso, o null si aún no hubo ninguno. */
fun shouldWarnLowMemory(availableMb: Long, nowMs: Long, lastWarnMs: Long?): Boolean =
    availableMb < LOW_MEMORY_THRESHOLD_MB &&
        (lastWarnMs == null || nowMs - lastWarnMs >= LOW_MEMORY_WARN_COOLDOWN_MS)

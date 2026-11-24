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

/**
 * Recorte automático de población (t21): con poca memoria disponible el mundo simula menos NPC y zombis. Con
 * histéresis (se recupera hasta [RECOVER_MB]) para que no suba y baje cada vez que la memoria cruza el umbral.
 */
object MemoryPressureRules {
    const val CRITICAL_MB = 200L
    const val RECOVER_MB = 400L
    const val LOW_FACTOR = 0.6f
    const val CRITICAL_FACTOR = 0.35f
    /** Cada cuánto se mide la memoria como máximo. */
    const val CHECK_INTERVAL_MS = 2_000L

    /** Factor de población para [availableMb] (-1 si no se pudo medir: no cambia nada); [current] da la histéresis. */
    fun factor(availableMb: Long, current: Float): Float = when {
        availableMb < 0L -> current
        availableMb < CRITICAL_MB -> CRITICAL_FACTOR
        availableMb < LOW_MEMORY_THRESHOLD_MB -> LOW_FACTOR
        availableMb >= RECOVER_MB -> 1.0f
        else -> current
    }

    /** MemAvailable de un `/proc/meminfo`, en MB, o -1 si no se encuentra (nunca lanza). */
    fun parseMemAvailableMb(meminfo: String): Long {
        val line = meminfo.lineSequence().firstOrNull { it.startsWith("MemAvailable:") } ?: return -1L
        val kb = line.removePrefix("MemAvailable:").trim().removeSuffix("kB").trim().toLongOrNull() ?: return -1L
        return if (kb < 0L) -1L else kb / 1024L
    }
}

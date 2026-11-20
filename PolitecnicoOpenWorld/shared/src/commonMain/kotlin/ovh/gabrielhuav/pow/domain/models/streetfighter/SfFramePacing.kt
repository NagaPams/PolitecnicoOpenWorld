package ovh.gabrielhuav.pow.domain.models.streetfighter

/** Presupuesto de espera: descontar la simulación y mantener una cesión tras un atasco. */
object SfFramePacing {
    fun waitMs(intervalMs: Long, previousStartMs: Long, nowMs: Long): Long =
        (intervalMs - (nowMs - previousStartMs).coerceAtLeast(0L)).coerceAtLeast(1L)
}

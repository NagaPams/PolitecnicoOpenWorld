package ovh.gabrielhuav.pow.domain.models.interior

import ovh.gabrielhuav.pow.domain.models.survival.BlackoutRules

// Generador (t14): con el apagón (desde el día 3) los interiores se quedan a oscuras; un generador encendido con un
// bidón devuelve la luz un rato. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

enum class GeneratorResult { STARTED, ALREADY_ON, NEEDS_FUEL, NOT_NEEDED }

object PowerRules {
    /** Lo que dura un bidón en el generador (ms de reloj real). */
    const val GENERATOR_MS = 10L * 60L * 1000L
    /** Bidones que gasta encenderlo. */
    const val CANS_PER_START = 1

    fun powered(nowMs: Long, poweredUntilMs: Long): Boolean = nowMs < poweredUntilMs

    /** ¿El interior está a oscuras? Solo con apagón y sin generador encendido. */
    fun isDark(day: Int, nowMs: Long, poweredUntilMs: Long): Boolean = BlackoutRules.active(day) && !powered(nowMs, poweredUntilMs)

    /** Qué pasa al usar el generador: [cans] bidones en la mochila. */
    fun check(day: Int, nowMs: Long, poweredUntilMs: Long, cans: Int): GeneratorResult = when {
        !BlackoutRules.active(day) -> GeneratorResult.NOT_NEEDED
        powered(nowMs, poweredUntilMs) -> GeneratorResult.ALREADY_ON
        cans < CANS_PER_START -> GeneratorResult.NEEDS_FUEL
        else -> GeneratorResult.STARTED
    }

    fun poweredUntil(nowMs: Long): Long = nowMs + GENERATOR_MS
}

package ovh.gabrielhuav.pow.domain.models.survival

import ovh.gabrielhuav.pow.platform.concurrencia.PowCerrojo

// Cola de avisos de supervivencia (t31): antes cada aviso pisaba al anterior (el de infección borraba el de agarrón) y
// los que llegaban con un diálogo abierto se perdían detrás de él. Ahora los de más prioridad se muestran al instante,
// los demás esperan su turno y nada se muestra mientras un diálogo está abierto. Ver 04_MAP_EXTERIOR.md.

object PromptPriority {
    const val LOW = 0
    const val NORMAL = 1
    const val HIGH = 2
}

data class QueuedPrompt(val text: String, val priority: Int)

class PromptQueue(private val maxQueued: Int = 3) {
    companion object {
        /** Cuánto dura un aviso en pantalla. */
        const val SHOW_MS = 3_000L
        /** Margen al pedir el siguiente justo cuando termina el anterior (los relojes no coinciden al milisegundo). */
        private const val SLACK_MS = 200L
    }

    /** Los avisos llegan de varios hilos (bucle de juego, corrutinas, reinicio de partida): todo bajo un cerrojo. */
    private val cerrojo = PowCerrojo()
    private val waiting = ArrayList<QueuedPrompt>()
    private var activeUntilMs = 0L
    private var activePriority = PromptPriority.LOW

    val queuedCount: Int get() = cerrojo.ejecutar { waiting.size }

    /** Un aviso nuevo: devuelve el que hay que mostrar YA (este u otro) o `null` si queda en espera. */
    fun offer(prompt: QueuedPrompt, nowMs: Long, blocked: Boolean): QueuedPrompt? = cerrojo.ejecutar {
        val free = nowMs + SLACK_MS >= activeUntilMs
        if (!blocked && (free || prompt.priority > activePriority)) return@ejecutar show(prompt, nowMs)
        if (waiting.none { it.text == prompt.text }) {
            waiting.add(prompt)
            while (waiting.size > maxQueued) {
                // Se descarta el de menor prioridad (el más viejo entre iguales).
                val drop = waiting.minByOrNull { it.priority } ?: break
                waiting.remove(drop)
            }
        }
        null
    }

    /** ¿Toca mostrar el siguiente en espera? Llamarlo al terminar un aviso y al cerrar un diálogo. */
    fun next(nowMs: Long, blocked: Boolean): QueuedPrompt? = cerrojo.ejecutar {
        if (blocked || waiting.isEmpty() || nowMs + SLACK_MS < activeUntilMs) return@ejecutar null
        val best = waiting.maxByOrNull { it.priority } ?: return@ejecutar null
        waiting.remove(best)
        show(best, nowMs)
    }

    fun clear() = cerrojo.ejecutar {
        waiting.clear()
        activeUntilMs = 0L
        activePriority = PromptPriority.LOW
    }

    private fun show(prompt: QueuedPrompt, nowMs: Long): QueuedPrompt {
        activeUntilMs = nowMs + SHOW_MS
        activePriority = prompt.priority
        return prompt
    }
}

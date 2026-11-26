package ovh.gabrielhuav.pow.domain.models.interior

// Guardado del estado de los interiores procedurales (t80): ventanas, supervivientes rescatados y generadores.
// Texto compacto; un guardado viejo o dañado se lee como vacío. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object InteriorStateRules {
    /** `clave=ESTADO;clave=ESTADO` (las cerradas, que es el valor por defecto, no se guardan). */
    fun encodeWindows(states: Map<String, WindowState>): String =
        states.filterValues { it != WindowState.CERRADA }.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value.name}" }

    fun decodeWindows(text: String): Map<String, WindowState> {
        val out = HashMap<String, WindowState>()
        for (part in text.split(';')) {
            val i = part.lastIndexOf('=')
            if (i <= 0) continue
            val state = WindowState.entries.firstOrNull { it.name == part.substring(i + 1) } ?: continue
            out[part.substring(0, i)] = state
        }
        return out
    }

    fun encodeIds(ids: Collection<Long>): String = ids.sorted().joinToString(",")

    fun decodeIds(text: String): Set<Long> = text.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

    /** `edificio:msRestantes;...` solo de los generadores aún encendidos en [nowMs] (el reloj no sobrevive a la app, por eso se guarda lo que falta). */
    fun encodePower(poweredUntil: Map<Long, Long>, nowMs: Long): String =
        poweredUntil.entries.filter { it.value > nowMs }.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value - nowMs}" }

    fun decodePower(text: String, nowMs: Long): Map<Long, Long> {
        val out = HashMap<Long, Long>()
        for (part in text.split(';')) {
            val i = part.indexOf(':')
            if (i <= 0) continue
            val id = part.substring(0, i).toLongOrNull() ?: continue
            val left = part.substring(i + 1).toLongOrNull() ?: continue
            if (left > 0) out[id] = nowMs + left
        }
        return out
    }
}

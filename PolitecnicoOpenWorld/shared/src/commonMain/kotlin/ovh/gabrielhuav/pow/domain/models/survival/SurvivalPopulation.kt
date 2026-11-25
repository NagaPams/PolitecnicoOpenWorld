package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.floor

// Población persistente (t18, modo supervivencia): se cuentan los zombis eliminados por zona (cuadrícula de ~200 m)
// y una zona con suficientes bajas ya no genera zombis nuevos, como en Project Zomboid. El conteo se guarda en la
// partida. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Resultado de anotar una baja: [justCleared] es true solo en la baja que despeja la zona. */
data class ZoneKillResult(val kills: Map<Long, Int>, val justCleared: Boolean)

object ZoneKillRules {
    /** Lado de la zona en grados (~200 m). */
    const val CELL_DEGREES = 0.0018
    /** Con tantas bajas la zona queda despejada y no genera zombis nuevos. */
    const val CLEARED_AT = 10
    /** Zonas que se recuerdan como máximo (el guardado no crece sin límite). */
    const val MAX_ZONES = 200

    /** t18: bajas que se "reponen" por cada día de supervivencia que pasa (la población vuelve a crecer). */
    const val REGROW_PER_DAY = 2

    /** Tras [days] días nuevos cada zona recupera [REGROW_PER_DAY] zombis por día; las zonas sin bajas se olvidan. */
    fun regrow(kills: Map<Long, Int>, days: Int): Map<Long, Int> {
        if (days <= 0 || kills.isEmpty()) return kills
        val out = LinkedHashMap<Long, Int>()
        for ((key, count) in kills) {
            val left = count - days.toLong().coerceAtMost(CLEARED_AT.toLong()).toInt() * REGROW_PER_DAY
            if (left > 0) out[key] = left
        }
        return out
    }

    fun keyOf(latitude: Double, longitude: Double): Long =
        (floor(latitude / CELL_DEGREES).toLong() shl 32) xor (floor(longitude / CELL_DEGREES).toLong() and 0xFFFFFFFFL)

    fun isCleared(kills: Map<Long, Int>, latitude: Double, longitude: Double): Boolean =
        kills.isNotEmpty() && (kills[keyOf(latitude, longitude)] ?: 0) >= CLEARED_AT

    /** Anota una baja; si ya hay [MAX_ZONES] zonas olvida la de menos bajas que no sea la actual. */
    fun record(kills: Map<Long, Int>, latitude: Double, longitude: Double): ZoneKillResult {
        val key = keyOf(latitude, longitude)
        val before = kills[key] ?: 0
        val after = (before + 1).coerceAtMost(CLEARED_AT)
        val next = LinkedHashMap(kills)
        next[key] = after
        if (next.size > MAX_ZONES) {
            val weakest = next.entries.filter { it.key != key }.minByOrNull { it.value }
            if (weakest != null) next.remove(weakest.key)
        }
        return ZoneKillResult(next, before < CLEARED_AT && after >= CLEARED_AT)
    }

    /** `clave:bajas;...` para el guardado. */
    fun encode(kills: Map<Long, Int>): String = kills.entries.joinToString(";") { "${it.key}:${it.value}" }

    /** Nunca lanza: lo ilegible se ignora (guardados viejos o editados a mano). */
    fun decode(text: String): Map<Long, Int> {
        if (text.isBlank()) return emptyMap()
        val out = LinkedHashMap<Long, Int>()
        for (part in text.split(';')) {
            val fields = part.split(':')
            if (fields.size != 2) continue
            val key = fields[0].toLongOrNull() ?: continue
            val count = fields[1].toIntOrNull() ?: continue
            if (count > 0 && out.size < MAX_ZONES) out[key] = count.coerceAtMost(CLEARED_AT)
        }
        return out
    }
}

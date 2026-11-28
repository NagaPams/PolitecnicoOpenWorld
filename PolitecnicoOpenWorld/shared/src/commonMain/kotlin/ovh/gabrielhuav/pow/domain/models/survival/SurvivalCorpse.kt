package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.hypot

// Cadáveres (t25, pieza 1): un zombi que muere a manos del jugador deja un cuerpo; pasado un rato apesta y atrae a otros
// zombis, y se quita quemándolo junto a una fogata encendida o enterrándolo (gasta un tablón). Solo memoria como las trampas.
// Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Qué se hace con un cadáver. Los valores nuevos van AL FINAL. */
enum class CorpseAction { NONE, BURN, BURY }

object CorpseRules {
    /** Cuerpos recordados a la vez (el más viejo desaparece). */
    const val MAX_CORPSES = 8
    /** Tras este tiempo (ms de reloj real) el cuerpo empieza a apestar. */
    const val STINK_AFTER_MS = 60_000L
    /** Cada cuánto suena la peste que atrae zombis (ms). */
    const val STINK_PERIOD_MS = 20_000L
    /** Tablones que cuesta enterrar un cuerpo. */
    const val BURY_BOARDS = 1
    /** XP de Condición física por quitar un cuerpo. */
    const val XP = 3

    fun stinks(nowMs: Long, bornMs: Long): Boolean = nowMs - bornMs >= STINK_AFTER_MS

    /** ¿Toca emitir la peste otra vez? */
    fun dueToStink(nowMs: Long, lastMs: Long): Boolean = nowMs - lastMs >= STINK_PERIOD_MS

    /** ¿El cuerpo en (lat, lon) está junto a la fogata en (fireLat, fireLon) para quemarlo? */
    fun nearFire(lat: Double, lon: Double, fireLat: Double, fireLon: Double): Boolean =
        hypot(lat - fireLat, lon - fireLon) <= CampfireRules.COOK_DEG

    /** Quemar si hay fogata encendida cerca; si no, enterrar si hay tablones; si no, nada. */
    fun action(fireLit: Boolean, boards: Int): CorpseAction = when {
        fireLit -> CorpseAction.BURN
        boards >= BURY_BOARDS -> CorpseAction.BURY
        else -> CorpseAction.NONE
    }
}

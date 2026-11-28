package ovh.gabrielhuav.pow.domain.models.survival

// Huerto (t87, paso 6): una semilla plantada en el mapa madura con el tiempo y da comida cruda (que se cocina en una estufa o
// fogata). Vive en memoria como las trampas y fogatas. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object GardenRules {
    /** Lo que tarda en madurar un huerto (ms de reloj real). */
    const val GROW_MS = 4L * 60L * 1000L
    /** Huertos plantados a la vez (el más viejo se pierde al plantar otro). */
    const val MAX_PLOTS = 4
    /** Comida cruda que da una cosecha. */
    const val YIELD_CRUDAS = 2

    fun isRipe(nowMs: Long, plantedAtMs: Long): Boolean = nowMs - plantedAtMs >= GROW_MS

    /** Segundos que faltan para madurar (0 si ya maduró). */
    fun secondsLeft(nowMs: Long, plantedAtMs: Long): Long = ((GROW_MS - (nowMs - plantedAtMs)).coerceAtLeast(0L) + 999L) / 1000L

    fun canPlant(pack: SurvivalPack): Boolean = pack.semillas > 0

    fun plant(pack: SurvivalPack): SurvivalPack = pack.copy(semillas = (pack.semillas - 1).coerceAtLeast(0))

    /** Mochila tras cosechar: hasta [YIELD_CRUDAS] de comida cruda y se guarda una semilla; null si no cabe nada. */
    fun harvest(pack: SurvivalPack): SurvivalPack? {
        var next = pack
        var gained = 0
        repeat(YIELD_CRUDAS) {
            if (SurvivalCrafting.canAddCruda(next)) {
                next = next.copy(cruda = next.cruda + 1)
                gained++
            }
        }
        if (gained == 0) return null
        if (SurvivalCrafting.canAddSemilla(next)) next = next.copy(semillas = next.semillas + 1)
        return next
    }
}

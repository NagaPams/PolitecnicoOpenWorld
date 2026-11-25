package ovh.gabrielhuav.pow.domain.models.survival

// Recetas con tiempo de crafteo (t16, modo supervivencia): el jugador se queda quieto mientras avanza una barra y
// los ingredientes se gastan SOLO al terminar. Un zombi cerca o una mordida interrumpen sin gastar nada. La
// carpintería acorta lo que se construye con tablones. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Los valores nuevos van AL FINAL. */
enum class Recipe { BOTIQUIN, PALO_CLAVOS, FERULA }

/** Crafteo en curso: lo muestra la barra de acción. */
data class CraftProgress(val recipe: Recipe, val startedMs: Long, val durationMs: Long)

/** Lo que las recetas leen y cambian. */
data class CraftInventory(val pack: SurvivalPack, val boards: Int, val arsenal: SurvivalArsenal)

object RecipeRules {
    /** Un zombi a esta distancia (m) o menos interrumpe el crafteo, y no deja empezarlo. */
    const val INTERRUPT_METERS = 10.0
    const val NAIL_BAT_BOARDS = 2
    private const val KIT_MS = 3000L
    private const val NAIL_BAT_MS = 4000L
    private const val SPLINT_MS = 3000L
    private const val FASTER_PER_LEVEL = 0.10
    private const val MIN_TIME_FACTOR = 0.5

    /** Duración de la receta; la carpintería acorta el palo con clavos (10 % por nivel, mínimo la mitad). */
    fun durationMs(recipe: Recipe, carpentryLevel: Int): Long = when (recipe) {
        Recipe.BOTIQUIN -> KIT_MS
        Recipe.FERULA -> SPLINT_MS
        Recipe.PALO_CLAVOS -> {
            val factor = (1.0 - FASTER_PER_LEVEL * carpentryLevel.coerceIn(0, SkillRules.MAX_LEVEL)).coerceAtLeast(MIN_TIME_FACTOR)
            (NAIL_BAT_MS * factor).toLong()
        }
    }

    fun canCraft(recipe: Recipe, inv: CraftInventory): Boolean = when (recipe) {
        Recipe.BOTIQUIN -> SurvivalCrafting.canCraftKit(inv.pack)
        Recipe.PALO_CLAVOS -> inv.boards >= NAIL_BAT_BOARDS
        Recipe.FERULA -> inv.boards >= 1 && inv.pack.vendas >= 1
    }

    /** Resultado de terminar la receta, o null si ya no se puede (cambió el inventario mientras tanto). */
    fun apply(recipe: Recipe, inv: CraftInventory): CraftInventory? {
        if (!canCraft(recipe, inv)) return null
        return when (recipe) {
            Recipe.BOTIQUIN -> inv.copy(pack = SurvivalCrafting.craftKit(inv.pack) ?: return null)
            Recipe.FERULA -> inv.copy(boards = inv.boards - 1, pack = inv.pack.copy(vendas = inv.pack.vendas - 1))
            Recipe.PALO_CLAVOS -> inv.copy(
                boards = inv.boards - NAIL_BAT_BOARDS,
                arsenal = WeaponRules.pickUp(inv.arsenal, WeaponKind.PALO_CLAVOS),
            )
        }
    }

    fun shouldInterrupt(nearestZombieMeters: Double): Boolean = nearestZombieMeters <= INTERRUPT_METERS

    fun isCrafting(nowMs: Long, untilMs: Long): Boolean = nowMs < untilMs
}

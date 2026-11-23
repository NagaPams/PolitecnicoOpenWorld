package ovh.gabrielhuav.pow.domain.models.survival

// Mochila de consumibles guardados y crafteo del botiquín (t09, modo supervivencia). Lo que no sirve en el
// momento (hambre llena, p. ej.) se guarda en vez de perderse; 2 refrescos + 1 pizza = 1 botiquín (+40 HP).
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

data class SurvivalPack(val pizza: Int = 0, val refresco: Int = 0, val botiquin: Int = 0)

object SurvivalCrafting {
    const val MAX_PER_ITEM = 5
    const val KIT_REFRESCOS = 2
    const val KIT_PIZZAS = 1
    const val KIT_HEAL = 40f

    /** Hambre o sed en este valor o menos: el botín se consume al recogerlo; si no, va a la mochila. */
    const val USE_NOW_THRESHOLD = 60.0

    /** ¿Vale la pena usar el consumible YA? (la tienda usa otra regla: cualquier cambio cuenta). */
    fun worthUsingNow(item: SurvivalItem, needs: SurvivalNeeds, infection: InfectionState): Boolean = when (item) {
        SurvivalItem.PIZZA -> needs.hunger <= USE_NOW_THRESHOLD
        SurvivalItem.REFRESCO -> needs.thirst <= USE_NOW_THRESHOLD
        SurvivalItem.SUERO -> infection.infected
        SurvivalItem.TABLONES -> false
    }

    fun canCraftKit(pack: SurvivalPack): Boolean =
        pack.refresco >= KIT_REFRESCOS && pack.pizza >= KIT_PIZZAS && pack.botiquin < MAX_PER_ITEM

    fun craftKit(pack: SurvivalPack): SurvivalPack? =
        if (canCraftKit(pack)) {
            SurvivalPack(pack.pizza - KIT_PIZZAS, pack.refresco - KIT_REFRESCOS, pack.botiquin + 1)
        } else null

    fun useKit(pack: SurvivalPack): SurvivalPack? =
        if (pack.botiquin > 0) pack.copy(botiquin = pack.botiquin - 1) else null

    /** Guarda un consumible; null si no se guarda (tipo sin lugar en la mochila o mochila llena de ese tipo). */
    fun store(pack: SurvivalPack, item: SurvivalItem): SurvivalPack? = when (item) {
        SurvivalItem.PIZZA -> if (pack.pizza < MAX_PER_ITEM) pack.copy(pizza = pack.pizza + 1) else null
        SurvivalItem.REFRESCO -> if (pack.refresco < MAX_PER_ITEM) pack.copy(refresco = pack.refresco + 1) else null
        else -> null
    }

    /** Valores de un guardado (viejo o editado a mano) dentro de rango. */
    fun sanitized(pack: SurvivalPack): SurvivalPack = SurvivalPack(
        pack.pizza.coerceIn(0, MAX_PER_ITEM), pack.refresco.coerceIn(0, MAX_PER_ITEM), pack.botiquin.coerceIn(0, MAX_PER_ITEM),
    )
}

package ovh.gabrielhuav.pow.domain.models.survival

// Mochila de consumibles guardados y crafteo del botiquín (t09, modo supervivencia). Lo que no sirve en el
// momento (hambre llena, p. ej.) se guarda en vez de perderse; 2 refrescos + 1 pizza = 1 botiquín (+40 HP).
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

data class SurvivalPack(
    val pizza: Int = 0,
    val refresco: Int = 0,
    val botiquin: Int = 0,
    val bidones: Int = 0,
    val vendas: Int = 0,
    /** Comida cruda y agua sucia (t16): hay que cocinarlas o hervirlas en una estufa antes de que sirvan. */
    val cruda: Int = 0,
    val sucia: Int = 0,
    /** Chapas de metal (t09): refuerzan una ventana ya tapiada con tablones. */
    val chapas: Int = 0,
    /** Llaves de coche (t80): abren un coche ajeno sin hacerle el puente. */
    val llaves: Int = 0,
)

object SurvivalCrafting {
    const val MAX_PER_ITEM = 5
    const val KIT_REFRESCOS = 2
    const val KIT_PIZZAS = 1
    const val KIT_HEAL = 40f

    // t61: peso de carga. Además del tope por tipo, la mochila entera no pasa de MAX_WEIGHT (un bidón pesa lo que tres vendas y media).
    const val MAX_WEIGHT = 16.0
    const val WEIGHT_PIZZA = 1.0
    const val WEIGHT_REFRESCO = 1.0
    const val WEIGHT_BOTIQUIN = 1.5
    const val WEIGHT_BIDON = 3.0
    const val WEIGHT_VENDA = 0.5
    const val WEIGHT_CRUDA = 1.0
    const val WEIGHT_SUCIA = 1.0
    const val WEIGHT_CHAPA = 1.5
    const val WEIGHT_LLAVE = 0.2

    fun weightOf(pack: SurvivalPack): Double =
        pack.pizza * WEIGHT_PIZZA + pack.refresco * WEIGHT_REFRESCO + pack.botiquin * WEIGHT_BOTIQUIN +
            pack.bidones * WEIGHT_BIDON + pack.vendas * WEIGHT_VENDA + pack.cruda * WEIGHT_CRUDA + pack.sucia * WEIGHT_SUCIA +
            pack.chapas * WEIGHT_CHAPA + pack.llaves * WEIGHT_LLAVE

    /** t67: hasta este peso (60 % del máximo) se camina normal; de ahí al máximo se frena hasta [MIN_SPEED_FACTOR]. */
    const val SLOW_FROM_WEIGHT = 9.6
    const val MIN_SPEED_FACTOR = 0.85

    fun speedFactor(weight: Double): Double {
        if (weight <= SLOW_FROM_WEIGHT) return 1.0
        val t = ((weight - SLOW_FROM_WEIGHT) / (MAX_WEIGHT - SLOW_FROM_WEIGHT)).coerceIn(0.0, 1.0)
        return 1.0 - t * (1.0 - MIN_SPEED_FACTOR)
    }

    private fun fits(pack: SurvivalPack, extra: Double): Boolean = weightOf(pack) + extra <= MAX_WEIGHT

    /** La mochila ya no admite ni un bidón (el objeto más pesado). */
    fun isHeavy(pack: SurvivalPack): Boolean = weightOf(pack) + WEIGHT_BIDON > MAX_WEIGHT

    /** t68: no se pudo añadir por el peso (y no porque el tipo ya esté en su tope). */
    fun vendaBlockedByWeight(pack: SurvivalPack): Boolean = pack.vendas < MAX_PER_ITEM && !fits(pack, WEIGHT_VENDA)

    fun bidonBlockedByWeight(pack: SurvivalPack): Boolean = pack.bidones < FuelRules.MAX_CANS && !fits(pack, WEIGHT_BIDON)

    fun canAddVenda(pack: SurvivalPack): Boolean = pack.vendas < MAX_PER_ITEM && fits(pack, WEIGHT_VENDA)

    fun canAddBidon(pack: SurvivalPack): Boolean = pack.bidones < FuelRules.MAX_CANS && fits(pack, WEIGHT_BIDON)

    fun canAddCruda(pack: SurvivalPack): Boolean = pack.cruda < MAX_PER_ITEM && fits(pack, WEIGHT_CRUDA)

    fun canAddSucia(pack: SurvivalPack): Boolean = pack.sucia < MAX_PER_ITEM && fits(pack, WEIGHT_SUCIA)

    fun canAddChapa(pack: SurvivalPack): Boolean = pack.chapas < MAX_PER_ITEM && fits(pack, WEIGHT_CHAPA)

    fun canAddLlave(pack: SurvivalPack): Boolean = pack.llaves < MAX_PER_ITEM && fits(pack, WEIGHT_LLAVE)

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
            pack.copy(pizza = pack.pizza - KIT_PIZZAS, refresco = pack.refresco - KIT_REFRESCOS, botiquin = pack.botiquin + 1)
        } else null

    fun useKit(pack: SurvivalPack): SurvivalPack? =
        if (pack.botiquin > 0) pack.copy(botiquin = pack.botiquin - 1) else null

    /** Guarda un consumible; null si no se guarda (tipo sin lugar en la mochila o mochila llena de ese tipo). */
    fun store(pack: SurvivalPack, item: SurvivalItem): SurvivalPack? = when (item) {
        SurvivalItem.PIZZA -> if (pack.pizza < MAX_PER_ITEM && fits(pack, WEIGHT_PIZZA)) pack.copy(pizza = pack.pizza + 1) else null
        SurvivalItem.REFRESCO -> if (pack.refresco < MAX_PER_ITEM && fits(pack, WEIGHT_REFRESCO)) pack.copy(refresco = pack.refresco + 1) else null
        else -> null
    }

    /**
     * t71: un guardado anterior al límite de peso puede traer más de [MAX_WEIGHT]. Se quita, de lo que sobra, el objeto
     * más ligero que baste para quedar dentro del límite; si ninguno basta solo, el más pesado, y se repite. Así un
     * exceso pequeño cuesta una venda y no un bidón.
     */
    fun fitToWeight(pack: SurvivalPack): SurvivalPack {
        var p = pack
        while (weightOf(p) > MAX_WEIGHT) {
            val excess = weightOf(p) - MAX_WEIGHT
            val options = ArrayList<Pair<Double, SurvivalPack>>(5)
            if (p.vendas > 0) options.add(WEIGHT_VENDA to p.copy(vendas = p.vendas - 1))
            if (p.pizza > 0) options.add(WEIGHT_PIZZA to p.copy(pizza = p.pizza - 1))
            if (p.refresco > 0) options.add(WEIGHT_REFRESCO to p.copy(refresco = p.refresco - 1))
            if (p.botiquin > 0) options.add(WEIGHT_BOTIQUIN to p.copy(botiquin = p.botiquin - 1))
            if (p.bidones > 0) options.add(WEIGHT_BIDON to p.copy(bidones = p.bidones - 1))
            if (p.cruda > 0) options.add(WEIGHT_CRUDA to p.copy(cruda = p.cruda - 1))
            if (p.sucia > 0) options.add(WEIGHT_SUCIA to p.copy(sucia = p.sucia - 1))
            if (options.isEmpty()) return p
            val enough = options.filter { it.first >= excess }.minByOrNull { it.first }
            p = (enough ?: options.maxByOrNull { it.first }!!).second
        }
        return p
    }
    /** Valores de un guardado (viejo o editado a mano) dentro de rango. */
    fun sanitized(pack: SurvivalPack): SurvivalPack = fitToWeight(SurvivalPack(
        pack.pizza.coerceIn(0, MAX_PER_ITEM), pack.refresco.coerceIn(0, MAX_PER_ITEM), pack.botiquin.coerceIn(0, MAX_PER_ITEM),
        pack.bidones.coerceIn(0, FuelRules.MAX_CANS), pack.vendas.coerceIn(0, MAX_PER_ITEM),
        pack.cruda.coerceIn(0, MAX_PER_ITEM), pack.sucia.coerceIn(0, MAX_PER_ITEM), pack.chapas.coerceIn(0, MAX_PER_ITEM),
        pack.llaves.coerceIn(0, MAX_PER_ITEM),
    ))
}

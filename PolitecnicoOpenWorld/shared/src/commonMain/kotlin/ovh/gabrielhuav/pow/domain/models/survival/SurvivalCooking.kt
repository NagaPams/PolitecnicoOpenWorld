package ovh.gabrielhuav.pow.domain.models.survival

// Cocinar (t16): en la estufa de un interior se calienta una comida de la mochila y alimenta más que comerla fría.
// Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Al final de la lista de valores: no insertar en medio. */
enum class CookResult { COOKED, NO_FOOD, NOT_HUNGRY, COOKED_RAW, BOILED, NOT_THIRSTY }

object CookRules {
    /** Lo que repone una comida caliente: la normal (40) más este extra. */
    const val HOT_MEAL_RESTORE = 60.0
    /** Con el hambre (0-100, más es mejor) por encima de esto no se cocina. */
    const val NOT_HUNGRY_AT = 90.0

    /** Lo que repone la comida cruda cocinada y el agua sucia hervida. */
    const val RAW_COOKED_RESTORE = 50.0
    const val BOILED_RESTORE = 40.0

    /** Qué se hace en la estufa: primero la comida cruda, luego la lista; el agua sucia se hierve si hay sed. */
    fun check(pack: SurvivalPack, needs: SurvivalNeeds): CookResult {
        val hungry = needs.hunger < NOT_HUNGRY_AT
        val thirsty = needs.thirst < NOT_HUNGRY_AT
        return when {
            hungry && pack.cruda > 0 -> CookResult.COOKED_RAW
            hungry && pack.pizza > 0 -> CookResult.COOKED
            thirsty && pack.sucia > 0 -> CookResult.BOILED
            pack.cruda > 0 || pack.pizza > 0 -> CookResult.NOT_HUNGRY
            pack.sucia > 0 -> CookResult.NOT_THIRSTY
            else -> CookResult.NO_FOOD
        }
    }

    /** Mochila sin una comida y necesidades tras comerla caliente (el hambre sube hasta 100). */
    fun cook(pack: SurvivalPack, needs: SurvivalNeeds, result: CookResult = check(pack, needs)): Pair<SurvivalPack, SurvivalNeeds> = when (result) {
        CookResult.COOKED -> pack.copy(pizza = (pack.pizza - 1).coerceAtLeast(0)) to needs.copy(hunger = (needs.hunger + HOT_MEAL_RESTORE).coerceAtMost(100.0))
        CookResult.COOKED_RAW -> pack.copy(cruda = (pack.cruda - 1).coerceAtLeast(0)) to needs.copy(hunger = (needs.hunger + RAW_COOKED_RESTORE).coerceAtMost(100.0))
        CookResult.BOILED -> pack.copy(sucia = (pack.sucia - 1).coerceAtLeast(0)) to needs.copy(thirst = (needs.thirst + BOILED_RESTORE).coerceAtMost(100.0))
        else -> pack to needs
    }
}

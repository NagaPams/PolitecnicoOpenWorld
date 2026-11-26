package ovh.gabrielhuav.pow.domain.models.survival

// Cocinar (t16): en la estufa de un interior se calienta una comida de la mochila y alimenta más que comerla fría.
// Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

enum class CookResult { COOKED, NO_FOOD, NOT_HUNGRY }

object CookRules {
    /** Lo que repone una comida caliente: la normal (40) más este extra. */
    const val HOT_MEAL_RESTORE = 60.0
    /** Con el hambre (0-100, más es mejor) por encima de esto no se cocina. */
    const val NOT_HUNGRY_AT = 90.0

    fun check(pack: SurvivalPack, needs: SurvivalNeeds): CookResult = when {
        pack.pizza <= 0 -> CookResult.NO_FOOD
        needs.hunger >= NOT_HUNGRY_AT -> CookResult.NOT_HUNGRY
        else -> CookResult.COOKED
    }

    /** Mochila sin una comida y necesidades tras comerla caliente (el hambre sube hasta 100). */
    fun cook(pack: SurvivalPack, needs: SurvivalNeeds): Pair<SurvivalPack, SurvivalNeeds> =
        pack.copy(pizza = (pack.pizza - 1).coerceAtLeast(0)) to needs.copy(hunger = (needs.hunger + HOT_MEAL_RESTORE).coerceAtMost(100.0))
}

package ovh.gabrielhuav.pow.domain.models.survival

// Ropa que se gasta (t25, pieza 2): el abrigo se desgasta a la intemperie con lluvia y con el frío de la noche; a 100 se rompe
// y deja de abrigar. Se remienda gastando una venda (tela). Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object ClothingRules {
    const val MAX = 100.0
    /** Desgaste por segundo a la intemperie: con lluvia (unos 8 min de lluvia lo rompen) y con el frío de la noche. */
    const val WEAR_RAIN_PER_SECOND = 0.2
    const val WEAR_COLD_PER_SECOND = 0.05
    /** Cuánto desgaste quita un remiendo y cuántas vendas cuesta. */
    const val MEND_AMOUNT = 40.0
    const val MEND_BANDAGES = 1

    /** Desgaste tras [seconds]: solo sube si el abrigo está puesto y expuesto (lluvia pesa más que el frío nocturno). */
    fun wearStep(wear: Double, rainExposed: Boolean, coldExposed: Boolean, seconds: Double): Double {
        val rate = when {
            rainExposed -> WEAR_RAIN_PER_SECOND
            coldExposed -> WEAR_COLD_PER_SECOND
            else -> 0.0
        }
        return (wear + rate * seconds).coerceIn(0.0, MAX)
    }

    fun isWornOut(wear: Double): Boolean = wear >= MAX

    fun canMend(coat: Boolean, wear: Double, bandages: Int): Boolean = coat && wear > 0.0 && bandages >= MEND_BANDAGES

    fun mend(wear: Double): Double = (wear - MEND_AMOUNT).coerceAtLeast(0.0)

    fun sanitized(wear: Double): Double = if (wear.isNaN()) 0.0 else wear.coerceIn(0.0, MAX)
}

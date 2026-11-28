package ovh.gabrielhuav.pow.domain.models.survival

// Agua y pesca (t25, segunda tanda): el mapa no trae agua como dato, así que hay charcos y estanques sueltos como botín (una
// parte de los puntos de comida de la cuadrícula, `LootKind.AGUA`). Sin caña se junta agua sucia (que se hierve en una
// fogata); con un palo o bate en la mano se pesca y a veces sale comida cruda. Reglas puras.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object PondRules {
    /** Probabilidad de que un intento de pesca saque algo. */
    const val CATCH_CHANCE = 0.4
    /** Con qué se pesca: un palo o un bate hacen de caña. */
    fun canFish(weapon: WeaponKind): Boolean = when (weapon) {
        WeaponKind.PALO, WeaponKind.BATE, WeaponKind.PALO_CLAVOS -> true
        WeaponKind.PUNOS, WeaponKind.CUCHILLO, WeaponKind.PISTOLA, WeaponKind.ESCOPETA -> false
    }

    fun caught(roll: Double): Boolean = roll < CATCH_CHANCE
}

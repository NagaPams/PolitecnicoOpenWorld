package ovh.gabrielhuav.pow.domain.models.survival

// Madera (t25, pieza 4): el mapa no trae árboles como dato, así que hay ramas y troncos sueltos como botín (un tercio de los puntos
// de tablones de la cuadrícula). Con un arma cortante o un palo salen más tablones. Reglas puras.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object WoodRules {
    /** XP de Carpintería por juntar ramas. */
    const val XP = 2

    /** Tablones que da un punto de ramas según el arma que se lleva en la mano. */
    fun boardsFor(weapon: WeaponKind): Int = when (weapon) {
        WeaponKind.PALO, WeaponKind.BATE, WeaponKind.CUCHILLO, WeaponKind.PALO_CLAVOS -> 2
        WeaponKind.PUNOS, WeaponKind.PISTOLA, WeaponKind.ESCOPETA -> 1
    }
}

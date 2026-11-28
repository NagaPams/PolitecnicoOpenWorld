package ovh.gabrielhuav.pow.domain.models.survival

// Desarmar (t25, segunda tanda): un coche estacionado cercano se desarma con un palo o un bate y da tablones y una refacción,
// con ruido. El coche desaparece. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object DismantleRules {
    /** Tablones que da desarmar un coche. */
    const val BOARDS = 2
    /** Refacciones que da (si caben en la mochila). */
    const val PARTS = 1
    /** XP de Carpintería. */
    const val XP = 3
    /** Distancia máxima al coche, en grados (~7 m). */
    const val NEAR_DEG = 0.00007

    /** Herramienta: palo, bate o palo con clavos. */
    fun hasTool(weapon: WeaponKind): Boolean = when (weapon) {
        WeaponKind.PALO, WeaponKind.BATE, WeaponKind.PALO_CLAVOS -> true
        WeaponKind.PUNOS, WeaponKind.CUCHILLO, WeaponKind.PISTOLA, WeaponKind.ESCOPETA -> false
    }

    fun inReach(distanceDeg: Double): Boolean = distanceDeg <= NEAR_DEG
}

package ovh.gabrielhuav.pow.domain.models.survival

// Sangrado por mordida (t10): quita vida poco a poco hasta vendarse; nunca baja del piso de vida del modo.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object BleedingRules {
    const val DAMAGE_PER_SECOND = 1.0
    const val BITE_PROBABILITY = 0.5

    fun bleedsOnBite(roll: Double): Boolean = roll < BITE_PROBABILITY

    /** Vida tras [seconds] de sangrado (puro; el llamador decide si sangra). */
    fun healthAfter(health: Double, seconds: Double): Double {
        if (!seconds.isFinite() || seconds <= 0.0 || health <= SurvivalRules.HEALTH_FLOOR) return health
        return (health - seconds * DAMAGE_PER_SECOND).coerceAtLeast(SurvivalRules.HEALTH_FLOOR)
    }
}

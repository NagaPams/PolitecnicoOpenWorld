package ovh.gabrielhuav.pow.domain.models.survival

// Mapa leíble (t25, segunda tanda): resume los edificios cercanos que aún no se registraron, por tipo. Reglas puras.
// Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object MapReadRules {
    /** Radio de lectura en grados (~280 m). */
    const val RADIUS_DEG = 0.0025

    fun within(lat: Double, lon: Double, buildingLat: Double, buildingLon: Double): Boolean =
        kotlin.math.abs(lat - buildingLat) <= RADIUS_DEG && kotlin.math.abs(lon - buildingLon) <= RADIUS_DEG

    /** Cuántos hay de cada tipo, en el orden del enum (solo los que tienen al menos uno). */
    fun counts(profiles: List<SearchProfile>): List<Pair<SearchProfile, Int>> =
        SearchProfile.entries.mapNotNull { p -> profiles.count { it == p }.takeIf { it > 0 }?.let { p to it } }
}

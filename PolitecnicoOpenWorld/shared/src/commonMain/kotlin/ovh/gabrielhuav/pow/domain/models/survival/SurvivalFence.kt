package ovh.gabrielhuav.pow.domain.models.survival

import kotlin.math.hypot

// Vallas que arma el jugador (t25, pieza 5): el mapa no trae vallas, así que una valla es un obstáculo redondo que se arma con
// tablones, solo bloquea al jugador a pie (no cambia la colisión del campus ni de NPC o coches) y se trepa con X con riesgo
// de cortarse (como la ventana rota). Solo memoria. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object FenceRules {
    /** Tablones que cuesta armar una valla. */
    const val BOARD_COST = 2
    /** Vallas a la vez (la más vieja se pierde al armar otra). */
    const val MAX_FENCES = 4
    /** Radio del obstáculo en grados de latitud (~2,8 m). */
    const val RADIUS_DEG = 0.000025
    /** Al trepar se cae un poco más allá del borde, para no quedar pegado. */
    private const val EXIT_MARGIN = 1.25
    /** Escala de la longitud a esta latitud (coseno de ~19,5°), para que el obstáculo sea redondo y no ovalado. */
    private const val LON_SCALE = 0.943

    fun canBuild(boards: Int): Boolean = boards >= BOARD_COST

    private fun distance(lat: Double, lon: Double, fenceLat: Double, fenceLon: Double): Double =
        hypot(lat - fenceLat, (lon - fenceLon) * LON_SCALE)

    /**
     * ¿La valla en ([fenceLat], [fenceLon]) impide pasar de [from] a [to]? Solo bloquea entrar: si el jugador ya está dentro
     * (armó la valla donde estaba), puede salir.
     */
    fun blocks(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double, fenceLat: Double, fenceLon: Double): Boolean {
        val after = distance(toLat, toLon, fenceLat, fenceLon)
        return after < RADIUS_DEG && after < distance(fromLat, fromLon, fenceLat, fenceLon)
    }

    /** Punto al otro lado de la valla al trepar desde ([playerLat], [playerLon]): opuesto respecto del centro. */
    fun climbExit(playerLat: Double, playerLon: Double, fenceLat: Double, fenceLon: Double): Pair<Double, Double> {
        var dLat = playerLat - fenceLat
        var dLon = (playerLon - fenceLon) * LON_SCALE
        var norm = hypot(dLat, dLon)
        if (norm < 1e-9) {
            dLat = 1.0
            dLon = 0.0
            norm = 1.0
        }
        val reach = RADIUS_DEG * EXIT_MARGIN
        return Pair(fenceLat - dLat / norm * reach, fenceLon - dLon / norm * reach / LON_SCALE)
    }

    /** ¿Está el jugador lo bastante cerca para trepar? (borde de la valla más un pequeño margen). */
    fun canClimb(playerLat: Double, playerLon: Double, fenceLat: Double, fenceLon: Double): Boolean =
        distance(playerLat, playerLon, fenceLat, fenceLon) <= RADIUS_DEG * 2.0
}

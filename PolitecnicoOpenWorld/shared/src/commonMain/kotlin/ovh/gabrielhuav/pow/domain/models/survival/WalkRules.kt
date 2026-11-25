package ovh.gabrielhuav.pow.domain.models.survival

// Tamaño del paso del jugador a pie (t72): antes vivía suelto en WorldMapSurvival.kt. Grados por tick.

object WalkRules {
    const val WALK_STEP = 0.000003
    const val RUN_STEP = 0.000006

    /** [load] viene de [SurvivalCrafting.speedFactor] y [grab] de [GrabRules.speedFactor]; ambos entre 0 y 1. */
    fun stepSize(running: Boolean, load: Double, grab: Double): Double =
        (if (running) RUN_STEP else WALK_STEP) * load.coerceIn(0.0, 1.0) * grab.coerceIn(0.0, 1.0)
}
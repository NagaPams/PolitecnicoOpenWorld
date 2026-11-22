package ovh.gabrielhuav.pow.domain.models.map

// Paso puro de velocidad y rumbo del coche del jugador (antes inline en WorldMapViewModel.startGameLoop, t37).
// No toca calles, NPC ni colisiones: eso sigue en el game loop. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Entradas del conductor y restricciones de la escena para un tick. */
data class DriveInput(
    val gas: Boolean,
    val brake: Boolean,
    val steerLeft: Boolean,
    val steerRight: Boolean,
    /** Prankedy sube al coche: no avanza ni frena hasta que suba. */
    val holdForBoarding: Boolean = false,
    /** Cerca de la ESCOM en la escolta: solo reversa. */
    val reverseOnly: Boolean = false,
)

data class DriveState(val speed: Double, val rotationDegrees: Float)

object VehicleDriving {
    private const val STEER_DEGREES_PER_TICK = 2f
    private const val GAS_FALLOFF = 0.75
    private const val REVERSE_FRACTION = 2.0

    fun step(
        state: DriveState,
        input: DriveInput,
        maxSpeed: Double,
        acceleration: Double,
        brakingFriction: Double,
    ): DriveState {
        var speed = if (input.holdForBoarding) 0.0 else state.speed
        var rotation = state.rotationDegrees
        if (input.steerLeft || input.steerRight) {
            val dir = if (speed >= 0) 1f else -1f
            if (input.steerLeft) rotation -= STEER_DEGREES_PER_TICK * dir
            if (input.steerRight) rotation += STEER_DEGREES_PER_TICK * dir
        }
        if (input.gas && !input.holdForBoarding && !input.reverseOnly) {
            val ratio = (speed / maxSpeed).coerceIn(0.0, 1.0)
            // Cuesta más llegar al 100 %.
            speed = (speed + acceleration * (1.0 - ratio * GAS_FALLOFF)).coerceAtMost(maxSpeed)
        } else if (input.brake && !input.holdForBoarding) {
            speed -= brakingFriction
            if (speed < -maxSpeed / REVERSE_FRACTION) speed = -maxSpeed / REVERSE_FRACTION
        } else {
            if (speed > 0) speed = (speed - acceleration / 2).coerceAtLeast(0.0)
            if (speed < 0) speed = (speed + acceleration / 2).coerceAtMost(0.0)
        }
        if (input.reverseOnly && speed > 0.0) speed = 0.0
        return DriveState(speed, rotation)
    }
}

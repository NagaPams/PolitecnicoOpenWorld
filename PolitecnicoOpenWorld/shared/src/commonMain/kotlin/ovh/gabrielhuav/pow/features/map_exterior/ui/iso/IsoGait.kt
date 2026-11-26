package ovh.gabrielhuav.pow.features.map_exterior.ui.iso

// Ritmo de la animación de los actores isométricos según su velocidad real (antes: 130 ms fijos para todos, sin correr
// y con la dirección saltando por micro-empujones). Reglas puras; ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

internal object IsoGait {
    /** Metros que avanza un actor por cada cuadro del ciclo de 4 pasos (idle no cuenta). */
    const val FRAME_METERS = 0.3f
    const val MIN_STEP_MS = 80f
    const val MAX_STEP_MS = 280f
    /** A esta velocidad (m/s) o más se usan los cuadros de correr. */
    const val RUN_MPS = 3.0f
    /** Un movimiento menor a esto (m) mueve el cuerpo pero no cambia la dirección ni la velocidad (anti-jitter). */
    const val HEADING_MIN_METERS = 0.12

    /** Milisegundos por cuadro del ciclo para [speedMps]: más rápido, pasos más cortos en el tiempo. */
    fun stepMs(speedMps: Float): Float = (FRAME_METERS / speedMps.coerceAtLeast(0.01f) * 1000f).coerceIn(MIN_STEP_MS, MAX_STEP_MS)

    fun isRunning(speedMps: Float): Boolean = speedMps >= RUN_MPS

    /** Suaviza la velocidad medida para que un empujón suelto no dispare el correr. */
    fun smooth(previous: Float, measured: Float): Float = if (previous <= 0f) measured else previous * 0.5f + measured * 0.5f
}

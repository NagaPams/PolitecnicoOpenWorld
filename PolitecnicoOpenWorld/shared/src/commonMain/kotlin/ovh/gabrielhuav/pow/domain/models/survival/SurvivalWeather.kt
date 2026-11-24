package ovh.gabrielhuav.pow.domain.models.survival

// Clima y días sobrevividos (t13, modo supervivencia). El clima es DETERMINISTA: sale del día y la hora del reloj de
// juego (1 min real = 1 h de juego), así todos los jugadores ven lo mismo y no se guarda. Ver 04_MAP_EXTERIOR.md.

/** Los valores nuevos van AL FINAL. */
enum class Weather { CLEAR, RAIN, FOG }

object WeatherRules {
    /** El día se divide en bloques de esta duración (h); el clima cambia solo entre bloques. */
    private const val BLOCK_HOURS = 6.0
    private const val BLOCKS_PER_DAY = 4
    private const val RAIN_BELOW = 25
    private const val FOG_BELOW = 35
    private const val ROLL_RANGE = 100
    /** La lluvia tapa el ruido: todos los radios valen el 70 %. */
    const val RAIN_NOISE_FACTOR = 0.7
    /** Con lluvia se junta agua: la sed baja más lento. */
    const val RAIN_THIRST_FACTOR = 0.6

    /** [day] es el número de día de juego del reloj y [hour] la hora continua (0..24). */
    fun at(day: Long, hour: Double): Weather {
        val block = (hour / BLOCK_HOURS).toInt().coerceIn(0, BLOCKS_PER_DAY - 1)
        val roll = hash(day, block) % ROLL_RANGE
        return when {
            roll < RAIN_BELOW -> Weather.RAIN
            roll < FOG_BELOW -> Weather.FOG
            else -> Weather.CLEAR
        }
    }

    fun noiseFactor(weather: Weather): Double = if (weather == Weather.RAIN) RAIN_NOISE_FACTOR else 1.0

    fun thirstFactor(weather: Weather): Double = if (weather == Weather.RAIN) RAIN_THIRST_FACTOR else 1.0

    /** Mezcla estable entre plataformas (no usa `hashCode`), siempre ≥ 0. */
    private fun hash(day: Long, block: Int): Int {
        var h = (day xor (day ushr 32)).toInt() * 73856093 xor block * 19349663
        h = h xor (h ushr 15)
        h *= 0x2c1b3c6d
        h = h xor (h ushr 12)
        h *= 0x297a2d39
        h = h xor (h ushr 15)
        return h and Int.MAX_VALUE
    }
}

object DayRules {
    /** Un día de juego dura 24 min reales (el mismo ciclo del reloj del mundo). */
    const val DAY_MS = 24 * 60 * 1000L

    /** Día en que va el jugador: empieza en 1 y cuenta solo el tiempo de supervivencia activa. */
    fun daysSurvived(activeMs: Long): Int = (activeMs.coerceAtLeast(0L) / DAY_MS).coerceAtMost(Int.MAX_VALUE - 1L).toInt() + 1
}

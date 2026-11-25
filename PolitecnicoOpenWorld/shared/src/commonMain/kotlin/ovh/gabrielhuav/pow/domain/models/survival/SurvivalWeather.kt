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

/** Apagón (t14): pasado cierto día de supervivencia se va la luz y la tienda del campus deja de vender. */
object BlackoutRules {
    /** t47: con el día de 48 min el día 4 llegaba a las 2 h 24 min reales (antes 1 h 12); el día 3 llega a la 1 h 36 min. */
    const val FROM_DAY = 3
    /** Sin alumbrado las noches se ven más oscuras: el velo nocturno se multiplica por esto. */
    const val NIGHT_VEIL_FACTOR = 1.5f
    const val MAX_VEIL = 0.6f

    fun active(day: Int): Boolean = day >= FROM_DAY

    /** Alpha del velo nocturno con o sin apagón; el día (alpha 0) no se oscurece. */
    fun veilAlpha(nightAlpha: Float, day: Int): Float =
        if (active(day)) (nightAlpha * NIGHT_VEIL_FACTOR).coerceAtMost(MAX_VEIL) else nightAlpha
}

object DayRules {
    /** Un día de juego dura 48 min reales (el mismo ciclo del reloj del mundo; t34: antes 24, una hora por minuto). */
    const val DAY_MS = 48 * 60 * 1000L

    /** Día en que va el jugador: empieza en 1 y cuenta solo el tiempo de supervivencia activa. */
    fun daysSurvived(activeMs: Long): Int = (activeMs.coerceAtLeast(0L) / DAY_MS).coerceAtMost(Int.MAX_VALUE - 1L).toInt() + 1
}

package ovh.gabrielhuav.pow.domain.models.survival

// Moodles (t11): iconos de estado con gravedad 1..4 al estilo Project Zomboid. Solo reglas puras; la columna de
// iconos vive en la UI de :app. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Al final de la lista de valores: no insertar en medio (el orden podría usarse al ordenar los iconos). */
enum class MoodleKind { HAMBRE, SED, CANSANCIO, SIN_ALIENTO, SANGRADO, FRACTURA, INFECCION, CARGA, FRIO, HERIDO }

data class Moodle(val kind: MoodleKind, val level: Int)

object MoodleRules {
    const val MAX_LEVEL = 4

    /** Gravedad de una necesidad (0-100, más alto = mejor): 0 sin molestia, 4 crítico. */
    fun needLevel(value: Double): Int = when {
        value >= 60.0 -> 0
        value >= 40.0 -> 1
        value >= 25.0 -> 2
        value >= 12.0 -> 3
        else -> 4
    }

    /** Gravedad del aliento (0-100). */
    fun breathLevel(stamina: Double): Int = when {
        stamina >= 70.0 -> 0
        stamina >= 40.0 -> 1
        stamina >= 25.0 -> 2
        stamina >= 10.0 -> 3
        else -> 4
    }

    /** Gravedad de la vida (0-100): por encima de 60 no molesta. */
    fun healthLevel(health: Double): Int = when {
        health >= 60.0 -> 0
        health >= 40.0 -> 1
        health >= 25.0 -> 2
        health >= 12.0 -> 3
        else -> 4
    }

    /** Gravedad de la carga según el peso de la mochila (máximo [SurvivalCrafting.MAX_WEIGHT]). */
    fun loadLevel(weight: Double): Int {
        val max = SurvivalCrafting.MAX_WEIGHT
        return when {
            weight >= max -> 4
            weight >= max * 0.9 -> 3
            weight >= max * 0.75 -> 2
            weight >= max * 0.55 -> 1
            else -> 0
        }
    }

    /** Moodles activos (solo los de gravedad ≥ 1), de más a menos grave. */
    fun active(
        needs: SurvivalNeeds, stamina: Double, bleeding: Boolean, fractured: Boolean,
        infection: InfectionState, weight: Double, cold: Int = 0, health: Double = 100.0,
    ): List<Moodle> {
        val all = listOf(
            Moodle(MoodleKind.HAMBRE, needLevel(needs.hunger)),
            Moodle(MoodleKind.SED, needLevel(needs.thirst)),
            Moodle(MoodleKind.CANSANCIO, needLevel(needs.energy)),
            Moodle(MoodleKind.SIN_ALIENTO, breathLevel(stamina)),
            Moodle(MoodleKind.SANGRADO, if (bleeding) 3 else 0),
            Moodle(MoodleKind.FRACTURA, if (fractured) 2 else 0),
            Moodle(MoodleKind.INFECCION, if (!infection.infected) 0 else if (infection.symptomatic) 3 else 1),
            Moodle(MoodleKind.CARGA, loadLevel(weight)),
            Moodle(MoodleKind.HERIDO, healthLevel(health)),
            Moodle(MoodleKind.FRIO, cold.coerceIn(0, TemperatureRules.MAX_COLD) * 2 - (if (cold > 0) 1 else 0)),
        )
        return all.filter { it.level > 0 }.sortedByDescending { it.level }
    }
}

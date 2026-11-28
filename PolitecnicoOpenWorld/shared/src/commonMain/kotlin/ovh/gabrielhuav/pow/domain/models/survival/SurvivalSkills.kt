package ovh.gabrielhuav.pow.domain.models.survival

// Habilidades y oficios (t15, modo supervivencia): tres habilidades que suben con el uso (XP) y un oficio que se
// elige una vez y da un punto de partida. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Los valores nuevos van AL FINAL (viajan por nombre en el guardado). */
enum class Skill { COMBATE, PUNTERIA, CARPINTERIA, FITNESS }

enum class Occupation { NINGUNO, BOXEADOR, SOLDADO, CARPINTERO }

/** XP acumulada por habilidad. */
data class SurvivalSkills(val combate: Int = 0, val punteria: Int = 0, val carpinteria: Int = 0, val fitness: Int = 0) {
    fun xpOf(skill: Skill): Int = when (skill) {
        Skill.COMBATE -> combate
        Skill.PUNTERIA -> punteria
        Skill.CARPINTERIA -> carpinteria
        Skill.FITNESS -> fitness
    }
}

/** Resultado de ganar XP: [levelUp] trae el nivel nuevo si se subió de nivel, o null. */
data class SkillGain(val skills: SurvivalSkills, val levelUp: Int?)

object SkillRules {
    const val MAX_LEVEL = 5
    /** XP total para llegar al nivel 1..5. */
    private val THRESHOLDS = intArrayOf(10, 25, 50, 90, 150)
    private const val MAX_XP = 100_000
    const val DAMAGE_PER_LEVEL = 0.08
    const val BARRICADE_PER_LEVEL = 0.12
    const val REPAIR_PER_LEVEL = 3.0
    /** XP con la que arranca la habilidad de un oficio (nivel 2). */
    const val OCCUPATION_XP = 25

    fun level(xp: Int): Int {
        var level = 0
        for (t in THRESHOLDS) if (xp >= t) level++
        return level
    }

    fun levelOf(skills: SurvivalSkills, skill: Skill): Int = level(skills.xpOf(skill))

    fun gain(skills: SurvivalSkills, skill: Skill, amount: Int): SkillGain {
        if (amount <= 0) return SkillGain(skills, null)
        val before = levelOf(skills, skill)
        val next = (skills.xpOf(skill) + amount).coerceAtMost(MAX_XP)
        val updated = when (skill) {
            Skill.COMBATE -> skills.copy(combate = next)
            Skill.PUNTERIA -> skills.copy(punteria = next)
            Skill.CARPINTERIA -> skills.copy(carpinteria = next)
            Skill.FITNESS -> skills.copy(fitness = next)
        }
        val after = level(next)
        return SkillGain(updated, if (after > before) after else null)
    }

    fun damageFactor(level: Int): Double = 1.0 + DAMAGE_PER_LEVEL * level.coerceIn(0, MAX_LEVEL)

    fun barricadeFactor(level: Int): Double = 1.0 + BARRICADE_PER_LEVEL * level.coerceIn(0, MAX_LEVEL)

    /** Puntos extra de salud del coche por tablón al repararlo. */
    fun repairBonus(level: Int): Double = REPAIR_PER_LEVEL * level.coerceIn(0, MAX_LEVEL)

    /** Habilidad que sube con un oficio. */
    fun skillOf(occupation: Occupation): Skill? = when (occupation) {
        Occupation.NINGUNO -> null
        Occupation.BOXEADOR -> Skill.COMBATE
        Occupation.SOLDADO -> Skill.PUNTERIA
        Occupation.CARPINTERO -> Skill.CARPINTERIA
    }

    /** Elige oficio: solo si aún no hay uno. Devuelve las habilidades con el punto de partida, o null si no corresponde. */
    fun choose(current: Occupation, wanted: Occupation, skills: SurvivalSkills): SurvivalSkills? {
        if (current != Occupation.NINGUNO || wanted == Occupation.NINGUNO) return null
        val skill = skillOf(wanted) ?: return null
        val missing = OCCUPATION_XP - skills.xpOf(skill)
        return if (missing > 0) gain(skills, skill, missing).skills else skills
    }

    fun encode(skills: SurvivalSkills): String = "${skills.combate},${skills.punteria},${skills.carpinteria},${skills.fitness}"

    /** Nunca lanza: lo ilegible vuelve a cero. */
    fun decode(text: String): SurvivalSkills {
        val parts = text.split(',').map { it.trim().toIntOrNull() }
        // Un guardado anterior a fitness trae 3 valores; el cuarto vale 0.
        if ((parts.size != 3 && parts.size != 4) || parts.any { it == null }) return SurvivalSkills()
        return SurvivalSkills(
            parts[0]!!.coerceIn(0, MAX_XP), parts[1]!!.coerceIn(0, MAX_XP), parts[2]!!.coerceIn(0, MAX_XP),
            (parts.getOrNull(3) ?: 0).coerceIn(0, MAX_XP),
        )
    }

    fun occupationOf(name: String): Occupation = Occupation.entries.firstOrNull { it.name == name } ?: Occupation.NINGUNO
}

package ovh.gabrielhuav.pow.domain.models.survival

// Descansar sin cama, entrenar (fitness) y leer libros de habilidad (t87, paso 1). Tres acciones cortas, a pie y sin zombis
// cerca, que se apoyan en las necesidades, el aliento y las habilidades que ya existen. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Las acciones con barra de espera que no son dormir ni craftear. */
enum class TrainKind { REST, FITNESS, BOOK }

/** Por qué una acción no puede empezar. */
enum class TrainCheck { OK, ZOMBIES_NEAR, TOO_RESTED, TOO_TIRED, NO_BOOK, HUNGRY_OR_THIRSTY }

object TrainRules {
    /** Un zombi más cerca que esto (m) impide empezar y, durante la acción, la corta. */
    const val ZOMBIE_METERS = SleepRules.WAKE_METERS

    const val REST_MS = 3000L
    const val FITNESS_MS = 4000L
    const val BOOK_MS = 4000L

    /** Descansar sin cama da menos energía que dormir ([SleepRules]) pero no cuesta casi nada. */
    const val REST_ENERGY = 12.0
    private const val REST_HUNGER = 1.0
    private const val REST_THIRST = 1.5

    /** Entrenar gasta aliento, hambre y sed, y da XP de fitness. */
    const val FITNESS_STAMINA = 40.0
    const val FITNESS_XP = 4
    private const val FITNESS_HUNGER = 2.0
    private const val FITNESS_THIRST = 3.0
    /** No se entrena con hambre o sed por debajo de esto. */
    const val FITNESS_MIN_NEED = 20.0

    /** Cada nivel de fitness reduce el gasto de aliento al correr en este porcentaje (nivel 5 = 30 %). */
    const val FITNESS_DRAIN_PER_LEVEL = 0.06

    /** Leer un libro da esta XP a una habilidad. */
    const val BOOK_XP = 15

    fun durationMs(kind: TrainKind): Long = when (kind) {
        TrainKind.REST -> REST_MS
        TrainKind.FITNESS -> FITNESS_MS
        TrainKind.BOOK -> BOOK_MS
    }

    fun isBusy(nowMs: Long, untilMs: Long): Boolean = nowMs < untilMs

    fun until(nowMs: Long, kind: TrainKind): Long = nowMs + durationMs(kind)

    /** [nearestZombieMeters] es `Double.MAX_VALUE` si no hay zombis. */
    fun check(
        kind: TrainKind,
        needs: SurvivalNeeds,
        stamina: StaminaState,
        pack: SurvivalPack,
        nearestZombieMeters: Double,
    ): TrainCheck {
        if (nearestZombieMeters <= ZOMBIE_METERS) return TrainCheck.ZOMBIES_NEAR
        return when (kind) {
            TrainKind.REST -> if (needs.energy >= SleepRules.TOO_RESTED_AT) TrainCheck.TOO_RESTED else TrainCheck.OK
            TrainKind.FITNESS -> when {
                stamina.exhausted || stamina.value < FITNESS_STAMINA -> TrainCheck.TOO_TIRED
                needs.hunger < FITNESS_MIN_NEED || needs.thirst < FITNESS_MIN_NEED -> TrainCheck.HUNGRY_OR_THIRSTY
                else -> TrainCheck.OK
            }
            TrainKind.BOOK -> if (pack.libros > 0) TrainCheck.OK else TrainCheck.NO_BOOK
        }
    }

    /** Necesidades al terminar [kind]; hambre y sed nunca bajan de 0 ni energía pasa de 100. */
    fun applyNeeds(kind: TrainKind, needs: SurvivalNeeds): SurvivalNeeds = when (kind) {
        TrainKind.REST -> needs.copy(
            hunger = (needs.hunger - REST_HUNGER).coerceAtLeast(0.0),
            thirst = (needs.thirst - REST_THIRST).coerceAtLeast(0.0),
            energy = (needs.energy + REST_ENERGY).coerceAtMost(100.0),
        )
        TrainKind.FITNESS -> needs.copy(
            hunger = (needs.hunger - FITNESS_HUNGER).coerceAtLeast(0.0),
            thirst = (needs.thirst - FITNESS_THIRST).coerceAtLeast(0.0),
        )
        TrainKind.BOOK -> needs
    }

    /** Aliento tras entrenar: gasta [FITNESS_STAMINA] (puede dejarlo agotado si quedaba justo). */
    fun applyStamina(stamina: StaminaState): StaminaState = StaminaRules.spend(stamina, FITNESS_STAMINA)

    /** Multiplicador del gasto de aliento al correr según el nivel de fitness. */
    fun drainFactor(fitnessLevel: Int): Double = 1.0 - FITNESS_DRAIN_PER_LEVEL * fitnessLevel.coerceIn(0, SkillRules.MAX_LEVEL)

    /** La habilidad que más falta: la de menos XP; con empate, la primera del enum. */
    fun bookTarget(skills: SurvivalSkills): Skill = Skill.entries.minByOrNull { skills.xpOf(it) } ?: Skill.COMBATE

    /** Paquete tras leer un libro, o null si no hay. */
    fun readBook(pack: SurvivalPack): SurvivalPack? = if (pack.libros > 0) pack.copy(libros = pack.libros - 1) else null
}

package ovh.gabrielhuav.pow.domain.models.map

// Estado de las puertas de edificio DURANTE la partida (el esquema solo trae el inicial). Con el modo
// supervivencia el jugador las cierra o barrica y los zombis desgastan la barricada. Ver 04_MAP_EXTERIOR.md.

/** [health] solo cuenta con la puerta barricada. */
data class DoorRuntime(val state: DoorState, val health: Double = 0.0)

/** Lo que el jugador puede hacer con una puerta desde el menú. */
enum class DoorAction { ENTER, OPEN, CLOSE, BARRICADE, DISMANTLE, LOCK, UNLOCK }

object DoorRules {
    const val BARRICADE_HEALTH = 100.0
    /** Daño por segundo de un zombi pegado a la barricada. */
    const val ZOMBIE_DAMAGE_PER_SECOND = 4.0
    const val ZOMBIE_REACH_METERS = 3.5
    const val BOARDS_PER_BARRICADE = 1
    /** Candado (t87, paso 5): la puerta con candado aguanta [LOCK_HEALTH] y los zombis la desgastan a [LOCK_ZOMBIE_FACTOR] de lo normal. */
    const val LOCK_HEALTH = 100.0
    const val LOCK_ZOMBIE_FACTOR = 0.5

    /** Acciones disponibles según el estado; BARRICADE solo si hay tablones. */
    fun actionsFor(state: DoorState, boards: Int, canEnter: Boolean, padlocks: Int = 0): List<DoorAction> = buildList {
        if (canEnter && state == DoorState.OPEN) add(DoorAction.ENTER)
        when (state) {
            DoorState.OPEN -> add(DoorAction.CLOSE)
            DoorState.CLOSED -> add(DoorAction.OPEN)
            DoorState.BARRICADED -> add(DoorAction.DISMANTLE)
            DoorState.LOCKED -> add(DoorAction.UNLOCK)
        }
        if (state != DoorState.BARRICADED && state != DoorState.LOCKED && boards >= BOARDS_PER_BARRICADE) add(DoorAction.BARRICADE)
        if ((state == DoorState.OPEN || state == DoorState.CLOSED) && padlocks >= 1) add(DoorAction.LOCK)
    }

    /** Aplica la acción; devuelve null si no corresponde (p. ej. barricadar sin tablones). */
    fun apply(action: DoorAction, current: DoorRuntime, boards: Int): Pair<DoorRuntime, Int>? = when (action) {
        DoorAction.ENTER, DoorAction.LOCK, DoorAction.UNLOCK -> null
        DoorAction.OPEN -> if (current.state == DoorState.CLOSED) DoorRuntime(DoorState.OPEN) to boards else null
        DoorAction.CLOSE -> if (current.state == DoorState.OPEN) DoorRuntime(DoorState.CLOSED) to boards else null
        DoorAction.BARRICADE ->
            if (current.state != DoorState.BARRICADED && boards >= BOARDS_PER_BARRICADE)
                DoorRuntime(DoorState.BARRICADED, BARRICADE_HEALTH) to (boards - BOARDS_PER_BARRICADE)
            else null
        // Desmontar devuelve el tablón solo si la barricada sigue entera a más de la mitad.
        DoorAction.DISMANTLE ->
            if (current.state == DoorState.BARRICADED)
                DoorRuntime(DoorState.CLOSED) to (boards + if (current.health > BARRICADE_HEALTH / 2) BOARDS_PER_BARRICADE else 0)
            else null
    }

    /**
     * Poner o quitar el candado (t87): poner gasta uno y deja la puerta LOCKED; quitarlo lo devuelve y la deja CLOSED.
     * Devuelve la puerta y los candados que quedan, o null si no corresponde.
     */
    fun applyLock(action: DoorAction, current: DoorRuntime, padlocks: Int): Pair<DoorRuntime, Int>? = when (action) {
        DoorAction.LOCK ->
            if ((current.state == DoorState.OPEN || current.state == DoorState.CLOSED) && padlocks >= 1) DoorRuntime(DoorState.LOCKED, LOCK_HEALTH) to (padlocks - 1) else null
        DoorAction.UNLOCK -> if (current.state == DoorState.LOCKED) DoorRuntime(DoorState.CLOSED) to (padlocks + 1) else null
        else -> null
    }

    /** Desgasta la barricada o el candado; al llegar a 0 se rompe (el candado se pierde) y la puerta queda ABIERTA. Otros estados no cambian. */
    fun damaged(current: DoorRuntime, zombiesAtDoor: Int, seconds: Double): DoorRuntime {
        if ((current.state != DoorState.BARRICADED && current.state != DoorState.LOCKED) || zombiesAtDoor <= 0 || !seconds.isFinite() || seconds <= 0.0) return current
        val health = current.health - zombiesAtDoor * ZOMBIE_DAMAGE_PER_SECOND * (if (current.state == DoorState.LOCKED) LOCK_ZOMBIE_FACTOR else 1.0) * seconds
        return if (health <= 0.0) DoorRuntime(DoorState.OPEN) else current.copy(health = health)
    }

    /** `id:ESTADO:vida;...` para el guardado (las puertas en su estado inicial no se guardan). */
    fun encode(doors: Map<String, DoorRuntime>): String =
        doors.entries.joinToString(";") { (id, d) -> "$id:${d.state.name}:${d.health.toInt()}" }

    /** Nunca lanza: lo ilegible se ignora (guardados viejos o editados a mano). */
    fun decode(text: String): Map<String, DoorRuntime> {
        if (text.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, DoorRuntime>()
        for (part in text.split(';')) {
            val fields = part.split(':')
            if (fields.size != 3) continue
            val state = DoorState.entries.firstOrNull { it.name == fields[1] } ?: continue
            val health = fields[2].toDoubleOrNull() ?: continue
            out[fields[0]] = DoorRuntime(state, health.coerceIn(0.0, BARRICADE_HEALTH))
        }
        return out
    }
}

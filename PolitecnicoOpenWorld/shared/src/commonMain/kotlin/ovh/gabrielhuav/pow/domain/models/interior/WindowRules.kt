package ovh.gabrielhuav.pow.domain.models.interior

// Ventanas de un interior procedural (t07): cerrada, rota o tapiada. Romperla hace ruido y puede cortar; una rota se
// tapia con un tablón. Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

/** Al final de la lista de valores: no insertar en medio. */
enum class WindowState { CERRADA, ROTA, TAPIADA, REFORZADA }

enum class WindowAction { ROMPER, TAPIAR, NINGUNA, REFORZAR }

object WindowRules {
    const val BOARDS_TO_COVER = 1
    const val PLATES_TO_REINFORCE = 1
    /** Probabilidad por ciclo de presión de que los zombis de afuera tumben una ventana solo tapiada (la reforzada aguanta). */
    const val BREACH_CHANCE = 0.2
    /** Vida que quita un corte con los cristales (se aplica con el piso de vida del modo). */
    const val CUT_DAMAGE = 6.0
    /** Probabilidad de que romper la ventana a mano corte al jugador (y lo haga sangrar). */
    const val CUT_CHANCE = 0.5

    /** Qué hace X sobre una ventana en [state] con [boards] tablones en la mochila. */
    fun actionFor(state: WindowState, boards: Int, plates: Int = 0): WindowAction = when (state) {
        WindowState.CERRADA -> WindowAction.ROMPER
        WindowState.ROTA -> if (boards >= BOARDS_TO_COVER) WindowAction.TAPIAR else WindowAction.NINGUNA
        WindowState.TAPIADA -> if (plates >= PLATES_TO_REINFORCE) WindowAction.REFORZAR else WindowAction.NINGUNA
        WindowState.REFORZADA -> WindowAction.NINGUNA
    }

    fun after(state: WindowState, action: WindowAction): WindowState = when (action) {
        WindowAction.ROMPER -> if (state == WindowState.CERRADA) WindowState.ROTA else state
        WindowAction.TAPIAR -> if (state == WindowState.ROTA) WindowState.TAPIADA else state
        WindowAction.REFORZAR -> if (state == WindowState.TAPIADA) WindowState.REFORZADA else state
        WindowAction.NINGUNA -> state
    }

    fun cutsOnBreak(roll: Double): Boolean = roll < CUT_CHANCE

    /** Cada cuánto entra un zombi por cada ventana rota mientras el jugador está dentro (ms). */
    const val PRESSURE_MS = 12_000L
    /** Zombis dentro a la vez: con tantos o más ya no entran más. */
    const val MAX_INSIDE = 5

    /** ¿Entra un zombi ahora? Solo por ventanas rotas (las tapiadas y las cerradas aguantan) y con lugar dentro. */
    fun letsZombieIn(state: WindowState, zombiesInside: Int): Boolean = state == WindowState.ROTA && zombiesInside < MAX_INSIDE

    /** Estado tras un ciclo de presión: la tapiada puede ceder ([roll] < [BREACH_CHANCE]); cerrada, rota y reforzada no cambian. */
    fun afterPressure(state: WindowState, roll: Double): WindowState =
        if (state == WindowState.TAPIADA && roll < BREACH_CHANCE) WindowState.ROTA else state

    /** Celda (col, fila) justo dentro de la ventana [window], donde aparece el zombi. */
    fun entryCell(window: InteriorWindow, cols: Int): Pair<Int, Int> = when (window.side) {
        WallSide.NORTE -> window.index to 1
        WallSide.ESTE -> cols - 2 to window.index
        WallSide.OESTE -> 1 to window.index
    }

    /** Clave estable de la ventana [index] del edificio [buildingId] (índice en `InteriorLayout.windows`). */
    fun key(buildingId: Long, index: Int): String = "ventana_${buildingId}_$index"
}

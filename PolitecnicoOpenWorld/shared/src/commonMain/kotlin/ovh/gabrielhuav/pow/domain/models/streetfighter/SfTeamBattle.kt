package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.random.Random

/** 3 contra 3 por eliminación. El orden elegido es el orden de entrada; cada equipo es único. */
data class SfTeamBattle(
    val playerTeam: List<SfFighterId>,
    val cpuTeam: List<SfFighterId>,
    val playerIndex: Int = 0,
    val cpuIndex: Int = 0,
) {
    init {
        require(validTeam(playerTeam) && validTeam(cpuTeam))
        require(playerIndex in 0..SIZE && cpuIndex in 0..SIZE)
        require(playerIndex < SIZE || cpuIndex < SIZE)
    }
    val finished: Boolean get() = playerIndex == SIZE || cpuIndex == SIZE
    val winner: Int? get() = when { cpuIndex == SIZE -> 0; playerIndex == SIZE -> 1; else -> null }
    val playerRemaining: Int get() = SIZE - playerIndex
    val cpuRemaining: Int get() = SIZE - cpuIndex
    val leg: Int get() = playerIndex + cpuIndex + 1
    val playerId: SfFighterId get() = playerTeam[playerIndex.coerceAtMost(SIZE - 1)]
    val cpuId: SfFighterId get() = cpuTeam[cpuIndex.coerceAtMost(SIZE - 1)]

    fun eliminate(winnerIndex: Int): SfTeamBattle {
        require(winnerIndex in 0..1)
        if (finished) return this
        return if (winnerIndex == 0) copy(cpuIndex = cpuIndex + 1) else copy(playerIndex = playerIndex + 1)
    }
    fun rematch(): SfTeamBattle = copy(playerIndex = 0, cpuIndex = 0)

    companion object {
        const val SIZE = 3
        fun validTeam(team: List<SfFighterId>): Boolean = team.size == SIZE &&
            team.distinct().size == SIZE && team.all { it in SfArcadeLadder.ALL_PARTICIPANTS }
        fun randomTeam(rng: Random = Random.Default): List<SfFighterId> =
            SfArcadeLadder.ALL_PARTICIPANTS.shuffled(rng).take(SIZE)
    }
}

package ovh.gabrielhuav.pow.features.streetfighter.viewmodel

import ovh.gabrielhuav.pow.domain.models.streetfighter.SfCpuDifficulty
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfStageCatalog
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfTeamBattle

/** Modalidad offline independiente del arcade: todo el elenco disponible, sin desbloqueos. */
fun StreetFighterViewModel.startTeamBattle(
    playerTeam: List<SfFighterId>, cpuTeam: List<SfFighterId>,
    difficulty: SfCpuDifficulty = SfCpuDifficulty.NORMAL,
) {
    if (isOnline || !SfTeamBattle.validTeam(playerTeam) || !SfTeamBattle.validTeam(cpuTeam)) return
    prepareTeamLeg(SfTeamBattle(playerTeam.toList(), cpuTeam.toList()), difficulty, first = true)
}

/** Reinicia relojes y entidades; únicamente el superviviente conserva vida y súper. */
internal fun StreetFighterViewModel.prepareTeamLeg(
    team: SfTeamBattle, difficulty: SfCpuDifficulty = _state.value.cpuDifficulty,
    first: Boolean = false,
) {
    if (team.finished) return
    val previous = _state.value
    val oldTeam = previous.teamBattle
    // El índice cambia al resolverse el KO, pero el peleador en pantalla aún es el saliente.
    val keepPlayer = !first && previous.winnerIndex == 0 && oldTeam != null
    val keepCpu = !first && previous.winnerIndex == 1 && oldTeam != null
    resetInternals()
    roundIntroUntilMs = StreetFighterViewModel.ROUND_INTRO_MS
    val base = StreetFighterState()
    val player = if (keepPlayer) base.player.copy(id = previous.player.id,
        hitPoints = previous.player.hitPoints.coerceAtLeast(1), superMeter = previous.player.superMeter,
        metamorphosed = previous.player.metamorphosed) else base.player.copy(id = team.playerId)
    val cpu = if (keepCpu) base.cpu.copy(id = previous.cpu.id,
        hitPoints = previous.cpu.hitPoints.coerceAtLeast(1), superMeter = previous.cpu.superMeter,
        metamorphosed = previous.cpu.metamorphosed) else base.cpu.copy(id = team.cpuId)
    dispHp0 = player.hitPoints.toFloat()
    dispHp1 = cpu.hitPoints.toFloat()
    _state.value = base.copy(player = player, cpu = cpu,
        teamBattle = team, inCharacterSelect = false, showRoundIntro = true,
        roundNumber = team.leg, playerRoundWins = team.cpuIndex, cpuRoundWins = team.playerIndex,
        playerScore = if (first) 0 else previous.playerScore,
        cpuScore = if (first) 0 else previous.cpuScore,
        displayHp0 = player.hitPoints.toFloat(), displayHp1 = cpu.hitPoints.toFloat(),
        cpuDifficulty = difficulty,
        arcadeMapFile = if (first) SfStageCatalog.mapForRival(team.cpuId, difficulty) else previous.arcadeMapFile,
    )
}

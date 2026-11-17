package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.*
import kotlin.random.Random

class SfTeamBattleTest {
    private val player = SfArcadeLadder.ALL_PARTICIPANTS.take(3)
    private val cpu = SfArcadeLadder.ALL_PARTICIPANTS.drop(3).take(3)
    @Test fun `se requieren tres personajes distintos del roster por equipo`() {
        assertFalse(SfTeamBattle.validTeam(emptyList()))
        assertFalse(SfTeamBattle.validTeam(player.take(2)))
        assertFalse(SfTeamBattle.validTeam(player + cpu.first()))
        assertFalse(SfTeamBattle.validTeam(listOf(player[0], player[0], player[1])))
        assertFalse(SfTeamBattle.validTeam(listOf(SfFighterId.LAZARO, player[0], player[1])))
        assertFailsWith<IllegalArgumentException> { SfTeamBattle(player.take(2), cpu) }
    }
    @Test fun `los equipos pueden enfrentarse en espejo`() {
        val team = SfTeamBattle(player, player)
        assertEquals(team.playerId, team.cpuId)
        assertEquals(3, team.playerRemaining)
        assertEquals(3, team.cpuRemaining)
    }
    @Test fun `ganar un duelo elimina solo al rival y mantiene el orden elegido`() {
        val next = SfTeamBattle(player, cpu).eliminate(0)
        assertEquals(player[0], next.playerId)
        assertEquals(cpu[1], next.cpuId)
        assertEquals(2, next.cpuRemaining)
        assertEquals(3, next.playerRemaining)
        assertFalse(next.finished)
    }
    @Test fun `dos victorias no terminan el combate por equipos`() {
        val next = SfTeamBattle(player, cpu).eliminate(0).eliminate(0)
        assertFalse(next.finished)
        assertEquals(null, next.winner)
        assertEquals(cpu[2], next.cpuId)
    }
    @Test fun `la tercera eliminacion decide al ganador sin acceder fuera del equipo`() {
        val next = SfTeamBattle(player, cpu).eliminate(0).eliminate(0).eliminate(0)
        assertTrue(next.finished)
        assertEquals(0, next.winner)
        assertEquals(0, next.cpuRemaining)
        assertEquals(cpu[2], next.cpuId)
        assertEquals(next, next.eliminate(1))
    }
    @Test fun `la derrota del jugador consume sus relevos y da la victoria a CPU`() {
        val next = SfTeamBattle(player, cpu).eliminate(1).eliminate(1).eliminate(1)
        assertEquals(1, next.winner)
        assertEquals(0, next.playerRemaining)
        assertEquals(3, next.cpuRemaining)
    }
    @Test fun `un combate parejo dura cinco duelos como maximo`() {
        val next = SfTeamBattle(player, cpu).eliminate(0).eliminate(1).eliminate(0).eliminate(1)
        assertEquals(5, next.leg)
        assertFalse(next.finished)
        assertTrue(next.eliminate(0).finished)
        assertTrue(next.eliminate(1).finished)
    }
    @Test fun `la revancha conserva los equipos completos y reinicia los indices`() {
        val original = SfTeamBattle(player, cpu)
        assertEquals(original, original.eliminate(1).eliminate(0).rematch())
    }
    @Test fun `el sorteo devuelve tres distintos y puede alcanzar a todo el elenco`() {
        val seen = mutableSetOf<SfFighterId>()
        repeat(200) { seed ->
            val team = SfTeamBattle.randomTeam(Random(seed))
            assertTrue(SfTeamBattle.validTeam(team))
            seen.addAll(team)
        }
        assertEquals(SfArcadeLadder.ALL_PARTICIPANTS.toSet(), seen)
        assertEquals(SfTeamBattle.randomTeam(Random(9)), SfTeamBattle.randomTeam(Random(9)))
    }
    @Test fun `se rechazan indices y ganadores invalidos`() {
        assertFailsWith<IllegalArgumentException> { SfTeamBattle(player, cpu, playerIndex = 4) }
        assertFailsWith<IllegalArgumentException> { SfTeamBattle(player, cpu, playerIndex = 3, cpuIndex = 3) }
        assertFailsWith<IllegalArgumentException> { SfTeamBattle(player, cpu).eliminate(2) }
    }
}

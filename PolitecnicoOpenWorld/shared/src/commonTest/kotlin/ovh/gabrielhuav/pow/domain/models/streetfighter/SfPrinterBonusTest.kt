package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ovh.gabrielhuav.pow.features.streetfighter.viewmodel.SfArcadeSession

class SfPrinterBonusTest {
    @Test fun `el bonus se activa solo tras la segunda victoria`() {
        (1..15).forEach { assertEquals(it == 2, SfPrinterBonus.followsVictory(it)) }
    }
    @Test fun `la introduccion no consume los treinta segundos jugables`() {
        val bonus = SfPrinterBonus().advance(1500)
        assertEquals(500L, bonus.introMs)
        assertEquals(30000L, bonus.remainingMs)
        assertEquals(29500L, bonus.advance(1000).remainingMs)
        assertEquals(bonus, bonus.hit(100, 1500))
    }
    @Test fun `destruir premia solo el dano restante y el tiempo una vez`() {
        val bonus = SfPrinterBonus(introMs = 0).advance(10000).hit(450, 10000)
        val destroyed = bonus.hit(100, 10100)
        assertTrue(destroyed.destroyed)
        assertTrue(destroyed.finished)
        assertEquals(12000, destroyed.score)
        assertEquals(2, destroyed.hits)
        assertEquals(destroyed, destroyed.hit(100, 10200))
        assertEquals(destroyed, destroyed.advance(1000))
    }
    @Test fun `agotar el reloj conserva puntos y bloquea golpes posteriores`() {
        val bonus = SfPrinterBonus(introMs = 0).hit(25, 100).advance(30000)
        assertTrue(bonus.finished)
        assertFalse(bonus.destroyed)
        assertEquals(250, bonus.score)
        assertEquals(0, bonus.seconds)
        assertEquals(bonus, bonus.hit(100, 31000))
    }
    @Test fun `los estados visuales avanzan hasta los restos`() {
        assertEquals(0, SfPrinterBonus().damageFrame)
        val stages = listOf(499, 400, 300, 200, 100, 1, 0).map {
            SfPrinterBonus(health = it).damageFrame
        }
        assertEquals(stages.sorted(), stages)
        assertEquals(7, stages.last())
    }
    @Test fun `el guardado comun conserva el bonus pendiente y acepta sesiones antiguas`() {
        val session = SfArcadeSession("ROBOT", 3, 15, listOf("PRANKEDY"), null, 0, 0,
            "NORMAL", printerBonusPending = true)
        assertEquals(session, Json.decodeFromString<SfArcadeSession>(Json.encodeToString(session)))
        val legacy = Json.encodeToString(session).replace(",\"printerBonusPending\":true", "")
        assertFalse(Json.decodeFromString<SfArcadeSession>(legacy).printerBonusPending)
    }
}

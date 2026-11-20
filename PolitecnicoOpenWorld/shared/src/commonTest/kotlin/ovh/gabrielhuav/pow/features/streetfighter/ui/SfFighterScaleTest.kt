package ovh.gabrielhuav.pow.features.streetfighter.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterId
import ovh.gabrielhuav.pow.domain.models.streetfighter.SfFighterState

class SfFighterScaleTest {
    @Test
    fun `el Titan conserva el zoom en todos los estados y alturas de pose`() {
        SfFighterState.entries.forEach { state ->
            listOf(40, 65, 80, 100, 140, 180).forEach { height ->
                assertEquals(1f, fighterContentScale(SfFighterId.TITAN_BIENESTAR, state, height, false))
                assertEquals(1f, fighterContentScale(SfFighterId.TITAN_BIENESTAR, state, height, true))
            }
        }
    }

    @Test
    fun `los atlas anteriores conservan su ajuste de escala`() {
        assertEquals(1.25f, fighterContentScale(SfFighterId.ESCOMBOY, SfFighterState.IDLE, 65, false))
        assertEquals(1f, fighterContentScale(SfFighterId.ESCOMBOY, SfFighterState.CROUCH, 65, false))
        assertEquals(1f, fighterContentScale(SfFighterId.ESCOMBOY, SfFighterState.IDLE, 140, true))
    }
}

package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals

class SfFramePacingTest {
    @Test
    fun `el procesamiento no se suma al intervalo en equipos normales ni modestos`() {
        assertEquals(8L, SfFramePacing.waitMs(16, 1000, 1008))
        assertEquals(21L, SfFramePacing.waitMs(33, 1000, 1012))
    }

    @Test
    fun `un atasco cede el hilo sin agregar otra pausa completa`() {
        assertEquals(1L, SfFramePacing.waitMs(16, 1000, 1050))
        assertEquals(16L, SfFramePacing.waitMs(16, 1000, 1000))
        assertEquals(16L, SfFramePacing.waitMs(16, 1000, 999))
    }
}

package ovh.gabrielhuav.pow.domain.models.survival

/**
 * Cerrojo de avisos de estado (poca gasolina, frío...): avisa UNA vez al cumplirse la condición y no vuelve a avisar
 * hasta que la situación se haya despejado de verdad ([clear]), para que un valor que oscila en el borde no repita el aviso.
 */
class NoticeLatch {
    private var armed = true

    fun fire(condition: Boolean, clear: Boolean): Boolean {
        if (armed && condition) {
            armed = false
            return true
        }
        if (clear) armed = true
        return false
    }
}

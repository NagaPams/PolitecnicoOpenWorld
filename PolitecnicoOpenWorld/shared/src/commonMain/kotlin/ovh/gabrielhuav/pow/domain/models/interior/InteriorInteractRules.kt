package ovh.gabrielhuav.pow.domain.models.interior

// A qué responde X dentro de un interior generado cuando hay una ventana y un mueble al alcance: gana lo MÁS CERCANO
// (antes ganaba siempre la ventana y no se podía usar el grifo ni registrar un mueble pegado a una ventana).
// Reglas puras. Ver README for IAS/MUNDO/04_MAP_EXTERIOR.md.

object InteriorInteractRules {
    /** ¿X debe actuar sobre la ventana? Distancias en celdas; null = no hay nada de ese tipo al alcance. */
    fun preferWindow(windowDistance: Float?, furnitureDistance: Float?): Boolean {
        if (windowDistance == null) return false
        if (furnitureDistance == null) return true
        return windowDistance < furnitureDistance
    }
}
